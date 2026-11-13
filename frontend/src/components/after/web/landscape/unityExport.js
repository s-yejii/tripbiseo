// ⑤ 웹 회고 → 유니티 3D 지도에 넘길 자료 만들기 (11/12)
// 유니티는 그리기만 하고, 자료 모으기(지형 높이·건물 윤곽)는 웹이 이미 하던 방식 그대로 여기서 해요
// 좌표: 가운데 점을 (0, 0)으로 두고 동쪽 = x(m), 북쪽 = z(m). 유니티가 경도·위도를 몰라도 되게 미터로 바꿔서 넘겨요
import { elevationAt } from './terrain'
import { VectorTile } from '@mapbox/vector-tile'
import { PbfReader } from 'pbf'
import { loadBuildings } from './buildingData' // 지도 건물 자료 읽기
import { plantTrees } from './unityTrees'
import { seasonOf } from './season'
import { isInRings, bboxOf } from './trees'
import { BUILDING_LOOKS } from './buildingLooks'
import { sunPosition, koreanDate } from '@/utils/sun'

const METERS_PER_DEGREE_LAT = 110540
const METERS_PER_DEGREE_LNG = 111320
const MAX_BUILDINGS = 40000 // 유니티는 건물을 한 덩어리로 그려서 많아도 됨
const CELL_DEG = 0.01       // 국가 건물 파일 한 칸 크기(도). make_buildings_seoul.py와 같아야 함
const CELL_UNIT = 1000000   // 칸 파일의 좌표 단위: 백만분의 1도
const SMOOTH_CELLS = 3 // 땅 높이를 고르게 할 때 양옆으로 몇 칸까지 평균 낼지
// 모양을 따로 만드는 눈에 띄는 건물. kind: 0 = N서울타워 · 1 = 롯데월드타워 (유니티 RetroMap.cs의 ShowLandmarks가 그림)
// 이 점이 들어간 건물 윤곽은 건물 목록에서 뺌 (상자 건물과 겹치지 않게)
const LANDMARKS = [
  { name: 'N서울타워', lng: 126.98822, lat: 37.55126, kind: 0 },
  { name: '롯데월드타워', lng: 127.10254, lat: 37.51254, kind: 1 },
]
// 한옥 동네: 이 둘레 안의 낮은 건물은 한옥 모양으로 그림 (유니티 BuildingData.style = 1)
const HANOK_ZONES = [
  { name: '익선동', lng: 126.9897, lat: 37.5741, radiusM: 230 },
  { name: '남산골한옥마을', lng: 126.9942, lat: 37.5593, radiusM: 120 },
]
const HANOK_MAX_M = 6 // 이 높이 이하만 한옥으로 봄 (한옥 동네 안이라도 높은 건물은 그대로)
// 을지로 인쇄 골목: 이 둘레 안의 낮은 상가는 흰 타일 벽·넓은 흰 간판·활짝 열린 1층으로 그림 (유니티 style = 2)
//   카카오 로드뷰(을지로3가, 2017)를 보고 정함: 흰 작은 타일 벽, 1층 위 넓은 흰 간판판, 셔터를 올린 어두운 창고형 1층
const EULJIRO_ZONES = [{ name: '을지로3가 인쇄 골목', lng: 126.9915, lat: 37.566, radiusM: 350 }]
const EULJIRO_MAX_M = 25 // 이 높이(7~8층) 이하 상가만
const EULJIRO_KINDS = [2, 4] // 상가 · 그 밖
const GRID_STEP_M = 15 // 지형 격자 한 칸 (DEM 한 픽셀이 약 7.6m라 이보다 촘촘해도 더 자세해지지 않음)
const WATER_DEPTH_M = 2.5 // 강바닥을 물 높이보다 이만큼 낮춤 (땅이 물 위로 비쳐 나오지 않게)
const WATER_EDGE_DEPTH_M = 0.3 // 강가 칸(옆에 땅이 있는 물 칸)은 조금만 낮춤 → 강가가 계단이 아니라 완만한 비탈이 됨
// 경로 둘레만 담는 지도(isCorridor): 하루에 여러 동네를 지나면 사각형 하나로는 건물이 너무 많아서
const WALK_KEEP_M = 300     // 걸은 곳에서 이 안의 건물·길·나무만 담음
const BOAT_KEEP_M = 900     // 배 탄 곳에서 이 안의 강변을 담음 (한강 폭이 1km쯤이라 건너편 강변까지)
const BOAT_VIEW_MIN_M = 9   // 배에서 보는 강변 건물은 이 높이(3층쯤) 이상만 (낮은 건물은 앞 건물에 가려서)
const BOAT_ROAD_MIN_M = 8   // 배에서 보는 강변 길은 이 폭 이상만 (강변북로·올림픽대로 같은 큰길)
const KEEP_CELL_M = 100     // 담을 곳 표시 칸 크기
const RIDE_KMH = 10         // 이보다 빠르게 움직였으면 걷기가 아니라 탈것(지하철·택시). 빨리 걸어도 시속 6~7km
const BOAT_MIN_M = 1500     // 물 위를 이어서 이보다 길게 갔으면 배 (한강 다리 하나 건너기는 1.2km 안쪽)
// 이동 방법 번호 (유니티 RouteData.modes와 같아야 함). taxi = 정류장 패턴이 없는 차 (택시·자가용)
const MODE = { walk: 0, taxi: 1, boat: 2, subway: 3, bus: 4 }
const isRideMode = (mode) => mode === MODE.taxi || mode === MODE.subway || mode === MODE.bus
const SUN_STEP_MINUTES = 10 // 해 위치를 이 간격마다 적어 보냄 (유니티는 그 사이를 이어서 씀)

