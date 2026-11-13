// ⑤ 웹 회고 일조 분석 (11/11): 걸은 구간마다 "그 시각에 햇볕을 받았나"
//  - 해 위치: 날짜·시각·위경도로 천문 공식 계산 (utils/sun.js) → 방향(방위각)과 높이(고도)
//  - 산 그림자: 그 자리에서 해 쪽으로 DEM을 따라가며, 해까지 가는 직선보다 땅이 높으면 가려진 것
//  - 숲 그늘: 임상도 숲 구역 안이면 나무 그늘
//  - 건물 그림자는 아직 안 봄 (건물 높이 자료를 따로 읽어야 해서)

import { elevationAt } from './terrain'

const METERS_PER_DEGREE_LAT = 110540
const METERS_PER_DEGREE_LNG = 111320
const RAY_STEP_M = 40        // 해 쪽으로 이 간격마다 땅 높이를 확인
const RAY_MAX_M = 2000       // 이 거리까지만 (서울 산 크기면 충분)
const EYE_M = 2              // 사람 키쯤 위에서 봄
const TREE_SHADE_RATIO = 0.5 // 구간의 반 넘게 숲 안이면 숲 그늘

/** 일조 상태 목록 (정해진 값) */
export const SUN_STATES = [
  { id: 'sun', name: '햇볕', color: '#F2B705' },
  { id: 'tree', name: '숲 그늘', color: '#2E8B57' },
  { id: 'hill', name: '산 그림자', color: '#6B7A99' },
  { id: 'night', name: '해 진 뒤', color: '#2B3A67' },
]

function stateOf(id) {
  for (const state of SUN_STATES) {
    if (state.id === id) return state
  }
  return SUN_STATES[0]
}

function bboxOf(ring) {
  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity
  for (const [x, y] of ring) {
    if (x < minX) minX = x
    if (x > maxX) maxX = x
    if (y < minY) minY = y
    if (y > maxY) maxY = y
  }
  return [minX, minY, maxX, maxY]
}

function isInRings(lng, lat, rings) {
  let inside = false
  for (const ring of rings) {
    for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
      const [x1, y1] = ring[i]
      const [x2, y2] = ring[j]
      if ((y1 > lat) !== (y2 > lat) && lng < ((x2 - x1) * (lat - y1)) / (y2 - y1) + x1) inside = !inside
    }
  }
  return inside
}

/**
 * 숲 비율 읽기 함수 만들기: (점들) → 그중 숲 구역 안에 든 비율 0~1
 * forest: forest-seoul.json. 구역마다 사각 범위를 미리 계산해 둠
 */
export function makeGreenReader(forest) {
  const polygons = []
  for (const feature of forest.features) {
    for (const polygon of feature.geometry.coordinates) polygons.push({ box: bboxOf(polygon[0]), rings: polygon })
  }
  const isForest = (lng, lat) => {
    for (const item of polygons) {
      if (lng < item.box[0] || lng > item.box[2] || lat < item.box[1] || lat > item.box[3]) continue
      if (isInRings(lng, lat, item.rings)) return true
    }
    return false
  }
  return (points) => {
    let count = 0
    for (const [lng, lat] of points) {
      if (isForest(lng, lat)) count++
    }
    return points.length > 0 ? count / points.length : 0
  }
}

/** 이 자리에서 해가 산에 가리는가. azimuth: 북쪽 0도부터 시계 방향, altitude: 지평선 위 각도 */
export async function isBehindHill(lng, lat, azimuth, altitude) {
  const ground = await elevationAt(lng, lat)
  if (ground === null) return false
  const direction = (azimuth * Math.PI) / 180
  const rise = Math.tan((altitude * Math.PI) / 180) // 1m 나아갈 때 시선이 올라가는 높이
  const lngScale = METERS_PER_DEGREE_LNG * Math.cos((lat * Math.PI) / 180)
  for (let distance = RAY_STEP_M; distance <= RAY_MAX_M; distance += RAY_STEP_M) {
    const height = await elevationAt(lng + (Math.sin(direction) * distance) / lngScale, lat + (Math.cos(direction) * distance) / METERS_PER_DEGREE_LAT)
    if (height !== null && height > ground + EYE_M + distance * rise) return true
  }
  return false
}

/**
 * 구간의 일조 상태. sun: { altitude, azimuth } (그 구간 가운데 시각), green: 숲 비율(없으면 null)
 * 돌려줌: { state: SUN_STATES 중 하나, altitude, azimuth }
 */
export async function sunStateOf(point, sun, green) {
  let id = 'sun'
  if (sun.altitude <= 0) id = 'night'
  else if (await isBehindHill(point[0], point[1], sun.azimuth, sun.altitude)) id = 'hill'
  else if (green !== null && green >= TREE_SHADE_RATIO) id = 'tree'
  return { state: stateOf(id), altitude: sun.altitude, azimuth: sun.azimuth }
}
