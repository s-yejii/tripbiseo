<script setup>
// W19 웹 회고 (⑤, 11/09 중간발표 피드백: "사용자는 앱, 자세한 건 웹")
// 앱 회고와 같은 API(retro·payments·companions)를 읽어서 PC 화면에 크게 보여 줘요
//  - 3D 서울 지도(건물·지형) 위에 다녀온 동네(행정동)가 머문 시간만큼 솟아오름
//  - 실제 경로(GPS, 코랄) vs 계획 경로(장소 순서, 회색 점선)
//  - 내비게이션처럼 카메라가 이동 경로를 따라가며 재생
//  - 오른쪽: 계획 vs 실제 표, 비용, 정산 (고치기는 앱 회고에서)
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import * as maplibregl from 'maplibre-gl' // MapLibre: 3D(기울이기·건물·지형)가 되는 무료 지도 라이브러리 (키 없음)
import 'maplibre-gl/dist/maplibre-gl.css'
// 지도 계산을 하는 뒤쪽 일꾼(worker) 파일 주소. Vite가 라이브러리를 옮겨 담아서 기본 주소를 못 찾기 때문에 직접 알려 줌
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?url'
import { get } from '@/api/http'
import { toTime, toMinutes, toDuration, toWon } from '@/utils/time'

const STYLE_URL = 'https://tiles.openfreemap.org/styles/liberty' // OpenFreeMap 지도 (OpenStreetMap 기반, 건물 높이 있음)
const DEM_TILES = 'https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png' // 지형 높이 그림 (AWS 공개 데이터)
const SATELLITE_TILES = 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}' // Esri 위성 사진
const SEOUL_CENTER = [126.978, 37.5665]
const TERRAIN_EXAGGERATION = 1.5 // 지형을 실제보다 1.5배 높게 (서울 산이 잘 보이게)
const MINUTES_PER_DAY = 24 * 60
const RISE_MS = 1800             // 동네가 솟아오르는 시간
const VISIT_BASE_M = 120         // 다녀온 동네 기둥 기본 높이(m)
const VISIT_PER_MINUTE_M = 1.3   // 머문 1분마다 더하는 높이(m)
const VISIT_MAX_MINUTES = 300    // 5시간 넘게 머물러도 높이는 여기까지
const PASS_HEIGHT_M = 30         // 지나가기만 한 동네 높이(m)
const OVERVIEW_PITCH = 55
const NAV_ZOOM = 15.9            // 내비 재생: 골목까지 보이게 확대
const NAV_PITCH = 56
const PLAY_SECONDS = 50          // 1배속에서 고른 날 전체를 재생하는 시간(초)
const LOOK_AHEAD_POINTS = 3      // 카메라 방향: 이만큼 앞 점을 바라봄
const MIN_TURN_METERS = 15       // 앞 점이 이보다 가까우면(머무는 중) 방향을 안 바꿈
const BEARING_SMOOTH = 0.06      // 카메라 방향을 프레임마다 이만큼씩만 돌림 (덜 흔들리게)

const route = useRoute()
const retro = ref(null)
const payments = ref([])
const companions = ref([])
const errorMessage = ref('')
const isLoading = ref(true)
const selectedDay = ref(0)        // 0 = 전체, 1 = 1일차 …
const isSatellite = ref(false)
const is3d = ref(true)
const playing = ref(false)
const speed = ref(1)              // 1·2·4배
const progress = ref(0)           // 재생 위치 = 보여 주는 GPS 점 번호 (소수, 예: 12.4 = 12번과 13번 사이)
const isFollow = ref(true)        // 재생 중 카메라가 따라가는지
const mePosition = ref(null)      // 재생 중 지금 위치 [경도, 위도]
const geoReady = ref(false)       // 서울 동·구 경계를 다 읽었는지
const mapBox = ref(null)

// 지도 관련 값은 화면 반응(ref)이 필요 없어서 보통 변수로 둠
let map = null
let dongGeo = null                // 서울 행정동 427개 (public/geo/seoul-dong.json)
let guGeo = null                  // 서울 구 25개
let stopMarkers = {}              // 일정 칸 id → 지도 핀
let otherMarkers = []             // 계획 밖 머문 곳 핀, 동네 이름표
let meMarker = null
let riseFactor = 0                // 동네 기둥 높이 배율 (0 = 납작, 1 = 다 솟음)
let riseFrame = null
let playFrame = null
let lastFrameTime = 0
let smoothBearing = -20
let introTimers = []

// ============================== 데이터 읽기
onMounted(async () => {
  try {
    const id = route.query.id
    retro.value = await get('/trips/' + id + '/retro')
    payments.value = await get('/trips/' + id + '/payments')
    companions.value = await get('/trips/' + id + '/companions')
    document.title = retro.value.title + ' · 웹 회고'
  } catch (e) {
    errorMessage.value = e.status === 401 ? '앱에서 로그인한 뒤 다시 열어 주세요' : '회고를 불러오지 못했어요 (' + e.message + ')'
    isLoading.value = false
    return
  }
  await loadGeo()
  isLoading.value = false
  createMap()
})

onUnmounted(() => {
  cancelAnimationFrame(playFrame)
  cancelAnimationFrame(riseFrame)
  for (const timer of introTimers) clearTimeout(timer)
  if (map) map.remove()
})

// 서울 동·구 경계 파일 읽기 + 동마다 사각 범위(bbox)를 미리 계산 (점이 어느 동인지 빨리 찾으려고)
async function loadGeo() {
  try {
    dongGeo = await (await fetch('/geo/seoul-dong.json')).json()
    guGeo = await (await fetch('/geo/seoul-gu.json')).json()
    for (const feature of dongGeo.features) feature.bbox = toBbox(feature)
    geoReady.value = true
  } catch (e) {
    geoReady.value = false // 경계가 없어도 경로·핀은 보여 줌
  }
}

// ============================== 계산 도우미
// 몇째 날 + "HH:mm" → 여행 첫날 0시부터 몇 분. 예: 2일차 09:30 → 1440 + 570 = 2010
function tripMinutes(day, time) {
  return (day - 1) * MINUTES_PER_DAY + toMinutes(time)
}

// 2010 → "2일차 09:30"
function toDayTime(minutes) {
  const day = Math.floor(minutes / MINUTES_PER_DAY) + 1
  return day + '일차 ' + toTime(Math.round(minutes) % MINUTES_PER_DAY)
}

// 두 점 사이 거리(m), 하버사인 공식
function distanceMeters(lat1, lng1, lat2, lng2) {
  const R = 6371000
  const toRad = Math.PI / 180
  const dLat = (lat2 - lat1) * toRad
  const dLng = (lng2 - lng1) * toRad
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1 * toRad) * Math.cos(lat2 * toRad) * Math.sin(dLng / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(h))
}

// a → b 방향(도, 북쪽 0 · 동쪽 90)
function bearingOf(a, b) {
  const toRad = Math.PI / 180
  const y = Math.sin((b[0] - a[0]) * toRad) * Math.cos(b[1] * toRad)
  const x = Math.cos(a[1] * toRad) * Math.sin(b[1] * toRad) - Math.sin(a[1] * toRad) * Math.cos(b[1] * toRad) * Math.cos((b[0] - a[0]) * toRad)
  return Math.atan2(y, x) / toRad
}