/**
 * 유니티에 넘길 지도 자료
 * 받는 것 (한 묶음으로):
 *   center: [경도, 위도], halfM: 가운데에서 가장자리까지(m), tileUrl: 건물·길 벡터 타일 주소
 *   dateText: 여행 첫날 '2026-11-07' (나무 색을 계절에 맞춤), forest: forest-seoul.json 내용(없으면 null), guNames: 가로수를 읽을 구 이름들
 *   track: 걸은 경로 [{ lng, lat, minute(여행 시작부터 몇 분) }], stops: 다녀온 곳 [{ lng, lat }]
 * 돌려줌 (유니티 MapData.cs와 같은 모양):
 *   sizeM, gridCount, heights: 높이(m) 목록(남쪽 줄부터, 한 줄은 서→동)
 *   buildings: [{ points: [x, z, x, z …], height, kind(0 단독주택 · 1 공동주택 · 2 상가 · 3 업무·숙박 · 4 그 밖), look(로드뷰로 따로 정한 모습, 없을 수 있음) }]
 *   roads: [{ points: [x, z, x, z …], width(m), kind(0 찻길 · 1 걷는 길) }]
 *   trees: unityTrees.js 참고
 *   route: { points: [x, z, x, z …], minutes: [점마다 몇 분째], modes: [점마다 0 걷기 · 1 탈것 · 2 배] }, stops: [{ x, z }], landmarks: [{ x, z, kind }]
 *   water: { level: 물 높이(m), cells: [격자 점마다 1 물 · 0 땅] }
 *   sun: { minutes: [여행 몇 분째], altitudes: [해 높이(도)], azimuths: [해 방향(도, 북 0 · 동 90)] }
 * 더 받는 것(없어도 됨): gridStepM 지형 격자 간격(넓은 지도는 30), isCorridor: true면 경로 둘레만 담음,
 *   startClock: 경로 0분이 그날 몇 시 몇 분인지를 0시부터 분으로 (예: 오전 10시 = 600), dayCount: 여행 날 수 (해 위치 표를 그만큼 적음)
 */
export async function buildMapData({ center, halfM, tileUrl, dateText, forest, guNames, track, stops, gridStepM = GRID_STEP_M, isCorridor = false, startClock = 600, dayCount = 1 }) {
  const lngScale = METERS_PER_DEGREE_LNG * Math.cos((center[1] * Math.PI) / 180)
  const gridCount = Math.round((halfM * 2) / gridStepM) + 1
  const heights = []
  for (let row = 0; row < gridCount; row++) {
    for (let column = 0; column < gridCount; column++) {
      const lng = center[0] + (-halfM + column * gridStepM) / lngScale
      const lat = center[1] + (-halfM + row * gridStepM) / METERS_PER_DEGREE_LAT
      const height = await elevationAt(lng, lat)
      heights.push(Math.round((height || 0) * 10) / 10)
    }
  }
  const box = [center[0] - halfM / lngScale, center[1] - halfM / METERS_PER_DEGREE_LAT, center[0] + halfM / lngScale, center[1] + halfM / METERS_PER_DEGREE_LAT]
  const isInBox = (lng, lat) => lng > box[0] && lng < box[2] && lat > box[1] && lat < box[3]
  const water = await buildWaterGrid({ center, halfM, gridStepM, tileUrl })
  water.level = waterLevelOf(heights, water.cells)
  const smooth = smoothHeights(heights, gridCount)
  for (let i = 0; i < smooth.length; i++) {
    if (water.cells[i] !== 1) continue
    const depth = isWaterEdge(water.cells, gridCount, i) ? WATER_EDGE_DEPTH_M : WATER_DEPTH_M
    smooth[i] = Math.min(smooth[i], Math.round((water.level - depth) * 10) / 10) // 강바닥
  }
  const toMeters = (position) => [Math.round((position[0] - center[0]) * lngScale * 100) / 100, Math.round((position[1] - center[1]) * METERS_PER_DEGREE_LAT * 100) / 100]
  const route = makeRoute(track, isInBox, toMeters, (x, z) => isWaterAt(water, halfM, gridStepM, x, z))
  markTransitRuns(route, await loadTransitStops(tileUrl, box, toMeters)) // 탈것 구간을 지하철·버스·택시로 나눔
  const keep = isCorridor ? makeKeepChecker(route, stops, toMeters) : null
  let found = await loadNationalBuildings(box) // 서울: 국가 건물 자료 (빈 곳 없음, 층수 정확)
  if (found.length === 0) found = await loadMapBuildings(tileUrl, box, isInBox) // 그 밖 지역: 지도(OpenStreetMap) 건물로 대신
  const buildings = []
  for (const building of found) {
    const points = []
    let isInside = true
    for (const position of building.ring) {
      if (!isInBox(position[0], position[1])) isInside = false
      points.push(Math.round((position[0] - center[0]) * lngScale * 100) / 100, Math.round((position[1] - center[1]) * METERS_PER_DEGREE_LAT * 100) / 100)
    }
    if (!isInside) continue // 지형 밖으로 걸친 건물은 뺌
    if (keep && !keep.isBuildingKept(points, building.height)) continue // 경로에서 먼 건물은 뺌
    if (hasLandmarkInside(building.ring)) continue // 랜드마크는 따로 그림
    const item = { points: points, height: building.height, kind: building.kind }
    if (building.height <= HANOK_MAX_M && isInZone(building.ring[0], lngScale, HANOK_ZONES)) item.style = 1
    else if (building.height <= EULJIRO_MAX_M && EULJIRO_KINDS.includes(building.kind) && isInZone(building.ring[0], lngScale, EULJIRO_ZONES)) item.style = 2
    const look = lookOf(building.ring)
    if (look) item.look = look
    buildings.push(item)
  }
  let roads = await loadRoads(tileUrl, box, isInBox, toMeters)
  if (keep) roads = keep.cutRoads(roads)
  const area = { center: center, box: box, lngScale: lngScale, latScale: METERS_PER_DEGREE_LAT }
  let trees = await plantTrees(area, roads, dateText, forest, guNames)
  if (keep) trees = trees.filter((tree) => keep.isNear(tree.x, tree.z)) // filter: 조건에 맞는 것만 남긴 새 목록
  const pins = []
  for (const stop of stops) {
    if (!isInBox(stop.lng, stop.lat)) continue
    const at = toMeters([stop.lng, stop.lat])
    pins.push({ x: at[0], z: at[1] })
  }
  const landmarks = []
  for (const landmark of LANDMARKS) {
    if (!isInBox(landmark.lng, landmark.lat)) continue
    const at = toMeters([landmark.lng, landmark.lat])
    landmarks.push({ x: at[0], z: at[1], kind: landmark.kind })
  }
  const sun = makeSunTable(center, dateText, startClock, dayCount)
  delete route.hints // 유니티에는 modes만 보냄
  return { sizeM: halfM * 2, gridCount: gridCount, heights: smooth, buildings: buildings, roads: roads, trees: trees, route: route, stops: pins, landmarks: landmarks, water: water, sun: sun, startClock: startClock }
}

