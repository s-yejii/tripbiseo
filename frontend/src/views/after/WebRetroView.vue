<script setup>
// W19 웹 회고 (⑤, 11/09 중간발표 피드백: "사용자는 앱, 자세한 건 웹")
// 앱 회고와 같은 API(retro·payments·companions)를 읽어서 PC 화면에 크게 보여 줘요
//  - 3D 서울 지도(건물, 산은 음영으로) 위에 다녀온 동네(행정동)가 머문 시간만큼 솟아오름
//  - 실제 경로(GPS, 파랑) vs 계획 경로(장소 순서, 회색 점선)
//  - 내비게이션처럼 카메라가 이동 경로를 따라가며 재생 (위에서 보기 / 거리 시점)
//  - 다녀온 장소의 건물은 일정 분류(음식점·카페·쇼핑·숙소 등)별 색
//  - 오른쪽: 계획 vs 실제 표, 비용, 정산 (고치기는 앱 회고에서)
//  - (11/10) 사진: 찍은 시각·장소에 맞춰 지도에 놓고, 재생 중 그 시각을 지나면 띄움
//  - (11/10) 해: 그날 일출·일몰과 재생 시각의 해 높이로 하늘 색·밝기·건물 빛 방향이 바뀜
//  - (11/10) 여행 메모하기(/web/journal)에 쓴 장소 메모를 핀 창에 같이 보여 줌
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as maplibregl from 'maplibre-gl' // MapLibre: 3D(기울이기·건물·지형)가 되는 무료 지도 라이브러리 (키 없음)
import 'maplibre-gl/dist/maplibre-gl.css'
// 지도 계산을 하는 뒤쪽 일꾼(worker) 파일 주소. Vite가 라이브러리를 옮겨 담아서 기본 주소를 못 찾기 때문에 직접 알려 줌
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?url'
import { get } from '@/api/http'
import { toTime, toMinutes, toDuration, toWon } from '@/utils/time'
import { sunTimes, sunPosition, sunPhase, skyAt, koreanDate } from '@/utils/sun'
import { loadJournal, editedOriginalIds } from '@/utils/webJournal'

const STYLE_URL = 'https://tiles.openfreemap.org/styles/liberty' // OpenFreeMap 지도 (OpenStreetMap 기반, 건물 높이 있음)
const DEM_TILES = 'https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png' // 지형 높이 그림 (AWS 공개 데이터)
const SATELLITE_TILES = 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}' // Esri 위성 사진
const SEOUL_CENTER = [126.978, 37.5665]
const MINUTES_PER_DAY = 24 * 60
const METERS_PER_DEGREE_LAT = 110540  // 위도 1도의 길이(m)
const METERS_PER_DEGREE_LNG = 111320  // 적도에서 경도 1도의 길이(m). 위도가 높을수록 cos(위도)만큼 짧아짐
const RISE_MS = 1800             // 동네가 솟아오르는 시간
const VISIT_BASE_M = 120         // 다녀온 동네 기둥 기본 높이(m)
const VISIT_PER_MINUTE_M = 1.3   // 머문 1분마다 더하는 높이(m)
const VISIT_MAX_MINUTES = 300    // 5시간 넘게 머물러도 높이는 여기까지
const PASS_HEIGHT_M = 30         // 지나가기만 한 동네 높이(m)
const OVERVIEW_PITCH = 55
const NAV_ZOOM = 15.2            // 내비 재생 확대 (너무 가까우면 화면이 빨리 흘러서 어지러움)
const NAV_PITCH = 45
const PLAY_MOVE_SECONDS = 40     // 1배속에서 이동 구간에 쓰는 시간(초). 여기에 머문 곳에서 멈추는 시간이 더해짐
const STAY_RADIUS_M = 60         // 이 반경 안에 오래 있으면 "머문 곳" (GPS는 가만히 있어도 흔들림)
const STAY_MIN_MINUTES = 8       // 이만큼 이상 그 반경에 있어야 머문 곳으로 봄
const STAY_HOLD_SECONDS = 3      // 머문 곳에 도착하면 위치·방향을 그대로 두고 가만히 있는 시간(초)
const STAY_TURN_MAX_SECONDS = 6  // 가만히 있은 뒤 출발 직전에 제자리에서 돌 시간, 이만큼까지만 (움직이면서 도는 것보다 덜 어지러움)
const LOOK_AHEAD_M = 300         // 카메라 방향: 경로를 따라 이만큼 앞을 바라봄
const MIN_TURN_METERS = 15       // 앞 지점이 이보다 가까우면 방향을 안 바꿈
const MAX_TURN_PER_SECOND = 18   // 카메라가 1초에 돌 수 있는 최대 각도 (멀미 방지)
const TURN_SMOOTH_SECONDS = 1    // 방향을 따라가는 부드러움 (클수록 천천히)
const CENTER_SMOOTH_SECONDS = 0.25 // 카메라 위치를 따라가는 부드러움
const START_FLY_MS = 1500        // 처음부터 재생할 때 출발점으로 날아가는 시간
const VIEW_CHANGE_MS = 1000      // 위에서 ↔ 거리 시점을 바꿀 때 카메라가 옮겨 가는 시간
const STREET_EYE_M = 12          // 거리 시점: 카메라 높이(m)
const STREET_BACK_M = 40         // 거리 시점: 내 위치에서 이만큼 뒤에서 봄(m)
const STREET_PITCH = 84          // 거리 시점: 기울기 (90이면 수평)
const STREET_SPEED = 40          // 거리 시점: 1배속 이동 속도(초당 m)
const STREET_LOOK_AHEAD_M = 100  // 거리 시점: 경로를 따라 이만큼 앞을 바라봄 (모퉁이에서 늦지 않게)
const STREET_MAX_TURN = 30       // 거리 시점: 1초에 돌 수 있는 최대 각도
const STREET_TURN_SMOOTH = 0.5   // 거리 시점: 방향을 따라가는 부드러움
const STREET_PIN_RANGE_M = 300   // 거리 시점: 이 거리 안의 장소 핀만 보여 줌 (먼 핀이 건물을 뚫고 보여서)
const BUILDING_SEARCH_M = 30     // 장소 좌표가 건물 밖이면 이 거리 안의 건물까지 찾음 (좌표가 입구·도로에 찍힌 경우)
const BUILDING_MIN_ZOOM = 15     // 이만큼 확대했을 때만 찾음 (더 멀면 지도 데이터에서 여러 건물이 한 덩어리로 합쳐져 있음)
const BUILDING_FIND_MS = 700     // 재생 중 건물을 찾아보는 간격
const BUILDING_FIND_RANGE_M = 600 // 지도 중심에서 이 거리 안의 장소만 찾음 (먼 곳은 건물 데이터가 덜 자세함)
const TURN_WINDOW_M = 40         // 회전 안내: 이 거리만큼 앞뒤 방향을 비교함 (GPS 잔떨림은 무시)
const TURN_STEP_M = 10           // 회전 안내: 경로를 이 간격으로 살펴봄
const TURN_MIN_ANGLE = 45        // 회전 안내: 이 각도 이상 꺾여야 안내함
const TURN_UTURN_ANGLE = 150     // 회전 안내: 이 각도 이상이면 유턴
const TURN_STAY_GAP_M = 60       // 회전 안내: 머문 곳에서 이 거리 안의 꺾임은 안내하지 않음
const TURN_NOTICE_M = 500        // 회전 안내: 이 거리 안에 들어오면 카드에 보여 줌
// 일정 분류 글자로 건물 색 정하기 (앞에서부터 먼저 맞는 것)
const BUILDING_KINDS = [
  { name: '카페', color: '#8D5A2B', words: ['카페', '디저트', '커피', '베이커리'] },
  { name: '숙소', color: '#7B5CD6', words: ['숙소', '호텔', '모텔', '펜션', '게스트하우스', '리조트'] },
  { name: '쇼핑', color: '#D6409F', words: ['백화점', '쇼핑', '마트', '아울렛', '할인매장', '시장'] },
  { name: '교통', color: '#1FA39A', words: ['정차역', '기차역', '지하철', '터미널', '공항', 'KTX', 'SRT'] },
  { name: '명소·문화', color: '#4CAF50', words: ['공원', '관광', '명소', '박물관', '미술관', '전시'] },
  { name: '음식점', color: '#F2A33A', words: ['요리', '식당', '음식', '한식', '중식', '일식', '양식', '분식', '이자카야', '주점', '술집', '고기', '구이', '국밥', '치킨', '피자', '라멘', '초밥', '찌개', '냉면', '뷔페', '맛집', '호프', '포차'] },
]
const OTHER_KIND = { name: '기타', color: '#8A93A6' }
const PHOTO_POP_MS = 3500        // 재생 중 사진 카드가 떠 있는 시간
const PHOTO_MARKER_OFFSET = [30, -44] // 장소 사진 동그라미를 핀 오른쪽 위에 (핀과 안 겹치게)
const SUN_STRIP_SAMPLES = 120    // 재생 막대의 낮·밤 띠를 이만큼 나눠서 색칠
const MOVE_GAP_MINUTES = 30      // GPS 점 사이가 이보다 멀면 그 사이는 '이동 중'이 아니라 기록 없음
const SKY_BLEND = { 'sky-horizon-blend': 0.6, 'horizon-fog-blend': 0.7, 'fog-ground-blend': 0.7 }
const DAY_SKY = { sky: '#A9C8EF', horizon: '#E9F0FA' } // 해 반영을 끄면 늘 이 하늘
const DEFAULT_LIGHT = { anchor: 'viewport', position: [1.15, 210, 30], color: '#FFFFFF', intensity: 0.5 } // MapLibre 기본 빛