// 각도를 조금씩 돌리기 (350° → 10°는 20°만 돌게)
function turnToward(from, to, rate) {
  let diff = ((to - from + 540) % 360) - 180
  return from + diff * rate
}

function toBbox(feature) {
  let minX = 180, minY = 90, maxX = -180, maxY = -90
  for (const polygon of feature.geometry.coordinates) {
    for (const [x, y] of polygon[0]) {
      if (x < minX) minX = x
      if (y < minY) minY = y
      if (x > maxX) maxX = x
      if (y > maxY) maxY = y
    }
  }
  return [minX, minY, maxX, maxY]
}

// 점이 다각형 테두리(ring) 안인가: 점에서 오른쪽으로 선을 그어 테두리를 홀수 번 지나면 안쪽
function isInRing(lng, lat, ring) {
  let inside = false
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const [xi, yi] = ring[i]
    const [xj, yj] = ring[j]
    const crosses = (yi > lat) !== (yj > lat) && lng < ((xj - xi) * (lat - yi)) / (yj - yi) + xi
    if (crosses) inside = !inside
  }
  return inside
}

// 점이 이 동 안인가 (MultiPolygon: 조각 여러 개, 각 조각의 0번은 바깥 테두리·나머지는 구멍)
function isInFeature(lng, lat, feature) {
  const [minX, minY, maxX, maxY] = feature.bbox
  if (lng < minX || lng > maxX || lat < minY || lat > maxY) return false
  for (const polygon of feature.geometry.coordinates) {
    if (!isInRing(lng, lat, polygon[0])) continue
    let isInHole = false
    for (let h = 1; h < polygon.length; h++) {
      if (isInRing(lng, lat, polygon[h])) isInHole = true
    }
    if (!isInHole) return true
  }
  return false
}

function findDong(lng, lat) {
  if (!dongGeo) return null
  for (const feature of dongGeo.features) {
    if (isInFeature(lng, lat, feature)) return feature
  }
  return null
}

// ============================== 화면에 보여 줄 목록 (고른 날 기준)
const dayCount = computed(() => {
  if (!retro.value) return 1
  const start = new Date(retro.value.startDate)
  const end = new Date(retro.value.endDate)
  return Math.round((end - start) / 86400000) + 1 // 86400000 = 하루(밀리초)
})

const dayButtons = computed(() => {
  const list = [{ day: 0, label: '전체' }]
  for (let day = 1; day <= dayCount.value; day++) list.push({ day: day, label: day + '일차' })
  return list
})

function isShownDay(day) {
  return selectedDay.value === 0 || day === selectedDay.value
}

// 결제 중 이 일정 칸에서 낸 돈 합계
function costOfStop(stopId) {
  let sum = 0
  for (const payment of payments.value) {
    if (payment.stopId === stopId) sum += payment.amount
  }
  return sum
}

// 일정 칸 (번호, 다녀왔는지, 계획보다 몇 분 더/덜, 비용)
const shownStops = computed(() => {
  if (!retro.value) return []
  const rows = []
  for (const stop of retro.value.stops) {
    if (!isShownDay(stop.day)) continue
    const isVisited = stop.actualArrive !== null
    let diff = null
    if (isVisited && stop.actualStay !== null) diff = stop.actualStay - stop.stay
    rows.push(Object.assign({}, stop, { no: rows.length + 1, isVisited: isVisited, diff: diff, cost: costOfStop(stop.id) }))
  }
  return rows
})

const shownTrack = computed(() => {
  if (!retro.value) return []
  const list = []
  for (const point of retro.value.track) {
    if (isShownDay(point.day)) list.push(point)
  }
  return list
})

const shownExtras = computed(() => {
  if (!retro.value) return []
  const list = []
  for (const extra of retro.value.extraStops) {
    if (isShownDay(extra.day)) list.push(extra)
  }
  return list
})

const shownPayments = computed(() => {
  const list = []
  for (const payment of payments.value) {
    if (isShownDay(payment.day)) list.push(payment)
  }
  return list
})

// 위쪽 숫자 4개
const summary = computed(() => {
  let visited = 0
  let skipped = 0
  let overMinutes = 0 // 계획보다 더 머문 분 합
  for (const row of shownStops.value) {
    if (row.isVisited) visited++
    if (row.state === 'skipped') skipped++
    if (row.diff !== null && row.diff > 0) overMinutes += row.diff
  }
  let meters = 0
  const track = shownTrack.value
  for (let i = 1; i < track.length; i++) {
    meters += distanceMeters(track[i - 1].lat, track[i - 1].lng, track[i].lat, track[i].lng)
  }
  let cost = 0
  for (const payment of shownPayments.value) cost += payment.amount
  const people = retro.value ? retro.value.people || 1 : 1
  return { visited, total: shownStops.value.length, skipped, overMinutes, meters, cost, perPerson: Math.round(cost / people / 10) * 10 }
})

const movedText = computed(() => {
  const meters = summary.value.meters
  return meters < 1000 ? Math.round(meters) + 'm' : (meters / 1000).toFixed(1) + 'km'
})

// 가장 많이 늦어진 곳 (회고 한 줄)
const biggestOver = computed(() => {
  let best = null
  for (const row of shownStops.value) {
    if (row.diff !== null && row.diff > 0 && (best === null || row.diff > best.diff)) best = row
  }
  return best
})

// 분류별 비용 (일정 칸 분류의 첫 낱말, 칸이 없는 결제는 "이동·기타")
const costByCategory = computed(() => {
  const sums = {}
  for (const payment of shownPayments.value) {
    let category = '이동·기타'
    for (const stop of retro.value.stops) {
      if (stop.id === payment.stopId && stop.category) category = stop.category.split(',')[0]
    }
    sums[category] = (sums[category] || 0) + payment.amount
  }
  const list = []
  let max = 1
  for (const name in sums) {
    list.push({ name: name, amount: sums[name] })
    if (sums[name] > max) max = sums[name]
  }
  list.sort((a, b) => b.amount - a.amount)
  for (const item of list) item.percent = Math.round((item.amount / max) * 100)
  return list
})

// ============================== 다닌 동네 (머문 곳이 들어 있는 행정동)
function addDongStay(stats, dong, placeName, minutes) {
  const code = dong.properties.code
  if (!stats[code]) {
    stats[code] = { code: code, name: dong.properties.name, gu: dong.properties.gu, count: 0, stay: 0, places: [], feature: dong }
  }
  stats[code].count++
  stats[code].stay += minutes
  stats[code].places.push(placeName)
}