// 여행 시각마다 해 위치 (유니티가 하늘 색·햇빛 방향·밤 불빛을 정함). 날짜·시각·위경도로 천문 공식 계산(utils/sun.js)
// 첫날 0시부터 마지막 날 자정까지 전부 적음 (유니티에서 [ ] 키로 경로 밖 시각도 볼 수 있게. 10분 간격이라 하루 144줄)
function makeSunTable(center, dateText, startClock, dayCount) {
  const sun = { minutes: [], altitudes: [], azimuths: [] }
  for (let minute = -startClock; minute <= dayCount * 24 * 60 - startClock; minute += SUN_STEP_MINUTES) {
    const position = sunPosition(koreanDate(dateText, startClock + minute), center[1], center[0])
    sun.minutes.push(minute)
    sun.altitudes.push(Math.round(position.altitude * 10) / 10)
    sun.azimuths.push(Math.round(position.azimuth * 10) / 10)
  }
  return sun
}

// ---------- 강·호수 (지도 벡터 타일의 water 층)
const WATER_ZOOM = 14
const WATER_CLASSES = ['river', 'lake', 'ocean'] // 수영장·분수 같은 작은 물은 뺌

/**
 * 지형 격자 점마다 물인지(1) 땅인지(0). buildMapData의 높이 격자와 같은 순서 (남쪽 줄부터, 한 줄은 서→동)
 * 돌려줌: { level: 0, cells: [0 또는 1 …] }  (level은 buildMapData가 채움)
 */
export async function buildWaterGrid({ center, halfM, gridStepM = GRID_STEP_M, tileUrl }) {
  const lngScale = METERS_PER_DEGREE_LNG * Math.cos((center[1] * Math.PI) / 180)
  const box = [center[0] - halfM / lngScale, center[1] - halfM / METERS_PER_DEGREE_LAT, center[0] + halfM / lngScale, center[1] + halfM / METERS_PER_DEGREE_LAT]
  const polygons = await loadWaterPolygons(tileUrl, box)
  const gridCount = Math.round((halfM * 2) / gridStepM) + 1
  const cells = []
  for (let row = 0; row < gridCount; row++) {
    for (let column = 0; column < gridCount; column++) {
      const lng = center[0] + (-halfM + column * gridStepM) / lngScale
      const lat = center[1] + (-halfM + row * gridStepM) / METERS_PER_DEGREE_LAT
      cells.push(isInAnyPolygon(polygons, lng, lat) ? 1 : 0)
    }
  }
  return { level: 0, cells: cells }
}

// 범위에 걸친 물 다각형들. 돌려줌: [{ rings: [[[경도, 위도] …] …], box: [서, 남, 동, 북] }]
async function loadWaterPolygons(tileUrl, box) {
  const count = Math.pow(2, WATER_ZOOM)
  const tileOf = (lng, lat) => {
    const rad = (lat * Math.PI) / 180
    return [Math.floor(((lng + 180) / 360) * count), Math.floor(((1 - Math.log(Math.tan(rad) + 1 / Math.cos(rad)) / Math.PI) / 2) * count)]
  }
  const [x0, y1] = tileOf(box[0], box[1])
  const [x1, y0] = tileOf(box[2], box[3])
  const polygons = []
  for (let x = x0; x <= x1; x++) {
    for (let y = y0; y <= y1; y++) {
      const layer = await fetchLayer(tileUrl.replace('{z}', WATER_ZOOM).replace('{x}', x).replace('{y}', y), 'water')
      for (let i = 0; layer && i < layer.length; i++) {
        const feature = layer.feature(i)
        if (!WATER_CLASSES.includes(feature.properties.class)) continue
        const geometry = feature.toGeoJSON(x, y, WATER_ZOOM).geometry
        const list = geometry.type === 'Polygon' ? [geometry.coordinates] : geometry.type === 'MultiPolygon' ? geometry.coordinates : []
        for (const rings of list) polygons.push({ rings: rings, box: bboxOf(rings[0]) })
      }
    }
  }
  return polygons
}

function isInAnyPolygon(polygons, lng, lat) {
  for (const polygon of polygons) {
    const box = polygon.box
    if (lng < box[0] || lng > box[2] || lat < box[1] || lat > box[3]) continue // 다각형을 감싸는 네모 밖이면 볼 필요 없음
    if (isInRings(lng, lat, polygon.rings)) return true
  }
  return false
}

// 물 높이: 물 칸들의 땅 높이 중 가운데 값 (높이 자료는 강에서 물 표면 높이가 나옴)
function waterLevelOf(heights, cells) {
  const list = []
  for (let i = 0; i < cells.length; i++) {
    if (cells[i] === 1) list.push(heights[i])
  }
  if (list.length === 0) return 0
  list.sort((a, b) => a - b)
  return list[Math.floor(list.length / 2)]
}

// 물 칸 i의 위·아래·왼쪽·오른쪽 중에 땅 칸이 있는지 (강가인지)
function isWaterEdge(cells, gridCount, i) {
  const column = i % gridCount
  const row = Math.floor(i / gridCount)
  const neighbors = [[column - 1, row], [column + 1, row], [column, row - 1], [column, row + 1]]
  for (const [c, r] of neighbors) {
    if (c < 0 || r < 0 || c >= gridCount || r >= gridCount) continue
    if (cells[r * gridCount + c] !== 1) return true
  }
  return false
}

// 미터 좌표(x, z)가 물 칸인지
function isWaterAt(water, halfM, gridStepM, x, z) {
  const gridCount = Math.round((halfM * 2) / gridStepM) + 1
  const column = Math.round((x + halfM) / gridStepM)
  const row = Math.round((z + halfM) / gridStepM)
  if (column < 0 || row < 0 || column >= gridCount || row >= gridCount) return false
  return water.cells[row * gridCount + column] === 1
}

// ---------- 경로 (점마다 걷기·탈것·배를 나눔)

// 걸은 경로를 미터로 바꾸고, 점마다(그 점 → 다음 점) 어떻게 움직였는지 정함
//   두 점이 다 물 위이고, 물 위 구간이 이어서 BOAT_MIN_M 넘게 길면 → 배 (다리를 건넌 지하철·택시는 1km 남짓이라 빠짐)
//   아니면 빠르게(RIDE_KMH 넘게) 갔으면 → 탈것, 느리면 → 걷기
function makeRoute(track, isInBox, toMeters, isWater) {
  const route = { points: [], minutes: [], modes: [], hints: [] } // hints: 앱이 계획에서 아는 이동 방법 (없으면 null). 유니티에는 안 보냄
  for (const point of track) {
    if (!isInBox(point.lng, point.lat)) continue // 지도 밖으로 나간 점은 뺌
    route.points.push(...toMeters([point.lng, point.lat]))
    route.minutes.push(point.minute)
    route.hints.push(point.mode || null)
  }
  const count = route.minutes.length
  for (let i = 0; i < count; i++) {
    const next = Math.min(i + 1, count - 1)
    const hours = (route.minutes[next] - route.minutes[i]) / 60
    const km = segmentMeters(route, i) / 1000
    route.modes.push(hours > 0 && km / hours > RIDE_KMH ? MODE.taxi : MODE.walk) // 빠르면 일단 탈것(택시), 아래에서 지하철·버스를 가림
  }
  fillShortStops(route)
  markBoatRuns(route, isWater)
  return route
}