const route = useRoute()
const router = useRouter()
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
const progress = ref(0)           // 재생 위치 = 재생 시작부터 몇 초째인지 (playRoute의 시간표 기준)
const isFollow = ref(true)        // 재생 중 카메라가 따라가는지
const viewMode = ref('top')       // 재생 시점: 'top' = 위에서, 'street' = 거리
const paintedKinds = ref([])      // 건물을 칠한 분류 (범례에 보여 줌)
const mePosition = ref(null)      // 재생 중 지금 위치 [경도, 위도]
const geoReady = ref(false)       // 서울 동·구 경계를 다 읽었는지
const mapBox = ref(null)
const photos = ref([])            // 이 여행 사진·영상 (GET /api/trips/{id}/photos)
const journal = loadJournal(route.query.id) // 여행 메모하기에서 쓴 메모·대표 사진 (이 브라우저)
const isPhotoShown = ref(true)    // 지도에 사진 보이기
const isSunOn = ref(true)         // 해·하늘 반영
const floatPhoto = ref(null)      // 재생 중 잠깐 뜨는 사진 카드
const viewer = ref(null)          // 크게 보기 { list, index }

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
let cameraCenter = null           // 재생 중 카메라가 보는 곳 [경도, 위도] (내 위치를 살짝 늦게 따라감)
let startFlyTimer = null          // 출발점으로 날아간 뒤 재생을 시작하는 타이머
let buildingOfStop = {}           // 일정 칸 id → 찾은 건물 하나 (null = 찾아봤는데 건물 없음)
let lastBuildingFind = 0          // 재생 중 건물을 마지막으로 찾아본 시각
let introTimers = []
let photoMarkers = []             // 사진 동그라미 마커
let lastPhotoMinutes = null       // 재생 중 사진을 마지막으로 확인한 여행 시각 (이 뒤에 찍은 사진만 띄움)
let floatTimer = null
let isMapReady = false            // 지도 스타일을 다 읽었는지 (하늘·빛은 그 뒤에만 바꿀 수 있음)
let lastSkyKey = ''               // 마지막으로 칠한 하늘 (같으면 다시 안 칠함)

// ============================== 데이터 읽기
onMounted(async () => {
  window.addEventListener('keydown', onViewerKey)
  try {
    const id = route.query.id
    retro.value = await get('/trips/' + id + '/retro')
    payments.value = await get('/trips/' + id + '/payments')
    companions.value = await get('/trips/' + id + '/companions')
    document.title = retro.value.title + ' · 웹 회고'
  } catch (e) {
    if (e.status === 401) {
      router.replace({ path: '/web', query: { next: route.fullPath } }) // 웹 로그인 뒤 이 회고로 돌아옴
      return
    }
    errorMessage.value = '회고를 불러오지 못했어요 (' + e.message + ')'
    isLoading.value = false
    return
  }
  await loadPhotos()
  await loadGeo()
  isLoading.value = false
  createMap()
})

