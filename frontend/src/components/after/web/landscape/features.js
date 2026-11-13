// ⑤ 웹 회고 풍경 분석 2단계: 구간마다 공간 자료를 겹쳐 특징값 계산 (11/11)
// 지금 쓰는 자료: GPS(길이·시간·속도), DEM(높이·경사), 근처 장소(② GET /api/places/nearby, PostGIS 반경 검색)
// + 숲 비율(임상도 숲 구역 안에 든 비율, layers.green), 일조(layers.sun). 물 비율·NDVI·가로수는 자료가 없으면 null로 두고 분류에서 안 씀

import { get } from '@/api/http'
import { distanceMeters } from '@/utils/geo'
import { elevationsOf } from './terrain'
import { middleOf } from './segments'

const SAMPLE_STEP_M = 25        // 걸은 구간 높이를 이 간격으로 읽음
const SMOOTH_HALF = 3           // 높이를 앞뒤 3점(약 75m)씩 평균 내서 흔들림을 줄임
const CLIMB_STEP_M = 2          // 이만큼 넘게 바뀌어야 오르막·내리막으로 셈
const MOVE_PLACE_RADIUS_M = 100 // 걸은 구간 가운데에서 이 반경 안 장소
const STAY_PLACE_RADIUS_M = 80  // 머문 곳에서 이 반경 안 장소
const placeCache = new Map()    // 반올림한 좌표 → Promise<장소 목록>

/**
 * 구간 하나의 특징값
 * layers: 나중에 넣을 자료 읽기 함수들 { green(points), water(points), ndvi(points), trees(segment) } (없으면 빈 객체)
 */
export async function measureSegment(segment, layers) {
  const features = { minutes: segment.minutes, lengthM: Math.round(segment.lengthM), speedKmh: 0,
    gainM: null, lossM: null, avgSlope: null, places: [], sun: null, green: null, water: null, ndvi: null, trees: null }
  if (segment.kind === 'move' && segment.minutes > 0) features.speedKmh = (segment.lengthM / 1000) / (segment.minutes / 60)
  if (segment.kind === 'move') Object.assign(features, await measureSlope(segment.points))
  features.places = await nearbyPlaces(middleOf(segment), segment.kind === 'stay' ? STAY_PLACE_RADIUS_M : MOVE_PLACE_RADIUS_M)
  await addLayerValues(features, segment, layers || {})
  return features
}

// ============================== 높이·경사 (DEM)
// 선을 SAMPLE_STEP_M마다 촘촘히 → 점마다 높이 → 오른 높이·내린 높이·경사
async function measureSlope(points) {
  const samples = densify(points, SAMPLE_STEP_M)
  const heights = smooth(await elevationsOf(samples.map((sample) => sample.point)))
  const distance = samples[samples.length - 1].distance
  if (distance === 0 || heights.length === 0) return {}
  const climb = countClimb(heights)
  return { gainM: Math.round(climb.gain), lossM: Math.round(climb.loss), avgSlope: (climb.gain + climb.loss) / distance }
}

// DEM은 도심에서 건물·다리 때문에 몇 m씩 흔들려서, 앞뒤 점과 평균을 내 부드럽게 (이동 평균)
// 못 읽은 점(null)은 빼고 평균. 모두 못 읽었으면 빈 배열
function smooth(heights) {
  const list = []
  for (let i = 0; i < heights.length; i++) {
    let sum = 0, count = 0
    for (let j = i - SMOOTH_HALF; j <= i + SMOOTH_HALF; j++) {
      if (j >= 0 && j < heights.length && heights[j] !== null) {
        sum += heights[j]
        count++
      }
    }
    if (count > 0) list.push(sum / count)
  }
  return list
}

// 오른·내린 높이: 기준 높이에서 CLIMB_STEP_M 넘게 바뀔 때만 셈 (작은 흔들림은 무시)
// 예: 20 → 21 → 20.5 → 23 이면 기준 20에서 23으로 3m 오름 한 번
function countClimb(heights) {
  let gain = 0, loss = 0
  let base = heights[0]
  for (const height of heights) {
    if (height - base >= CLIMB_STEP_M) {
      gain += height - base
      base = height
    } else if (base - height >= CLIMB_STEP_M) {
      loss += base - height
      base = height
    }
  }
  return { gain: gain, loss: loss }
}

// 선 위에 step(m)마다 점 찍기: [{ point: [경도, 위도], distance: 처음부터 m }]
function densify(points, step) {
  const list = [{ point: points[0], distance: 0 }]
  let total = 0
  for (let i = 1; i < points.length; i++) {
    const [lng1, lat1] = points[i - 1]
    const [lng2, lat2] = points[i]
    const length = distanceMeters(lat1, lng1, lat2, lng2)
    for (let walked = step; walked < length; walked += step) {
      const ratio = walked / length
      list.push({ point: [lng1 + (lng2 - lng1) * ratio, lat1 + (lat2 - lat1) * ratio], distance: total + walked })
    }
    total += length
    list.push({ point: points[i], distance: total })
  }
  return list
}

// ============================== 근처 장소 (② 장소 API: DB에서 PostGIS로 반경 검색)
// 같은 자리(약 10m)는 한 번만 물어봄
function nearbyPlaces(point, radius) {
  const key = point[0].toFixed(4) + ',' + point[1].toFixed(4) + ',' + radius
  if (!placeCache.has(key)) {
    const url = '/places/nearby?lat=' + point[1] + '&lng=' + point[0] + '&radius=' + radius
    placeCache.set(key, get(url).then(toPlaceList).catch(() => []))
  }
  return placeCache.get(key)
}

function toPlaceList(cards) {
  const list = []
  for (const card of cards || []) list.push({ name: card.name, category: card.category || '', distance: card.distance })
  return list
}

// ============================== 나중에 넣을 자료 (토지피복·NDVI·가로수)
async function addLayerValues(features, segment, layers) {
  if (layers.green) features.green = await layers.green(segment.points)
  if (layers.water) features.water = await layers.water(segment.points)
  if (layers.ndvi) features.ndvi = await layers.ndvi(segment.points)
  if (layers.trees) features.trees = await layers.trees(segment)
  if (layers.sun) features.sun = await layers.sun(segment, features) // 일조: 숲 비율(green)을 본 뒤에 계산
}