// 탈것 사이에 낀 짧은 걷기(RIDE_GAP_MINUTES 안)는 탈것으로 메움
//   버스는 신호·정류장에서 서고 느려져서, 점마다 빠르기로만 보면 "걷기"로 잘게 끊김
function fillShortStops(route) {
  let i = 0
  while (i < route.modes.length) {
    if (route.modes[i] !== MODE.walk) {
      i++
      continue
    }
    let end = i
    while (end + 1 < route.modes.length && route.modes[end + 1] === MODE.walk) end++
    const isBetweenRides = i > 0 && end + 1 < route.modes.length && route.modes[i - 1] === MODE.taxi && route.modes[end + 1] === MODE.taxi
    const minutes = route.minutes[end + 1] - route.minutes[i] // 이 걷기 덩어리가 걸린 시간
    if (isBetweenRides && minutes <= RIDE_GAP_MINUTES) {
      for (let k = i; k <= end; k++) route.modes[k] = MODE.taxi
    }
    i = end + 1
  }
}

// 물 위 구간(두 점 다 물)이 이어진 덩어리마다 길이를 재서, 길면 배로 바꿈
function markBoatRuns(route, isWater) {
  const count = route.minutes.length
  const isOnWater = (i) => isWater(route.points[i * 2], route.points[i * 2 + 1])
  let start = -1 // 지금 덩어리의 첫 구간 번호
  let meters = 0
  for (let i = 0; i < count; i++) {
    const isWaterSegment = i + 1 < count && isOnWater(i) && isOnWater(i + 1)
    if (isWaterSegment) {
      if (start < 0) start = i
      meters += segmentMeters(route, i)
      continue
    }
    if (start >= 0 && meters >= BOAT_MIN_M) {
      for (let k = start; k < i; k++) route.modes[k] = MODE.boat
    }
    start = -1
    meters = 0
  }
}

// ---------- 탈것 구간을 지하철·버스·택시로 나누기
const SUBWAY_NEAR_M = 250   // 탈것 구간의 처음과 끝이 둘 다 지하철역에서 이 안이면 지하철
const BUS_STOP_NEAR_M = 40  // 멈춘 자리가 버스 정류장에서 이 안이면 "정류장에 섰다"
const STOP_MOVE_M = 15      // 다음 점까지 이보다 적게 움직였으면 멈춘 것
const BUS_MIN_STOPS = 2     // 정류장에 이만큼 넘게 섰으면 버스
const RIDE_GAP_MINUTES = 3  // 탈것 사이 걷기가 이 시간(분) 안이면 탈것이 잠깐 느려진 것으로 봄
const SUBWAY_GAP_MINUTES = 1.5 // 지하철은 땅속이라 GPS가 드문드문: 점 사이 평균 간격이 이 시간(분)보다 길어야 지하철

/**
 * 탈것 구간(빠르게 움직인 덩어리)마다 지하철·버스·택시를 정함. 순서대로 봄:
 *   1) 앱이 계획(길 안내)에서 아는 이동 방법이 점에 적혀 있으면 그걸 씀 (point.mode: 'subway' | 'bus' | 'taxi')
 *   2) 버스 정류장에서 여러 번 섰으면 버스
 *   3) 처음과 끝이 둘 다 지하철역 근처이고 GPS가 드문드문하면 지하철 (땅속이라 GPS가 끊겼다가 역 근처에서 다시 잡힘)
 *      도심은 어디서 타고 내려도 역이 가까워서, 역만 보면 버스도 지하철로 보임 → GPS 간격도 봄
 *   4) 나머지는 택시 (정류장 패턴 없이 문 앞에서 문 앞까지)
 */
function markTransitRuns(route, transit) {
  const count = route.modes.length
  let start = 0
  while (start < count) {
    if (route.modes[start] !== MODE.taxi) {
      start++
      continue
    }
    let end = start
    while (end + 1 < count && route.modes[end + 1] === MODE.taxi) end++
    const mode = transitModeOf(route, start, end + 1, transit) // end + 1 = 이 덩어리의 마지막 점
    for (let k = start; k <= end; k++) route.modes[k] = mode
    start = end + 1
  }
}

// 점 first ~ last 사이 탈것 구간 하나의 이동 방법
function transitModeOf(route, first, last, transit) {
  for (let k = first; k <= last; k++) {
    const hint = route.hints[k]
    if (hint && MODE[hint] !== undefined) return MODE[hint]
  }
  const pointAt = (k) => [route.points[k * 2], route.points[k * 2 + 1]]
  let busStops = 0
  for (let k = first; k < last; k++) {
    const isStopped = Math.hypot(route.points[k * 2 + 2] - route.points[k * 2], route.points[k * 2 + 3] - route.points[k * 2 + 1]) < STOP_MOVE_M
    if (isStopped && nearestMeters(transit.bus, pointAt(k)) <= BUS_STOP_NEAR_M) busStops++
  }
  if (busStops >= BUS_MIN_STOPS) return MODE.bus
  const isNearSubway = (point) => nearestMeters(transit.subway, point) <= SUBWAY_NEAR_M
  const averageGap = (route.minutes[last] - route.minutes[first]) / Math.max(1, last - first) // 점 사이 평균 시간(분)
  if (isNearSubway(pointAt(first)) && isNearSubway(pointAt(last)) && averageGap >= SUBWAY_GAP_MINUTES) return MODE.subway
  return MODE.taxi
}

// 점 목록(미터) 중 point에서 가장 가까운 점까지 거리
function nearestMeters(list, point) {
  let best = Infinity
  for (const other of list) best = Math.min(best, Math.hypot(other[0] - point[0], other[1] - point[1]))
  return best
}