onUnmounted(() => {
  window.removeEventListener('keydown', onViewerKey)
  cancelAnimationFrame(playFrame)
  cancelAnimationFrame(riseFrame)
  clearTimeout(startFlyTimer)
  clearTimeout(floatTimer)
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

// from에서 to로 가는 가장 짧은 각도 (오른쪽 +, 왼쪽 −). 예: 350° → 10°는 +20°
function angleDiff(from, to) {
  return ((((to - from + 180) % 360) + 360) % 360) - 180
}

// 각도를 조금씩 돌리기 (350° → 10°는 20°만 돌게). 지난 시간(초)으로 계산해서 모니터 주사율이 달라도 같은 속도
function turnToward(from, to, seconds) {
  const diff = angleDiff(from, to)
  const wanted = diff * (1 - Math.exp(-seconds / (isStreet() ? STREET_TURN_SMOOTH : TURN_SMOOTH_SECONDS)))
  const limit = maxTurnPerSecond() * seconds
  return from + Math.max(-limit, Math.min(limit, wanted))
}

function isStreet() {
  return viewMode.value === 'street'
}

function maxTurnPerSecond() {
  return isStreet() ? STREET_MAX_TURN : MAX_TURN_PER_SECOND
}

// 이 위치에서 bearing 방향으로 meters만큼 옮긴 위치
function movePoint(position, bearing, meters) {
  const toRad = Math.PI / 180
  return [position[0] + (Math.sin(bearing * toRad) * meters) / metersPerLngAt(position[1]), position[1] + (Math.cos(bearing * toRad) * meters) / METERS_PER_DEGREE_LAT]
}

// 이 위도에서 경도 1도의 길이(m)
function metersPerLngAt(lat) {
  return METERS_PER_DEGREE_LNG * Math.cos((lat * Math.PI) / 180)
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

// ============================== 사진 (찍은 시각·장소로 지도에 놓기)
async function loadPhotos() {
  try {
    photos.value = await get('/trips/' + route.query.id + '/photos')
  } catch (e) {
    photos.value = [] // 사진이 없어도 회고는 보여 줌
  }
}

// 여행 첫날 + (day − 1)일 → "2026-11-07"
function dateOfDay(day) {
  const date = new Date(retro.value.startDate + 'T00:00:00')
  date.setDate(date.getDate() + day - 1)
  return date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0') + '-' + String(date.getDate()).padStart(2, '0')
}

function findStopRow(stopId) {
  for (const stop of retro.value.stops) {
    if (stop.id === stopId) return stop
  }
  return null
}

// 이동 중 사진의 자리: 같은 날 GPS 점 중 찍은 시각과 가장 가까운 점
function trackPointAt(day, minutes) {
  let best = null
  let bestGap = Infinity
  for (const point of retro.value.track) {
    if (point.day !== day) continue
    const gap = Math.abs(tripMinutes(point.day, point.time) - minutes)
    if (gap < bestGap) {
      best = point
      bestGap = gap
    }
  }
  return best
}

// 고른 날의 사진 + 지도 자리. 편집본이 있는 원본은 빼고 편집본만 (여행 메모하기에서 편집한 것)
const shownPhotos = computed(() => {
  if (!retro.value) return []
  const hidden = editedOriginalIds(journal)
  const list = []
  for (const photo of photos.value) {
    if (!isShownDay(photo.day) || hidden.includes(photo.id)) continue
    const minutes = tripMinutes(photo.day, photo.takenAt)
    const stop = photo.stopId ? findStopRow(photo.stopId) : null
    const point = stop && stop.lat !== null ? stop : trackPointAt(photo.day, minutes)
    if (!point) continue
    list.push(Object.assign({}, photo, { minutes: minutes, lng: point.lng, lat: point.lat, placeName: stop ? stop.name : '이동 중' }))
  }
  return list
})

// 지도에 놓을 사진 묶음: 장소 사진은 장소마다 하나로, 이동 중 사진은 한 장씩
const photoGroups = computed(() => {
  const groups = {}
  for (const photo of shownPhotos.value) {
    const key = photo.stopId ? 'stop-' + photo.stopId : 'photo-' + photo.id
    if (!groups[key]) groups[key] = { key: key, stopId: photo.stopId, lng: photo.lng, lat: photo.lat, photos: [] }
    groups[key].photos.push(photo)
  }
  return Object.values(groups)
})

// 묶음 대표 사진: 여행 메모에서 고른 대표, 없으면 첫 사진(영상 말고)
function coverOfGroup(group) {
  const coverId = group.stopId ? journal.covers[group.stopId] : null
  for (const photo of group.photos) {
    if (photo.id === coverId) return photo
  }
  for (const photo of group.photos) {
    if (photo.mediaType === 'photo') return photo
  }
  return group.photos[0]
}

function photosOfStop(stopId) {
  return shownPhotos.value.filter((photo) => photo.stopId === stopId)
}

// ============================== 해 (일출·일몰, 재생 시각의 해 높이)
// 해를 계산할 자리: 고른 날 첫 장소 (여행 하나는 한 도시 안이라 하루 안에서는 차이가 1분 안쪽)
const sunPlace = computed(() => {
  for (const row of shownStops.value) {
    if (row.lat !== null) return { lat: row.lat, lng: row.lng }
  }
  return { lat: SEOUL_CENTER[1], lng: SEOUL_CENTER[0] }
})

// 날마다 해 시각 { day, date, times: { dawn, sunrise, sunset, dusk } }
const daySuns = computed(() => {
  if (!retro.value) return []
  const list = []
  for (let day = 1; day <= dayCount.value; day++) {
    if (!isShownDay(day)) continue
    const times = sunTimes(dateOfDay(day), sunPlace.value.lat, sunPlace.value.lng)
    if (times) list.push({ day: day, date: dateOfDay(day), times: times })
  }
  return list
})

// 여행 시각(분) → 그 순간 해: 높이·방향·하늘 색
function sunAt(tripMinute) {
  const day = Math.floor(tripMinute / MINUTES_PER_DAY) + 1
  const minuteOfDay = Math.round(tripMinute) % MINUTES_PER_DAY
  const position = sunPosition(koreanDate(dateOfDay(day), minuteOfDay), sunPlace.value.lat, sunPlace.value.lng)
  return { day: day, minuteOfDay: minuteOfDay, altitude: position.altitude, azimuth: position.azimuth, phase: sunPhase(position.altitude), sky: skyAt(position.altitude) }
}

// 지금 재생 위치의 해 (재생을 시작하기 전이면 null → 낮 하늘)
const currentSun = computed(() => {
  if (!isSunOn.value || !canPlay.value || (!playing.value && progress.value === 0)) return null
  return sunAt(nowMinutes.value)
})

function timesOfDay(day) {
  for (const item of daySuns.value) {
    if (item.day === day) return item.times
  }
  return null
}

// 내비 카드 해 한 줄: "🌇 노을 · 일몰 17:30까지 25분"
const sunBadge = computed(() => {
  const sun = currentSun.value
  const times = sun ? timesOfDay(sun.day) : null
  if (!sun || !times) return null
  const minute = sun.minuteOfDay
  const isMorning = minute < 12 * 60
  const names = { night: '🌙 밤', twilight: isMorning ? '🌄 해 뜨기 전' : '🌆 해 진 뒤', golden: isMorning ? '🌅 아침 햇살' : '🌇 노을', day: '☀️ 낮' }
  let detail = '일몰 ' + toTime(times.sunset) + ' 지남'
  if (minute < times.sunrise) detail = '일출 ' + toTime(times.sunrise) + '까지 ' + toDuration(times.sunrise - minute)
  else if (minute < times.sunset) detail = '일몰 ' + toTime(times.sunset) + '까지 ' + toDuration(times.sunset - minute)
  return { name: names[sun.phase], detail: detail, phase: sun.phase }
})

// 그 시각에 어디 있었나: 머문 곳 이름 → 숙소에서 자는 중이면 숙소 이름 → GPS가 이어지면 '이동 중' → 모르면 null
function placeAtMinute(tripMinute) {
  const stayName = stayNameAt(tripMinute)
  if (stayName) return stayName
  const lodging = lodgingBefore(tripMinute)
  if (lodging) return lodging
  return isMovingAt(tripMinute) ? '이동 중' : null
}

// 장소에 머무는 중이면 그 이름 (일정 칸, 계획 밖에 머문 곳)
function stayNameAt(tripMinute) {
  for (const row of shownStops.value) {
    if (!row.isVisited || row.actualStay === null) continue
    const arrive = tripMinutes(row.day, row.actualArrive)
    if (arrive <= tripMinute && tripMinute < arrive + row.actualStay) return row.name
  }
  for (const extra of shownExtras.value) {
    const arrive = tripMinutes(extra.day, extra.arrive)
    if (arrive <= tripMinute && tripMinute < arrive + extra.stay) return extra.kind === 'lodging' ? '숙소' : extra.name || '계획 밖에 들른 곳'
  }
  return null
}

// 이 시각 전에 마지막으로 떠난 곳이 숙소면 그 이름 (밤새 GPS가 없어도 숙소에서 잔 것으로 봄)
function lodgingBefore(tripMinute) {
  let last = null
  let lastLeave = -Infinity
  for (const row of shownStops.value) {
    if (!row.isVisited || row.actualStay === null) continue
    const leave = tripMinutes(row.day, row.actualArrive) + row.actualStay
    if (leave <= tripMinute && leave > lastLeave) {
      last = row
      lastLeave = leave
    }
  }
  if (last && kindOfCategory(last.category).name === '숙소') return last.name
  return null
}

// 앞뒤 GPS 점이 MOVE_GAP_MINUTES 안으로 이어져 있으면 움직이는 중 (기록이 끊긴 밤은 이동 중이 아님)
function isMovingAt(tripMinute) {
  let before = null
  let after = null
  for (const point of shownTrack.value) {
    const time = tripMinutes(point.day, point.time)
    if (time <= tripMinute) before = time
    else if (after === null) after = time
  }
  return before !== null && after !== null && after - before <= MOVE_GAP_MINUTES
}

// 해 뜰 때·질 때 있던 곳 (오른쪽 카드 이야기)
const sunMoments = computed(() => {
  const list = []
  for (const item of daySuns.value) {
    const events = [{ kind: 'sunrise', icon: '🌅', name: '해가 뜰 때', minute: item.times.sunrise }, { kind: 'sunset', icon: '🌇', name: '해가 질 때', minute: item.times.sunset }]
    for (const event of events) {
      const tripMinute = (item.day - 1) * MINUTES_PER_DAY + event.minute
      const place = placeAtMinute(tripMinute)
      if (place) list.push({ key: item.day + event.kind, day: item.day, icon: event.icon, name: event.name, time: toTime(event.minute), place: place, tripMinute: tripMinute })
    }
  }
  return list
})

// "N시간 M분" 낮 길이
function dayLengthText(times) {
  return toDuration(times.sunset - times.sunrise)
}

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
    maxPitch: 85, // 거리 시점은 거의 수평으로 봄
    attributionControl: { compact: true },
  })
  map.addControl(new maplibregl.NavigationControl({ visualizePitch: true }), 'bottom-right')
  map.on('load', () => {
    useKoreanLabels()
    map.setPaintProperty('building-3d', 'fill-extrusion-opacity', 0.72) // 건물을 살짝 비치게 (뒤 경로가 보이게)
    addHillshade()
    addSatellite()
    addBoundaryLayers()
    addRouteLayers()
    drawDay()
    runIntro()
    map.on('idle', findStopBuildings) // 지도가 멈출 때마다 아직 못 찾은 건물을 찾아봄
    isMapReady = true
    applySun()
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

// 산 그림자(hillshade)만 깔기. 땅을 실제로 올리면 지형이 갑자기 튀어나오고 카메라가 출렁여서 쓰지 않음
function addHillshade() {
  map.addSource('dem-shade', { type: 'raster-dem', tiles: [DEM_TILES], encoding: 'terrarium', tileSize: 256, maxzoom: 14 })
  map.addLayer({ id: 'hillshade', type: 'hillshade', source: 'dem-shade', paint: { 'hillshade-exaggeration': 0.35, 'hillshade-shadow-color': '#5a6b86' } }, 'building')
  setSkyColors(DAY_SKY)
}

function setSkyColors(sky) {
  try {
    map.setSky(Object.assign({ 'sky-color': sky.sky, 'horizon-color': sky.horizon, 'fog-color': sky.horizon }, SKY_BLEND))
  } catch (e) {
    // 하늘 색은 없어도 됨
  }
}

// 재생 시각의 해에 맞춰 하늘 색 + 건물 비추는 빛(방향·색·세기). 해 반영을 끄거나 재생 전이면 낮 하늘
// setLight position = [거리, 방향(북쪽 0도부터 시계 방향), 높이(0 = 머리 위, 90 = 지평선)]
function applySun() {
  if (!map || !isMapReady) return
  const sun = currentSun.value
  const key = sun ? sun.sky.sky + sun.sky.horizon + Math.round(sun.azimuth / 3) : 'day'
  if (key === lastSkyKey) return // 같은 하늘이면 다시 안 칠함 (재생 중 매 화면 칠하면 느려짐)
  lastSkyKey = key
  if (!sun) {
    setSkyColors(DAY_SKY)
    map.setLight(DEFAULT_LIGHT)
    return
  }
  setSkyColors(sun.sky)
  const polar = Math.max(10, Math.min(85, 90 - sun.altitude)) // 해가 지평선 아래면 낮게 비추되 밤 빛(약하게)
  map.setLight({ anchor: 'map', position: [1.5, sun.azimuth, polar], color: sun.sky.light, intensity: sun.sky.intensity })
}

// 화면 전체에 덮는 밤·노을 색 (지도 위, 카드 아래)
const skyShade = computed(() => (currentSun.value ? currentSun.value.sky.shade : 'rgba(0, 0, 0, 0)'))

function addSatellite() {
  map.addSource('satellite', { type: 'raster', tiles: [SATELLITE_TILES], tileSize: 256, attribution: 'Esri, Maxar, Earthstar Geographics' })
  map.addLayer({ id: 'satellite', type: 'raster', source: 'satellite', layout: { visibility: 'none' } }, 'building')
}

// 구 경계선 + 동네 기둥(fill-extrusion: 다각형을 높이만큼 세우는 3D 레이어)
function addBoundaryLayers() {
  const empty = { type: 'FeatureCollection', features: [] }
  map.addSource('stop-building', { type: 'geojson', data: empty })
  // 다녀온 장소의 건물: 원래 건물 위에 분류 색으로 살짝 더 높게 덮어씌움
  map.addLayer({ id: 'stop-building', type: 'fill-extrusion', source: 'stop-building', paint: {
    'fill-extrusion-color': ['get', 'color'],
    'fill-extrusion-height': ['+', ['get', 'h'], 1.5],
    'fill-extrusion-base': ['get', 'base'],
    'fill-extrusion-opacity': 0.95 } })
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

// 계획 경로(회색 점선), 실제 경로(파랑), 재생으로 지나온 부분(진한 파랑)
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
    paint: { 'line-color': '#2F7BFF', 'line-width': 5 } })
  map.addLayer({ id: 'track-done', type: 'line', source: 'track-done', layout: { 'line-join': 'round', 'line-cap': 'round' },
    paint: { 'line-color': '#0B4FC4', 'line-width': 7 } })
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
  drawStopBuildings()
  findStopBuildings()
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

// 지도에 그리는 실제 경로 = 머문 곳의 GPS 흔들림을 한 점으로 정리한 선
function trackCoordinates() {
  return playRoute.value.coordinates
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

// ============================== 다녀온 장소의 건물 색칠
// 지도 데이터의 건물에는 용도가 없어서, 일정 칸의 분류로 그 장소가 있는 건물만 칠함
function kindOfCategory(category) {
  if (category) {
    for (const kind of BUILDING_KINDS) {
      for (const word of kind.words) {
        if (category.includes(word)) return kind
      }
    }
  }
  return OTHER_KIND
}

// 건물 도형을 폴리곤 목록으로 (MultiPolygon은 지도 데이터에서 여러 건물이 묶여 온 것이라 하나씩 나눔)
function polygonsOf(geometry) {
  if (geometry.type === 'Polygon') return [geometry.coordinates]
  if (geometry.type === 'MultiPolygon') return geometry.coordinates
  return []
}

// 테두리 넓이(㎡, 신발끈 공식)
function ringAreaMeters(ring) {
  const metersPerLng = metersPerLngAt(ring[0][1])
  let sum = 0
  for (let i = 1; i < ring.length; i++) {
    sum += ring[i - 1][0] * metersPerLng * (ring[i][1] * METERS_PER_DEGREE_LAT) - ring[i][0] * metersPerLng * (ring[i - 1][1] * METERS_PER_DEGREE_LAT)
  }
  return Math.abs(sum) / 2
}

// 점에서 테두리까지 가장 가까운 거리(m)
function distanceToRing(lng, lat, ring) {
  const metersPerLng = metersPerLngAt(lat)
  let best = Infinity
  for (let i = 1; i < ring.length; i++) {
    const ax = (ring[i - 1][0] - lng) * metersPerLng
    const ay = (ring[i - 1][1] - lat) * METERS_PER_DEGREE_LAT
    const bx = (ring[i][0] - lng) * metersPerLng
    const by = (ring[i][1] - lat) * METERS_PER_DEGREE_LAT
    const lengthSquared = (bx - ax) ** 2 + (by - ay) ** 2
    const rate = lengthSquared > 0 ? Math.max(0, Math.min(1, -(ax * (bx - ax) + ay * (by - ay)) / lengthSquared)) : 0
    best = Math.min(best, Math.hypot(ax + (bx - ax) * rate, ay + (by - ay) * rate))
  }
  return best
}

// 이 장소의 건물 하나 찾기: 좌표가 들어 있는 건물 중 가장 작은 것, 없으면 BUILDING_SEARCH_M 안의 가장 가까운 건물
function findBuilding(row, features) {
  let inside = null
  let insideArea = Infinity
  let nearest = null
  let nearestDistance = BUILDING_SEARCH_M
  for (const feature of features) {
    if (feature.properties.hide_3d) continue
    for (const polygon of polygonsOf(feature.geometry)) {
      const ring = polygon[0]
      if (isInRing(row.lng, row.lat, ring)) {
        const area = ringAreaMeters(ring)
        if (area < insideArea) {
          inside = { polygon: polygon, feature: feature }
          insideArea = area
        }
      } else {
        const distance = distanceToRing(row.lng, row.lat, ring)
        if (distance < nearestDistance) {
          nearest = { polygon: polygon, feature: feature }
          nearestDistance = distance
        }
      }
    }
  }
  const found = inside || nearest
  if (found === null) return null
  return {
    geometry: { type: 'Polygon', coordinates: found.polygon },
    h: Number(found.feature.properties.render_height) || 12,
    base: Number(found.feature.properties.render_min_height) || 0,
  }
}

// 화면 근처에서 아직 안 찾아본 장소의 건물 찾기 (건물 데이터는 확대해서 불러온 곳에만 있음)
function findStopBuildings() {
  if (!map) return
  const layer = map.getLayer('building-3d')
  if (!layer || map.getZoom() < BUILDING_MIN_ZOOM || !map.isSourceLoaded(layer.source)) return
  const center = map.getCenter()
  const waiting = []
  for (const row of shownStops.value) {
    if (!row.isVisited || row.lat === null || buildingOfStop[row.id] !== undefined) continue
    if (distanceMeters(center.lat, center.lng, row.lat, row.lng) <= BUILDING_FIND_RANGE_M) waiting.push(row)
  }
  if (waiting.length === 0) return
  const features = map.querySourceFeatures(layer.source, { sourceLayer: layer.sourceLayer })
  if (features.length === 0) return
  for (const row of waiting) buildingOfStop[row.id] = findBuilding(row, features)
  drawStopBuildings()
}

// 찾아 둔 건물을 분류 색으로 그리기 (같은 건물에 장소가 둘이면 일정 순서가 빠른 쪽 색)
function drawStopBuildings() {
  if (!map || !map.getSource('stop-building')) return
  const features = []
  const seen = new Set()
  const kinds = []
  for (const row of shownStops.value) {
    const building = buildingOfStop[row.id]
    if (!building) continue
    const key = JSON.stringify(building.geometry)
    if (seen.has(key)) continue
    seen.add(key)
    const kind = kindOfCategory(row.category)
    features.push({ type: 'Feature', geometry: building.geometry, properties: { color: kind.color, h: building.h, base: building.base } })
    if (!kinds.includes(kind)) kinds.push(kind)
  }
  map.getSource('stop-building').setData({ type: 'FeatureCollection', features: features })
  paintedKinds.value = kinds
}

// ============================== 핀·이름표 (HTML로 만든 마커)
function clearMarkers() {
  for (const id in stopMarkers) stopMarkers[id].remove()
  for (const marker of otherMarkers) marker.remove()
  stopMarkers = {}
  otherMarkers = []
  clearPhotoMarkers()
}

function clearPhotoMarkers() {
  for (const marker of photoMarkers) marker.remove()
  photoMarkers = []
}

// 사진 동그라미: 대표 사진 + 장 수. 누르면 그 묶음을 크게 보기
function drawPhotoMarkers() {
  clearPhotoMarkers()
  if (!map || !isPhotoShown.value) return
  for (const group of photoGroups.value) {
    const cover = coverOfGroup(group)
    const element = document.createElement('button')
    element.type = 'button'
    element.className = 'wr-photo' + (cover.mediaType === 'video' ? ' wr-photo--video' : '')
    element.style.backgroundImage = 'url(' + cover.url + ')'
    if (group.photos.length > 1) element.appendChild(makeTag('wr-photo__count', String(group.photos.length)))
    element.addEventListener('click', (event) => {
      event.stopPropagation() // 지도 클릭으로 번지지 않게
      openViewer(group.photos, group.photos.indexOf(cover))
    })
    const offset = group.stopId ? PHOTO_MARKER_OFFSET : [0, 0]
    photoMarkers.push(new maplibregl.Marker({ element: element, anchor: 'bottom', offset: offset }).setLngLat([group.lng, group.lat]).addTo(map))
  }
}

watch(isPhotoShown, drawPhotoMarkers)

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
  drawPhotoMarkers()
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
  const note = journal.stopNotes[row.id]
  if (note) box.appendChild(makeTag('wr-pop__note', '📝 ' + note))
  appendPopupPhotos(box, row)
  return box
}

// 핀 창 아래: 이 장소 사진 작게 + [사진 N장 보기]
function appendPopupPhotos(box, row) {
  const list = photosOfStop(row.id)
  if (list.length === 0) return
  const strip = document.createElement('div')
  strip.className = 'wr-pop__photos'
  for (let i = 0; i < list.length && i < 4; i++) {
    const thumb = document.createElement('button')
    thumb.type = 'button'
    thumb.style.backgroundImage = 'url(' + list[i].url + ')'
    thumb.addEventListener('click', () => openViewer(list, i))
    strip.appendChild(thumb)
  }
  box.appendChild(strip)
  const more = makeTag('wr-pop__more', '📷 사진 ' + list.length + '장 보기')
  more.addEventListener('click', () => openViewer(list, 0))
  box.appendChild(more)
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
  clearIntro()
  resetPlay()
  showDongs(true)
  setRise(0)
  map.jumpTo({ center: SEOUL_CENTER, zoom: 10.4, pitch: is3d.value ? 45 : 0, bearing: is3d.value ? -10 : 0 })
  introTimers = [
    setTimeout(() => animateRise(1, RISE_MS), 500),
    setTimeout(() => fitTrip(2600), 2600),
    setTimeout(() => animateRise(0, 1400, () => showDongs(false)), 3600), // 다 날아가면 기둥을 눕혀서 바닥 색만 (경로가 보이게)
  ]
}

// 진행 중인 처음 연출(동네 솟아오름·날아가기) 멈추기
function clearIntro() {
  for (const timer of introTimers) clearTimeout(timer)
  introTimers = []
  cancelAnimationFrame(riseFrame)
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
  setStreetLook(false) // 내비 시점에서 숨겼던 핀·바닥 표시를 되돌림
  map.flyTo({ center: [row.lng, row.lat], zoom: 16.5, pitch: is3d.value ? 60 : 0, duration: 1600, essential: true })
  const marker = stopMarkers[row.id]
  if (marker && !marker.getPopup().isOpen()) marker.togglePopup()
}

function focusDong(dong) {
  stopPlay()
  setStreetLook(false) // 내비 시점에서 숨겼던 핀·바닥 표시를 되돌림
  map.flyTo({ center: centerOf(dong.feature), zoom: 14.2, pitch: is3d.value ? 58 : 0, duration: 1600, essential: true })
}

// ============================== 지도 고르기
function changeDay(day) {
  selectedDay.value = day
}

watch(selectedDay, () => {
  resetPlay()
  drawDay()
  fitTrip(1800)
})

function toggleSatellite() {
  isSatellite.value = !isSatellite.value
  map.setLayoutProperty('satellite', 'visibility', isSatellite.value ? 'visible' : 'none')
}

function toggle3d() {
  is3d.value = !is3d.value
  map.setLayoutProperty('building-3d', 'visibility', is3d.value ? 'visible' : 'none')
  map.setLayoutProperty('stop-building', 'visibility', is3d.value ? 'visible' : 'none')
  if (!is3d.value) {
    showDongs(false) // 2D에서는 기둥 없이 바닥 색만
    changeView('top') // 거리 시점은 3D에서만
  }
  map.easeTo({ pitch: is3d.value ? OVERVIEW_PITCH : 0, bearing: is3d.value ? -20 : 0, duration: 900 })
}

// ============================== 내비 재생
// 머문 시간대 목록: 일정 칸의 실제 도착~떠남, 계획 밖에 머문 곳
function stayWindows() {
  const list = []
  for (const row of shownStops.value) {
    if (!row.isVisited || row.lat === null || row.actualStay === null) continue
    const from = tripMinutes(row.day, row.actualArrive)
    list.push({ lng: row.lng, lat: row.lat, from: from, to: from + row.actualStay })
  }
  for (const extra of shownExtras.value) {
    const from = tripMinutes(extra.day, extra.arrive)
    list.push({ lng: extra.lng, lat: extra.lat, from: from, to: from + extra.stay })
  }
  return list
}

// 이 GPS 점이 그 머문 시간대 안인가 (떠나는 그 분의 점은 장소 근처일 때만, 이미 출발한 점은 경로로 남김)
function isInStayWindow(point, stay) {
  const time = tripMinutes(point.day, point.time)
  if (time < stay.from || time > stay.to) return false
  return time < stay.to || distanceMeters(point.lat, point.lng, stay.lat, stay.lng) <= STAY_RADIUS_M
}

// GPS 점을 재생용 지점으로 정리: 머문 시간대의 점은 흔들림이 커도 전부 그 장소 한 지점으로 묶음
function toRouteNodes(track) {
  const nodes = []
  const windows = stayWindows()
  let start = 0
  while (start < track.length) {
    const first = track[start]
    const stay = windows.find((item) => isInStayWindow(first, item))
    if (stay) {
      let last = start
      while (last + 1 < track.length && isInStayWindow(track[last + 1], stay)) last++
      nodes.push({ lng: stay.lng, lat: stay.lat, from: stay.from, to: stay.to, isStay: true })
      start = last + 1
      continue
    }
    // 일정에 없는 시간대는 GPS만 보고 판단: 한 반경에 오래 있던 점들을 한 지점으로
    let end = start
    while (end + 1 < track.length && distanceMeters(first.lat, first.lng, track[end + 1].lat, track[end + 1].lng) <= STAY_RADIUS_M) end++
    const from = tripMinutes(first.day, first.time)
    const to = tripMinutes(track[end].day, track[end].time)
    if (to - from >= STAY_MIN_MINUTES) {
      let sumLng = 0, sumLat = 0
      for (let i = start; i <= end; i++) {
        sumLng += track[i].lng
        sumLat += track[i].lat
      }
      const count = end - start + 1
      nodes.push({ lng: sumLng / count, lat: sumLat / count, from: from, to: to, isStay: true })
      start = end + 1
    } else {
      nodes.push({ lng: first.lng, lat: first.lat, from: from, to: from, isStay: false })
      start++
    }
  }
  return nodes
}

// 머문 곳에서 멈출 시간: 도착 방향과 떠나는 방향이 많이 다를수록 멈춰서 도는 시간을 더 줌
function stayPauseSeconds(nodes, i) {
  if (i === 0 || i === nodes.length - 1) return STAY_HOLD_SECONDS
  const toPoint = (node) => [node.lng, node.lat]
  const arriving = bearingOf(toPoint(nodes[i - 1]), toPoint(nodes[i]))
  const leaving = bearingOf(toPoint(nodes[i]), toPoint(nodes[i + 1]))
  const angle = Math.abs(angleDiff(arriving, leaving))
  return STAY_HOLD_SECONDS + Math.min(STAY_TURN_MAX_SECONDS, angle / maxTurnPerSecond())
}

// 재생 시간표: 이동은 거리에 비례한 시간, 머문 곳은 멈춤 (이동 속도가 일정해서 덜 어지러움)
const playRoute = computed(() => {
  const nodes = toRouteNodes(shownTrack.value)
  const coordinates = []
  const meters = [] // 출발부터 이 지점까지 경로 거리(m)
  for (let i = 0; i < nodes.length; i++) {
    coordinates.push([nodes[i].lng, nodes[i].lat])
    meters.push(i === 0 ? 0 : meters[i - 1] + distanceMeters(nodes[i - 1].lat, nodes[i - 1].lng, nodes[i].lat, nodes[i].lng))
  }
  const totalMeters = nodes.length > 0 ? meters[nodes.length - 1] : 0
  let secondsPerMeter = totalMeters > 0 ? PLAY_MOVE_SECONDS / totalMeters : 0
  if (isStreet()) secondsPerMeter = 1 / STREET_SPEED // 거리 시점은 거리와 상관없이 일정한 속도
  let clock = 0
  for (let i = 0; i < nodes.length; i++) {
    nodes[i].enter = clock
    if (nodes[i].isStay) clock += stayPauseSeconds(nodes, i)
    nodes[i].leave = clock
    if (i + 1 < nodes.length) clock += (meters[i + 1] - meters[i]) * secondsPerMeter
  }
  return { nodes: nodes, coordinates: coordinates, meters: meters, seconds: clock, secondsPerMeter: secondsPerMeter }
})

const playEnd = computed(() => playRoute.value.seconds)
const canPlay = computed(() => shownTrack.value.length >= 2 && playEnd.value > 0)

// 재생 몇 초째의 상태: 위치, 출발부터 거리(m), 여행 시각(분), 머문 곳에서 가만히 있는 중인지
function routeAt(seconds) {
  const { nodes, meters } = playRoute.value
  if (nodes.length === 0) return { position: SEOUL_CENTER, meters: 0, minutes: 0 }
  let i = 0
  while (i + 1 < nodes.length && nodes[i + 1].enter <= seconds) i++
  const node = nodes[i]
  if (seconds <= node.leave || i + 1 === nodes.length) {
    const rate = node.leave > node.enter ? Math.min(1, (seconds - node.enter) / (node.leave - node.enter)) : 0
    return { position: [node.lng, node.lat], meters: meters[i], minutes: node.from + (node.to - node.from) * rate, isHolding: node.isStay && seconds - node.enter < STAY_HOLD_SECONDS }
  }
  const next = nodes[i + 1]
  const rate = (seconds - node.leave) / (next.enter - node.leave)
  return {
    position: [node.lng + (next.lng - node.lng) * rate, node.lat + (next.lat - node.lat) * rate],
    meters: meters[i] + (meters[i + 1] - meters[i]) * rate,
    minutes: node.to + (next.from - node.to) * rate,
    isHolding: false,
  }
}

// 출발부터 이 거리(m)만큼 경로를 따라간 위치
function pointAtMeters(target) {
  const { coordinates, meters } = playRoute.value
  const last = coordinates.length - 1
  if (target >= meters[last]) return coordinates[last]
  let i = 0
  while (meters[i + 1] < target) i++
  const gap = meters[i + 1] - meters[i]
  const rate = gap > 0 ? (target - meters[i]) / gap : 0
  return [coordinates[i][0] + (coordinates[i + 1][0] - coordinates[i][0]) * rate, coordinates[i][1] + (coordinates[i + 1][1] - coordinates[i][1]) * rate]
}

// 출발부터 이 거리(m)까지 오는 데 재생 몇 초째인지 (시점을 바꿔도 같은 자리에서 이어 가려고)
function secondsAtMeters(target) {
  const { nodes, meters, secondsPerMeter } = playRoute.value
  for (let i = 0; i + 1 < nodes.length; i++) {
    if (target < meters[i + 1]) return nodes[i].leave + (target - meters[i]) * secondsPerMeter
  }
  return playEnd.value
}

// 경로에서 꺾이는 곳 찾기 (내비 안내용): 앞뒤 TURN_WINDOW_M 방향을 비교해서 많이 꺾인 곳
const routeTurns = computed(() => {
  const { nodes, meters } = playRoute.value
  const turns = []
  if (nodes.length < 3) return turns
  const total = meters[meters.length - 1]
  let best = null // 지금 꺾이는 중인 구간에서 가장 많이 꺾인 지점
  for (let at = TURN_WINDOW_M; at <= total - TURN_WINDOW_M; at += TURN_STEP_M) {
    const here = pointAtMeters(at)
    const angle = angleDiff(bearingOf(pointAtMeters(at - TURN_WINDOW_M), here), bearingOf(here, pointAtMeters(at + TURN_WINDOW_M)))
    if (Math.abs(angle) >= TURN_MIN_ANGLE) {
      if (best === null || Math.abs(angle) > Math.abs(best.angle)) best = { meters: at, angle: angle }
    } else if (best !== null) {
      if (!isNearStay(best.meters)) turns.push(toTurn(best))
      best = null
    }
  }
  if (best !== null && !isNearStay(best.meters)) turns.push(toTurn(best))
  return turns
})

// 머문 곳 근처인가 (머문 곳에서 방향이 바뀌는 건 회전 안내가 아님)
function isNearStay(target) {
  const { nodes, meters } = playRoute.value
  for (let i = 0; i < nodes.length; i++) {
    if (nodes[i].isStay && Math.abs(meters[i] - target) < TURN_STAY_GAP_M) return true
  }
  return false
}

function toTurn(found) {
  if (Math.abs(found.angle) >= TURN_UTURN_ANGLE) return { meters: found.meters, name: '유턴', arrow: '↩' }
  return found.angle > 0 ? { meters: found.meters, name: '우회전', arrow: '↱' } : { meters: found.meters, name: '좌회전', arrow: '↰' }
}

// 지금 재생 시각 (여행 첫날 0시부터 분)
const nowMinutes = computed(() => routeAt(progress.value).minutes)

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
  if (mePosition.value) meters = metersLeftTo(next)
  return { isStay: false, row: next, meters: meters, turn: nextTurn(meters) }
})

