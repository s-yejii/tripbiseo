// ⑤ 여행 하나의 실감 3D 지도 자료 만들기 (11/13)
// 웹 회고를 열면 그 여행의 GPS·장소로 지도 자료(JSON)와 바닥 사진을 이 브라우저에서 만들어 유니티에 넘김
//   만드는 일은 unityExport.js가 함 (높이·건물·길·나무·강·해 위치). 여기서는 여행 자료를 그 모양으로 바꿔 줌
//   한 번 만든 자료는 화면을 닫기 전까지 기억해 둠 (같은 여행을 다시 켜면 바로 뜨게)
import { buildMapData, buildGroundPhoto } from './unityExport'

const METERS_PER_DEGREE_LAT = 110540
const METERS_PER_DEGREE_LNG = 111320
const MARGIN_M = 500        // 경로 바깥으로 이만큼 더 담음
const MIN_HALF_M = 1000     // 지도 반 변 최소 (너무 좁으면 주변이 안 보여서)
const MAX_HALF_M = 6500     // 최대 (너무 넓으면 만들기가 오래 걸려서)
const PHOTO_PX = 4096       // 바닥 사진 한 변 (웹에서 빨리 받으려고 시연용 8192의 절반)
const CLEAN_RADIUS_M = 260  // 걸은 길 둘레 이 안은 위성 사진 대신 지도 자료로 깔끔하게 칠함
const WALK_MAX_KMH = 10     // 이보다 느리면 걸은 것 (바닥을 새로 칠할 점)
const SATELLITE_URL = 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}'

const made = new Map() // 여행 번호 → { json, photo } (이번에 만든 것)

/**
 * 여행 하나의 지도 자료
 * retro: GET /api/trips/{id}/retro 결과 (track: [{ lng, lat, day, time }], stops: [{ lng, lat }], startDate)
 * nodes: 웹 재생이 쓰는 경로 지점 [{ lng, lat, from, to }] (분: 첫날 0시부터). 머문 곳은 GPS 여러 점이 한 지점으로 묶여 있음
 * guNames: 가로수를 읽을 구 이름들, onStep(글): 지금 무엇을 만드는지 알려 줌
 * 돌려줌: { json: 지도 자료 글자, photo: 바닥 사진(base64) }
 */
export async function makeTripMap(tripId, retro, nodes, guNames, onStep) {
  if (made.has(tripId)) return made.get(tripId)
  const startClock = nodes[0].from // 경로 0분 = 첫 지점 시각
  const track = toTrack(nodes, startClock)
  const stops = retro.stops.filter((stop) => stop.lat !== null && stop.lng !== null).map((stop) => ({ lng: stop.lng, lat: stop.lat }))
  const area = areaAround([...track, ...stops]) // ...: 두 목록을 이어 붙인 새 목록
  const dayCount = Math.max(...retro.track.map((point) => point.day))

  onStep('지도 자료를 모으는 중 (땅 높이·건물·길·나무)…')
  const tileUrl = (await (await fetch('https://tiles.openfreemap.org/planet')).json()).tiles[0]
  const forest = await (await fetch('/geo/forest-seoul.json')).json()
  const data = await buildMapData({
    center: area.center, halfM: area.halfM, tileUrl: tileUrl, dateText: retro.startDate, forest: forest, guNames: guNames,
    track: track, stops: stops, gridStepM: area.halfM > 3000 ? 30 : 20, isCorridor: true, startClock: startClock, dayCount: dayCount,
  })
  onStep('바닥 사진을 만드는 중…')
  const clean = { tileUrl: tileUrl, points: walkedPoints(track), radiusM: CLEAN_RADIUS_M }
  const photo = await buildGroundPhoto(area.center, area.halfM, SATELLITE_URL, PHOTO_PX, forest, retro.startDate, clean)
  const result = { json: JSON.stringify(data), photo: photo }
  made.set(tripId, result)
  return result
}

// 재생 지점 → 3D 경로 점: 머문 곳은 도착 시각·떠난 시각 두 점을 같은 자리에 (그 사이 3D에서 가만히 있음)
function toTrack(nodes, startClock) {
  const track = []
  for (const node of nodes) {
    track.push({ lng: node.lng, lat: node.lat, minute: node.from - startClock })
    if (node.to > node.from) track.push({ lng: node.lng, lat: node.lat, minute: node.to - startClock })
  }
  return track
}

// 점들을 감싸는 정사각형 지도: 가운데 [경도, 위도]와 반 변(m)
function areaAround(points) {
  let west = Infinity, east = -Infinity, south = Infinity, north = -Infinity
  for (const point of points) {
    west = Math.min(west, point.lng)
    east = Math.max(east, point.lng)
    south = Math.min(south, point.lat)
    north = Math.max(north, point.lat)
  }
  const center = [(west + east) / 2, (south + north) / 2]
  const lngScale = METERS_PER_DEGREE_LNG * Math.cos((center[1] * Math.PI) / 180)
  const widthM = (east - west) * lngScale
  const heightM = (north - south) * METERS_PER_DEGREE_LAT
  const halfM = Math.max(widthM, heightM) / 2 + MARGIN_M
  return { center: center, halfM: Math.round(Math.min(MAX_HALF_M, Math.max(MIN_HALF_M, halfM))) }
}

// 걸은 점만 (지하철·택시처럼 빠른 구간은 빼고) → 이 둘레 바닥을 새로 칠함
function walkedPoints(track) {
  const points = []
  for (let k = 1; k < track.length; k++) {
    const hours = (track[k].minute - track[k - 1].minute) / 60
    const km = distanceMeters(track[k - 1], track[k]) / 1000
    if (hours > 0 && km / hours <= WALK_MAX_KMH) points.push([track[k].lng, track[k].lat])
  }
  return points
}

function distanceMeters(a, b) {
  const lngScale = METERS_PER_DEGREE_LNG * Math.cos((a.lat * Math.PI) / 180)
  return Math.hypot((b.lng - a.lng) * lngScale, (b.lat - a.lat) * METERS_PER_DEGREE_LAT)
}