// 범위 안의 지하철역·버스 정류장 위치(미터). 지도 벡터 타일 poi 층 (railway/subway · bus/bus_stop)
async function loadTransitStops(tileUrl, box, toMeters) {
  const count = Math.pow(2, GROUND_ZOOM)
  const tileOf = (lng, lat) => {
    const rad = (lat * Math.PI) / 180
    return [Math.floor(((lng + 180) / 360) * count), Math.floor(((1 - Math.log(Math.tan(rad) + 1 / Math.cos(rad)) / Math.PI) / 2) * count)]
  }
  const [x0, y1] = tileOf(box[0], box[1])
  const [x1, y0] = tileOf(box[2], box[3])
  const transit = { subway: [], bus: [] }
  for (let x = x0; x <= x1; x++) {
    for (let y = y0; y <= y1; y++) {
      const layer = await fetchLayer(tileUrl.replace('{z}', GROUND_ZOOM).replace('{x}', x).replace('{y}', y), 'poi')
      for (let i = 0; layer && i < layer.length; i++) {
        const feature = layer.feature(i)
        const kind = feature.properties.subclass
        const isSubway = feature.properties.class === 'railway' && (kind === 'subway' || kind === 'station')
        const isBus = kind === 'bus_stop'
        if (!isSubway && !isBus) continue
        const geometry = feature.toGeoJSON(x, y, GROUND_ZOOM).geometry
        if (geometry.type !== 'Point') continue
        if (isSubway) transit.subway.push(toMeters(geometry.coordinates))
        else transit.bus.push(toMeters(geometry.coordinates))
      }
    }
  }
  return transit
}

// i번째 점에서 다음 점까지 거리(m)
function segmentMeters(route, i) {
  const next = Math.min(i + 1, route.minutes.length - 1)
  return Math.hypot(route.points[next * 2] - route.points[i * 2], route.points[next * 2 + 1] - route.points[i * 2 + 1])
}

// ---------- 경로 둘레만 담기 (isCorridor)

// 100m 칸마다 "걸은 곳 둘레(1)" 또는 "배에서 보이는 곳(2)"을 표시해 두고, 건물·길·나무를 담을지 빨리 알려 줌
function makeKeepChecker(route, stops, toMeters) {
  const cells = new Map() // "칸x,칸z" → 1 걸은 곳 둘레 · 2 배에서 보이는 곳
  const mark = (x, z, radiusM, value) => {
    const reach = Math.ceil(radiusM / KEEP_CELL_M)
    const cellX = Math.floor(x / KEEP_CELL_M)
    const cellZ = Math.floor(z / KEEP_CELL_M)
    for (let dx = -reach; dx <= reach; dx++) {
      for (let dz = -reach; dz <= reach; dz++) {
        if (Math.hypot(dx, dz) * KEEP_CELL_M > radiusM + KEEP_CELL_M) continue // 둥글게
        const key = cellX + dx + ',' + (cellZ + dz)
        if (cells.get(key) !== 1) cells.set(key, value) // 걸은 곳(1)이 배(2)보다 먼저
      }
    }
  }
  for (let i = 0; i + 1 < route.modes.length; i++) {
    if (isRideMode(route.modes[i])) continue // 지하철·버스·택시로 지나간 곳은 안 담음
    const radius = route.modes[i] === MODE.boat ? BOAT_KEEP_M : WALK_KEEP_M
    const value = route.modes[i] === MODE.boat ? 2 : 1
    const x0 = route.points[i * 2], z0 = route.points[i * 2 + 1]
    const x1 = route.points[i * 2 + 2], z1 = route.points[i * 2 + 3]
    const steps = Math.max(1, Math.ceil(Math.hypot(x1 - x0, z1 - z0) / (KEEP_CELL_M / 2))) // 50m마다 표시
    for (let s = 0; s <= steps; s++) mark(x0 + ((x1 - x0) * s) / steps, z0 + ((z1 - z0) * s) / steps, radius, value)
  }
  for (const stop of stops) {
    const at = toMeters([stop.lng, stop.lat])
    mark(at[0], at[1], WALK_KEEP_M, 1)
  }
  const valueAt = (x, z) => cells.get(Math.floor(x / KEEP_CELL_M) + ',' + Math.floor(z / KEEP_CELL_M))

  return {
    isNear: (x, z) => valueAt(x, z) !== undefined,
    // 건물: 걸은 곳 둘레면 다, 배에서 보이는 곳이면 높은 것만
    isBuildingKept: (points, height) => {
      const value = valueAt(points[0], points[1])
      return value === 1 || (value === 2 && height >= BOAT_VIEW_MIN_M)
    },
    // 길: 담을 곳 밖으로 나가면 거기서 끊음 (배에서 보이는 곳은 큰길만)
    cutRoads: (roads) => {
      const result = []
      for (const road of roads) {
        let points = []
        for (let i = 0; i + 1 < road.points.length; i += 2) {
          const value = valueAt(road.points[i], road.points[i + 1])
          const isKept = value === 1 || (value === 2 && road.width >= BOAT_ROAD_MIN_M)
          if (isKept) points.push(road.points[i], road.points[i + 1])
          else {
            if (points.length >= 4) result.push({ ...road, points: points }) // ...road: 폭·종류는 그대로 두고 점만 바꾼 복사본
            points = []
          }
        }
        if (points.length >= 4) result.push({ ...road, points: points })
      }
      return result
    },
  }
}

// 땅 높이 고르게 하기: 점마다 주변 5×5칸(약 75m)의 평균으로 바꿈
// 왜: 이 높이 자료는 도심에서 건물 높이가 섞여 땅이 울퉁불퉁하게 나옴 → 평균을 내면 건물만 한 혹이 사라지고 산 모양은 남음
function smoothHeights(heights, gridCount) {
  const result = []
  for (let row = 0; row < gridCount; row++) {
    for (let column = 0; column < gridCount; column++) {
      let sum = 0
      let count = 0
      for (let r = Math.max(0, row - SMOOTH_CELLS); r <= Math.min(gridCount - 1, row + SMOOTH_CELLS); r++) {
        for (let c = Math.max(0, column - SMOOTH_CELLS); c <= Math.min(gridCount - 1, column + SMOOTH_CELLS); c++) {
          sum += heights[r * gridCount + c]
          count++
        }
      }
      result.push(Math.round((sum / count) * 10) / 10)
    }
  }
  return result
}

// 로드뷰를 보고 따로 적어 둔 모습(buildingLooks.js)이 있으면 돌려줌: 적어 둔 점이 이 건물 윤곽 안에 있으면 그 건물 것
function lookOf(ring) {
  for (const look of BUILDING_LOOKS) {
    if (isInRings(look.lng, look.lat, [ring])) return { wall: look.wall, sign: look.sign, pattern: look.pattern, ground: look.ground }
  }
  return null
}

// 이 건물 윤곽 안에 랜드마크 점이 있는지
function hasLandmarkInside(ring) {
  for (const landmark of LANDMARKS) {
    if (isInRings(landmark.lng, landmark.lat, [ring])) return true
  }
  return false
}