// 다음 회전 안내: 목적지 전에, TURN_NOTICE_M 안에 있는 첫 꺾임
function nextTurn(metersToGoal) {
  const now = routeAt(progress.value).meters
  for (const turn of routeTurns.value) {
    const left = turn.meters - now
    if (left <= 0) continue
    if (left > TURN_NOTICE_M || (metersToGoal !== null && left > metersToGoal)) return null
    return { name: turn.name, arrow: turn.arrow, meters: left }
  }
  return null
}

// 다음 목적지까지 남은 거리: 경로를 따라 잰 거리 (머문 지점을 못 찾으면 직선거리)
function metersLeftTo(row) {
  const { nodes, meters } = playRoute.value
  const arrive = tripMinutes(row.day, row.actualArrive)
  let best = -1
  for (let i = 0; i < nodes.length; i++) {
    if (nodes[i].isStay && (best === -1 || Math.abs(nodes[i].from - arrive) < Math.abs(nodes[best].from - arrive))) best = i
  }
  if (best !== -1 && Math.abs(nodes[best].from - arrive) <= 30) {
    const left = meters[best] - routeAt(progress.value).meters
    if (left >= 0) return left
  }
  return distanceMeters(mePosition.value[1], mePosition.value[0], row.lat, row.lng)
}

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
  clearIntro() // 처음 연출 중에 눌러도 카메라가 서로 싸우지 않게
  if (progress.value >= playEnd.value) resetPlay()
  playing.value = true
  isFollow.value = true
  showDongs(false) // 동네 기둥을 숨겨서 길·건물이 보이게 (테두리만 남김)
  setStreetLook(isStreet())
  if (progress.value === 0) {
    flyToStart()
    startFlyTimer = setTimeout(beginFrames, START_FLY_MS) // 출발점에 도착한 뒤 재생 시작
  } else {
    beginFrames() // 일시정지한 곳에서 이어서
  }
}

