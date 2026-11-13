// ⑤ 지도 건물 자료 읽기 (11/11, 11/13 3D 모델 코드를 빼고 이 부분만 남김)
// 지도 벡터 타일(OpenStreetMap) 건물 층에서 건물 바닥 모양·높이를 읽음 → 유니티 3D 지도 자료(unityExport.js)가 씀
// 건물 높이: 지도 자료에 높이가 없으면 기본 5m로 와요. 그런 건물은 2~4층(6~12m)으로 봐요 (실제 높이 아님)

import { VectorTile } from '@mapbox/vector-tile' // 벡터 타일(지도 조각) 읽기
import { PbfReader } from 'pbf'                  // 벡터 타일의 압축 형식 풀기

const TILE_ZOOM = 14
const FLOOR_M = 3            // 한 층 높이
const UNKNOWN_HEIGHT_M = 5.5 // 이 높이 이하면 높이 자료가 없는 건물로 봄
const MAX_BUILDINGS = 6000

// 0~1 사이의 정해진 난수
function seeded(a, b) {
  const x = Math.sin(a * 127.1 + b * 311.7) * 43758.5453
  return x - Math.floor(x)
}

// ============================== 건물 자료 읽기 (벡터 타일)
function tileXY(lng, lat) {
  const count = Math.pow(2, TILE_ZOOM)
  const rad = (lat * Math.PI) / 180
  return [Math.floor(((lng + 180) / 360) * count), Math.floor(((1 - Math.log(Math.tan(rad) + 1 / Math.cos(rad)) / Math.PI) / 2) * count)]
}

/**
 * 범위 box [서, 남, 동, 북] 안의 건물 읽기. tileUrl: "https://…/{z}/{x}/{y}.pbf"
 * isWanted(경도, 위도): 이 건물을 쓸지 (걸은 길 가까운지)
 * maxCount: 최대 몇 채까지 (안 주면 MAX_BUILDINGS). 유니티로 넘길 때는 넓은 범위를 다 받으려고 크게 줌
 * 돌려줌: [{ rings: [[경도, 위도], …][] (첫 고리가 바깥), height, minHeight }]
 */
export async function loadBuildings(tileUrl, box, isWanted, maxCount) {
  const limit = maxCount || MAX_BUILDINGS
  const [x0, y1] = tileXY(box[0], box[1])
  const [x1, y0] = tileXY(box[2], box[3])
  const list = []
  for (let x = x0; x <= x1; x++) {
    for (let y = y0; y <= y1; y++) {
      const layer = await fetchBuildingLayer(tileUrl.replace('{z}', TILE_ZOOM).replace('{x}', x).replace('{y}', y))
      if (!layer) continue
      for (let i = 0; i < layer.length && list.length < limit; i++) addFeature(list, layer.feature(i), x, y, isWanted)
    }
  }
  return list
}

async function fetchBuildingLayer(url) {
  try {
    const response = await fetch(url)
    if (!response.ok) return null
    return new VectorTile(new PbfReader(new Uint8Array(await response.arrayBuffer()))).layers.building || null
  } catch (e) {
    return null // 못 받은 조각은 건너뜀 (그 자리는 지도 건물이 그대로 보임)
  }
}

function addFeature(list, feature, x, y, isWanted) {
  if (feature.properties.hide_3d) return
  const geometry = feature.toGeoJSON(x, y, TILE_ZOOM).geometry
  const polygons = geometry.type === 'Polygon' ? [geometry.coordinates] : geometry.type === 'MultiPolygon' ? geometry.coordinates : []
  for (const rings of polygons) {
    if (rings[0].length < 4 || !isWanted(rings[0][0][0], rings[0][0][1])) continue
    let height = Number(feature.properties.render_height) || 0
    if (height <= UNKNOWN_HEIGHT_M) height = FLOOR_M * (2 + Math.floor(seeded(rings[0][0][0] * 5000, rings[0][0][1] * 5000) * 3)) // 높이 모름 → 2~4층
    list.push({ rings: rings, height: height, minHeight: Number(feature.properties.render_min_height) || 0 })
  }
}