// 이 자리([경도, 위도])가 zones(둘레 목록) 중 하나 안인지
function isInZone(position, lngScale, zones) {
  for (const zone of zones) {
    const dx = (position[0] - zone.lng) * lngScale
    const dz = (position[1] - zone.lat) * METERS_PER_DEGREE_LAT
    if (Math.hypot(dx, dz) <= zone.radiusM) return true
  }
  return false
}

// 국가 건물 자료(GIS건물통합정보) 읽기: 범위에 걸친 칸 파일(/geo/buildings/경도칸_위도칸.json)만 받음
// 파일은 db/geo/make_buildings_seoul.py가 만듦. 한 건물 = [높이, 종류, x, y, x, y, …] (x·y는 칸 모서리에서 백만분의 1도)
// 돌려줌: [{ ring: [[경도, 위도], …], height, kind }]
async function loadNationalBuildings(box) {
  const list = []
  for (let cellX = Math.floor(box[0] / CELL_DEG); cellX <= Math.floor(box[2] / CELL_DEG); cellX++) {
    for (let cellY = Math.floor(box[1] / CELL_DEG); cellY <= Math.floor(box[3] / CELL_DEG); cellY++) {
      for (const item of await fetchCell(cellX + '_' + cellY)) {
        const ring = []
        for (let i = 2; i + 1 < item.length; i += 2) ring.push([cellX * CELL_DEG + item[i] / CELL_UNIT, cellY * CELL_DEG + item[i + 1] / CELL_UNIT])
        list.push({ ring: ring, height: item[0], kind: item[1] })
      }
    }
  }
  return list
}

// 칸 파일 하나. 없는 칸(서울 밖·산·강)은 빈 목록
async function fetchCell(name) {
  try {
    const response = await fetch('/geo/buildings/' + name + '.json')
    return response.ok ? await response.json() : []
  } catch (e) {
    return [] // 파일이 없으면 개발 서버가 다른 내용을 돌려줘서 JSON으로 못 읽음 → 없는 칸으로 봄
  }
}

// 지도(OpenStreetMap) 건물 읽기. 종류를 몰라서 전부 4(그 밖)
async function loadMapBuildings(tileUrl, box, isInBox) {
  const list = []
  for (const building of await loadBuildings(tileUrl, box, isInBox, MAX_BUILDINGS)) {
    list.push({ ring: building.rings[0], height: Math.round(building.height * 10) / 10, kind: 4 }) // 바깥 고리만
  }
  return list
}

// ---------- 땅에 입힐 위성 사진
const PHOTO_MAX_ZOOM = 17 // 가장 자세한 조각 (한 픽셀이 서울에서 약 0.95m)
const PHOTO_PARALLEL = 16  // 위성 사진 조각을 동시에 이만큼 받음 (한 장씩 받으면 넓은 지도는 30초 가까이 걸려서)
const METERS_PER_PIXEL_Z0 = 156543 // 확대 0단계에서 적도의 한 픽셀 = 156,543m (단계가 하나 오를 때마다 절반)
const TILE_PX = 256

// 만들 그림의 1픽셀(m)보다 조금 더 자세한 조각 단계 (그보다 자세하면 받아도 줄여서 버려짐)
//   예) 지도 한 변 8.5km를 4096px로 → 1px = 2.1m → 16단계(1.9m) 조각. 17단계보다 받을 조각이 4분의 1
function photoZoomFor(lat, metersPerPixel) {
  const zoom = Math.ceil(Math.log2((METERS_PER_PIXEL_Z0 * Math.cos((lat * Math.PI) / 180)) / metersPerPixel))
  return Math.min(PHOTO_MAX_ZOOM, Math.max(12, zoom))
}

// 경도·위도 → 위성 사진 조각 번호(소수). 웹 지도 공통 계산식(메르카토르)
function toTile(lng, lat, zoom) {
  const count = Math.pow(2, zoom)
  const rad = (lat * Math.PI) / 180
  return [((lng + 180) / 360) * count, ((1 - Math.log(Math.tan(rad) + 1 / Math.cos(rad)) / Math.PI) / 2) * count]
}

function loadImage(url) {
  return new Promise((resolve) => {
    const image = new Image()
    image.crossOrigin = 'anonymous' // 다른 사이트 그림을 캔버스에서 다시 꺼내려면 필요
    image.onload = () => resolve(image)
    image.onerror = () => resolve(null) // 못 받은 조각은 비워 둠
    image.src = url
  })
}

/**
 * 지도 범위의 위성 사진 한 장. buildMapData와 같은 center·halfM을 주면 땅에 딱 맞음
 * photoUrl: "https://…/{z}/{y}/{x}", sizePx: 만들 그림 한 변(픽셀)
 * forest·dateText를 주면 숲 구역 바닥을 그 계절의 흙·낙엽 색으로 덧칠함 (위성 사진이 겨울에 찍혀 숲 바닥이 잿빛이라서)
 * clean을 주면 걸은 길 둘레는 위성 사진 대신 지도 자료(땅 용도)로 깔끔하게 새로 칠하고, 멀어질수록 위성 사진으로 바뀜
 *   clean: { tileUrl, points: [[경도, 위도] …] (경로·다녀온 곳), radiusM: 이 거리까지 새로 칠함 }
 * 돌려줌: JPEG 그림을 글자로 바꾼 것(base64). 유니티에 글자로만 넘길 수 있어서
 */
export async function buildGroundPhoto(center, halfM, photoUrl, sizePx, forest, dateText, clean = null) {
  const lngScale = METERS_PER_DEGREE_LNG * Math.cos((center[1] * Math.PI) / 180)
  const zoom = photoZoomFor(center[1], (halfM * 2) / sizePx)
  const [west, north] = toTile(center[0] - halfM / lngScale, center[1] + halfM / METERS_PER_DEGREE_LAT, zoom)
  const [east, south] = toTile(center[0] + halfM / lngScale, center[1] - halfM / METERS_PER_DEGREE_LAT, zoom)
  const canvas = document.createElement('canvas')
  canvas.width = sizePx
  canvas.height = sizePx
  const ctx = canvas.getContext('2d')
  const scale = sizePx / ((east - west) * TILE_PX) // 조각 픽셀 → 만들 그림 픽셀
  const tiles = []
  for (let x = Math.floor(west); x <= Math.floor(east); x++) {
    for (let y = Math.floor(north); y <= Math.floor(south); y++) tiles.push([x, y])
  }
  // 받을 조각 목록을 PHOTO_PARALLEL개의 "일꾼"이 나눠 받음: 일꾼마다 목록에서 다음 조각을 꺼내 받고 그림에 그리기를 되풀이
  let next = 0
  const worker = async () => {
    while (next < tiles.length) {
      const [x, y] = tiles[next++]
      const image = await loadImage(photoUrl.replace('{z}', zoom).replace('{x}', x).replace('{y}', y))
      if (image) ctx.drawImage(image, (x - west) * TILE_PX * scale, (y - north) * TILE_PX * scale, TILE_PX * scale + 1, TILE_PX * scale + 1)
    }
  }
  const workers = []
  for (let k = 0; k < PHOTO_PARALLEL; k++) workers.push(worker())
  await Promise.all(workers) // 일꾼이 다 끝날 때까지 기다림
  const toPixel = (lng, lat) => {
    const [x, y] = toTile(lng, lat, zoom)
    return [(x - west) * TILE_PX * scale, (y - north) * TILE_PX * scale]
  }
  if (clean) await paintCleanGround(ctx, sizePx, clean, toPixel, sizePx / (halfM * 2)) // 그림 한 변 = 지도 한 변(halfM × 2) → 1m가 몇 픽셀인지
  if (forest) paintForestFloor(ctx, forest, FOREST_FLOORS[seasonOf(dateText)], toPixel)
  return canvas.toDataURL('image/jpeg', 0.85).split(',')[1]
}

