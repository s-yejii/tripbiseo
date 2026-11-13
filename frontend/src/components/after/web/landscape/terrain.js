// ⑤ 웹 회고 풍경 분석: DEM(지형 높이) 읽기 (11/11)
// 지도가 이미 쓰는 AWS Terrarium 높이 그림 타일을 직접 받아서, 한 점의 높이(m)를 픽셀 색으로 계산해요
// 높이 = (빨강 × 256 + 초록 + 파랑 ÷ 256) − 32768   (Terrarium 형식)
// 나중에 국토지리정보원 5m DEM으로 바꾸면 DEM_URL만 우리 서버 주소로 바꾸면 돼요

const DEM_URL = 'https://s3.amazonaws.com/elevation-tiles-prod/terrarium/' // {z}/{x}/{y}.png
const DEM_ZOOM = 14          // 이 확대 수준이면 한 픽셀이 서울에서 약 7.6m
const TILE_SIZE = 256
const tileCache = new Map()  // "z/x/y" → Promise<픽셀 자료 또는 null>
const loadedTiles = new Map() // "z/x/y" → 다 받은 픽셀 자료 (기다리지 않고 바로 읽을 때)

// 경도·위도 → 타일 번호(소수) (웹 지도에서 쓰는 메르카토르 투영)
function toTileXY(lng, lat, zoom) {
  const count = Math.pow(2, zoom)
  const latRad = (lat * Math.PI) / 180
  const x = ((lng + 180) / 360) * count
  const y = ((1 - Math.log(Math.tan(latRad) + 1 / Math.cos(latRad)) / Math.PI) / 2) * count
  return { x: x, y: y }
}

// 타일 그림을 받아 픽셀 색을 읽을 수 있게 (같은 타일은 한 번만 받음)
function loadTile(zoom, x, y) {
  const key = zoom + '/' + x + '/' + y
  if (!tileCache.has(key)) tileCache.set(key, fetchTile(DEM_URL + key + '.png'))
  return tileCache.get(key)
}

async function fetchTile(url) {
  try {
    const response = await fetch(url)
    if (!response.ok) return null
    const bitmap = await createImageBitmap(await response.blob()) // 받은 그림 파일을 그림으로 읽기
    const canvas = document.createElement('canvas')
    canvas.width = TILE_SIZE
    canvas.height = TILE_SIZE
    const ctx = canvas.getContext('2d', { willReadFrequently: true })
    ctx.drawImage(bitmap, 0, 0)
    const pixels = ctx.getImageData(0, 0, TILE_SIZE, TILE_SIZE).data
    loadedTiles.set(url.slice(DEM_URL.length, -4), pixels)
    return pixels
  } catch (e) {
    return null // 인터넷이 없거나 막히면 높이 없이 분석
  }
}

/** 한 점의 높이(m). 못 읽으면 null. zoom을 주면 그 확대 수준의 자료로 (넓은 범위는 12처럼 낮게 → 받는 양이 적음) */
export async function elevationAt(lng, lat, zoom) {
  const level = zoom || DEM_ZOOM
  const at = toTileXY(lng, lat, level)
  const tileX = Math.floor(at.x)
  const tileY = Math.floor(at.y)
  const pixels = await loadTile(level, tileX, tileY)
  if (!pixels) return null
  const px = Math.min(TILE_SIZE - 1, Math.floor((at.x - tileX) * TILE_SIZE))
  const py = Math.min(TILE_SIZE - 1, Math.floor((at.y - tileY) * TILE_SIZE))
  const i = (py * TILE_SIZE + px) * 4
  return pixels[i] * 256 + pixels[i + 1] + pixels[i + 2] / 256 - 32768
}

function heightOf(pixels, px, py) {
  const i = (Math.min(TILE_SIZE - 1, py) * TILE_SIZE + Math.min(TILE_SIZE - 1, px)) * 4
  return pixels[i] * 256 + pixels[i + 1] + pixels[i + 2] / 256 - 32768
}

/**
 * 한 점의 높이(m)를 기다리지 않고 바로 (재생 중 카메라 높이용). 그 조각을 아직 안 받았으면 null
 * 둘레 네 픽셀을 거리 비율로 섞어서(쌍선형 보간) 계단처럼 튀지 않게
 */
export function elevationNow(lng, lat) {
  const at = toTileXY(lng, lat, DEM_ZOOM)
  const tileX = Math.floor(at.x)
  const tileY = Math.floor(at.y)
  const pixels = loadedTiles.get(DEM_ZOOM + '/' + tileX + '/' + tileY)
  if (!pixels) return null
  const fx = (at.x - tileX) * TILE_SIZE - 0.5
  const fy = (at.y - tileY) * TILE_SIZE - 0.5
  const px = Math.max(0, Math.floor(fx))
  const py = Math.max(0, Math.floor(fy))
  const rx = Math.max(0, fx - px)
  const ry = Math.max(0, fy - py)
  const upper = heightOf(pixels, px, py) * (1 - rx) + heightOf(pixels, px + 1, py) * rx
  const lower = heightOf(pixels, px, py + 1) * (1 - rx) + heightOf(pixels, px + 1, py + 1) * rx
  return upper * (1 - ry) + lower * ry
}

/** 여러 점의 높이 (순서대로). 못 읽은 점은 null */
export async function elevationsOf(points) {
  const list = []
  for (const point of points) list.push(await elevationAt(point[0], point[1]))
  return list
}