function beginFrames() {
  if (isStreet()) {
    cameraCenter = routeAt(progress.value).position // 거리 시점은 지도 중심이 앞쪽 먼 곳이라 내 위치부터 따라감
  } else {
    const center = map.getCenter()
    cameraCenter = [center.lng, center.lat]
  }
  smoothBearing = map.getBearing()
  lastFrameTime = performance.now()
  playFrame = requestAnimationFrame(playStep)
}

function stopPlay() {
  playing.value = false
  clearTimeout(startFlyTimer)
  cancelAnimationFrame(playFrame)
}

// 재생을 맨 처음 상태로: 위치 0, 내 위치 점과 지나온 선 지우기
function resetPlay() {
  stopPlay()
  progress.value = 0
  mePosition.value = null
  cameraCenter = null
  lastPhotoMinutes = null
  floatPhoto.value = null
  setStreetLook(false)
  if (meMarker) {
    meMarker.remove()
    meMarker = null
  }
  if (map && map.getSource('track-done')) map.getSource('track-done').setData(toLine([]))
}

// 출발점에 내 위치 점을 놓고, 카메라를 첫 이동 방향으로 부드럽게 보내기
function flyToStart() {
  const at = routeAt(0)
  drawPlayPosition(at)
  map.flyTo(Object.assign(cameraFor(at.position, headingAt(at, map.getBearing())), { duration: START_FLY_MS, essential: true }))
}