// ---------- 걸은 길 둘레 바닥을 지도 자료로 새로 칠하기 (위성 사진의 그림자·차·옥상이 바닥에 깔리지 않게)
const GROUND_ZOOM = 14
// 땅 용도(지도 벡터 타일 landcover·landuse·park 층의 class) → 바닥 색
const GROUND_COLORS = {
  grass: '#62793f', wood: '#475e33', forest: '#475e33', farmland: '#827a52', sand: '#a3946c', wetland: '#566d4a', scrub: '#576d3e',
  park: '#68804a', cemetery: '#62793f', pitch: '#55783c', stadium: '#71795f', playground: '#968770',
  residential: '#7c7871', commercial: '#827d76', retail: '#827d76', industrial: '#6e6a66', railway: '#6a655d',
  school: '#898170', university: '#898170', college: '#898170', hospital: '#86817a', kindergarten: '#898170',
}
// 색은 햇빛·색 보정(ACES)을 받으면 꽤 밝아져서 실제보다 한 단계 어둡게 잡음 (밝게 잡았더니 눈 온 것처럼 하얘졌음)
const URBAN_COLOR = '#7a766f' // 용도가 없는 곳 (도로 옆 맨땅·광장 등)
const GROUND_LAYERS = ['landuse', 'park', 'landcover'] // 뒤 층이 앞 층을 덮음 (잔디·숲이 주거지 위에 오게)

// clean.points 둘레 clean.radiusM 안을 새로 칠함. 가장자리는 몇 겹으로 옅게 칠해 위성 사진과 자연스럽게 이어짐
async function paintCleanGround(ctx, sizePx, clean, toPixel, pixelsPerMeter) {
  const layer = document.createElement('canvas') // 새로 칠할 바닥
  layer.width = sizePx
  layer.height = sizePx
  const paint = layer.getContext('2d')
  paint.fillStyle = URBAN_COLOR
  paint.fillRect(0, 0, sizePx, sizePx)
  const box = boxAround(clean.points, clean.radiusM)
  for (const polygon of await loadGroundPolygons(clean.tileUrl, box)) {
    paint.fillStyle = polygon.color
    paint.beginPath()
    for (const ring of polygon.rings) {
      for (let i = 0; i < ring.length; i++) {
        const [x, y] = toPixel(ring[i][0], ring[i][1])
        if (i === 0) paint.moveTo(x, y)
        else paint.lineTo(x, y)
      }
      paint.closePath()
    }
    paint.fill('evenodd')
  }
  addSpeckles(paint, sizePx)

  // 경로를 굵은 선으로 여러 번 겹쳐 그린 "가림판" 모양만 남김 (destination-in: 가림판이 칠해진 곳만 남기기)
  const mask = document.createElement('canvas')
  mask.width = sizePx
  mask.height = sizePx
  const pen = mask.getContext('2d')
  pen.lineCap = 'round'
  pen.lineJoin = 'round'
  pen.strokeStyle = '#000'
  const FADE_STEPS = 6
  for (let k = 0; k <= FADE_STEPS; k++) {
    pen.globalAlpha = k === FADE_STEPS ? 1 : 0.22
    pen.lineWidth = clean.radiusM * 2 * pixelsPerMeter * (1 - k * 0.07) // 바깥 겹일수록 넓고 옅게
    pen.beginPath()
    clean.points.forEach(([lng, lat], i) => {
      const [x, y] = toPixel(lng, lat)
      if (i === 0) pen.moveTo(x, y)
      else pen.lineTo(x, y)
    })
    pen.stroke()
  }
  paint.globalCompositeOperation = 'destination-in'
  paint.drawImage(mask, 0, 0)
  ctx.drawImage(layer, 0, 0)
}

// 점들을 감싸는 [서, 남, 동, 북] + 둘레 radiusM
function boxAround(points, radiusM) {
  const box = bboxOf(points)
  const padLat = radiusM / METERS_PER_DEGREE_LAT
  const padLng = radiusM / (METERS_PER_DEGREE_LNG * Math.cos((box[1] * Math.PI) / 180))
  return [box[0] - padLng, box[1] - padLat, box[2] + padLng, box[3] + padLat]
}

// 범위의 땅 용도 다각형들 (색이 정해진 class만). 돌려줌: [{ rings, color }] — 그리는 순서 = GROUND_LAYERS 순서
async function loadGroundPolygons(tileUrl, box) {
  const count = Math.pow(2, GROUND_ZOOM)
  const tileOf = (lng, lat) => {
    const rad = (lat * Math.PI) / 180
    return [Math.floor(((lng + 180) / 360) * count), Math.floor(((1 - Math.log(Math.tan(rad) + 1 / Math.cos(rad)) / Math.PI) / 2) * count)]
  }
  const [x0, y1] = tileOf(box[0], box[1])
  const [x1, y0] = tileOf(box[2], box[3])
  const byLayer = { landuse: [], park: [], landcover: [] }
  for (let x = x0; x <= x1; x++) {
    for (let y = y0; y <= y1; y++) {
      const url = tileUrl.replace('{z}', GROUND_ZOOM).replace('{x}', x).replace('{y}', y)
      for (const layerName of GROUND_LAYERS) {
        const layer = await fetchLayer(url, layerName)
        for (let i = 0; layer && i < layer.length; i++) {
          const feature = layer.feature(i)
          const color = GROUND_COLORS[feature.properties.class] || (layerName === 'park' ? GROUND_COLORS.park : null)
          if (!color) continue
          const geometry = feature.toGeoJSON(x, y, GROUND_ZOOM).geometry
          const list = geometry.type === 'Polygon' ? [geometry.coordinates] : geometry.type === 'MultiPolygon' ? geometry.coordinates : []
          for (const rings of list) byLayer[layerName].push({ rings: rings, color: color })
        }
      }
    }
  }
  return [...byLayer.landuse, ...byLayer.park, ...byLayer.landcover] // ...: 세 목록을 차례로 이어 붙인 새 목록
}