const visitedDongs = computed(() => {
  if (!geoReady.value) return []
  const stats = {} // 동 코드 → { name, gu, count, stay, places }
  for (const row of shownStops.value) {
    if (!row.isVisited || row.lat === null) continue
    const dong = findDong(row.lng, row.lat)
    if (dong) addDongStay(stats, dong, row.name, row.actualStay !== null ? row.actualStay : row.stay)
  }
  for (const extra of shownExtras.value) {
    const dong = findDong(extra.lng, extra.lat)
    // 숙소로 보이는 곳(밤새 머묾)은 시간이 너무 길어서 높이에는 안 넣고 곳 수만 셈
    if (dong) addDongStay(stats, dong, extra.kind === 'lodging' ? '숙소' : extra.name || '계획 밖', extra.kind === 'lodging' ? 0 : extra.stay)
  }
  const list = Object.values(stats)
  list.sort((a, b) => b.stay - a.stay)
  return list
})

// GPS가 지나가기만 한 동 (머문 동 빼고)
const passedDongs = computed(() => {
  if (!geoReady.value) return []
  const visitedCodes = new Set()
  for (const dong of visitedDongs.value) visitedCodes.add(dong.code)
  const found = {}
  for (const point of shownTrack.value) {
    const dong = findDong(point.lng, point.lat)
    if (dong && !visitedCodes.has(dong.properties.code)) found[dong.properties.code] = dong
  }
  return Object.values(found)
})

// 머문 시간 글자 (숙소만 있는 동은 시간 대신 "숙소")
function dongStayText(dong) {
  return dong.stay > 0 ? toDuration(dong.stay) : '숙소'
}

const visitedGuText = computed(() => {
  const names = []
  for (const dong of visitedDongs.value) {
    if (!names.includes(dong.gu)) names.push(dong.gu)
  }
  return names.join(' · ')
})

// ============================== 정산 (앱 회고와 같은 계산, 여기서는 보기만)
const settlement = computed(() => {
  const list = companions.value
  if (list.length < 2 || payments.value.length === 0) return null
  let total = 0
  const paid = {}
  for (const companion of list) paid[companion.name] = 0
  for (const payment of payments.value) {
    total += payment.amount
    if (paid[payment.payer] !== undefined) paid[payment.payer] += payment.amount
  }
  let fixedTotal = 0
  const autoNames = []
  for (const companion of list) {
    if (companion.shareAmount !== null) fixedTotal += companion.shareAmount
    else autoNames.push(companion.name)
  }
  const rest = total - fixedTotal
  let autoShare = 0
  if (autoNames.length > 0) autoShare = Math.floor(rest / autoNames.length / 10) * 10
  const people = []
  for (const companion of list) {
    let should = companion.shareAmount
    if (should === null) should = companion.name === autoNames[0] ? rest - autoShare * (autoNames.length - 1) : autoShare
    people.push({ name: companion.name, paid: paid[companion.name], should: should, balance: paid[companion.name] - should })
  }
  return { total: total, people: people, transfers: toTransfers(people) }
})

// 보낼 사람이 받을 사람에게 차례로 보내기 (앱 회고 toTransfers와 같음)
function toTransfers(people) {
  const receivers = []
  for (const person of people) {
    if (person.balance > 0) receivers.push({ name: person.name, left: person.balance })
  }
  const transfers = []
  for (const person of people) {
    let owe = -person.balance
    for (const receiver of receivers) {
      if (owe <= 0) break
      if (receiver.left <= 0) continue
      const amount = Math.min(owe, receiver.left)
      transfers.push({ from: person.name, to: receiver.name, amount: amount })
      owe -= amount
      receiver.left -= amount
    }
  }
  return transfers
}

// ============================== 지도 만들기
function createMap() {
  maplibregl.setWorkerUrl(workerUrl)
  map = new maplibregl.Map({
    container: mapBox.value,
    style: STYLE_URL,
    center: SEOUL_CENTER,
    zoom: 10.4,
    pitch: 45,
    bearing: -10,
    maxPitch: 75,
    attributionControl: { compact: true },
  })
  map.addControl(new maplibregl.NavigationControl({ visualizePitch: true }), 'bottom-right')
  map.on('load', () => {
    useKoreanLabels()
    map.setPaintProperty('building-3d', 'fill-extrusion-opacity', 0.72) // 건물을 살짝 비치게 (뒤 경로가 보이게)
    addTerrain()
    addSatellite()
    addBoundaryLayers()
    addRouteLayers()
    drawDay()
    runIntro()
  })
}

// 지명은 한국어 이름 먼저 (없으면 기본 이름)
function useKoreanLabels() {
  for (const layer of map.getStyle().layers) {
    if (layer.type === 'symbol' && layer.layout && layer.layout['text-field']) {
      map.setLayoutProperty(layer.id, 'text-field', ['coalesce', ['get', 'name:ko'], ['get', 'name']])
    }
  }
}

// 지형: 높이 그림(DEM)으로 땅을 올리고, 산 그림자(hillshade)를 깔기
function addTerrain() {
  map.addSource('dem', { type: 'raster-dem', tiles: [DEM_TILES], encoding: 'terrarium', tileSize: 256, maxzoom: 14 })
  map.addSource('dem-shade', { type: 'raster-dem', tiles: [DEM_TILES], encoding: 'terrarium', tileSize: 256, maxzoom: 14 })
  map.addLayer({ id: 'hillshade', type: 'hillshade', source: 'dem-shade', paint: { 'hillshade-exaggeration': 0.35, 'hillshade-shadow-color': '#5a6b86' } }, 'building')
  map.setTerrain({ source: 'dem', exaggeration: TERRAIN_EXAGGERATION })
  try {
    map.setSky({ 'sky-color': '#a9c8ef', 'horizon-color': '#e9f0fa', 'fog-color': '#e9f0fa', 'sky-horizon-blend': 0.6, 'horizon-fog-blend': 0.7, 'fog-ground-blend': 0.7 })
  } catch (e) {
    // 하늘 색은 없어도 됨
  }
}

function addSatellite() {
  map.addSource('satellite', { type: 'raster', tiles: [SATELLITE_TILES], tileSize: 256, attribution: 'Esri, Maxar, Earthstar Geographics' })
  map.addLayer({ id: 'satellite', type: 'raster', source: 'satellite', layout: { visibility: 'none' } }, 'building')
}

// 구 경계선 + 동네 기둥(fill-extrusion: 다각형을 높이만큼 세우는 3D 레이어)
function addBoundaryLayers() {
  const empty = { type: 'FeatureCollection', features: [] }
  map.addSource('gu', { type: 'geojson', data: guGeo || empty })
  map.addLayer({ id: 'gu-line', type: 'line', source: 'gu', paint: { 'line-color': '#1F3A68', 'line-opacity': 0.45, 'line-width': 1.4 } })
  map.addSource('dong-rise', { type: 'geojson', data: empty })
  // 바닥 색: 기둥을 눕힌 뒤에도 다닌 동네가 보이게 (경로 선보다 아래)
  map.addLayer({ id: 'dong-fill', type: 'fill', source: 'dong-rise', paint: {
    'fill-color': ['case', ['==', ['get', 'kind'], 'visit'], '#E8603F', '#8FA6CF'],
    'fill-opacity': ['case', ['==', ['get', 'kind'], 'visit'], 0.22, 0.14] } })
  map.addLayer({
    id: 'dong-rise',
    type: 'fill-extrusion',
    source: 'dong-rise',
    paint: {
      'fill-extrusion-color': ['case', ['==', ['get', 'kind'], 'visit'],
        ['interpolate', ['linear'], ['get', 'stay'], 0, '#F6B9A6', 120, '#E8603F', 300, '#A9412A'],
        '#8FA6CF'],
      'fill-extrusion-height': ['*', ['get', 'h'], 0],
      'fill-extrusion-base': 0,
      'fill-extrusion-opacity': 0.55,
    },
  })
  map.addLayer({ id: 'dong-line', type: 'line', source: 'dong-rise', filter: ['==', ['get', 'kind'], 'visit'], paint: { 'line-color': '#A9412A', 'line-width': 2 } })
}