// 시점에 맞는 카메라 값. position = 내 위치, bearing = 바라볼 방향
function cameraFor(position, bearing) {
  if (isStreet()) {
    const eye = movePoint(position, bearing + 180, STREET_BACK_M)
    return map.calculateCameraOptionsFromCameraLngLatAltRotation(eye, STREET_EYE_M, bearing, STREET_PITCH)
  }
  return { center: position, zoom: NAV_ZOOM, pitch: is3d.value ? NAV_PITCH : 0, bearing: bearing }
}

// [위에서 / 거리] 버튼: 재생 중이면 카메라를 옮긴 뒤 같은 자리에서 이어서 재생
function changeView(mode) {
  if (viewMode.value === mode) return
  const wasPlaying = playing.value
  const hasStarted = progress.value > 0
  const meters = routeAt(progress.value).meters
  cancelAnimationFrame(playFrame)
  clearTimeout(startFlyTimer)
  viewMode.value = mode
  setStreetLook(isStreet() && (hasStarted || wasPlaying))
  if (hasStarted) progress.value = Math.min(playEnd.value, secondsAtMeters(meters))
  if (!hasStarted && !wasPlaying) return
  const at = routeAt(progress.value)
  drawPlayPosition(at)
  map.flyTo(Object.assign(cameraFor(at.position, headingAt(at, map.getBearing())), { duration: VIEW_CHANGE_MS, essential: true }))
  if (wasPlaying) startFlyTimer = setTimeout(beginFrames, VIEW_CHANGE_MS)
}

// 거리 시점으로 볼 때는 바닥에 그린 면·경계선·계획 점선을 숨겨서 길과 건물만 보이게
function setStreetLook(isOn) {
  if (!map || !map.getLayer('plan-line')) return
  for (const id of ['dong-fill', 'dong-line', 'gu-line', 'plan-line']) map.setLayoutProperty(id, 'visibility', isOn ? 'none' : 'visible')
  if (!isOn) showAllPins()
}

// 거리 시점: 가까운 장소 핀만 보이게
function updateStreetPins(position) {
  for (const id in stopMarkers) {
    const point = stopMarkers[id].getLngLat()
    stopMarkers[id].getElement().style.display = distanceMeters(position[1], position[0], point.lat, point.lng) <= STREET_PIN_RANGE_M ? '' : 'none'
  }
  for (const marker of otherMarkers) marker.getElement().style.display = 'none'
  for (const marker of photoMarkers) marker.getElement().style.display = 'none'
}

function showAllPins() {
  for (const id in stopMarkers) stopMarkers[id].getElement().style.display = ''
  for (const marker of otherMarkers) marker.getElement().style.display = ''
  for (const marker of photoMarkers) marker.getElement().style.display = ''
}

// [처음으로] 버튼
function goToStart() {
  if (!canPlay.value) return
  clearIntro()
  resetPlay()
  showDongs(false)
  setStreetLook(isStreet())
  flyToStart()
}

function playStep(now) {
  const seconds = Math.max(0, Math.min(0.1, (now - lastFrameTime) / 1000)) // 탭을 다녀와도 순간이동하지 않게 한 번에 0.1초까지만
  lastFrameTime = now
  progress.value = Math.min(playEnd.value, progress.value + seconds * speed.value)
  showProgress(seconds)
  checkPhotoPopup()
  if (progress.value >= playEnd.value) {
    playing.value = false
    return
  }
  playFrame = requestAnimationFrame(playStep)
}

// 재생 위치를 지도에: 내 위치 점, 지나온 선, 카메라. frameSeconds가 없으면(막대 끌기) 카메라를 바로 옮김
function showProgress(frameSeconds) {
  if (!canPlay.value) return
  const at = routeAt(progress.value)
  drawPlayPosition(at)
  if (isFollow.value) followCamera(at, frameSeconds)
}

function drawPlayPosition(at) {
  mePosition.value = at.position
  if (!meMarker) {
    meMarker = new maplibregl.Marker({ element: makeTag('wr-me', ''), anchor: 'center' }).setLngLat(at.position).addTo(map)
  } else {
    meMarker.setLngLat(at.position)
  }
  const { coordinates, meters } = playRoute.value
  const done = []
  for (let i = 0; i < coordinates.length && meters[i] <= at.meters; i++) done.push(coordinates[i])
  done.push(at.position)
  map.getSource('track-done').setData(toLine(done))
  if (isStreet()) updateStreetPins(at.position)
  const now = performance.now()
  if (now - lastBuildingFind > BUILDING_FIND_MS) {
    lastBuildingFind = now
    findStopBuildings()
  }
}

// 이 위치에서 경로를 따라 조금 앞을 보는 방향 (앞이 너무 가까우면 지금 방향 유지)
function headingAt(at, current) {
  // 다음 머문 곳 너머는 내다보지 않음 (도착할 때까지 목적지를 바라보게)
  const { nodes, meters } = playRoute.value
  let limit = meters[meters.length - 1]
  for (let i = 0; i < nodes.length; i++) {
    if (nodes[i].isStay && meters[i] > at.meters + 1) {
      limit = meters[i]
      break
    }
  }
  const ahead = pointAtMeters(Math.min(limit, at.meters + (isStreet() ? STREET_LOOK_AHEAD_M : LOOK_AHEAD_M)))
  if (distanceMeters(at.position[1], at.position[0], ahead[1], ahead[0]) <= MIN_TURN_METERS) return current
  return bearingOf(at.position, ahead)
}

// 내비처럼: 앞을 바라보며 따라가되, 회전은 1초에 MAX_TURN_PER_SECOND까지만
function followCamera(at, frameSeconds) {
  const heading = headingAt(at, smoothBearing)
  if (frameSeconds === undefined || cameraCenter === null) {
    smoothBearing = heading
    cameraCenter = at.position
  } else {
    if (!at.isHolding) smoothBearing = turnToward(smoothBearing, heading, frameSeconds) // 머문 곳에서 가만히 있는 동안은 방향 그대로
    const rate = 1 - Math.exp(-frameSeconds / CENTER_SMOOTH_SECONDS)
    cameraCenter = [cameraCenter[0] + (at.position[0] - cameraCenter[0]) * rate, cameraCenter[1] + (at.position[1] - cameraCenter[1]) * rate]
  }
  map.jumpTo(cameraFor(cameraCenter, smoothBearing))
}

function changeSpeed() {
  speed.value = speed.value === 4 ? 1 : speed.value * 2
}

// 막대를 끌면 그 위치로
const sliderValue = computed({
  get: () => (playEnd.value === 0 ? 0 : Math.round((progress.value / playEnd.value) * 1000)),
  set: (value) => {
    progress.value = (Number(value) / 1000) * playEnd.value
    lastPhotoMinutes = nowMinutes.value // 막대로 건너뛴 사이의 사진은 띄우지 않음
    showProgress()
  },
})

function backToOverview() {
  resetPlay()
  fitTrip(1600)
}

// ============================== 재생 중 사진 띄우기
// 지난 화면 시각 ~ 지금 시각 사이에 찍은 사진이 있으면 카드로 잠깐 띄움
function checkPhotoPopup() {
  const now = nowMinutes.value
  const before = lastPhotoMinutes === null ? now - 1 : lastPhotoMinutes
  lastPhotoMinutes = now
  for (const photo of shownPhotos.value) {
    if (photo.minutes > before && photo.minutes <= now) {
      showFloatPhoto(photo)
      return
    }
  }
}

function showFloatPhoto(photo) {
  floatPhoto.value = photo
  clearTimeout(floatTimer)
  floatTimer = setTimeout(() => {
    floatPhoto.value = null
  }, PHOTO_POP_MS)
}

// ============================== 사진 크게 보기
function openViewer(list, index) {
  stopPlay()
  viewer.value = { list: list, index: Math.max(0, index) }
}

const viewerPhoto = computed(() => (viewer.value ? viewer.value.list[viewer.value.index] : null))

function moveViewer(step) {
  const count = viewer.value.list.length
  viewer.value.index = (viewer.value.index + step + count) % count
}

// 크게 보기 단축키: ← → 넘기기, Esc 닫기
function onViewerKey(event) {
  if (!viewer.value) return
  if (event.key === 'Escape') viewer.value = null
  else if (event.key === 'ArrowLeft') moveViewer(-1)
  else if (event.key === 'ArrowRight') moveViewer(1)
}

function photoCaption(photo) {
  return photo.day + '일차 ' + photo.takenAt + ' · ' + photo.placeName
}