// 바닥이 한 가지 색 판처럼 보이지 않게 작은 얼룩을 흩뿌림 (밝고 어두운 점을 아주 옅게)
function addSpeckles(paint, sizePx) {
  const SPECKLES = 60000
  for (let i = 0; i < SPECKLES; i++) {
    const x = seededNumber(i * 2) * sizePx
    const y = seededNumber(i * 2 + 1) * sizePx
    paint.fillStyle = i % 2 === 0 ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.06)'
    paint.fillRect(x, y, 6 + (i % 5) * 3, 6 + (i % 7) * 2)
  }
}

// 늘 같은 값이 나오는 0~1 수
function seededNumber(n) {
  const value = Math.sin(n * 12.9898) * 43758.5453
  return value - Math.floor(value)
}

// 계절별 숲 바닥 색 (나무 사이로 보이는 땅)
const FOREST_FLOORS = { spring: '#5E7A3C', summer: '#3F6332', autumn: '#7C6A3E', winter: '#6F665A' }
const FOREST_FLOOR_ALPHA = 0.7 // 덧칠 진하기 (1이면 사진이 안 비침)

// 숲 구역을 그림 위에 반투명하게 칠함. toPixel(경도, 위도) → 그림 좌표
function paintForestFloor(ctx, forest, color, toPixel) {
  ctx.fillStyle = color
  ctx.globalAlpha = FOREST_FLOOR_ALPHA
  for (const feature of forest.features) {
    for (const polygon of feature.geometry.coordinates) {
      ctx.beginPath()
      for (const ring of polygon) {
        for (let i = 0; i < ring.length; i++) {
          const [x, y] = toPixel(ring[i][0], ring[i][1])
          if (i === 0) ctx.moveTo(x, y)
          else ctx.lineTo(x, y)
        }
        ctx.closePath()
      }
      ctx.fill('evenodd') // 구역 안의 구멍(숲이 아닌 곳)은 칠하지 않음
    }
  }
  ctx.globalAlpha = 1
}

// ---------- 길 (지도 벡터 타일의 도로 선)
const ROAD_ZOOM = 14
const ONE_WAY_RATIO = 0.55 // 일방통행 선 하나의 폭 = 길 전체 폭의 이만큼
// 도로 등급 → [폭(m), 종류(0 찻길 · 1 걷는 길)]. 여기 없는 등급(철길·뱃길 등)은 안 그림
const ROAD_KINDS = {
  motorway: [16, 0], trunk: [16, 0], primary: [14, 0], secondary: [10, 0], tertiary: [8, 0],
  minor: [5, 0], service: [3, 0], track: [3, 1], path: [2.5, 1],
}

/**
 * 범위 안의 길 읽기. 터널은 뺌(산 위에 그려지면 안 돼서). 범위를 벗어나는 곳에서 선을 끊음
 * 돌려줌: [{ points: [x, z, x, z …], width, kind }]
 */
async function loadRoads(tileUrl, box, isInBox, toMeters) {
  const count = Math.pow(2, ROAD_ZOOM)
  const tileOf = (lng, lat) => {
    const rad = (lat * Math.PI) / 180
    return [Math.floor(((lng + 180) / 360) * count), Math.floor(((1 - Math.log(Math.tan(rad) + 1 / Math.cos(rad)) / Math.PI) / 2) * count)]
  }
  const [x0, y1] = tileOf(box[0], box[1])
  const [x1, y0] = tileOf(box[2], box[3])
  const roads = []
  for (let x = x0; x <= x1; x++) {
    for (let y = y0; y <= y1; y++) {
      const layer = await fetchLayer(tileUrl.replace('{z}', ROAD_ZOOM).replace('{x}', x).replace('{y}', y), 'transportation')
      for (let i = 0; layer && i < layer.length; i++) {
        const feature = layer.feature(i)
        const kind = ROAD_KINDS[feature.properties.class]
        if (!kind || feature.properties.brunnel === 'tunnel') continue
        const geometry = feature.toGeoJSON(x, y, ROAD_ZOOM).geometry
        const lines = geometry.type === 'LineString' ? [geometry.coordinates] : geometry.type === 'MultiLineString' ? geometry.coordinates : []
        // 큰길은 지도 자료에 가는 방향·오는 방향이 선 두 개로 따로 들어 있음(일방통행 표시) → 한쪽 선은 폭을 절반쯤으로
        const width = feature.properties.oneway === 1 ? Math.max(4, kind[0] * ONE_WAY_RATIO) : kind[0]
        const isOneWay = feature.properties.oneway === 1 // 큰길이 가는 쪽·오는 쪽 두 선으로 나뉜 것 중 한쪽 (골목이 아님)
        const isBridge = feature.properties.brunnel === 'bridge'
        for (const line of lines) addRoadPieces(roads, line, [width, kind[1], isOneWay, isBridge], isInBox, toMeters)
      }
    }
  }
  return roads
}

// 벡터 타일 한 조각에서 층 하나(길 'transportation', 물 'water')
async function fetchLayer(url, layerName) {
  try {
    const response = await fetch(url)
    if (!response.ok) return null
    return new VectorTile(new PbfReader(new Uint8Array(await response.arrayBuffer()))).layers[layerName] || null
  } catch (e) {
    return null // 못 받은 조각은 건너뜀
  }
}

// 선 하나를 범위 안에 있는 토막들로 나눠 담음 (범위 밖 점을 만나면 거기서 끊고 새 토막)
function addRoadPieces(roads, line, kind, isInBox, toMeters) {
  let points = []
  for (const position of line) {
    if (isInBox(position[0], position[1])) {
      const at = toMeters(position)
      points.push(at[0], at[1])
    } else {
      if (points.length >= 4) roads.push(makeRoad(points, kind))
      points = []
    }
  }
  if (points.length >= 4) roads.push(makeRoad(points, kind))
}

// 길 하나. kind: [폭, 종류, 일방통행 한쪽인지, 다리인지]
function makeRoad(points, kind) {
  const road = { points: points, width: kind[0], kind: kind[1], isOneWay: kind[2] === true }
  if (kind[3]) road.isBridge = true
  return road
}