// 계획 경로(회색 점선), 실제 경로(코랄), 재생으로 지나온 부분(진한 갈색)
function addRouteLayers() {
  const emptyLine = { type: 'Feature', geometry: { type: 'LineString', coordinates: [] } }
  map.addSource('plan', { type: 'geojson', data: emptyLine })
  map.addSource('track', { type: 'geojson', data: emptyLine })
  map.addSource('track-done', { type: 'geojson', data: emptyLine })
  map.addLayer({ id: 'plan-line', type: 'line', source: 'plan', layout: { 'line-join': 'round', 'line-cap': 'round' },
    paint: { 'line-color': '#7C8496', 'line-width': 4, 'line-dasharray': [1.2, 1.4], 'line-opacity': 0.9 } })
  map.addLayer({ id: 'track-casing', type: 'line', source: 'track', layout: { 'line-join': 'round', 'line-cap': 'round' },
    paint: { 'line-color': '#FFFFFF', 'line-width': 8, 'line-opacity': 0.9 } })
  map.addLayer({ id: 'track-line', type: 'line', source: 'track', layout: { 'line-join': 'round', 'line-cap': 'round' },
    paint: { 'line-color': '#E8603F', 'line-width': 5 } })
  map.addLayer({ id: 'track-done', type: 'line', source: 'track-done', layout: { 'line-join': 'round', 'line-cap': 'round' },
    paint: { 'line-color': '#A9412A', 'line-width': 7 } })
}

// ============================== 고른 날 다시 그리기
function drawDay() {
  if (!map) return
  map.getSource('dong-rise').setData(toDongCollection())
  map.getSource('plan').setData(toLine(planCoordinates()))
  map.getSource('track').setData(toLine(trackCoordinates()))
  map.getSource('track-done').setData(toLine([]))
  drawMarkers()
  setRise(riseFactor)
}

function toLine(coordinates) {
  return { type: 'Feature', geometry: { type: 'LineString', coordinates: coordinates } }
}

// 계획 경로 = 좌표 있는 일정 칸을 계획 순서대로 이은 선 (건너뛴 곳 포함)
function planCoordinates() {
  const list = []
  for (const row of shownStops.value) {
    if (row.lat !== null) list.push([row.lng, row.lat])
  }
  return list
}

function trackCoordinates() {
  const list = []
  for (const point of shownTrack.value) list.push([point.lng, point.lat])
  return list
}

// 동네 기둥 데이터: 머문 동은 머문 시간만큼 높게, 지나간 동은 낮게
function toDongCollection() {
  const features = []
  for (const dong of visitedDongs.value) {
    const height = VISIT_BASE_M + Math.min(dong.stay, VISIT_MAX_MINUTES) * VISIT_PER_MINUTE_M
    features.push({ type: 'Feature', geometry: dong.feature.geometry, properties: { kind: 'visit', h: height, stay: dong.stay } })
  }
  for (const dong of passedDongs.value) {
    features.push({ type: 'Feature', geometry: dong.geometry, properties: { kind: 'pass', h: PASS_HEIGHT_M, stay: 0 } })
  }
  return { type: 'FeatureCollection', features: features }
}

function setRise(factor) {
  riseFactor = factor
  if (map && map.getLayer('dong-rise')) {
    map.setPaintProperty('dong-rise', 'fill-extrusion-height', ['*', ['get', 'h'], factor])
  }
}

// 동네 기둥 보이기·숨기기 (내비 재생 중에는 숨김, 테두리 선은 그대로)
function showDongs(isShown) {
  if (map && map.getLayer('dong-rise')) map.setLayoutProperty('dong-rise', 'visibility', isShown && is3d.value ? 'visible' : 'none')
}

// 동네 기둥 높이를 from → to로 ms 동안 부드럽게
function animateRise(to, ms, onDone) {
  cancelAnimationFrame(riseFrame)
  const from = riseFactor
  const startTime = performance.now()
  function frame(now) {
    const t = Math.min(1, (now - startTime) / ms)
    const eased = 1 - Math.pow(1 - t, 3) // 처음엔 빠르게, 끝에서 천천히
    setRise(from + (to - from) * eased)
    if (t < 1) riseFrame = requestAnimationFrame(frame)
    else if (onDone) onDone()
  }
  riseFrame = requestAnimationFrame(frame)
}

// ============================== 핀·이름표 (HTML로 만든 마커)
function clearMarkers() {
  for (const id in stopMarkers) stopMarkers[id].remove()
  for (const marker of otherMarkers) marker.remove()
  stopMarkers = {}
  otherMarkers = []
}

function drawMarkers() {
  clearMarkers()
  for (const row of shownStops.value) {
    if (row.lat === null) continue
    const marker = new maplibregl.Marker({ element: makePin(row), anchor: 'bottom' })
      .setLngLat([row.lng, row.lat])
      .setPopup(new maplibregl.Popup({ offset: 30, maxWidth: '280px' }).setDOMContent(makePopup(row)))
      .addTo(map)
    stopMarkers[row.id] = marker
  }
  for (const extra of shownExtras.value) {
    const element = makeTag('wr-extra', (extra.kind === 'lodging' ? '숙소? ' : '계획 밖 ') + (extra.name || '') + ' · ' + toDuration(extra.stay))
    otherMarkers.push(new maplibregl.Marker({ element: element, anchor: 'bottom' }).setLngLat([extra.lng, extra.lat]).addTo(map))
  }
  for (const dong of visitedDongs.value) {
    const element = document.createElement('div')
    element.className = 'wr-dong'
    element.appendChild(makeTag('wr-dong__name', dong.name))
    element.appendChild(makeTag('wr-dong__info', dong.count + '곳 · ' + dongStayText(dong)))
    otherMarkers.push(new maplibregl.Marker({ element: element, anchor: 'bottom' }).setLngLat(centerOf(dong.feature)).addTo(map))
  }
}

function makeTag(className, text) {
  const element = document.createElement('div')
  element.className = className
  element.textContent = text // textContent: 글자만 넣음 (장소 이름에 HTML이 섞여도 실행 안 됨)
  return element
}

// 일정 칸 핀: 번호 동그라미 + 이름 + (낸 돈)
function makePin(row) {
  const element = document.createElement('button')
  element.type = 'button'
  element.className = 'wr-pin' + (row.isVisited ? '' : ' wr-pin--skip') + (row.diff !== null && row.diff >= 10 ? ' wr-pin--over' : '')
  element.appendChild(makeTag('wr-pin__no', String(row.no)))
  element.appendChild(makeTag('wr-pin__name', row.name + (row.cost > 0 ? ' · ' + toWon(row.cost) : '')))
  return element
}