// ============================== 해: 재생 막대의 낮·밤 띠, 일출·일몰 바로 가기
// 이 여행 시각(분)에 해당하는 재생 위치(초). 재생 시각은 계속 늘기만 해서 반씩 좁혀 찾음 (이분 탐색)
function secondsAtMinutes(target) {
  let low = 0
  let high = playEnd.value
  for (let i = 0; i < 30; i++) {
    const middle = (low + high) / 2
    if (routeAt(middle).minutes < target) low = middle
    else high = middle
  }
  return high
}

// 막대 띠 색: 재생 위치마다 그 시각의 하늘 색 (낮은 하늘색, 노을은 주황, 밤은 남색)
const sunStripStyle = computed(() => {
  if (!canPlay.value || !retro.value) return {}
  const stops = []
  for (let i = 0; i <= SUN_STRIP_SAMPLES; i++) {
    const sun = sunAt(routeAt((playEnd.value * i) / SUN_STRIP_SAMPLES).minutes)
    stops.push((sun.altitude > 6 ? sun.sky.sky : sun.sky.horizon) + ' ' + ((i / SUN_STRIP_SAMPLES) * 100).toFixed(1) + '%')
  }
  return { background: 'linear-gradient(to right, ' + stops.join(', ') + ')' }
})

// 막대 위 눈금: 재생 구간 안에 든 일출·일몰
const sunTicks = computed(() => {
  if (!canPlay.value) return []
  const first = routeAt(0).minutes
  const last = routeAt(playEnd.value).minutes
  const ticks = []
  for (const item of daySuns.value) {
    const events = [{ icon: '🌅', name: '일출', minute: item.times.sunrise }, { icon: '🌇', name: '일몰', minute: item.times.sunset }]
    for (const event of events) {
      const tripMinute = (item.day - 1) * MINUTES_PER_DAY + event.minute
      if (tripMinute <= first || tripMinute >= last) continue
      const percent = (secondsAtMinutes(tripMinute) / playEnd.value) * 100
      ticks.push({ key: item.day + event.name, label: event.icon + ' ' + toTime(event.minute), title: item.day + '일차 ' + event.name, percent: percent, tripMinute: tripMinute })
    }
  }
  return ticks
})

// 그 시각으로 재생 위치를 옮기고 멈춤 (일출·일몰 눈금, 오른쪽 카드에서)
function goToMinute(tripMinute) {
  if (!canPlay.value) return
  clearIntro()
  stopPlay()
  showDongs(false)
  setStreetLook(isStreet())
  isFollow.value = true
  progress.value = secondsAtMinutes(tripMinute)
  lastPhotoMinutes = nowMinutes.value
  showProgress()
}

function toggleSun() {
  isSunOn.value = !isSunOn.value
}

// 재생 시각이 바뀌어 해가 움직이면 하늘·빛을 다시 칠함
watch(currentSun, applySun)

// 메모·사진은 고른 날이 바뀌면 다시 그림 (drawDay 안에서 drawMarkers가 사진도 그림)
watch(shownPhotos, drawPhotoMarkers)
</script>