// 핀을 누르면 뜨는 칸: 계획 vs 실제 + 결제
function makePopup(row) {
  const box = document.createElement('div')
  box.className = 'wr-pop'
  box.appendChild(makeTag('wr-pop__title', row.no + '. ' + row.name))
  box.appendChild(makeTag('wr-pop__line', '계획 ' + row.day + '일차 ' + row.time + ' · ' + toDuration(row.stay)))
  if (row.isVisited) {
    const stayText = row.actualStay !== null ? ' · ' + toDuration(row.actualStay) : ''
    box.appendChild(makeTag('wr-pop__line wr-pop__line--real', '실제 ' + row.actualArrive + stayText))
  } else {
    box.appendChild(makeTag('wr-pop__line', '안 감 (건너뜀)'))
  }
  if (row.diff !== null && row.diff !== 0) box.appendChild(makeTag('wr-pop__diff', diffText(row.diff)))
  for (const payment of payments.value) {
    if (payment.stopId === row.id) box.appendChild(makeTag('wr-pop__pay', payment.paidAt + ' ' + payment.storeName + ' ' + toWon(payment.amount) + ' (' + payment.payer + ')'))
  }
  return box
}

function diffText(diff) {
  if (diff > 0) return '계획보다 ' + toDuration(diff) + ' 더'
  return '계획보다 ' + toDuration(-diff) + ' 덜'
}

// 동 이름표 자리 = 그 동의 바깥 테두리 점들의 평균
function centerOf(feature) {
  let sumX = 0, sumY = 0, count = 0
  for (const polygon of feature.geometry.coordinates) {
    for (const [x, y] of polygon[0]) {
      sumX += x
      sumY += y
      count++
    }
  }
  return [sumX / count, sumY / count]
}

// ============================== 카메라
// 처음: 서울 전체 → 다닌 동네가 솟아오름 → 여행한 곳으로 날아감
function runIntro() {
  for (const timer of introTimers) clearTimeout(timer)
  stopPlay()
  showDongs(true)
  setRise(0)
  map.jumpTo({ center: SEOUL_CENTER, zoom: 10.4, pitch: is3d.value ? 45 : 0, bearing: is3d.value ? -10 : 0 })
  introTimers = [
    setTimeout(() => animateRise(1, RISE_MS), 500),
    setTimeout(() => fitTrip(2600), 2600),
    setTimeout(() => animateRise(0, 1400, () => showDongs(false)), 3600), // 다 날아가면 기둥을 눕혀서 바닥 색만 (경로가 보이게)
  ]
}

// 고른 날의 장소·GPS가 다 보이게
function fitTrip(duration) {
  const bounds = new maplibregl.LngLatBounds()
  for (const point of planCoordinates()) bounds.extend(point)
  for (const point of trackCoordinates()) bounds.extend(point)
  if (bounds.isEmpty()) return
  const camera = map.cameraForBounds(bounds, { padding: { top: 140, bottom: 170, left: 80, right: 80 } })
  if (!camera) return
  map.flyTo({ center: camera.center, zoom: Math.min(camera.zoom, 15.5), pitch: is3d.value ? OVERVIEW_PITCH : 0, bearing: is3d.value ? -20 : 0, duration: duration, essential: true })
}

function focusStop(row) {
  if (row.lat === null || !map) return
  stopPlay()
  map.flyTo({ center: [row.lng, row.lat], zoom: 16.5, pitch: is3d.value ? 60 : 0, duration: 1600, essential: true })
  const marker = stopMarkers[row.id]
  if (marker && !marker.getPopup().isOpen()) marker.togglePopup()
}

function focusDong(dong) {
  stopPlay()
  map.flyTo({ center: centerOf(dong.feature), zoom: 14.2, pitch: is3d.value ? 58 : 0, duration: 1600, essential: true })
}

// ============================== 지도 고르기
function changeDay(day) {
  selectedDay.value = day
}

watch(selectedDay, () => {
  stopPlay()
  progress.value = 0
  mePosition.value = null
  if (meMarker) {
    meMarker.remove()
    meMarker = null
  }
  drawDay()
  fitTrip(1800)
})

function toggleSatellite() {
  isSatellite.value = !isSatellite.value
  map.setLayoutProperty('satellite', 'visibility', isSatellite.value ? 'visible' : 'none')
}

function toggle3d() {
  is3d.value = !is3d.value
  map.setTerrain(is3d.value ? { source: 'dem', exaggeration: TERRAIN_EXAGGERATION } : null)
  map.setLayoutProperty('building-3d', 'visibility', is3d.value ? 'visible' : 'none')
  if (!is3d.value) showDongs(false) // 2D에서는 기둥 없이 바닥 색만
  map.easeTo({ pitch: is3d.value ? OVERVIEW_PITCH : 0, bearing: is3d.value ? -20 : 0, duration: 900 })
}

// ============================== 내비 재생
const lastIndex = computed(() => Math.max(0, shownTrack.value.length - 1))
const canPlay = computed(() => shownTrack.value.length >= 2)

// 소수 번호 위치의 좌표 (두 점 사이를 비율로)
function positionAt(index) {
  const track = shownTrack.value
  const i = Math.min(Math.floor(index), track.length - 1)
  const next = Math.min(i + 1, track.length - 1)
  const rate = index - i
  return [track[i].lng + (track[next].lng - track[i].lng) * rate, track[i].lat + (track[next].lat - track[i].lat) * rate]
}

// 지금 재생 시각 (여행 첫날 0시부터 분)
const nowMinutes = computed(() => {
  const track = shownTrack.value
  if (track.length === 0) return 0
  const i = Math.min(Math.floor(progress.value), track.length - 1)
  const next = Math.min(i + 1, track.length - 1)
  const from = tripMinutes(track[i].day, track[i].time)
  const to = tripMinutes(track[next].day, track[next].time)
  return from + (to - from) * (progress.value - i)
})

// 내비 카드: 지금 머무는 곳 또는 다음 목적지
const navCard = computed(() => {
  const now = nowMinutes.value
  let next = null
  for (const row of shownStops.value) {
    if (!row.isVisited) continue
    const arrive = tripMinutes(row.day, row.actualArrive)
    const leave = arrive + (row.actualStay !== null ? row.actualStay : row.stay)
    if (arrive <= now && now < leave) return { isStay: true, row: row, minutes: Math.round(now - arrive) }
    if (arrive > now && next === null) next = row
  }
  if (next === null) return null
  let meters = null
  if (mePosition.value) meters = distanceMeters(mePosition.value[1], mePosition.value[0], next.lat, next.lng)
  return { isStay: false, row: next, meters: meters }
})

function metersText(meters) {
  if (meters === null) return ''
  return meters < 1000 ? Math.round(meters / 10) * 10 + 'm' : (meters / 1000).toFixed(1) + 'km'
}

function togglePlay() {
  if (playing.value) stopPlay()
  else startPlay()
}

function startPlay() {
  if (!canPlay.value) return
  if (progress.value >= lastIndex.value) progress.value = 0
  playing.value = true
  isFollow.value = true
  showDongs(false) // 동네 기둥을 숨겨서 길·건물이 보이게 (테두리만 남김)
  smoothBearing = map.getBearing()
  lastFrameTime = performance.now()
  playFrame = requestAnimationFrame(playStep)
}

function stopPlay() {
  playing.value = false
  cancelAnimationFrame(playFrame)
}

function playStep(now) {
  const seconds = (now - lastFrameTime) / 1000
  lastFrameTime = now
  const step = (seconds * speed.value * lastIndex.value) / PLAY_SECONDS
  progress.value = Math.min(lastIndex.value, progress.value + step)
  showProgress()
  if (progress.value >= lastIndex.value) {
    playing.value = false
    return
  }
  playFrame = requestAnimationFrame(playStep)
}

// 재생 위치를 지도에: 내 위치 점, 지나온 선, 카메라
function showProgress() {
  if (!canPlay.value) return
  const position = positionAt(progress.value)
  mePosition.value = position
  if (!meMarker) {
    meMarker = new maplibregl.Marker({ element: makeTag('wr-me', ''), anchor: 'center' }).setLngLat(position).addTo(map)
  } else {
    meMarker.setLngLat(position)
  }
  const done = []
  const track = shownTrack.value
  for (let i = 0; i <= Math.floor(progress.value); i++) done.push([track[i].lng, track[i].lat])
  done.push(position)
  map.getSource('track-done').setData(toLine(done))
  if (isFollow.value) followCamera(position)
}

// 내비처럼: 몇 점 앞을 바라보며 기울여서 따라감
function followCamera(position) {
  const ahead = positionAt(Math.min(lastIndex.value, progress.value + LOOK_AHEAD_POINTS))
  const meters = distanceMeters(position[1], position[0], ahead[1], ahead[0])
  if (meters > MIN_TURN_METERS) smoothBearing = turnToward(smoothBearing, bearingOf(position, ahead), BEARING_SMOOTH)
  map.jumpTo({ center: position, zoom: NAV_ZOOM, pitch: is3d.value ? NAV_PITCH : 0, bearing: smoothBearing })
}

function changeSpeed() {
  speed.value = speed.value === 4 ? 1 : speed.value * 2
}

// 막대를 끌면 그 위치로
const sliderValue = computed({
  get: () => (lastIndex.value === 0 ? 0 : Math.round((progress.value / lastIndex.value) * 1000)),
  set: (value) => {
    progress.value = (Number(value) / 1000) * lastIndex.value
    showProgress()
  },
})

function backToOverview() {
  stopPlay()
  fitTrip(1600)
}
</script>