<template>
  <div class="wr">
    <section class="wr-map">
      <div ref="mapBox" class="wr-map__canvas"></div>
      <!-- 밤·노을 덮개: 재생 시각의 해 높이에 맞춘 색 (지도 위, 카드 아래, 누르기는 통과) -->
      <div class="wr-sky" :style="{ background: skyShade }"></div>

      <div v-if="isLoading || errorMessage" class="wr-state">
        <p>{{ errorMessage || '회고를 불러오는 중…' }}</p>
      </div>

      <template v-if="retro">
        <!-- 위 왼쪽: 여행 제목 -->
        <div class="wr-title">
          <span class="wr-badge">착착트립 · 웹 회고</span>
          <h1>{{ retro.title }}</h1>
          <p>{{ retro.startDate }} ~ {{ retro.endDate }} · {{ retro.people }}명<template v-if="visitedGuText"> · {{ visitedGuText }}</template></p>
          <div class="wr-title__links">
            <RouterLink to="/web/trips" class="wr-btn">← 지난 여행</RouterLink>
            <RouterLink :to="'/web/journal?id=' + retro.id" class="wr-btn wr-btn--memo">📝 여행 메모하기</RouterLink>
          </div>
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
          <button type="button" class="wr-btn" :class="{ 'is-on': isPhotoShown }" @click="isPhotoShown = !isPhotoShown">📷 사진 {{ isPhotoShown ? '켜짐' : '꺼짐' }}</button>
          <button type="button" class="wr-btn" :class="{ 'is-on': isSunOn }" title="재생 시각에 맞춰 하늘·밝기가 바뀌어요" @click="toggleSun">🌇 해·하늘 {{ isSunOn ? '켜짐' : '꺼짐' }}</button>
          <button type="button" class="wr-btn" @click="runIntro">동네 다시 띄우기</button>
        </div>

        <!-- 내비 카드 -->
        <div v-if="navCard && (playing || progress > 0)" class="wr-nav" :class="{ 'wr-nav--street': viewMode === 'street' }">
          <div class="wr-nav__time">{{ toDayTime(nowMinutes) }}<span v-if="sunBadge" class="wr-nav__sun" :class="'is-' + sunBadge.phase">{{ sunBadge.name }} · {{ sunBadge.detail }}</span></div>
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
            <div v-if="navCard.turn" class="wr-nav__turn"><span>{{ navCard.turn.arrow }}</span> {{ metersText(navCard.turn.meters) }} 앞 {{ navCard.turn.name }}</div>
            <div class="wr-nav__sub">계획 {{ navCard.row.time }} 도착 · 실제 {{ navCard.row.actualArrive }}</div>
          </template>
        </div>

        <!-- 범례 -->
        <div class="wr-legend">
          <span><i class="wr-legend__real"></i>실제 경로 (GPS)</span>
          <span><i class="wr-legend__plan"></i>계획 경로</span>
          <span><i class="wr-legend__dong"></i>머문 동네 (높을수록 오래)</span>
          <span><i class="wr-legend__pass"></i>지나간 동네</span>
          <span v-for="kind in paintedKinds" :key="kind.name"><i class="wr-legend__dong" :style="{ background: kind.color, opacity: 1 }"></i>{{ kind.name }} 건물</span>
          <span v-if="shownPhotos.length > 0"><i class="wr-legend__photo"></i>사진 {{ shownPhotos.length }}장 (찍은 곳)</span>
        </div>

        <!-- 재생 중 사진을 찍은 시각을 지나면 잠깐 뜨는 카드 -->
        <Transition name="wr-float">
          <button v-if="floatPhoto" type="button" class="wr-float" @click="openViewer([floatPhoto], 0)">
            <video v-if="floatPhoto.mediaType === 'video'" :src="floatPhoto.url" muted autoplay loop></video>
            <img v-else :src="floatPhoto.url" alt="" />
            <span>📷 {{ floatPhoto.takenAt }} · {{ floatPhoto.placeName }}</span>
          </button>
        </Transition>

        <!-- 재생 막대 -->
        <div class="wr-player">
          <button type="button" class="wr-player__play" :disabled="!canPlay" @click="togglePlay">{{ playing ? '일시정지' : '▶ 내비로 다시 걷기' }}</button>
          <button type="button" class="wr-btn" :disabled="!canPlay" @click="goToStart">처음으로</button>
          <div class="wr-seg wr-seg--view">
            <button type="button" :class="{ 'is-on': viewMode === 'top' }" @click="changeView('top')">위에서</button>
            <button type="button" :class="{ 'is-on': viewMode === 'street' }" :disabled="!is3d" @click="changeView('street')">내비</button>
          </div>
          <button type="button" class="wr-btn" :disabled="!canPlay" @click="changeSpeed">{{ speed }}배속</button>
          <div class="wr-player__barwrap">
            <!-- 낮·밤 띠 + 일출·일몰 눈금 (누르면 그 시각으로) -->
            <div v-if="isSunOn && canPlay" class="wr-player__sun" :style="sunStripStyle"></div>
            <template v-if="isSunOn">
              <button v-for="tick in sunTicks" :key="tick.key" type="button" class="wr-player__tick" :style="{ left: tick.percent + '%' }" :title="tick.title + '으로 가기'" @click="goToMinute(tick.tripMinute)">{{ tick.label }}</button>
            </template>
            <input v-model="sliderValue" type="range" min="0" max="1000" :disabled="!canPlay" class="wr-player__bar" aria-label="재생 위치" />
          </div>
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

      <!-- 해 뜨고 진 시각 -->
      <section v-if="daySuns.length > 0" class="wr-card">
        <h2>해 뜨고 진 시각 <small>여행한 곳 기준</small></h2>
        <div v-for="item in daySuns" :key="item.day" class="wr-sunrow">
          <span class="wr-sunrow__day">{{ item.day }}일차</span>
          <span>🌅 {{ toTime(item.times.sunrise) }}</span>
          <span>🌇 {{ toTime(item.times.sunset) }}</span>
          <span class="wr-sunrow__len">낮 {{ dayLengthText(item.times) }}</span>
        </div>
        <button v-for="moment in sunMoments" :key="moment.key" type="button" class="wr-moment" :disabled="!canPlay" @click="goToMinute(moment.tripMinute)">
          <span class="wr-moment__icon">{{ moment.icon }}</span>
          <span>{{ moment.day }}일차 {{ moment.time }} {{ moment.name }} <b>{{ moment.place }}</b>{{ moment.place === '이동 중' ? '이었어요' : '에 있었어요' }}</span>
          <span class="wr-moment__go">그 순간 보기 →</span>
        </button>
      </section>

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

    <!-- 사진 크게 보기 -->
    <div v-if="viewerPhoto" class="wr-viewer" role="dialog" aria-modal="true" aria-label="사진 크게 보기" @click.self="viewer = null">
      <video v-if="viewerPhoto.mediaType === 'video'" :key="viewerPhoto.id" :src="viewerPhoto.url" controls autoplay></video>
      <img v-else :src="viewerPhoto.url" alt="" />
      <div class="wr-viewer__bar">
        <span>{{ photoCaption(viewerPhoto) }} <small v-if="viewer.list.length > 1">{{ viewer.index + 1 }} / {{ viewer.list.length }}</small></span>
        <span v-if="viewerPhoto.stopId && journal.stopNotes[viewerPhoto.stopId]" class="wr-viewer__note">📝 {{ journal.stopNotes[viewerPhoto.stopId] }}</span>
        <RouterLink :to="'/web/journal?id=' + retro.id + '&tab=photo'" class="wr-btn">편집하기</RouterLink>
      </div>
      <button v-if="viewer.list.length > 1" type="button" class="wr-viewer__move wr-viewer__move--prev" aria-label="이전 사진" @click="moveViewer(-1)">‹</button>
      <button v-if="viewer.list.length > 1" type="button" class="wr-viewer__move wr-viewer__move--next" aria-label="다음 사진" @click="moveViewer(1)">›</button>
      <button type="button" class="wr-viewer__close" aria-label="닫기" @click="viewer = null">✕</button>
    </div>
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
.wr-nav__turn { margin-top: 2px; font-size: 18px; font-weight: 700; color: #FFE08A; }
.wr-nav__turn span { font-size: 22px; }
.wr-nav--street { min-width: 420px; padding: 16px 24px; }
.wr-nav--street .wr-nav__turn { font-size: 26px; line-height: 34px; }
.wr-nav--street .wr-nav__turn span { font-size: 32px; }
.wr-nav--street .wr-nav__place { font-size: 28px; line-height: 36px; }
.wr-nav--street .wr-nav__place span { display: block; margin-left: 0; font-size: 22px; font-weight: 700; opacity: 1; }

.wr-legend { left: 16px; bottom: 92px; display: flex; flex-direction: column; gap: 4px; padding: 10px 12px; font-size: 12px; }
.wr-legend i { display: inline-block; width: 22px; height: 0; margin-right: 6px; vertical-align: middle; }
.wr-legend__real { border-top: 5px solid #2F7BFF; }
.wr-legend__plan { border-top: 4px dashed #7C8496; }
.wr-legend__dong { height: 10px !important; background: #E8603F; opacity: 0.7; }
.wr-legend__pass { height: 10px !important; background: #8FA6CF; opacity: 0.7; }

.wr-player { left: 16px; right: 16px; bottom: 16px; display: flex; flex-wrap: wrap; align-items: center; gap: 10px; padding: 10px 14px; }
.wr-player__play { padding: 8px 16px; border: 0; border-radius: var(--radius-md); background: var(--coral); color: #fff; font-weight: 700; cursor: pointer; white-space: nowrap; }
.wr-player__bar { flex: 1; min-width: 60px; accent-color: var(--coral); }
.wr-player__time { min-width: 92px; font-variant-numeric: tabular-nums; }
.wr-player__follow { white-space: nowrap; }
.wr-player .wr-btn { white-space: nowrap; }
.wr-seg--view { flex: none; }
.wr-seg--view button { white-space: nowrap; }
.wr-seg--view button:disabled { opacity: 0.5; cursor: default; }

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
:deep(.wr-me) { width: 18px; height: 18px; border: 3px solid #fff; border-radius: 50%; background: #2F7BFF; box-shadow: 0 0 0 6px rgba(47, 123, 255, 0.3); }
:deep(.wr-pop__title) { font-weight: 700; margin-bottom: 4px; }
:deep(.wr-pop__line) { font-size: 13px; }
:deep(.wr-pop__line--real) { color: var(--navy); font-weight: 700; }
:deep(.wr-pop__diff) { margin-top: 2px; color: var(--coral-ink); font-weight: 700; font-size: 13px; }
:deep(.wr-pop__pay) { margin-top: 4px; font-size: 12px; color: var(--ink-muted); }

/* (11/10) 웹사이트 이동 버튼 · 사진 · 해 */
.wr-btn { display: inline-block; color: var(--ink); font: inherit; text-decoration: none; }
.wr-btn.is-on { border-color: var(--navy); background: var(--navy-soft); color: var(--navy); font-weight: 700; }
.wr-btn--memo { border-color: var(--coral); background: var(--coral); color: #fff; font-weight: 700; }
.wr-title__links { display: flex; gap: 6px; margin-top: 10px; }
.wr-sky { position: absolute; inset: 0; z-index: 1; pointer-events: none; transition: background 0.8s; }
.wr-nav__sun { margin-left: 8px; padding: 1px 8px; border-radius: var(--radius-full); background: rgba(255, 255, 255, 0.16); }
.wr-nav__sun.is-golden { background: rgba(255, 154, 60, 0.4); }
.wr-nav__sun.is-twilight { background: rgba(160, 120, 220, 0.4); }
.wr-nav__sun.is-night { background: rgba(10, 20, 60, 0.6); }
.wr-legend__photo { width: 14px !important; height: 14px !important; border: 2px solid #fff; border-radius: 50%; background: #F2A33A; box-shadow: 0 0 0 1px #F2A33A; }
.wr-player__barwrap { position: relative; flex: 1; min-width: 60px; padding-top: 24px; }
.wr-player__barwrap .wr-player__bar { width: 100%; }
.wr-player__sun { position: absolute; left: 0; right: 0; top: 18px; height: 6px; border-radius: var(--radius-full); }
.wr-player__tick { position: absolute; top: 0; transform: translateX(-50%); padding: 0 6px; border: 0; border-radius: var(--radius-full); background: var(--navy); color: #fff; font: inherit; font-size: 11px; line-height: 16px; white-space: nowrap; cursor: pointer; }
.wr-float { position: absolute; z-index: 3; right: 16px; bottom: 120px; width: 240px; padding: 0; overflow: hidden; border: 3px solid #fff; border-radius: var(--radius-lg); background: #000; box-shadow: 0 8px 28px rgba(0, 0, 0, 0.35); cursor: zoom-in; }
.wr-float img, .wr-float video { display: block; width: 100%; height: 170px; object-fit: cover; }
.wr-float span { display: block; padding: 6px 10px; background: #fff; color: var(--ink); font-size: 13px; text-align: left; }
.wr-float-enter-active, .wr-float-leave-active { transition: opacity 0.3s, transform 0.3s; }
.wr-float-enter-from, .wr-float-leave-to { opacity: 0; transform: translateY(16px); }
.wr-sunrow { display: grid; grid-template-columns: 56px 1fr 1fr auto; gap: 6px; padding: 6px 0; border-top: 1px solid var(--line); font-variant-numeric: tabular-nums; }
.wr-sunrow__day { font-weight: 700; }
.wr-sunrow__len { color: var(--ink-muted); font-size: 12px; }
.wr-moment { display: flex; align-items: center; gap: 8px; width: 100%; margin-top: 8px; padding: 8px 10px; border: 0; border-radius: var(--radius-md); background: linear-gradient(90deg, #FFE9D6, #F3E6FF); color: var(--ink); font: inherit; font-size: 13px; text-align: left; cursor: pointer; }
.wr-moment:disabled { cursor: default; }
.wr-moment__icon { font-size: 20px; }
.wr-moment__go { margin-left: auto; color: var(--coral-ink); font-weight: 700; white-space: nowrap; }
.wr-viewer { position: fixed; inset: 0; z-index: 30; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; background: rgba(8, 12, 22, 0.92); }
.wr-viewer img, .wr-viewer video { max-width: 86vw; max-height: 78vh; border-radius: var(--radius-md); box-shadow: 0 12px 40px rgba(0, 0, 0, 0.5); }
.wr-viewer__bar { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; justify-content: center; max-width: 86vw; color: #fff; }
.wr-viewer__bar small { margin-left: 6px; opacity: 0.7; }
.wr-viewer__note { padding: 4px 10px; border-radius: var(--radius-md); background: rgba(255, 255, 255, 0.12); }
.wr-viewer__move, .wr-viewer__close { position: absolute; border: 0; border-radius: 50%; background: rgba(255, 255, 255, 0.14); color: #fff; cursor: pointer; }
.wr-viewer__move { top: 50%; width: 52px; height: 52px; margin-top: -26px; font-size: 32px; line-height: 48px; }
.wr-viewer__move--prev { left: 24px; }
.wr-viewer__move--next { right: 24px; }
.wr-viewer__close { top: 20px; right: 20px; width: 42px; height: 42px; font-size: 18px; }
:deep(.wr-photo) { position: relative; width: 46px; height: 46px; padding: 0; border: 3px solid #fff; border-radius: 50%; background: #F2A33A center / cover no-repeat; box-shadow: 0 3px 10px rgba(0, 0, 0, 0.35); cursor: zoom-in; }
:deep(.wr-photo:hover) { transform: scale(1.12); }
:deep(.wr-photo--video)::after { content: '▶'; position: absolute; inset: 0; display: grid; place-items: center; color: #fff; font-size: 14px; text-shadow: 0 1px 3px rgba(0, 0, 0, 0.6); }
:deep(.wr-photo__count) { position: absolute; right: -6px; top: -6px; min-width: 18px; height: 18px; padding: 0 4px; border-radius: 9px; background: var(--coral); color: #fff; font-size: 11px; line-height: 18px; text-align: center; }
:deep(.wr-pop__note) { margin-top: 6px; padding: 6px 8px; border-radius: var(--radius-sm); background: #FFF5E8; font-size: 12px; white-space: pre-wrap; }
:deep(.wr-pop__photos) { display: grid; grid-template-columns: repeat(4, 1fr); gap: 4px; margin-top: 8px; }
:deep(.wr-pop__photos button) { aspect-ratio: 1; padding: 0; border: 0; border-radius: var(--radius-sm); background: #ddd center / cover no-repeat; cursor: zoom-in; }
:deep(.wr-pop__more) { margin-top: 6px; color: var(--navy); font-size: 12px; font-weight: 700; cursor: pointer; }

@media (max-width: 1100px) {
  .wr { flex-direction: column; overflow-y: auto; height: auto; }
  .wr-map { height: 70vh; flex: none; }
  .wr-side { width: auto; border-left: 0; }
}
</style>