<template>
  <div class="wr">
    <section class="wr-map">
      <div ref="mapBox" class="wr-map__canvas"></div>

      <div v-if="isLoading || errorMessage" class="wr-state">
        <p>{{ errorMessage || '회고를 불러오는 중…' }}</p>
      </div>

      <template v-if="retro">
        <!-- 위 왼쪽: 여행 제목 -->
        <div class="wr-title">
          <span class="wr-badge">착착트립 · 웹 회고</span>
          <h1>{{ retro.title }}</h1>
          <p>{{ retro.startDate }} ~ {{ retro.endDate }} · {{ retro.people }}명<template v-if="visitedGuText"> · {{ visitedGuText }}</template></p>
        </div>

        <!-- 위 오른쪽: 날짜·지도 고르기 -->
        <div class="wr-tools">
          <div class="wr-seg">
            <button v-for="item in dayButtons" :key="item.day" type="button" :class="{ 'is-on': selectedDay === item.day }" @click="changeDay(item.day)">{{ item.label }}</button>
          </div>
          <div class="wr-seg">
            <button type="button" :class="{ 'is-on': !isSatellite }" @click="isSatellite && toggleSatellite()">기본</button>
            <button type="button" :class="{ 'is-on': isSatellite }" @click="!isSatellite && toggleSatellite()">위성</button>
          </div>
          <div class="wr-seg">
            <button type="button" :class="{ 'is-on': is3d }" @click="!is3d && toggle3d()">3D</button>
            <button type="button" :class="{ 'is-on': !is3d }" @click="is3d && toggle3d()">2D</button>
          </div>
          <button type="button" class="wr-btn" @click="runIntro">동네 다시 띄우기</button>
        </div>

        <!-- 내비 카드 -->
        <div v-if="navCard && (playing || progress > 0)" class="wr-nav">
          <div class="wr-nav__time">{{ toDayTime(nowMinutes) }}</div>
          <template v-if="navCard.isStay">
            <div class="wr-nav__label">머무는 중</div>
            <div class="wr-nav__place">{{ navCard.row.name }}</div>
            <div class="wr-nav__sub">
              {{ navCard.minutes }}분째 · 계획 {{ toDuration(navCard.row.stay) }}
              <b v-if="navCard.row.diff !== null && navCard.row.diff > 0" class="is-over"> → 실제 {{ toDuration(navCard.row.actualStay) }} ({{ diffText(navCard.row.diff) }})</b>
            </div>
          </template>
          <template v-else>
            <div class="wr-nav__label">다음 목적지</div>
            <div class="wr-nav__place">{{ navCard.row.name }} <span v-if="navCard.meters !== null">{{ metersText(navCard.meters) }}</span></div>
            <div class="wr-nav__sub">계획 {{ navCard.row.time }} 도착 · 실제 {{ navCard.row.actualArrive }}</div>
          </template>
        </div>

        <!-- 범례 -->
        <div class="wr-legend">
          <span><i class="wr-legend__real"></i>실제 경로 (GPS)</span>
          <span><i class="wr-legend__plan"></i>계획 경로</span>
          <span><i class="wr-legend__dong"></i>머문 동네 (높을수록 오래)</span>
          <span><i class="wr-legend__pass"></i>지나간 동네</span>
        </div>

        <!-- 재생 막대 -->
        <div class="wr-player">
          <button type="button" class="wr-player__play" :disabled="!canPlay" @click="togglePlay">{{ playing ? '일시정지' : '▶ 내비로 다시 걷기' }}</button>
          <button type="button" class="wr-btn" :disabled="!canPlay" @click="changeSpeed">{{ speed }}배속</button>
          <input v-model="sliderValue" type="range" min="0" max="1000" :disabled="!canPlay" class="wr-player__bar" aria-label="재생 위치" />
          <span class="wr-player__time">{{ canPlay ? toDayTime(nowMinutes) : 'GPS 기록 없음' }}</span>
          <label class="wr-player__follow"><input v-model="isFollow" type="checkbox" /> 따라가기</label>
          <button type="button" class="wr-btn" @click="backToOverview">전체 보기</button>
        </div>
      </template>
    </section>

    <aside v-if="retro" class="wr-side">
      <!-- 숫자 4개 -->
      <div class="wr-kpis">
        <div><b>{{ summary.visited }}<small>/{{ summary.total }}</small></b><span>다녀온 곳</span></div>
        <div><b>{{ movedText }}</b><span>이동 거리</span></div>
        <div><b>{{ toWon(summary.cost) }}</b><span>쓴 돈</span></div>
        <div><b>{{ toWon(summary.perPerson) }}</b><span>1인당</span></div>
      </div>
      <p v-if="biggestOver" class="wr-insight">
        <b>{{ biggestOver.name }}</b>에서 계획보다 <b>{{ toDuration(biggestOver.diff) }}</b> 더 머물렀어요.
        <template v-if="summary.skipped > 0"> 건너뛴 곳 {{ summary.skipped }}곳.</template>
      </p>

      <!-- 다닌 동네 -->
      <section v-if="visitedDongs.length > 0" class="wr-card">
        <h2>다닌 동네 <small>{{ visitedDongs.length }}개 동 · 지나간 동 {{ passedDongs.length }}개</small></h2>
        <button v-for="(dong, i) in visitedDongs" :key="dong.code" type="button" class="wr-dongrow" @click="focusDong(dong)">
          <span class="wr-dongrow__rank">{{ i + 1 }}</span>
          <span class="wr-dongrow__name">{{ dong.name }} <small>{{ dong.gu }}</small></span>
          <span class="wr-dongrow__stay">{{ dongStayText(dong) }}</span>
          <span class="wr-dongrow__places">{{ dong.places.join(', ') }}</span>
        </button>
      </section>

      <!-- 계획 vs 실제 -->
      <section class="wr-card">
        <h2>계획 vs 실제 <small>줄을 누르면 지도로 가요</small></h2>
        <table class="wr-table">
          <thead>
            <tr><th>#</th><th>장소</th><th>계획</th><th>실제</th><th>차이</th><th>비용</th></tr>
          </thead>
          <tbody>
            <tr v-for="row in shownStops" :key="row.id" :class="{ 'is-skip': !row.isVisited }" @click="focusStop(row)">
              <td>{{ row.no }}</td>
              <td class="wr-table__name">{{ row.name }}<small v-if="selectedDay === 0">{{ row.day }}일차</small></td>
              <td>{{ row.time }}<small>{{ toDuration(row.stay) }}</small></td>
              <td v-if="row.isVisited">{{ row.actualArrive }}<small v-if="row.actualStay !== null">{{ toDuration(row.actualStay) }}</small></td>
              <td v-else>건너뜀</td>
              <td :class="{ 'is-over': row.diff > 0, 'is-under': row.diff < 0 }">{{ row.diff === null || row.diff === 0 ? '-' : (row.diff > 0 ? '+' : '−') + Math.abs(row.diff) + '분' }}</td>
              <td>{{ row.cost > 0 ? toWon(row.cost) : '' }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <!-- 비용 -->
      <section class="wr-card">
        <h2>쓴 돈 <small>{{ toWon(summary.cost) }}</small></h2>
        <p v-if="costByCategory.length === 0" class="wr-empty">결제 내역이 없어요. 앱 회고에서 넣을 수 있어요.</p>
        <div v-for="item in costByCategory" :key="item.name" class="wr-bar">
          <span class="wr-bar__name">{{ item.name }}</span>
          <span class="wr-bar__track"><i :style="{ width: item.percent + '%' }"></i></span>
          <span class="wr-bar__amount">{{ toWon(item.amount) }}</span>
        </div>
      </section>

      <!-- 정산 -->
      <section class="wr-card">
        <h2>정산 <small>여행 전체 · 고치기는 앱 회고에서</small></h2>
        <p v-if="!settlement" class="wr-empty">같이 간 사람이 2명 이상이고 결제가 있으면 보여요.</p>
        <template v-else>
          <div v-for="person in settlement.people" :key="person.name" class="wr-person">
            <span class="wr-person__name">{{ person.name }}</span>
            <span>낸 돈 {{ toWon(person.paid) }}</span>
            <span>낼 돈 {{ toWon(person.should) }}</span>
            <b :class="person.balance >= 0 ? 'is-plus' : 'is-minus'">{{ person.balance >= 0 ? '+' : '−' }}{{ toWon(Math.abs(person.balance)) }}</b>
          </div>
          <div class="wr-transfers">
            <p v-for="(transfer, i) in settlement.transfers" :key="i">{{ transfer.from }} → {{ transfer.to }} <b>{{ toWon(transfer.amount) }}</b></p>
          </div>
        </template>
      </section>
    </aside>
  </div>
</template>

<style scoped>
.wr {
  display: flex;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: var(--surface);
  color: var(--ink);
  font-family: var(--font-sans);
  font-size: 14px;
  word-break: keep-all;
}
.wr-map { position: relative; flex: 1; min-width: 0; }
.wr-map__canvas { position: absolute; inset: 0; }
.wr-state { position: absolute; inset: 0; display: grid; place-items: center; background: var(--surface); font-size: 16px; z-index: 5; }

.wr-title, .wr-tools, .wr-nav, .wr-legend, .wr-player {
  position: absolute;
  z-index: 2;
  background: rgba(255, 255, 255, 0.94);
  border-radius: var(--radius-lg);
  box-shadow: 0 4px 18px rgba(24, 33, 58, 0.16);
}
.wr-title { top: 16px; left: 16px; padding: 14px 18px; max-width: 420px; }
.wr-title h1 { margin: 6px 0 2px; font-size: 22px; line-height: 28px; }
.wr-title p { margin: 0; color: var(--ink-muted); }
.wr-badge { display: inline-block; padding: 2px 8px; border-radius: var(--radius-full); background: var(--navy); color: var(--on-navy); font-size: 12px; }

.wr-tools { top: 16px; right: 16px; display: flex; flex-wrap: wrap; gap: 8px; padding: 8px; max-width: 560px; justify-content: flex-end; }
.wr-seg { display: flex; border: 1px solid var(--line); border-radius: var(--radius-md); overflow: hidden; }
.wr-seg button { padding: 6px 12px; border: 0; background: #fff; cursor: pointer; }
.wr-seg button.is-on { background: var(--navy); color: var(--on-navy); }
.wr-btn { padding: 6px 12px; border: 1px solid var(--line); border-radius: var(--radius-md); background: #fff; cursor: pointer; }
.wr-btn:disabled, .wr-player__play:disabled { opacity: 0.5; cursor: default; }

.wr-nav { top: 92px; left: 50%; transform: translateX(-50%); min-width: 340px; padding: 12px 18px; background: rgba(31, 58, 104, 0.95); color: #fff; }
.wr-nav__time { font-size: 12px; opacity: 0.8; }
.wr-nav__label { margin-top: 2px; font-size: 12px; color: #F6B9A6; }
.wr-nav__place { font-size: 20px; font-weight: 700; line-height: 28px; }
.wr-nav__place span { margin-left: 6px; font-size: 15px; font-weight: 400; opacity: 0.85; }
.wr-nav__sub { font-size: 13px; opacity: 0.9; }
.wr-nav__sub .is-over { color: #FFB49E; }

.wr-legend { left: 16px; bottom: 92px; display: flex; flex-direction: column; gap: 4px; padding: 10px 12px; font-size: 12px; }
.wr-legend i { display: inline-block; width: 22px; height: 0; margin-right: 6px; vertical-align: middle; }
.wr-legend__real { border-top: 5px solid #E8603F; }
.wr-legend__plan { border-top: 4px dashed #7C8496; }
.wr-legend__dong { height: 10px !important; background: #E8603F; opacity: 0.7; }
.wr-legend__pass { height: 10px !important; background: #8FA6CF; opacity: 0.7; }

.wr-player { left: 16px; right: 16px; bottom: 16px; display: flex; align-items: center; gap: 10px; padding: 10px 14px; }
.wr-player__play { padding: 8px 16px; border: 0; border-radius: var(--radius-md); background: var(--coral); color: #fff; font-weight: 700; cursor: pointer; white-space: nowrap; }
.wr-player__bar { flex: 1; accent-color: var(--coral); }
.wr-player__time { min-width: 92px; font-variant-numeric: tabular-nums; }
.wr-player__follow { white-space: nowrap; }

.wr-side { width: 440px; flex: none; overflow-y: auto; padding: 16px; border-left: 1px solid var(--line); background: var(--surface); }
.wr-kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
.wr-kpis div { padding: 10px; border-radius: var(--radius-lg); background: #fff; box-shadow: var(--shadow-card); }
.wr-kpis b { display: block; font-size: 17px; line-height: 24px; }
.wr-kpis small { font-size: 12px; color: var(--ink-muted); }
.wr-kpis span { font-size: 12px; color: var(--ink-muted); }
.wr-insight { margin: 12px 0 0; padding: 10px 12px; border-radius: var(--radius-lg); background: var(--coral-soft); color: var(--coral-ink); }

.wr-card { margin-top: 12px; padding: 14px; border-radius: var(--radius-lg); background: #fff; box-shadow: var(--shadow-card); }
.wr-card h2 { margin: 0 0 10px; font-size: 16px; }
.wr-card h2 small { margin-left: 6px; font-size: 12px; font-weight: 400; color: var(--ink-muted); }
.wr-empty { margin: 0; color: var(--ink-muted); }

.wr-dongrow { display: grid; grid-template-columns: 24px 1fr auto; gap: 2px 8px; width: 100%; padding: 8px 4px; border: 0; border-top: 1px solid var(--line); background: none; text-align: left; cursor: pointer; }
.wr-dongrow:hover { background: var(--surface); }
.wr-dongrow__rank { grid-row: span 2; display: grid; place-items: center; width: 24px; height: 24px; border-radius: 50%; background: var(--coral); color: #fff; font-size: 12px; }
.wr-dongrow__name { font-weight: 700; }
.wr-dongrow__name small { font-weight: 400; color: var(--ink-muted); }
.wr-dongrow__stay { color: var(--coral-ink); font-weight: 700; white-space: nowrap; text-align: right; }
.wr-dongrow__places { grid-column: 2 / 4; font-size: 12px; color: var(--ink-muted); }

.wr-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.wr-table th { padding: 4px; color: var(--ink-muted); font-weight: 400; text-align: left; border-bottom: 1px solid var(--line); }
.wr-table td { padding: 6px 4px; border-bottom: 1px solid var(--line); vertical-align: top; }
.wr-table td small { display: block; font-size: 11px; color: var(--ink-muted); }
.wr-table tbody tr { cursor: pointer; }
.wr-table tbody tr:hover { background: var(--surface); }
.wr-table__name { font-weight: 700; }
.wr-table .is-skip { color: var(--ink-disabled); }
.wr-table .is-over { color: var(--coral-ink); font-weight: 700; }
.wr-table .is-under { color: var(--sky); font-weight: 700; }

.wr-bar { display: grid; grid-template-columns: 80px 1fr 90px; align-items: center; gap: 8px; margin-top: 6px; }
.wr-bar__track { height: 10px; border-radius: var(--radius-full); background: var(--surface-sunken); overflow: hidden; }
.wr-bar__track i { display: block; height: 100%; background: var(--coral); }
.wr-bar__amount { text-align: right; font-variant-numeric: tabular-nums; }

.wr-person { display: grid; grid-template-columns: 70px 1fr 1fr 90px; gap: 6px; padding: 6px 0; border-top: 1px solid var(--line); font-size: 13px; }
.wr-person__name { font-weight: 700; }
.wr-person b { text-align: right; }
.wr-person .is-plus { color: var(--success); }
.wr-person .is-minus { color: var(--danger); }
.wr-transfers { margin-top: 8px; padding: 8px 10px; border-radius: var(--radius-md); background: var(--navy-soft); }
.wr-transfers p { margin: 2px 0; }

/* 지도 위 HTML 마커 (scoped가 안 닿아서 :deep) */
:deep(.wr-pin) { display: flex; flex-direction: column; align-items: center; padding: 0; border: 0; background: none; cursor: pointer; }
:deep(.wr-pin__no) { display: grid; place-items: center; width: 28px; height: 28px; border: 2px solid #fff; border-radius: 50%; background: var(--navy); color: #fff; font-weight: 700; box-shadow: 0 2px 6px rgba(0, 0, 0, 0.3); }
:deep(.wr-pin__name) { margin-top: 2px; padding: 1px 6px; border-radius: var(--radius-sm); background: rgba(255, 255, 255, 0.92); font-size: 12px; white-space: nowrap; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2); }
:deep(.wr-pin--over .wr-pin__no) { background: var(--coral); }
:deep(.wr-pin--skip .wr-pin__no) { background: #A3ABBA; }
:deep(.wr-pin--skip .wr-pin__name) { color: var(--ink-subtle); text-decoration: line-through; }
:deep(.wr-extra) { padding: 2px 8px; border: 1px dashed #6B4FA0; border-radius: var(--radius-full); background: rgba(255, 255, 255, 0.92); color: #6B4FA0; font-size: 12px; white-space: nowrap; }
:deep(.wr-dong) { padding: 4px 10px; border-radius: var(--radius-md); background: rgba(169, 65, 42, 0.92); color: #fff; text-align: center; pointer-events: none; transform: translateY(-40px); }
:deep(.wr-dong__name) { font-size: 15px; font-weight: 700; }
:deep(.wr-dong__info) { font-size: 12px; opacity: 0.9; }
:deep(.wr-me) { width: 18px; height: 18px; border: 3px solid #fff; border-radius: 50%; background: var(--coral); box-shadow: 0 0 0 6px rgba(232, 96, 63, 0.3); }
:deep(.wr-pop__title) { font-weight: 700; margin-bottom: 4px; }
:deep(.wr-pop__line) { font-size: 13px; }
:deep(.wr-pop__line--real) { color: var(--navy); font-weight: 700; }
:deep(.wr-pop__diff) { margin-top: 2px; color: var(--coral-ink); font-weight: 700; font-size: 13px; }
:deep(.wr-pop__pay) { margin-top: 4px; font-size: 12px; color: var(--ink-muted); }

@media (max-width: 1100px) {
  .wr { flex-direction: column; overflow-y: auto; height: auto; }
  .wr-map { height: 70vh; flex: none; }
  .wr-side { width: auto; border-left: 0; }
}
</style>
