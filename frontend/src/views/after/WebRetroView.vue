<script setup>
// W19 웹 회고 (⑤, 11/09 중간발표 피드백: "사용자는 앱, 자세한 건 웹")
// 앱 회고와 같은 API(retro·payments·companions)를 읽어서 PC 화면에 크게 보여 줘요
//  - 3D 서울 지도(건물, 산은 음영으로) 위에 다녀온 동네(행정동)가 머문 시간만큼 솟아오름
//  - 실제 경로(GPS, 파랑)
//  - 내비게이션처럼 카메라가 이동 경로를 따라가며 재생 (위에서 보기 / 거리 시점)
//  - 다녀온 장소의 건물은 일정 분류(음식점·카페·쇼핑·숙소 등)별 색
//  - 오른쪽: 계획 vs 실제 표, 비용, 정산 (11/11: 정산은 여기서도 고쳐요 — 앱과 같은 API)
//  - (11/10) 사진: 찍은 시각·장소에 맞춰 지도에 놓고, 재생 중 그 시각을 지나면 띄움
//  - (11/10) 해: 재생 시각의 해 높이로 하늘 색·밝기·건물 빛 방향이 바뀜 (11/11 일출·일몰 시각 카드는 뺌)
//  - (11/11) 오른쪽 메모 카드: 여행 메모·날짜별 메모 + 핀에서 고른 장소의 메모·별점 (이 브라우저에 저장)
//  - (11/11) 풍경 분석: GPS를 구간으로 묶고 DEM·주변 장소를 겹쳐 구간 성격을 분류 (landscape/*, RetroLandscapeCard)
//  - (11/11) 3D 지형(DEM)은 3D에서 늘 켬. 내비(거리 시점) 재생 때만 끔
//  - (11/11) 오른쪽 사진 카드: 따로 있던 메모·사진 페이지(/web/journal)를 지우고 올리기·앨범·꾸미기·대표 사진을 여기로 (RetroPhotoCard)
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as maplibregl from 'maplibre-gl' // MapLibre: 3D(기울이기·건물·지형)가 되는 무료 지도 라이브러리 (키 없음)
import 'maplibre-gl/dist/maplibre-gl.css'
// 지도 계산을 하는 뒤쪽 일꾼(worker) 파일 주소. Vite가 라이브러리를 옮겨 담아서 기본 주소를 못 찾기 때문에 직접 알려 줌
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?url'
import { get, post, put, del, upload } from '@/api/http'
import { toTime, toMinutes, toDuration, toWon } from '@/utils/time'
import { sunPosition, sunPhase, skyAt, koreanDate } from '@/utils/sun'
import { loadJournal, editedOriginalIds, makeDelayedSave, toggleStopRating } from '@/utils/webJournal'
import RetroPhotoCard from '@/components/after/web/RetroPhotoCard.vue'
import RetroLandscapeCard from '@/components/after/web/RetroLandscapeCard.vue'
import RetroUnityMap from '@/components/after/web/RetroUnityMap.vue' // 유니티로 만든 실감 3D 지도 (11/13)
import { makeTripMap } from '@/components/after/web/landscape/unityTrip'
import { analyzeLandscape } from '@/components/after/web/landscape/analyze'
import { makeGreenReader, sunStateOf } from '@/components/after/web/landscape/sunlight'
import { middleOf } from '@/components/after/web/landscape/segments'
import { findStays } from '@/components/after/web/landscape/dbscan'
import { elevationNow } from '@/components/after/web/landscape/terrain'

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
const NAV_ZOOM = 15.2            // 내비 재생 확대 (너무 가까우면 화면이 빨리 흘러서 어지러움)
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
const STREET_EYE_M = 2.4         // 거리 시점(1인칭): 눈높이(m). 사람보다 조금 높아야 길이 보임
const STREET_BACK_M = 3          // 거리 시점: 내 위치에서 이만큼 뒤 (거의 내 눈 자리)
const STREET_PITCH = 85          // 거리 시점: 기울기 (90이면 수평, 지도가 85까지 됨)
const STREET_SPEED = 9           // 거리 시점: 1배속 이동 속도(초당 m). 걷는 느낌이 나게 천천히 (배속 버튼으로 빨리)
const SPLIT_STAY_RADIUS_M = 30   // 나눈 머문 곳 조각의 동그라미 반경(m)
const UNITY_TOP_SPEED = 50       // 3D 지도 위에서 보기: 1배속 이동 속도(초당 m). 평면 지도(여행 전체 40초)보다 천천히 (건물이 가까이 보여서)
const STREET_LOOK_AHEAD_M = 35   // 거리 시점: 경로를 따라 이만큼 앞을 바라봄 (모퉁이에서 늦지 않게)
const STREET_MAX_TURN = 30       // 거리 시점: 1초에 돌 수 있는 최대 각도
const STREET_TURN_SMOOTH = 0.5   // 거리 시점: 방향을 따라가는 부드러움
const STREET_PITCH_FLAT = 80     // 1인칭: 평지에서 기울기. 오르막이면 고개를 들고(85까지) 내리막이면 숙임(72까지)
const STREET_SLOPE_AHEAD_M = 30  // 1인칭: 이만큼 앞의 땅 높이와 비교해 경사를 잼
const STREET_PITCH_PER_SLOPE = 60 // 1인칭: 경사 10%면 고개를 6도 움직임
const STREET_PITCH_SMOOTH = 0.08 // 1인칭: 고개 움직임을 이 비율만큼씩 따라감 (튀지 않게)
const STREET_GROUND_OPACITY = 0.6 // 1인칭 바닥에 까는 위성 사진 진하기
const STREET_PIN_RANGE_M = 300   // 거리 시점: 이 거리 안의 장소 핀만 보여 줌 (먼 핀이 건물을 뚫고 보여서)
const TURN_WINDOW_M = 40         // 회전 안내: 이 거리만큼 앞뒤 방향을 비교함 (GPS 잔떨림은 무시)
const TURN_STEP_M = 10           // 회전 안내: 경로를 이 간격으로 살펴봄
const TURN_MIN_ANGLE = 45        // 회전 안내: 이 각도 이상 꺾여야 안내함
const TURN_UTURN_ANGLE = 150     // 회전 안내: 이 각도 이상이면 유턴
const TURN_STAY_GAP_M = 60       // 회전 안내: 머문 곳에서 이 거리 안의 꺾임은 안내하지 않음
const TURN_NOTICE_M = 500        // 회전 안내: 이 거리 안에 들어오면 카드에 보여 줌
const SIDE_KEY = 'chakchak.webRetroSide'
const SIDE_DEFAULT = 440         // 오른쪽 칸 기본 너비(px)
const SIDE_MIN = 320             // 오른쪽 칸 최소 너비
const SIDE_MAX_RATIO = 0.55      // 오른쪽 칸은 화면의 55%까지 (지도가 너무 좁아지지 않게)
const FOREST_URL = '/geo/forest-seoul.json' // 숲 구역 (임상도 + NDVI를 미리 계산해 넣은 파일, 없으면 숲 층 없음)
const PHOTO_POP_MS = 3500        // 재생 중 사진 카드가 떠 있는 시간
const PHOTO_MARKER_OFFSET = [30, -44] // 장소 사진 동그라미를 핀 오른쪽 위에 (핀과 안 겹치게)
const SUN_STRIP_SAMPLES = 120    // 재생 막대의 낮·밤 띠를 이만큼 나눠서 색칠
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
const isUnity3d = ref(false) // 실감 3D(유니티) 칸을 지도 위에 띄웠는지
const isDayNight = ref(true) // 재생 시각에 맞춰 밤낮이 바뀌는지 (끄면 늘 낮)
const unityMap = ref(null)   // 실감 3D 부품 (재생 시각·시점을 넘겨줌)
const unityTrip = ref(null)  // 이 여행의 실감 3D 지도 자료 { json, photo } (켜면 이 브라우저에서 만듦)
const unityStatus = ref('')  // 지도 자료를 만드는 중 안내 글
const playing = ref(false)
const speed = ref(1)              // 1·2·4배
const progress = ref(0)           // 재생 위치 = 재생 시작부터 몇 초째인지 (playRoute의 시간표 기준)
const isFollow = ref(true)        // 재생 중 카메라가 따라가는지
const viewMode = ref('top')       // 재생 시점: 'top' = 위에서, 'street' = 거리
const mePosition = ref(null)      // 재생 중 지금 위치 [경도, 위도]
const geoReady = ref(false)       // 서울 동·구 경계를 다 읽었는지
const mapBox = ref(null)
const photos = ref([])            // 이 여행 사진·영상 (GET /api/trips/{id}/photos)
const journal = reactive(loadJournal(route.query.id)) // 여행·장소 메모, 장소 별점, 대표 사진 (이 브라우저). reactive: 안의 값이 바뀌면 화면이 따라 바뀜
const memoStopId = ref(null)     // 메모 카드에서 고른 장소 (핀·표를 누르면 바뀜)
const memoSavedText = ref('')
const isPhotoShown = ref(true)    // 지도에 사진 보이기
const isSunOn = ref(true)         // 해·하늘 반영
const floatPhoto = ref(null)      // 재생 중 잠깐 뜨는 사진 카드
const viewer = ref(null)          // 크게 보기 { list, index }
const isNavFolded = ref(false)    // 내비 카드를 한 줄로 접었는지
const navOffset = ref({ x: 0, y: 0 }) // 내비 카드를 끌어 옮긴 거리(px)
const clusters = ref({ isOn: false, stays: [] }) // DBSCAN으로 찾은 머문 곳 군집
const navSlope = ref(null)        // 1인칭에서 지금 자리의 경사·해발 { percent, altitude } (DEM)
const sideWidth = ref(readSideWidth()) // 오른쪽 칸 너비(px). 가운데 막대를 끌어서 바꿈
const photoCard = ref(null)       // 오른쪽 사진 카드 (사진 크게 보기의 [꾸미기]에서 편집기를 열 때)
const landscape = ref({ status: 'idle', done: 0, total: 0, items: [], message: '' }) // 풍경 분석 결과
const isLandscapeOn = ref(false)  // 지도 경로를 구간 성격 색으로

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
let introTimers = []
let photoMarkers = []             // 사진 동그라미 마커
let lastPhotoMinutes = null       // 재생 중 사진을 마지막으로 확인한 여행 시각 (이 뒤에 찍은 사진만 띄움)
let floatTimer = null
let isMapReady = false            // 지도 스타일을 다 읽었는지 (하늘·빛은 그 뒤에만 바꿀 수 있음)
let lastSkyKey = ''               // 마지막으로 칠한 하늘 (같으면 다시 안 칠함)
let forestData = null             // 숲 구역 자료 (forest-seoul.json)
let forestReady = null            // 숲 자료를 다 읽으면 끝나는 약속(Promise)
let streetPitch = STREET_PITCH_FLAT // 1인칭 카메라의 지금 기울기 (경사를 부드럽게 따라감)
let roadLabelIds = null           // 길 이름 글자 층 (1인칭에서 숨김)
let navDrag = null                // 내비 카드를 끄는 중 { startX, startY, x, y }

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
  stopSplitDrag()
  stopNavDrag()
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

// 사진 카드가 올리기·꾸미기 뒤에 다시 읽어 달라고 하면: 읽고 나서 새 목록을 알려 줌
async function reloadPhotosFor(resolve) {
  await loadPhotos()
  resolve(photos.value)
}

// 사진 크게 보기 [꾸미기] → 보기를 닫고 사진 카드의 편집기를 엶
function editFromViewer(photoId) {
  viewer.value = null
  if (photoCard.value) photoCard.value.openEditorById(photoId)
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

// 고른 날의 사진 + 지도 자리. 편집본이 있는 원본은 빼고 편집본만 (메모·사진 화면에서 편집한 것)
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

// ============================== 정산 (11/11: 앱 회고와 같은 API로 여기서도 고쳐요)
// 계산: 직접 정한 사람(shareAmount)은 그 금액, 나머지는 (합계 − 정한 금액들)을 똑같이 나눔 (10원 단위, 남는 돈은 자동 중 첫 사람)
// 예: 3명, 합계 90,000, 민지 20,000으로 정함 → 나·지수가 70,000을 35,000씩
const settleDay = ref(0) // 정산 카드에서 볼 날 (0 = 전체). 날짜별은 그날 결제만 똑같이 나눔 (직접 정한 금액은 전체에서만)

// 정산에 넣을 결제: 전체면 모두, 날짜를 고르면 그날 것만
const settlePayments = computed(() => {
  if (settleDay.value === 0) return payments.value
  return payments.value.filter((payment) => payment.day === settleDay.value)
})

// 직접 정한 낼 돈은 여행 전체 기준이라 날짜별로 볼 때는 안 씀 (null = 자동)
function shareOf(companion) {
  return settleDay.value === 0 ? companion.shareAmount : null
}

const settlement = computed(() => {
  const list = companions.value
  if (list.length < 2 || settlePayments.value.length === 0) return null
  let total = 0
  const paid = {}
  for (const companion of list) paid[companion.name] = 0
  for (const payment of settlePayments.value) {
    total += payment.amount
    if (paid[payment.payer] !== undefined) paid[payment.payer] += payment.amount
  }
  let fixedTotal = 0
  const autoNames = []
  for (const companion of list) {
    if (shareOf(companion) !== null) fixedTotal += shareOf(companion)
    else autoNames.push(companion.name)
  }
  const rest = total - fixedTotal
  let autoShare = 0
  if (autoNames.length > 0) autoShare = Math.floor(rest / autoNames.length / 10) * 10
  const people = []
  for (const companion of list) {
    let should = shareOf(companion)
    if (should === null) should = companion.name === autoNames[0] ? rest - autoShare * (autoNames.length - 1) : autoShare
    people.push({ id: companion.id, name: companion.name, isMe: companion.sortNo === 0, isFixed: shareOf(companion) !== null,
      paid: paid[companion.name], should: should, balance: paid[companion.name] - should })
  }
  // 안 맞는 돈: 모두 직접 정했는데 합이 다르거나, 정한 금액이 합계보다 많으면
  let gap = 0
  if (autoNames.length === 0) gap = total - fixedTotal
  else if (rest < 0) gap = rest
  return { total: total, people: people, transfers: toTransfers(people), gap: gap }
})

const settleMessage = ref('')      // 정산 칸 아래 한 줄 알림 (웹 화면에는 앱의 토스트가 없어서)
const personEdit = ref(null)       // 이름 칸 { id, name }. id가 null이면 새 사람
const shareEdit = ref(null)        // 낼 돈 칸 { id, amount(글자) }
const askingRemoveId = ref(null)   // "뺄까요?"를 띄운 사람
const askingDeleteId = ref(null)   // "지울까요?"를 띄운 결제
const isSettleBusy = ref(false)

// 서버에 한 번 고치는 공통 순서: 바쁨 표시 → 실행 → 실패하면 알림
async function runSettle(work) {
  isSettleBusy.value = true
  settleMessage.value = ''
  try {
    await work()
  } catch (e) {
    settleMessage.value = e.message
  }
  isSettleBusy.value = false
}

async function reloadPayments() {
  payments.value = await get('/trips/' + route.query.id + '/payments')
}

function startAddPerson() {
  personEdit.value = { id: null, name: '' }
}

// 사람 이름 누르기 → 이름 칸 열기 ('나'는 못 바꿈)
function startRenamePerson(person) {
  if (person.isMe) return
  personEdit.value = { id: person.id, name: person.name }
}

// 사람 추가(POST)·이름 바꾸기(PUT). 이름이 바뀌면 서버가 결제의 낸 사람 이름도 바꿔서 결제를 다시 읽음
function savePerson() {
  const edit = personEdit.value
  const name = edit.name.trim()
  if (name === '') return
  runSettle(async () => {
    const base = '/trips/' + route.query.id + '/companions'
    if (edit.id === null) {
      companions.value = await post(base, { name: name })
    } else {
      companions.value = await put(base + '/' + edit.id, { name: name })
      await reloadPayments()
    }
    personEdit.value = null
  })
}

// 사람 빼기. 그 사람이 낸 결제는 서버가 '나'로 바꿈
function removePerson(person) {
  runSettle(async () => {
    companions.value = await del('/trips/' + route.query.id + '/companions/' + person.id)
    await reloadPayments()
    askingRemoveId.value = null
    settleMessage.value = person.name + '을(를) 정산에서 뺐어요'
  })
}

function startShareEdit(person) {
  shareEdit.value = { id: person.id, amount: String(person.should) }
}

// 낼 돈 저장. amount가 null이면 자동(똑같이 나누기)으로 되돌림
function saveShare(personId, amount) {
  const value = amount === null ? null : Number(String(amount).replace(/,/g, ''))
  if (value !== null && !(value >= 0)) {
    settleMessage.value = '0원 이상 숫자로 넣어 주세요'
    return
  }
  runSettle(async () => {
    companions.value = await put('/trips/' + route.query.id + '/companions/' + personId + '/share', { amount: value })
    shareEdit.value = null
  })
}

// 결제의 낸 사람을 다음 사람으로 (나 → 민지 → 나 …)
function cyclePayer(payment) {
  const names = []
  for (const companion of companions.value) names.push(companion.name)
  if (names.length < 2) return
  const next = names[(names.indexOf(payment.payer) + 1) % names.length] // %: 끝을 넘으면 처음으로
  runSettle(async () => {
    const changed = await put('/trips/' + route.query.id + '/payments/' + payment.id + '/payer', { payer: next })
    payment.payer = changed.payer
  })
}

function deletePayment(payment) {
  runSettle(async () => {
    await del('/trips/' + route.query.id + '/payments/' + payment.id)
    await reloadPayments()
    askingDeleteId.value = null
    settleMessage.value = payment.storeName + ' 결제를 지웠어요'
  })
}

// 결제 목록: 날짜 → 시각 순 (paidAt은 "14:20" 글자라 같은 날이면 글자 비교로 순서가 맞음)
const sortedPayments = computed(() => {
  const list = payments.value.slice()
  list.sort((a, b) => (a.day !== b.day ? a.day - b.day : (a.paidAt < b.paidAt ? -1 : 1)))
  return list
})

// ---------- 장소별 결제 (11/11): 장소마다 결제를 묶어 보여 주고, 그 장소에 넣기·고치기·지우기
// 서버는 결제 시각으로 장소를 정해요(실제 도착 ≤ 결제 < 떠남). 그래서 새 결제는 그 장소에 머문 시간 가운데 시각으로 보냄
const MIDNIGHT_MINUTES = 24 * 60 - 1
const paymentEdit = ref(null) // 고치는 칸 { id(null이면 새로), groupKey, day, time, storeName, amount(글자), payer, old }

// 묶음: 일정 순서대로 [다녀온 장소 또는 결제가 있는 장소] + 마지막에 '이동 중·계획 밖'
const paymentGroups = computed(() => {
  if (!retro.value) return []
  const groups = []
  const countOfDay = {}
  for (const stop of retro.value.stops) {
    countOfDay[stop.day] = (countOfDay[stop.day] || 0) + 1
    if (settleDay.value !== 0 && stop.day !== settleDay.value) continue
    const list = paymentsOfStop(stop.id)
    const canAdd = stop.actualArrive !== null && stop.actualStay !== null
    if (!canAdd && list.length === 0) continue
    groups.push({ key: 'stop-' + stop.id, stop: stop, title: stop.day + '일차 · ' + countOfDay[stop.day] + '. ' + stop.name,
      sub: canAdd ? stop.actualArrive + ' ~ ' + toTime((toMinutes(stop.actualArrive) + stop.actualStay) % (24 * 60)) : '안 감',
      canAdd: canAdd, payments: list, total: sumAmount(list) })
  }
  const moving = paymentsOfStop(null)
  if (moving.length > 0) groups.push({ key: 'moving', stop: null, title: '이동 중·계획 밖', sub: '', canAdd: false, payments: moving, total: sumAmount(moving) })
  return groups
})

// 이 장소의 결제 (stopId가 null이면 이동 중·계획 밖 — 날짜를 골랐으면 그날 것만)
function paymentsOfStop(stopId) {
  return sortedPayments.value.filter((payment) => payment.stopId === stopId && (stopId !== null || settleDay.value === 0 || payment.day === settleDay.value))
}

function sumAmount(list) {
  let total = 0
  for (const payment of list) total += payment.amount
  return total
}

// [+ 결제]: 그 장소에 머문 시간 가운데 시각으로 새 칸 열기
function startAddPayment(group) {
  const arrive = toMinutes(group.stop.actualArrive)
  const time = toTime(Math.min(arrive + Math.floor(group.stop.actualStay / 2), MIDNIGHT_MINUTES))
  paymentEdit.value = { id: null, groupKey: group.key, day: group.stop.day, time: time, storeName: group.stop.name, amount: '', payer: '나', old: null }
}

// [직접 입력]: 장소와 상관없이 결제 하나를 넣는 칸 (날짜·시각도 고름)
function startManualPayment() {
  const day = settleDay.value === 0 ? 1 : settleDay.value
  paymentEdit.value = { id: null, groupKey: 'top', day: day, time: '12:00', storeName: '', amount: '', payer: '나', old: null, source: 'manual' }
}

// [영수증 가져오기]: 사진을 고르면 서버(Gemini OCR, ③ 주형)가 가게·금액·시각을 읽어 칸을 채움. 틀리면 고친 뒤 넣기
const receiptInput = ref(null)
function openReceipt() {
  receiptInput.value.click()
}
async function readReceipt(event) {
  const file = event.target.files[0]
  event.target.value = '' // 같은 사진을 다시 골라도 동작하게
  if (!file) return
  settleMessage.value = '영수증을 읽는 중…'
  try {
    const form = new FormData()
    form.append('image', file)
    const result = await upload('/plans/capture/receipt', form)
    const paidAt = result.paidAt ? result.paidAt.replace('T', ' ') : '' // 예: "2026-11-07 13:20"
    paymentEdit.value = { id: null, groupKey: 'top', day: dayOfDate(paidAt.slice(0, 10)), time: paidAt.slice(11, 16) || '12:00',
      storeName: result.storeName || '', amount: result.totalAmount ? String(result.totalAmount) : '', payer: '나', old: null, source: 'capture' }
    settleMessage.value = '영수증에서 읽었어요. 오류 시 직접 수정해 주세요.'
  } catch (e) {
    settleMessage.value = e.message
  }
}

// 날짜("2026-11-07") → 여행 몇 일차 (여행 날짜 밖이거나 없으면 1일차)
function dayOfDate(dateText) {
  for (let day = 1; day <= dayCount.value; day++) {
    if (dateOfDay(day) === dateText) return day
  }
  return 1
}

// ✎: 있던 결제를 고치는 칸 열기
function startEditPayment(group, payment) {
  paymentEdit.value = { id: payment.id, groupKey: group.key, day: payment.day, time: payment.paidAt, storeName: payment.storeName,
    amount: String(payment.amount), payer: payment.payer, old: payment }
}

// 저장: 새로면 POST 한 번. 고치기는 결제 고치기 API가 없어서 새로 넣고(POST) 옛것을 지움(DELETE)
// 낸 사람만 바뀌었으면 낸 사람 API(PUT) 하나로
function savePayment() {
  const edit = paymentEdit.value
  const amount = Number(String(edit.amount).replace(/,/g, ''))
  if (edit.storeName.trim() === '' || !(amount >= 1) || !Number.isInteger(amount)) {
    settleMessage.value = '가게 이름과 1원 이상 금액을 넣어 주세요'
    return
  }
  const body = { paidAt: dateOfDay(edit.day) + 'T' + edit.time, storeName: edit.storeName.trim(), amount: amount, payer: edit.payer }
  runSettle(async () => {
    const base = '/trips/' + route.query.id + '/payments'
    if (edit.old && isOnlyPayerChanged(edit.old, body)) {
      await put(base + '/' + edit.id + '/payer', { payer: body.payer })
    } else {
      if (edit.old) Object.assign(body, { method: edit.old.method, source: edit.old.source, memo: edit.old.memo })
      else if (edit.source) body.source = edit.source // 'manual' 직접 입력 · 'capture' 영수증
      await post(base, body)
      if (edit.old) await del(base + '/' + edit.id) // 새로 넣은 뒤에 지움 (중간에 실패해도 결제가 사라지지는 않게)
    }
    await reloadPayments()
    paymentEdit.value = null
  })
}

function isOnlyPayerChanged(old, body) {
  return old.storeName === body.storeName && old.amount === body.amount
}

function companionById(id) {
  for (const companion of companions.value) {
    if (companion.id === id) return companion
  }
  return null
}

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

// ============================== 메모 (11/11): 여행 메모 + 고른 장소의 메모·별점. 쓰고 잠깐 멈추면 이 브라우저에 저장
const saveJournalLater = makeDelayedSave(route.query.id, (isSaved) => {
  memoSavedText.value = isSaved ? '저장됨 · ' + new Date().toTimeString().slice(0, 5) : '이 브라우저에서는 저장할 수 없어요'
})
watch(journal, () => {
  memoSavedText.value = '저장 중…'
  saveJournalLater(journal)
}, { deep: true })

// 메모 카드 장소 고르기 목록: 날짜 상관없이 모든 일정 칸 (번호는 그날 기준)
const allStopRows = computed(() => {
  if (!retro.value) return []
  const list = []
  const countOfDay = {}
  for (const stop of retro.value.stops) {
    countOfDay[stop.day] = (countOfDay[stop.day] || 0) + 1
    list.push({ id: stop.id, day: stop.day, no: countOfDay[stop.day], name: stop.name })
  }
  return list
})

// 메모 카드에서 고른 장소
const memoStop = computed(() => {
  for (const row of allStopRows.value) {
    if (row.id === memoStopId.value) return row
  }
  return null
})

// 별 누르기 (같은 별을 다시 누르면 지움)
function setStopRating(value) {
  toggleStopRating(journal, memoStopId.value, value)
}

// 별 글자: 3점 → "★★★☆☆"
function starText(rating) {
  return '★'.repeat(rating) + '☆'.repeat(5 - rating)
}

// ============================== 지도 ↔ 오른쪽 칸 너비 (11/11)
function readSideWidth() {
  try {
    const saved = Number(localStorage.getItem(SIDE_KEY))
    if (saved >= SIDE_MIN) return saved
  } catch (e) {
    // 저장소를 못 쓰면 기본 너비
  }
  return SIDE_DEFAULT
}

// 너비를 최소~최대 안으로 맞추고, 지도 크기도 다시 계산
function setSideWidth(width) {
  sideWidth.value = Math.round(Math.max(SIDE_MIN, Math.min(window.innerWidth * SIDE_MAX_RATIO, width)))
  if (map) requestAnimationFrame(() => map.resize())
}

function startSplitDrag(event) {
  event.preventDefault()
  window.addEventListener('pointermove', onSplitMove)
  window.addEventListener('pointerup', stopSplitDrag)
}

// 막대를 끈 자리부터 화면 오른쪽 끝까지가 오른쪽 칸 너비
function onSplitMove(event) {
  setSideWidth(window.innerWidth - event.clientX)
}

function stopSplitDrag() {
  window.removeEventListener('pointermove', onSplitMove)
  window.removeEventListener('pointerup', stopSplitDrag)
  try {
    localStorage.setItem(SIDE_KEY, String(sideWidth.value))
  } catch (e) {
    // 기억 못 해도 지금 화면에서는 바뀜
  }
}

// 키보드: ←는 오른쪽 칸을 넓게, →는 좁게 (40px씩)
function onSplitKey(event) {
  if (event.key === 'ArrowLeft') setSideWidth(sideWidth.value + 40)
  else if (event.key === 'ArrowRight') setSideWidth(sideWidth.value - 40)
  else return
  event.preventDefault()
  stopSplitDrag()
}

// ============================== 지도 만들기
function createMap() {
  maplibregl.setWorkerUrl(workerUrl)
  map = new maplibregl.Map({
    container: mapBox.value,
    style: STYLE_URL,
    center: SEOUL_CENTER,
    zoom: 10.4,
    pitch: 0,
    bearing: 0,
    maxPitch: 85, // 거리 시점은 거의 수평으로 봄
    attributionControl: { compact: true },
  })
  map.addControl(new maplibregl.NavigationControl({ visualizePitch: true }), 'bottom-right')
  map.on('load', () => {
    useKoreanLabels()
    addHillshade()
    addSatellite()
    addBoundaryLayers()
    addRouteLayers()
    addLandscapeLayers()
    forestReady = loadForest()
    drawDay()
    runIntro()
    startLandscape()
    isMapReady = true
    applySun()
    hideFlatMapExtras()
  })
}

// 평면 지도에서는 건물 기둥(3D 건물)을 숨김 (3D는 유니티 "3D 지도"가 맡음)
function hideFlatMapExtras() {
  for (const layerId of ['building-3d']) {
    if (map.getLayer(layerId)) map.setLayoutProperty(layerId, 'visibility', 'none')
  }
}

// 지명은 한국어 이름 먼저 (없으면 기본 이름)
function useKoreanLabels() {
  for (const layer of map.getStyle().layers) {
    if (layer.type === 'symbol' && layer.layout && layer.layout['text-field']) {
      map.setLayoutProperty(layer.id, 'text-field', ['coalesce', ['get', 'name:ko'], ['get', 'name']])
    }
  }
}

// 산 그림자(hillshade) + 3D 지형용 높이 자료. 그림자와 지형은 자료를 따로 둬야 MapLibre가 경고 없이 그림
function addHillshade() {
  map.addSource('dem-shade', { type: 'raster-dem', tiles: [DEM_TILES], encoding: 'terrarium', tileSize: 256, maxzoom: 14 })
  map.addLayer({ id: 'hillshade', type: 'hillshade', source: 'dem-shade', paint: { 'hillshade-exaggeration': 0.45, 'hillshade-shadow-color': '#5a6b86', 'hillshade-illumination-anchor': 'map' } }, 'building')
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

// 산 그림자(음영) 방향을 해 방향으로
function applySunLook() {
  if (!map || !isMapReady) return
  const sun = currentSun.value
  map.setPaintProperty('hillshade', 'hillshade-illumination-direction', sun && sun.altitude > 0 ? Math.round(sun.azimuth) : 335)
}

// 화면 전체에 덮는 밤·노을 색 (지도 위, 카드 아래)
const skyShade = computed(() => (currentSun.value && isDayNight.value ? currentSun.value.sky.shade : 'rgba(0, 0, 0, 0)'))

function addSatellite() {
  map.addSource('satellite', { type: 'raster', tiles: [SATELLITE_TILES], tileSize: 256, maxzoom: 18, attribution: 'Esri, Maxar, Earthstar Geographics' })
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

// 실제 경로(파랑), 재생으로 지나온 부분(진한 파랑). 계획 경로 선은 그리지 않음 (팀에서 빼기로 함)
function addRouteLayers() {
  const emptyLine = { type: 'Feature', geometry: { type: 'LineString', coordinates: [] } }
  map.addSource('track', { type: 'geojson', data: emptyLine })
  map.addSource('track-done', { type: 'geojson', data: emptyLine })
  map.addLayer({ id: 'track-casing', type: 'line', source: 'track', layout: { 'line-join': 'round', 'line-cap': 'round' },
    paint: { 'line-color': '#FFFFFF', 'line-width': 8, 'line-opacity': 0.9 } })
  map.addLayer({ id: 'track-line', type: 'line', source: 'track', layout: { 'line-join': 'round', 'line-cap': 'round' },
    paint: { 'line-color': '#2F7BFF', 'line-width': 5 } })
  map.addLayer({ id: 'track-done', type: 'line', source: 'track-done', layout: { 'line-join': 'round', 'line-cap': 'round' },
    paint: { 'line-color': '#0B4FC4', 'line-width': 7 } })
}

// ============================== 풍경 분석 (11/11): 이동 경로 객체 분석
// 여행 계절 (첫날 기준)

// 구간 선(걸은 구간)과 점(머문 곳)을 성격 색으로 그릴 층. 처음엔 숨김
function addLandscapeLayers() {
  map.addSource('landscape', { type: 'geojson', data: { type: 'FeatureCollection', features: [] } })
  map.addLayer({ id: 'landscape-line', type: 'line', source: 'landscape', filter: ['==', ['geometry-type'], 'LineString'],
    layout: { 'line-join': 'round', 'line-cap': 'round', visibility: 'none' }, paint: { 'line-color': ['get', 'color'], 'line-width': 7 } }, 'track-done')
  map.addLayer({ id: 'landscape-stay', type: 'circle', source: 'landscape', filter: ['==', ['geometry-type'], 'Point'],
    layout: { visibility: 'none' }, paint: { 'circle-color': ['get', 'color'], 'circle-radius': 9, 'circle-stroke-color': '#FFFFFF', 'circle-stroke-width': 2 } }, 'track-done')
  // 가시권: 보이는 칸을 옅은 노랑으로 (건물 아래에 깔아서 건물·나무는 그대로 보임)
  // 살펴본 범위(반경 6km 동그라미)와 관찰 지점. 회색 위에 노랑을 덮어서, 회색이 남은 곳 = 가려진 곳
  // DBSCAN 군집: 머문 곳 동그라미 (계획한 곳 남색, 계획 밖 주황)
  map.addSource('stay-cluster', { type: 'geojson', data: { type: 'FeatureCollection', features: [] } })
  const clusterColor = ['case', ['get', 'planned'], '#1F3A68', '#E8603F']
  map.addLayer({ id: 'stay-cluster-fill', type: 'fill', source: 'stay-cluster', layout: { visibility: 'none' }, paint: { 'fill-color': clusterColor, 'fill-opacity': 0.25 } })
  map.addLayer({ id: 'stay-cluster-line', type: 'line', source: 'stay-cluster', layout: { visibility: 'none' }, paint: { 'line-color': clusterColor, 'line-width': 2.5, 'line-dasharray': [2, 1.5] } })
  // 넓게 보면 동그라미(반경 25m쯤)가 너무 작아서, 화면 크기가 변하지 않는 가운데 점을 같이 그림
  map.addLayer({ id: 'stay-cluster-dot', type: 'circle', source: 'stay-cluster', filter: ['==', ['geometry-type'], 'Point'], layout: { visibility: 'none' },
    paint: { 'circle-color': clusterColor, 'circle-radius': 9, 'circle-stroke-color': '#FFFFFF', 'circle-stroke-width': 3 } })
  for (const layer of ['landscape-line', 'landscape-stay']) {
    map.on('click', layer, (event) => openLandscapePopup(event.features[0].properties.id, event.lngLat))
    map.on('mouseenter', layer, () => { map.getCanvas().style.cursor = 'pointer' })
    map.on('mouseleave', layer, () => { map.getCanvas().style.cursor = '' })
  }
}

// ---------- 머문 곳 찾기: DBSCAN 군집 (landscape/dbscan.js)
// GPS 점을 군집으로 묶어 머문 곳을 찾고, 분석용 지점 목록(머문 곳은 한 지점, 나머지는 이동 점)으로 바꿈
function toAnalysisNodes(track) {
  const points = track.map((point) => ({ lng: point.lng, lat: point.lat, minute: tripMinutes(point.day, point.time) }))
  const found = findStays(points)
  const partsOf = {} // 군집 번호 → 머문 곳 조각들 (보통 하나, 붙어 있는 두 가게면 둘)
  const allParts = []
  for (const stay of found.stays) {
    partsOf[stay.id] = splitByPlannedStops(stay)
    allParts.push(...partsOf[stay.id]) // ...: 목록을 풀어서 하나씩 넣음
  }
  clusters.value = { isOn: clusters.value.isOn, stays: allParts }
  if (map && map.getSource('stay-cluster')) map.getSource('stay-cluster').setData(toClusterCollection(allParts))
  const nodes = []
  const isAdded = {}
  for (let i = 0; i < points.length; i++) {
    const parts = partsOf[found.labels[i]]
    if (!parts) {
      nodes.push({ lng: points[i].lng, lat: points[i].lat, from: points[i].minute, to: points[i].minute, isStay: false })
    } else if (!isAdded[found.labels[i]]) {
      isAdded[found.labels[i]] = true
      for (const part of parts) nodes.push({ lng: part.lng, lat: part.lat, from: part.from, to: part.to, isStay: true, name: part.name, category: part.category })
    }
  }
  return nodes
}

// 한 군집 안에 계획 장소 두 곳 이상의 도착이 있으면 도착 시각마다 나눔
//   예) 50m 떨어진 분식집(11:39 도착) → 디저트 가게(13:22 도착): GPS로는 한 덩어리지만 두 곳에 머문 것
function splitByPlannedStops(stay) {
  const arrivals = []
  for (const stop of retro.value.stops) {
    if (stop.actualArrive === null || stop.lat === null) continue
    const arrive = tripMinutes(stop.day, stop.actualArrive)
    const isNear = distanceMeters(stay.lat, stay.lng, stop.lat, stop.lng) <= 120
    if (isNear && arrive >= stay.from - 30 && arrive <= stay.to) arrivals.push({ stop: stop, arrive: arrive })
  }
  if (arrivals.length < 2) return [Object.assign(stay, nameOfStay(stay))]
  arrivals.sort((a, b) => a.arrive - b.arrive)
  const parts = []
  for (let k = 0; k < arrivals.length; k++) {
    const from = k === 0 ? stay.from : arrivals[k].arrive
    const to = k + 1 < arrivals.length ? arrivals[k + 1].arrive : stay.to
    const stop = arrivals[k].stop
    parts.push({ id: stay.id + '-' + k, lng: stop.lng, lat: stop.lat, from: from, to: to, minutes: to - from, count: 0, radiusM: SPLIT_STAY_RADIUS_M, name: stop.name, category: stop.category, isPlanned: true })
  }
  return parts
}

// 군집에 이름 붙이기: 가까이(120m) 있고 그 시간대에 도착한 일정 칸 → 계획한 곳. 없으면 계획 밖
function nameOfStay(stay) {
  for (const stop of retro.value.stops) {
    if (stop.actualArrive === null || stop.lat === null) continue
    const arrive = tripMinutes(stop.day, stop.actualArrive)
    const isNear = distanceMeters(stay.lat, stay.lng, stop.lat, stop.lng) <= 120
    if (isNear && arrive >= stay.from - 30 && arrive <= stay.to + 30) return { name: stop.name, category: stop.category, isPlanned: true }
  }
  return { name: null, category: '', isPlanned: false }
}

// 군집마다 동그라미(가운데에서 가장 먼 점까지, 최소 25m) → 지도에 그릴 도형
function toClusterCollection(stays) {
  const features = []
  for (const stay of stays) {
    const ring = []
    const radius = Math.max(25, stay.radiusM)
    for (let i = 0; i <= 32; i++) ring.push(movePoint([stay.lng, stay.lat], (i / 32) * 360, radius))
    features.push({ type: 'Feature', geometry: { type: 'Polygon', coordinates: [ring] }, properties: { planned: stay.isPlanned } })
    features.push({ type: 'Feature', geometry: { type: 'Point', coordinates: [stay.lng, stay.lat] }, properties: { planned: stay.isPlanned } })
  }
  return { type: 'FeatureCollection', features: features }
}

const CLUSTER_LAYERS = ['stay-cluster-fill', 'stay-cluster-line', 'stay-cluster-dot']

function toggleClusters() {
  clusters.value = { isOn: !clusters.value.isOn, stays: clusters.value.stays }
  const visibility = clusters.value.isOn ? 'visible' : 'none'
  for (const layer of CLUSTER_LAYERS) map.setLayoutProperty(layer, 'visibility', visibility)
}

// 군집 줄을 누르면 지도에서 그 자리로
function focusCluster(stay) {
  if (!clusters.value.isOn) toggleClusters()
  stopPlay()
  setStreetLook(false)
  map.flyTo({ center: [stay.lng, stay.lat], zoom: 17, pitch: 0, duration: 1400, essential: true })
}

// 여행 전체(모든 날)를 한 번 분석. 지도는 날짜를 고르면 그날 것만 보여 줌 (filterLandscapeDay)
async function startLandscape() {
  const allTrack = retro.value.track
  if (allTrack.length < 2) {
    landscape.value = { status: 'done', done: 0, total: 0, items: [], message: '' }
    return
  }
  landscape.value = { status: 'running', done: 0, total: 0, items: [], message: '' }
  try {
    await forestReady // 숲 자료가 있어야 숲 비율·숲 그늘을 볼 수 있음
    const layers = { sun: (segment, features) => sunStateOf(middleOf(segment), sunAt((segment.from + segment.to) / 2), features.green) }
    if (forestData) layers.green = makeGreenReader(forestData)
    const result = await analyzeLandscape(toAnalysisNodes(allTrack), {
      layers: layers,
      onProgress: (done, total) => { landscape.value = Object.assign({}, landscape.value, { done: done, total: total }) },
    })
    landscape.value = { status: 'done', done: result.items.length, total: result.items.length, items: result.items, message: '' }
    map.getSource('landscape').setData(toLandscapeCollection(result.items))
    filterLandscapeDay()
  } catch (e) {
    landscape.value = { status: 'error', done: 0, total: 0, items: [], message: e.message }
  }
}

function toLandscapeCollection(items) {
  const features = []
  for (const item of items) {
    const segment = item.segment
    const geometry = segment.kind === 'stay' ? { type: 'Point', coordinates: segment.points[0] } : { type: 'LineString', coordinates: segment.points }
    features.push({ type: 'Feature', geometry: geometry, properties: { id: segment.id, day: segment.day, color: item.type.color } })
  }
  return { type: 'FeatureCollection', features: features }
}

function filterLandscapeDay() {
  if (!map || !map.getLayer('landscape-line')) return
  const dayFilter = selectedDay.value === 0 ? true : ['==', ['get', 'day'], selectedDay.value]
  map.setFilter('landscape-line', ['all', ['==', ['geometry-type'], 'LineString'], dayFilter])
  map.setFilter('landscape-stay', ['all', ['==', ['geometry-type'], 'Point'], dayFilter])
}

// 켜면 구간 색 선이 보이고 원래 파란 GPS 선은 흐리게
function toggleLandscape() {
  isLandscapeOn.value = !isLandscapeOn.value
  const visibility = isLandscapeOn.value ? 'visible' : 'none'
  map.setLayoutProperty('landscape-line', 'visibility', visibility)
  map.setLayoutProperty('landscape-stay', 'visibility', visibility)
  map.setPaintProperty('track-line', 'line-opacity', isLandscapeOn.value ? 0.25 : 1)
}

function findLandscapeItem(id) {
  for (const item of landscape.value.items) {
    if (item.segment.id === id) return item
  }
  return null
}

// 구간 누르기 → 성격·근거·특징값 창 (글자는 textContent로만 넣음)
function openLandscapePopup(id, lngLat) {
  const item = findLandscapeItem(id)
  if (!item) return
  const box = document.createElement('div')
  box.className = 'wr-pop'
  const chip = makeTag('wr-pop__type', item.type.name + ' · 확신도 ' + Math.round(item.confidence * 100) + '%')
  chip.style.background = item.type.color
  box.appendChild(chip)
  box.appendChild(makeTag('wr-pop__title', item.segment.kind === 'stay' ? (item.segment.name || '머문 곳') : '이동 구간'))
  box.appendChild(makeTag('wr-pop__line', toDayTime(item.segment.from) + ' · ' + toDuration(Math.max(1, item.segment.minutes))))
  box.appendChild(makeTag('wr-pop__line wr-pop__line--real', '근거: ' + item.reasons.join(' · ')))
  for (const line of featureLines(item.features)) box.appendChild(makeTag('wr-pop__feature', line))
  new maplibregl.Popup({ offset: 12, maxWidth: '280px' }).setLngLat(lngLat).setDOMContent(box).addTo(map)
}

// 특징값을 사람이 읽을 줄로
function featureLines(features) {
  const lines = []
  if (features.lengthM > 0) lines.push('길이 ' + features.lengthM + 'm · 평균 ' + features.speedKmh.toFixed(1) + 'km/h')
  if (features.gainM !== null) lines.push('오른 높이 ' + features.gainM + 'm · 내린 높이 ' + features.lossM + 'm · 평균 경사 ' + Math.round(features.avgSlope * 100) + '%')
  if (features.places.length > 0) lines.push('주변: ' + features.places.slice(0, 3).map((place) => place.name).join(', '))
  if (features.green !== null) lines.push('숲 구역 ' + Math.round(features.green * 100) + '%')
  if (features.sun) lines.push('일조: ' + features.sun.state.name + (features.sun.altitude > 0 ? ' (해 높이 ' + Math.round(features.sun.altitude) + '°, 방향 ' + Math.round(features.sun.azimuth) + '°)' : ''))
  return lines
}

// 오른쪽 목록에서 구간 누르기 → 지도에서 그 구간으로 + 창
function focusLandscape(item) {
  if (!isLandscapeOn.value) toggleLandscape()
  stopPlay()
  setStreetLook(false)
  const points = item.segment.points
  if (points.length === 1) {
    map.flyTo({ center: points[0], zoom: 16.5, pitch: 0, duration: 1400, essential: true })
  } else {
    const bounds = new maplibregl.LngLatBounds()
    for (const point of points) bounds.extend(point)
    map.fitBounds(bounds, { padding: 120, maxZoom: 17, duration: 1400 })
  }
  openLandscapePopup(item.segment.id, points[Math.floor((points.length - 1) / 2)])
}

// ============================== 숲 구역 (임상도): 여행 분석에서 "숲길"·숲 그늘을 볼 때 씀 (지도에 칠하지는 않음, 계절 나무는 3D 지도에서)
async function loadForest() {
  let data = null
  try {
    const response = await fetch(FOREST_URL)
    if (response.ok && (response.headers.get('content-type') || '').includes('json')) data = await response.json()
  } catch (e) {
    data = null // 파일이 없으면 숲 층 없이
  }
  if (!map) return
  forestData = data
}

// ============================== 고른 날 다시 그리기
function drawDay() {
  if (!map) return
  map.getSource('dong-rise').setData(toDongCollection())
  map.getSource('track').setData(toLine(trackCoordinates()))
  map.getSource('track-done').setData(toLine([]))
  drawMarkers()
  setRise(riseFactor)
}

function toLine(coordinates) {
  return { type: 'Feature', geometry: { type: 'LineString', coordinates: coordinates } }
}

// 좌표 있는 일정 칸 자리를 계획 순서대로 (건너뛴 곳 포함). 지도를 일정 전체가 보이게 맞출 때 씀
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
  if (map && map.getLayer('dong-rise')) map.setLayoutProperty('dong-rise', 'visibility', 'none')
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
    const popup = new maplibregl.Popup({ offset: 30, maxWidth: '280px' }).setDOMContent(makePopup(row))
    popup.on('open', () => popup.setDOMContent(makePopup(row))) // 열 때마다 새로 (방금 쓴 메모·별점이 보이게)
    const marker = new maplibregl.Marker({ element: makePin(row), anchor: 'bottom' })
      .setLngLat([row.lng, row.lat])
      .setPopup(popup)
      .addTo(map)
    marker.getElement().addEventListener('click', () => { memoStopId.value = row.id }) // 메모 카드도 이 장소로
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
  const rating = journal.stopRatings[row.id]
  if (rating) box.appendChild(makeTag('wr-pop__stars', starText(rating)))
  const note = journal.stopNotes[row.id]
  if (note) box.appendChild(makeTag('wr-pop__note', '📝 ' + note))
  else box.appendChild(makeTag('wr-pop__hint', '오른쪽 메모 칸에 이 장소 메모를 쓸 수 있어요'))
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
  map.jumpTo({ center: SEOUL_CENTER, zoom: 10.4, pitch: 0, bearing: 0 })
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
  map.flyTo({ center: camera.center, zoom: Math.min(camera.zoom, 15.5), pitch: 0, bearing: 0, duration: duration, essential: true })
}

function focusStop(row) {
  memoStopId.value = row.id // 메모 카드도 이 장소로
  if (row.lat === null || !map) return
  stopPlay()
  setStreetLook(false) // 내비 시점에서 숨겼던 핀·바닥 표시를 되돌림
  map.flyTo({ center: [row.lng, row.lat], zoom: 16.5, pitch: 0, duration: 1600, essential: true })
  const marker = stopMarkers[row.id]
  if (marker && !marker.getPopup().isOpen()) marker.togglePopup()
}

function focusDong(dong) {
  stopPlay()
  setStreetLook(false) // 내비 시점에서 숨겼던 핀·바닥 표시를 되돌림
  map.flyTo({ center: centerOf(dong.feature), zoom: 14.2, pitch: 0, duration: 1600, essential: true })
}

// ============================== 지도 고르기
function changeDay(day) {
  selectedDay.value = day
}

watch(selectedDay, () => {
  resetPlay()
  drawDay()
  filterLandscapeDay()
  fitTrip(1800)
})

function toggleSatellite() {
  isSatellite.value = !isSatellite.value
  map.setPaintProperty('satellite', 'raster-opacity', 1)
  map.setLayoutProperty('satellite', 'visibility', isSatellite.value ? 'visible' : 'none')
}

// ============================== 내비 재생
// 머문 시간대 목록: 일정 칸의 실제 도착~떠남, 계획 밖에 머문 곳
function stayWindows() {
  const list = []
  for (const row of shownStops.value) {
    if (!row.isVisited || row.lat === null || row.actualStay === null) continue
    const from = tripMinutes(row.day, row.actualArrive)
    list.push({ lng: row.lng, lat: row.lat, from: from, to: from + row.actualStay, name: row.name, category: row.category })
  }
  for (const extra of shownExtras.value) {
    const from = tripMinutes(extra.day, extra.arrive)
    list.push({ lng: extra.lng, lat: extra.lat, from: from, to: from + extra.stay, name: extra.kind === 'lodging' ? '숙소' : extra.name, category: extra.kind === 'lodging' ? '숙소' : '' })
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
      nodes.push({ lng: stay.lng, lat: stay.lat, from: stay.from, to: stay.to, isStay: true, name: stay.name, category: stay.category })
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
  spreadSameMinutes(nodes)
  return nodes
}

// GPS 시각은 분 단위라 같은 분에 찍힌 점이 여럿 있음 (영등포 예시: 645점 중 180점)
// 그대로 두면 3D는 시각으로 자리를 정해서, 그 점들 사이를 0초 만에 건너뜀(순간이동) → 다음 시각까지를 거리 비율로 나눠 줌
// 예) 17:26에 A·B 두 점, 17:29에 C, A→B 100m · B→C 200m → B는 17:27 (3분 × 100/300)
function spreadSameMinutes(nodes) {
  let i = 0
  while (i + 1 < nodes.length) {
    let j = i + 1
    while (j < nodes.length && !nodes[j].isStay && nodes[j].from === nodes[i].to) j++
    // i+1 ~ j-1: i와 같은 분에 찍힌 이동 점들, j: 시각이 다음으로 넘어간 점
    if (j > i + 1 && j < nodes.length) spreadBetween(nodes, i, j)
    i = j
  }
}

// i와 j 사이 점들에 i의 시각 ~ j의 시각을 거리 비율로 나눠 줌
function spreadBetween(nodes, i, j) {
  const steps = [] // i부터 각 점까지 거리(m)
  let walked = 0
  for (let k = i + 1; k <= j; k++) {
    walked += distanceMeters(nodes[k - 1].lat, nodes[k - 1].lng, nodes[k].lat, nodes[k].lng)
    steps.push(walked)
  }
  const start = nodes[i].to
  const span = nodes[j].from - start
  for (let k = i + 1; k < j; k++) {
    const rate = walked > 0 ? steps[k - i - 1] / walked : (k - i) / (j - i)
    nodes[k].from = start + span * rate
    nodes[k].to = nodes[k].from
  }
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
  else if (isUnity3d.value) secondsPerMeter = 1 / UNITY_TOP_SPEED // 3D 위에서 보기도 일정한 속도 (평면보다 천천히)
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

// 실감 3D를 켜면 이 여행의 지도 자료를 만듦 (처음 한 번은 1~3분. 같은 화면에서 다시 켜면 바로)
watch(isUnity3d, async (isOn) => {
  if (!isOn || unityTrip.value || unityStatus.value) return
  try {
    const guNames = visitedGuText.value ? visitedGuText.value.split(' · ') : []
    const nodes = toRouteNodes(retro.value.track) // 머문 곳의 흔들린 GPS는 한 점으로 묶은 것 (3D에서도 머무는 동안 가만히 있게)
    unityTrip.value = await makeTripMap(route.query.id, retro.value, nodes, guNames, (text) => { unityStatus.value = text })
    unityStatus.value = ''
  } catch (e) {
    unityStatus.value = '3D 지도 자료를 못 만들었어요: ' + e.message
  }
})

// 실감 3D(유니티)를 웹 재생에 맞춤: 재생 시각·위에서/내비·따라가기를 그대로 넘김 (속도는 웹의 배속이 정함)
function syncUnity() {
  if (!unityMap.value) return
  unityMap.value.setView(viewMode.value)
  unityMap.value.setFollow(isFollow.value)
  unityMap.value.setDayNight(isDayNight.value)
  if (canPlay.value) unityMap.value.seekClock(nowMinutes.value)
}
watch(isDayNight, (isOn) => {
  if (isUnity3d.value && unityMap.value) unityMap.value.setDayNight(isOn)
})

// [3D 지도] 버튼: 켜고 끄면 재생 속도가 바뀌니(위에서 보기) 지금 간 거리 자리에서 이어지게
function toggleUnity3d() {
  const hasStarted = progress.value > 0
  const meters = routeAt(progress.value).meters
  isUnity3d.value = !isUnity3d.value
  if (hasStarted) progress.value = Math.min(playEnd.value, secondsAtMeters(meters))
}
watch(nowMinutes, (minutes) => {
  if (isUnity3d.value && unityMap.value && canPlay.value) unityMap.value.seekClock(minutes)
})
watch(viewMode, (view) => {
  if (isUnity3d.value && unityMap.value) unityMap.value.setView(view)
})
watch(isFollow, (isOn) => {
  if (isUnity3d.value && unityMap.value) unityMap.value.setFollow(isOn)
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
    return map.calculateCameraOptionsFromCameraLngLatAltRotation(eye, STREET_EYE_M, bearing, streetPitchAt(position, bearing))
  }
  return { center: position, zoom: NAV_ZOOM, pitch: 0, bearing: bearing }
}

// 1인칭 기울기: 지금 자리와 30m 앞의 땅 높이(DEM)로 경사를 재서, 오르막이면 고개를 들고 내리막이면 숙임
// (1인칭에서는 지도 지형을 꺼서 땅은 평평함 — 켜면 카메라가 튀어서. 경사는 고개 각도와 카드 글자로 느끼게)
function streetPitchAt(position, bearing) {
  const here = elevationNow(position[0], position[1])
  const ahead = movePoint(position, bearing, STREET_SLOPE_AHEAD_M)
  const there = elevationNow(ahead[0], ahead[1])
  let slope = 0
  if (here !== null && there !== null) slope = (there - here) / STREET_SLOPE_AHEAD_M
  const target = Math.max(72, Math.min(STREET_PITCH, STREET_PITCH_FLAT + slope * STREET_PITCH_PER_SLOPE))
  streetPitch += (target - streetPitch) * STREET_PITCH_SMOOTH
  showSlope(slope, here)
  return streetPitch
}

// 카드에 보여 줄 경사·해발 (1% 단위로 바뀔 때만 화면을 다시 그림)
function showSlope(slope, altitude) {
  const percent = Math.round(slope * 100)
  const meters = altitude === null ? null : Math.round(altitude)
  if (!navSlope.value || navSlope.value.percent !== percent || navSlope.value.altitude !== meters) navSlope.value = { percent: percent, altitude: meters }
}

// "↗ 오르막 8% · 해발 182m"
function slopeText(info) {
  let text = '평지'
  if (info.percent >= 2) text = '↗ 오르막 ' + info.percent + '%'
  else if (info.percent <= -2) text = '↘ 내리막 ' + -info.percent + '%'
  return info.altitude === null ? text : text + ' · 해발 ' + info.altitude + 'm'
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
  if (!map || !map.getLayer('gu-line')) return
  for (const id of ['dong-fill', 'dong-line', 'gu-line']) map.setLayoutProperty(id, 'visibility', isOn ? 'none' : 'visible')
  for (const id of findRoadLabelIds()) map.setLayoutProperty(id, 'visibility', isOn ? 'none' : 'visible') // 길 이름이 땅에 줄줄이 깔려 보여서
  showStreetGround(isOn)
  if (!isOn) showAllPins()
}

// 1인칭에서는 바닥이 텅 비어 보여서 위성 사진을 옅게 깔아 줌 (아스팔트·보도·잔디 색). [위성]을 켜 둔 상태면 그대로 둠
function showStreetGround(isOn) {
  if (isSatellite.value) return
  map.setPaintProperty('satellite', 'raster-opacity', isOn ? STREET_GROUND_OPACITY : 1)
  map.setLayoutProperty('satellite', 'visibility', isOn ? 'visible' : 'none')
}

// 길을 따라 쓰는 글자 층(길 이름) 찾기: 한 번만 찾아서 기억
function findRoadLabelIds() {
  if (roadLabelIds === null) {
    roadLabelIds = []
    for (const layer of map.getStyle().layers) {
      const placement = layer.layout ? layer.layout['symbol-placement'] : null
      const isRoadName = layer['source-layer'] === 'transportation_name' || placement === 'line' || placement === 'line-center'
      if (layer.type === 'symbol' && isRoadName) roadLabelIds.push(layer.id)
    }
  }
  return roadLabelIds
}

// ---------- 내비 카드 끌어 옮기기 (접기 버튼을 누를 때는 안 옮김)
function startNavDrag(event) {
  if (event.target.closest('button')) return
  navDrag = { startX: event.clientX, startY: event.clientY, x: navOffset.value.x, y: navOffset.value.y }
  window.addEventListener('pointermove', onNavDrag)
  window.addEventListener('pointerup', stopNavDrag)
  event.preventDefault()
}

function onNavDrag(event) {
  navOffset.value = { x: navDrag.x + event.clientX - navDrag.startX, y: navDrag.y + event.clientY - navDrag.startY }
}

function stopNavDrag() {
  window.removeEventListener('pointermove', onNavDrag)
  window.removeEventListener('pointerup', stopNavDrag)
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
  if (isFollow.value && !isUnity3d.value) followCamera(at, frameSeconds) // 3D 지도를 켜면 아래 평면 지도는 카메라를 안 움직임 (가려져 있는데 그리느라 그래픽 카드를 쓰지 않게)
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

// ============================== 해: 재생 막대의 낮·밤 띠
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

// 재생 시각이 바뀌어 해가 움직이면 하늘·빛을 다시 칠함
watch(currentSun, applySun)
watch(currentSun, applySunLook)

// 메모·사진은 고른 날이 바뀌면 다시 그림 (drawDay 안에서 drawMarkers가 사진도 그림)
watch(shownPhotos, drawPhotoMarkers)
</script>

<template>
  <div class="wr" :class="{ 'is-unity': isUnity3d }">
    <section class="wr-map">
      <div ref="mapBox" class="wr-map__canvas"></div>
      <!-- 실감 3D (유니티 WebGL): 켜면 지도 위를 덮음. 지금은 시연 자료(1박 2일)를 그림 -->
      <RetroUnityMap v-if="isUnity3d" ref="unityMap" class="wr-unity" :trip-data="unityTrip" :status-text="unityStatus" @ready="syncUnity" />
      <!-- 밤·노을 덮개: 재생 시각의 해 높이에 맞춘 색 (지도 위, 카드 아래, 누르기는 통과) -->
      <div v-if="!isUnity3d" class="wr-sky" :style="{ background: skyShade }"></div><!-- 3D는 유니티가 하늘을 직접 그려서 덮개를 안 씀 -->
      <!-- 3D 위에서 보기 가까이·멀리 (마우스 휠도 됨) -->
      <div v-if="isUnity3d && viewMode === 'top'" class="wr-unity-zoom">
        <button type="button" title="가까이" @click="unityMap && unityMap.zoom(1)">＋</button>
        <button type="button" title="멀리" @click="unityMap && unityMap.zoom(-1)">－</button>
      </div>

      <div v-if="isLoading || errorMessage" class="wr-state">
        <p>{{ errorMessage || '회고를 불러오는 중…' }}</p>
      </div>

      <template v-if="retro">
        <!-- 위 왼쪽: 여행 제목 -->
        <div class="wr-title">
          <div class="wr-title__links">
            <RouterLink to="/web/trips" class="wr-btn">← 지난 여행</RouterLink>
          </div>
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
          <button type="button" class="wr-btn" :class="{ 'is-on': isUnity3d }" title="건물·한강·밤 불빛까지 보이는 3D 지도 (누르면 켜고 끔)" @click="toggleUnity3d">🏙 3D 지도</button>
          <button type="button" class="wr-btn" :class="{ 'is-on': isDayNight }" title="재생 시각에 맞춰 밤낮 바꾸기 (끄면 늘 낮)" @click="isDayNight = !isDayNight">🌙 밤낮</button>
          <!-- 11/13: 지도 버튼은 실감 3D와 기본/위성만 남김 (3D·2D, 사진, 해·하늘, 동네 다시 띄우기, 머문 곳, 가시권 버튼은 뺌. 기능 코드는 그대로) -->
        </div>

        <!-- 내비 카드 -->
        <div v-if="navCard && (playing || progress > 0)" class="wr-nav" :class="{ 'wr-nav--street': viewMode === 'street' && !isNavFolded, 'is-folded': isNavFolded }"
          :style="{ transform: 'translate(calc(-50% + ' + navOffset.x + 'px), ' + navOffset.y + 'px)' }" title="끌어서 옮길 수 있어요" @pointerdown="startNavDrag">
          <button type="button" class="wr-nav__fold" :aria-expanded="!isNavFolded" :title="isNavFolded ? '안내 펼치기' : '안내 접기'" :aria-label="isNavFolded ? '안내 펼치기' : '안내 접기'" @click="isNavFolded = !isNavFolded">{{ isNavFolded ? '▾' : '▴' }}</button>
          <!-- 재생 중 사진을 찍은 시각을 지나면 내비 카드 바로 밑에 잠깐 뜨는 카드 (카드를 옮기면 같이 움직임) -->
          <Transition name="wr-float">
            <button v-if="floatPhoto" type="button" class="wr-float" @click="openViewer([floatPhoto], 0)">
              <video v-if="floatPhoto.mediaType === 'video'" :src="floatPhoto.url" muted autoplay loop></video>
              <img v-else :src="floatPhoto.url" alt="" />
              <span>📷 {{ floatPhoto.takenAt }} · {{ floatPhoto.placeName }}</span>
            </button>
          </Transition>
          <div v-if="isNavFolded" class="wr-nav__line">{{ toDayTime(nowMinutes) }} · {{ navCard.isStay ? '머무는 중' : '다음' }} <b>{{ navCard.row.name }}</b><template v-if="!navCard.isStay && navCard.meters !== null"> {{ metersText(navCard.meters) }}</template></div>
          <div v-if="!isNavFolded" class="wr-nav__time">{{ toDayTime(nowMinutes) }}<span v-if="viewMode === 'street' && navSlope" class="wr-nav__slope">{{ slopeText(navSlope) }}</span></div>
          <template v-if="isNavFolded"></template>
          <template v-else-if="navCard.isStay">
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
          <span><i class="wr-legend__dong"></i>머문 동네 (높을수록 오래)</span>
          <span><i class="wr-legend__pass"></i>지나간 동네</span>
          <span v-if="shownPhotos.length > 0"><i class="wr-legend__photo"></i>사진 {{ shownPhotos.length }}장 (찍은 곳)</span>
          <template v-if="clusters.isOn">
            <span><i class="wr-legend__dot" style="background: #1F3A68"></i>머문 곳 · 계획한 곳</span>
            <span><i class="wr-legend__dot" style="background: #E8603F"></i>머문 곳 · 계획 밖</span>
          </template>
        </div>

        <!-- 재생 중 사진을 찍은 시각을 지나면 잠깐 뜨는 카드 -->

        <!-- 재생 막대 -->
        <div class="wr-player">
          <button type="button" class="wr-player__play" :disabled="!canPlay" @click="togglePlay">{{ playing ? '일시정지' : '▶ 내비로 다시 걷기' }}</button>
          <button type="button" class="wr-btn" :disabled="!canPlay" @click="goToStart">처음으로</button>
          <div class="wr-seg wr-seg--view">
            <button type="button" :class="{ 'is-on': viewMode === 'top' }" @click="changeView('top')">위에서</button>
            <button type="button" :class="{ 'is-on': viewMode === 'street' }" :disabled="!isUnity3d" @click="changeView('street')">내비</button>
          </div>
          <button type="button" class="wr-btn" :disabled="!canPlay" @click="changeSpeed">{{ speed }}배속</button>
          <div class="wr-player__barwrap">
            <!-- 낮·밤 띠 (재생 시각의 하늘 색) -->
            <div v-if="isSunOn && canPlay" class="wr-player__sun" :style="sunStripStyle"></div>
            <input v-model="sliderValue" type="range" min="0" max="1000" :disabled="!canPlay" class="wr-player__bar" aria-label="재생 위치" />
          </div>
          <span class="wr-player__time">{{ canPlay ? toDayTime(nowMinutes) : 'GPS 기록 없음' }}</span>
          <label class="wr-player__follow"><input v-model="isFollow" type="checkbox" /> 따라가기</label>
          <button type="button" class="wr-btn" @click="backToOverview">전체 보기</button>
        </div>
      </template>
    </section>

    <div v-if="retro" class="wr-split" role="separator" aria-orientation="vertical" aria-label="지도와 오른쪽 칸 너비 (끌거나 ← → 키)" :aria-valuenow="sideWidth" tabindex="0" title="끌어서 너비 바꾸기" @pointerdown="startSplitDrag" @keydown="onSplitKey"><i></i></div>
    <aside v-if="retro" class="wr-side" :style="{ width: sideWidth + 'px' }">
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

      <!-- 여행 분석 (11/11, 11/12 이름 바꿈): 걸은 길 구성 + 머문 곳 -->
      <section class="wr-card">
        <RetroLandscapeCard :landscape="landscape" :is-on="isLandscapeOn" :day="selectedDay" :clusters="clusters"
          @toggle="toggleLandscape" @focus="focusLandscape"
          @toggle-clusters="toggleClusters" @focus-cluster="focusCluster" />
      </section>

      <!-- 메모 (11/11): 여행 메모 + 고른 장소의 메모·별점 (메모·사진 화면과 같은 저장소) -->
      <section class="wr-card">
        <h2>메모 <small>{{ memoSavedText || '이 브라우저에 저장돼요' }}</small></h2>
        <label class="wr-memo__label" for="wr-trip-memo">여행 메모</label>
        <textarea id="wr-trip-memo" v-model="journal.diary" rows="3" class="wr-memo__text" placeholder="이번 여행은 어땠나요? 첫 줄은 지난 여행 목록 카드에 보여요."></textarea>
        <template v-if="dayButtons.length > 2">
          <span class="wr-memo__label">날짜별 한 줄 메모</span>
          <input v-for="item in dayButtons.slice(1)" :key="item.day" v-model="journal.dayNotes[item.day]" class="wr-memo__day" :placeholder="item.label + ' 한 줄'" :aria-label="item.label + ' 한 줄 메모'" />
        </template>
        <label class="wr-memo__label" for="wr-stop-pick">장소 메모 <small>지도 핀을 눌러도 골라져요</small></label>
        <select id="wr-stop-pick" v-model="memoStopId" class="wr-memo__select">
          <option :value="null">장소를 골라 주세요</option>
          <option v-for="row in allStopRows" :key="row.id" :value="row.id">{{ row.day }}일차 {{ row.no }}. {{ row.name }}</option>
        </select>
        <template v-if="memoStop">
          <div class="wr-memo__stars" :aria-label="memoStop.name + ' 별점'">
            <button v-for="value in 5" :key="value" type="button" :class="{ 'is-on': value <= (journal.stopRatings[memoStop.id] || 0) }" :aria-label="value + '점'" @click="setStopRating(value)">★</button>
            <span>{{ journal.stopRatings[memoStop.id] ? journal.stopRatings[memoStop.id] + '점' : '별을 눌러 점수를 줘요' }}</span>
          </div>
          <textarea v-model="journal.stopNotes[memoStop.id]" rows="3" class="wr-memo__text" :placeholder="memoStop.name + '에서 있었던 일, 맛, 기분…'"></textarea>
        </template>
      </section>

      <!-- 사진 (11/11: 메모·사진 페이지에서 옮김) -->
      <section class="wr-card">
        <RetroPhotoCard ref="photoCard" :retro="retro" :photos="photos" :journal="journal" @reload="reloadPhotosFor" @view="openViewer([Object.assign({ placeName: '' }, $event)], 0)" />
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

      <!-- 계획 vs 실제 카드: 11/11 화면에서 뺌 (일단 주석으로 남김, 다시 쓰려면 이 주석을 풀기)
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
      -->

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

      <!-- 정산 (11/11: 여기서도 고쳐요. 앱 회고와 같은 API라 양쪽이 같이 바뀜) -->
      <section class="wr-card">
        <div class="wr-card__head">
          <h2>정산 <small>{{ settleDay === 0 ? '여행 전체' : settleDay + '일차만' }} · 앱과 같이 바뀌어요</small></h2>
          <div v-if="dayButtons.length > 2" class="wr-seg wr-seg--small" role="group" aria-label="정산 날짜">
            <button v-for="item in dayButtons" :key="item.day" type="button" :class="{ 'is-on': settleDay === item.day }" :aria-pressed="settleDay === item.day" @click="settleDay = item.day">{{ item.label }}</button>
          </div>
        </div>

        <h3 class="wr-sub">같이 간 사람 <small>이름을 누르면 바꿔요</small></h3>
        <div class="wr-chips">
          <span v-for="companion in companions" :key="companion.id" class="wr-chip">
            <button type="button" class="wr-chip__name" :disabled="companion.sortNo === 0" @click="startRenamePerson({ id: companion.id, name: companion.name, isMe: companion.sortNo === 0 })">{{ companion.name }}</button>
            <button v-if="companion.sortNo !== 0" type="button" class="wr-chip__x" :aria-label="companion.name + ' 빼기'" @click="askingRemoveId = companion.id">✕</button>
          </span>
          <button type="button" class="wr-chip wr-chip--add" @click="startAddPerson">＋ 사람 추가</button>
        </div>
        <form v-if="personEdit" class="wr-inline" @submit.prevent="savePerson">
          <input v-model="personEdit.name" maxlength="20" :placeholder="personEdit.id === null ? '새 사람 이름' : '바꿀 이름'" aria-label="이름" />
          <button type="submit" class="wr-btn wr-btn--main" :disabled="isSettleBusy || personEdit.name.trim() === ''">저장</button>
          <button type="button" class="wr-btn" @click="personEdit = null">취소</button>
        </form>
        <div v-if="askingRemoveId !== null && companionById(askingRemoveId)" class="wr-ask">
          <span><b>{{ companionById(askingRemoveId).name }}</b>을(를) 정산에서 뺄까요? 이 사람이 낸 결제는 '나'로 바뀌어요.</span>
          <button type="button" class="wr-btn" @click="askingRemoveId = null">취소</button>
          <button type="button" class="wr-btn wr-btn--danger" :disabled="isSettleBusy" @click="removePerson(companionById(askingRemoveId))">빼기</button>
        </div>

        <h3 class="wr-sub">나눠 내기 <small>{{ settleDay === 0 ? '낼 돈을 누르면 직접 정해요' : '그날 쓴 돈을 똑같이 나눠요' }}</small></h3>
        <p v-if="!settlement" class="wr-empty">같이 간 사람이 2명 이상이고 결제가 있으면 나눠 계산해요.</p>
        <template v-else>
          <div class="wr-person wr-person--head"><span>사람</span><span>낸 돈</span><span>낼 돈</span><span>받을·보낼 돈</span></div>
          <div v-for="person in settlement.people" :key="person.id" class="wr-person">
            <span class="wr-person__name">{{ person.name }}</span>
            <span>{{ toWon(person.paid) }}</span>
            <form v-if="shareEdit && shareEdit.id === person.id" class="wr-person__edit" @submit.prevent="saveShare(person.id, shareEdit.amount)">
              <input v-model="shareEdit.amount" inputmode="numeric" :aria-label="person.name + ' 낼 돈'" />
              <button type="submit" :disabled="isSettleBusy">저장</button>
              <button type="button" :disabled="isSettleBusy" title="똑같이 나누기로 되돌리기" @click="saveShare(person.id, null)">자동</button>
            </form>
            <span v-else-if="settleDay !== 0" class="wr-person__auto">{{ toWon(person.should) }}</span>
            <button v-else type="button" class="wr-person__should" title="낼 돈 직접 정하기" @click="startShareEdit(person)">{{ toWon(person.should) }} <small>{{ person.isFixed ? '직접' : '자동' }}</small></button>
            <b :class="person.balance >= 0 ? 'is-plus' : 'is-minus'">{{ person.balance >= 0 ? '+' : '−' }}{{ toWon(Math.abs(person.balance)) }}</b>
          </div>
          <p v-if="settlement.gap !== 0" class="wr-warn">낼 돈을 더하면 쓴 돈과 {{ toWon(Math.abs(settlement.gap)) }} 달라요. 한 사람을 '자동'으로 두면 맞춰져요.</p>
          <div class="wr-transfers">
            <p v-for="(transfer, i) in settlement.transfers" :key="i">{{ transfer.from }} → {{ transfer.to }} <b>{{ toWon(transfer.amount) }}</b></p>
            <p v-if="settlement.transfers.length === 0">주고받을 돈이 없어요.</p>
          </div>
        </template>

        <h3 class="wr-sub">장소별 결제 {{ payments.length }}건 <small>낸 사람을 누르면 다음 사람으로</small></h3>
        <!-- 결제 넣기: 영수증 사진으로 읽어 오기 · 직접 입력 (앱 회고의 ⋯ 메뉴와 같은 API) -->
        <div class="wr-payadd">
          <button type="button" class="wr-btn" :disabled="isSettleBusy" @click="openReceipt">🧾 영수증 가져오기</button>
          <button type="button" class="wr-btn" :disabled="isSettleBusy" @click="startManualPayment">✎ 직접 입력</button>
          <input ref="receiptInput" type="file" accept="image/*" hidden @change="readReceipt" />
        </div>
        <form v-if="paymentEdit && paymentEdit.groupKey === 'top'" class="wr-payform wr-payform--full" @submit.prevent="savePayment">
          <select v-model="paymentEdit.day" aria-label="날짜"><option v-for="item in dayButtons.slice(1)" :key="item.day" :value="item.day">{{ item.label }}</option></select>
          <input v-model="paymentEdit.time" type="time" aria-label="시각" />
          <input v-model="paymentEdit.storeName" maxlength="50" placeholder="가게 이름" aria-label="가게 이름" />
          <input v-model="paymentEdit.amount" inputmode="numeric" placeholder="금액(원)" aria-label="금액" />
          <select v-model="paymentEdit.payer" aria-label="낸 사람"><option v-for="companion in companions" :key="companion.id" :value="companion.name">{{ companion.name }}</option></select>
          <button type="submit" class="wr-btn wr-btn--main" :disabled="isSettleBusy">넣기</button>
          <button type="button" class="wr-btn" @click="paymentEdit = null">취소</button>
        </form>
        <p v-if="paymentGroups.length === 0" class="wr-empty">다녀온 장소가 없어서 결제를 넣을 곳이 없어요.</p>
        <div v-for="group in paymentGroups" :key="group.key" class="wr-paygroup">
          <div class="wr-paygroup__head">
            <span class="wr-paygroup__title">{{ group.title }} <small>{{ group.sub }}</small></span>
            <b>{{ group.total > 0 ? toWon(group.total) : '' }}</b>
            <button v-if="group.canAdd" type="button" class="wr-mini" @click="startAddPayment(group)">＋ 결제</button>
          </div>
          <template v-for="payment in group.payments" :key="payment.id">
            <div v-if="!(paymentEdit && paymentEdit.id === payment.id)" class="wr-payrow">
              <span class="wr-payrow__time">{{ payment.paidAt }}</span>
              <span class="wr-payrow__store">{{ payment.storeName }}</span>
              <span class="wr-payrow__amount">{{ toWon(payment.amount) }}</span>
              <button type="button" class="wr-payer" :disabled="companions.length < 2 || isSettleBusy" :title="companions.length < 2 ? '같이 간 사람을 먼저 추가해요' : '낸 사람 바꾸기'" @click="cyclePayer(payment)">{{ payment.payer }}</button>
              <button type="button" class="wr-payrow__x" :aria-label="payment.storeName + ' 결제 고치기'" title="고치기" @click="startEditPayment(group, payment)">✎</button>
              <button type="button" class="wr-payrow__x" :aria-label="payment.storeName + ' 결제 지우기'" title="지우기" @click="askingDeleteId = payment.id">✕</button>
            </div>
            <div v-if="askingDeleteId === payment.id" class="wr-ask">
              <span><b>{{ payment.storeName }}</b> {{ toWon(payment.amount) }} 결제를 지울까요? 되돌릴 수 없어요.</span>
              <button type="button" class="wr-btn" @click="askingDeleteId = null">취소</button>
              <button type="button" class="wr-btn wr-btn--danger" :disabled="isSettleBusy" @click="deletePayment(payment)">지우기</button>
            </div>
            <form v-if="paymentEdit && paymentEdit.id === payment.id" class="wr-payform" @submit.prevent="savePayment">
              <input v-model="paymentEdit.storeName" maxlength="50" placeholder="가게 이름" aria-label="가게 이름" />
              <input v-model="paymentEdit.amount" inputmode="numeric" placeholder="금액(원)" aria-label="금액" />
              <select v-model="paymentEdit.payer" aria-label="낸 사람"><option v-for="companion in companions" :key="companion.id" :value="companion.name">{{ companion.name }}</option></select>
              <button type="submit" class="wr-btn wr-btn--main" :disabled="isSettleBusy">저장</button>
              <button type="button" class="wr-btn" @click="paymentEdit = null">취소</button>
            </form>
          </template>
          <form v-if="paymentEdit && paymentEdit.id === null && paymentEdit.groupKey === group.key" class="wr-payform" @submit.prevent="savePayment">
            <input v-model="paymentEdit.storeName" maxlength="50" placeholder="가게 이름" aria-label="가게 이름" />
            <input v-model="paymentEdit.amount" inputmode="numeric" placeholder="금액(원)" aria-label="금액" />
            <select v-model="paymentEdit.payer" aria-label="낸 사람"><option v-for="companion in companions" :key="companion.id" :value="companion.name">{{ companion.name }}</option></select>
            <button type="submit" class="wr-btn wr-btn--main" :disabled="isSettleBusy">넣기</button>
            <button type="button" class="wr-btn" @click="paymentEdit = null">취소</button>
          </form>
        </div>
        <p v-if="settleMessage" class="wr-settle-msg" role="status">{{ settleMessage }}</p>
      </section>
    </aside>

    <!-- 사진 크게 보기 -->
    <div v-if="viewerPhoto" class="wr-viewer" role="dialog" aria-modal="true" aria-label="사진 크게 보기" @click.self="viewer = null">
      <video v-if="viewerPhoto.mediaType === 'video'" :key="viewerPhoto.id" :src="viewerPhoto.url" controls autoplay></video>
      <img v-else :src="viewerPhoto.url" alt="" />
      <div class="wr-viewer__bar">
        <span>{{ photoCaption(viewerPhoto) }} <small v-if="viewer.list.length > 1">{{ viewer.index + 1 }} / {{ viewer.list.length }}</small></span>
        <span v-if="viewerPhoto.stopId && journal.stopNotes[viewerPhoto.stopId]" class="wr-viewer__note">📝 {{ journal.stopNotes[viewerPhoto.stopId] }}</span>
        <button v-if="viewerPhoto.mediaType === 'photo'" type="button" class="wr-btn" @click="editFromViewer(viewerPhoto.id)">꾸미기</button>
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

.wr-tools { background: none; box-shadow: none; top: 16px; right: 16px; display: flex; flex-wrap: wrap; gap: 8px; padding: 0; max-width: min(560px, max(260px, calc(100% - 470px))); justify-content: flex-end; } /* 지도가 좁아지면 제목 카드와 안 겹치게 여러 줄로 */
.wr-tools > * { box-shadow: 0 2px 8px rgba(24, 33, 58, 0.18); } /* 상자 없이 버튼마다 그림자 (지도 위에서도 보이게) */
.wr-seg { display: flex; border: 1px solid var(--line); border-radius: var(--radius-md); overflow: hidden; }
.wr-seg button { padding: 6px 12px; border: 0; background: #fff; cursor: pointer; }
.wr-seg button.is-on { background: var(--navy); color: var(--on-navy); }
.wr-btn { padding: 6px 12px; border: 1px solid var(--line); border-radius: var(--radius-md); background: #fff; cursor: pointer; }
.wr-btn:disabled, .wr-player__play:disabled { opacity: 0.5; cursor: default; }

.wr-nav__fold { position: absolute; top: 8px; right: 8px; width: 28px; height: 28px; border: 0; border-radius: 50%; background: rgba(255, 255, 255, 0.16); color: #fff; font-size: 12px; cursor: pointer; }
.wr-nav.is-folded { min-width: 0; padding: 8px 44px 8px 14px; }
.wr-nav__line { font-size: 13px; white-space: nowrap; }
.wr-nav__slope { margin-left: 10px; padding: 1px 8px; border-radius: var(--radius-full); background: rgba(255, 255, 255, 0.16); font-weight: 700; }
.wr-nav { cursor: grab; touch-action: none; user-select: none; top: 176px; left: 50%; transform: translateX(-50%); min-width: 340px; padding: 12px 18px; background: rgba(31, 58, 104, 0.95); color: #fff; }
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

.wr-side { width: 440px; flex: none; overflow-y: auto; padding: 16px; background: var(--surface); box-sizing: border-box; }
.wr-split { position: relative; width: 8px; flex: none; background: var(--line); cursor: col-resize; touch-action: none; }
.wr-split:hover, .wr-split:focus-visible { background: var(--navy-soft); outline: none; }
.wr-split i { position: absolute; top: 50%; left: 2px; width: 4px; height: 44px; margin-top: -22px; border-radius: var(--radius-full); background: var(--ink-muted); opacity: 0.6; }
.wr-card__head { display: flex; align-items: center; justify-content: space-between; gap: 8px; flex-wrap: wrap; margin-bottom: 10px; }
.wr-card__head h2 { margin: 0; }
.wr-seg--small button { padding: 4px 10px; font-size: 12px; }
.wr-person__auto { padding: 2px 0; }
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

.wr-memo__label { display: block; margin: 10px 0 6px; font-size: 13px; font-weight: 700; }
.wr-memo__label:first-of-type { margin-top: 0; }
.wr-memo__label small { margin-left: 4px; font-weight: 400; color: var(--ink-muted); }
.wr-memo__text { width: 100%; padding: 8px 10px; border: 1px solid var(--line); border-radius: var(--radius-md); font: inherit; line-height: 20px; resize: vertical; box-sizing: border-box; color: var(--ink); }
.wr-memo__text:focus, .wr-memo__select:focus, .wr-inline input:focus, .wr-person__edit input:focus { outline: 2px solid var(--navy); outline-offset: -1px; }
.wr-memo__day { display: block; width: 100%; margin-bottom: 6px; padding: 6px 10px; border: 1px solid var(--line); border-radius: var(--radius-md); background: var(--surface); font: inherit; font-size: 13px; box-sizing: border-box; }
.wr-memo__day:focus { outline: 2px solid var(--navy); outline-offset: -1px; }
.wr-memo__select { width: 100%; padding: 7px 8px; border: 1px solid var(--line); border-radius: var(--radius-md); background: #fff; font: inherit; color: var(--ink); }
.wr-memo__stars { display: flex; align-items: center; gap: 2px; margin: 8px 0 6px; }
.wr-memo__stars button { padding: 0 1px; border: 0; background: none; color: var(--line); font-size: 24px; line-height: 1; cursor: pointer; }
.wr-memo__stars button.is-on { color: #F2A33A; }
.wr-memo__stars span { margin-left: 6px; font-size: 12px; color: var(--ink-muted); }
.wr-sub { margin: 14px 0 8px; font-size: 13px; }
.wr-sub small { margin-left: 4px; font-weight: 400; color: var(--ink-muted); }
.wr-card h2 + .wr-sub { margin-top: 0; }
.wr-chips { display: flex; flex-wrap: wrap; gap: 6px; }
.wr-chip { display: inline-flex; align-items: center; border: 1px solid var(--line); border-radius: var(--radius-full); background: #fff; font-size: 13px; }
.wr-chip__name { padding: 4px 10px; border: 0; background: none; font: inherit; color: var(--ink); cursor: pointer; }
.wr-chip__name:disabled { font-weight: 700; cursor: default; }
.wr-chip__x { padding: 4px 8px 4px 0; border: 0; background: none; color: var(--ink-muted); font-size: 11px; cursor: pointer; }
.wr-chip--add { padding: 4px 10px; border-style: dashed; color: var(--navy); font: inherit; font-size: 13px; cursor: pointer; }
.wr-inline { display: flex; gap: 6px; margin-top: 8px; }
.wr-inline input { flex: 1; min-width: 0; padding: 6px 8px; border: 1px solid var(--line); border-radius: var(--radius-md); font: inherit; }
.wr-ask { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; margin-top: 8px; padding: 8px 10px; border-radius: var(--radius-md); background: #FFF3EE; font-size: 13px; }
.wr-ask span { flex: 1 1 100%; }
.wr-btn--danger { border-color: var(--danger); color: var(--danger); }
.wr-person { display: grid; grid-template-columns: 64px 1fr 1.25fr 92px; align-items: center; gap: 6px; padding: 6px 0; border-top: 1px solid var(--line); font-size: 13px; }
.wr-person--head { border-top: 0; padding-top: 0; color: var(--ink-muted); font-size: 12px; }
.wr-person--head span:last-child { text-align: right; }
.wr-person__name { font-weight: 700; }
.wr-person b { text-align: right; }
.wr-person .is-plus { color: var(--success); }
.wr-person .is-minus { color: var(--danger); }
.wr-person__should { justify-self: start; padding: 2px 8px; border: 1px dashed var(--line); border-radius: var(--radius-md); background: none; font: inherit; color: var(--ink); cursor: pointer; }
.wr-person__should small { color: var(--ink-muted); font-size: 11px; }
.wr-person__edit { display: flex; gap: 4px; }
.wr-person__edit input { width: 72px; padding: 2px 6px; border: 1px solid var(--line); border-radius: var(--radius-sm); font: inherit; }
.wr-person__edit button { padding: 2px 6px; border: 1px solid var(--line); border-radius: var(--radius-sm); background: #fff; font: inherit; font-size: 12px; cursor: pointer; }
.wr-warn { margin: 6px 0 0; color: var(--danger); font-size: 12px; }
.wr-payrow { display: grid; grid-template-columns: 78px 1fr auto auto 22px; align-items: center; gap: 6px; padding: 6px 0; border-top: 1px solid var(--line); font-size: 13px; }
.wr-payrow__time { color: var(--ink-muted); font-size: 12px; font-variant-numeric: tabular-nums; }
.wr-payrow__store { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.wr-payrow__amount { font-weight: 700; font-variant-numeric: tabular-nums; }
.wr-payer { min-width: 44px; padding: 2px 8px; border: 0; border-radius: var(--radius-full); background: var(--navy-soft); color: var(--navy); font: inherit; font-size: 12px; font-weight: 700; cursor: pointer; }
.wr-payer:disabled { cursor: default; opacity: 0.7; }
.wr-payrow__x { padding: 0; border: 0; background: none; color: var(--ink-muted); font-size: 12px; cursor: pointer; }
.wr-paygroup { margin-top: 8px; padding: 8px 10px; border-radius: var(--radius-md); background: var(--surface); }
.wr-paygroup__head { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.wr-paygroup__title { flex: 1; min-width: 0; font-weight: 700; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.wr-paygroup__title small { margin-left: 4px; font-weight: 400; color: var(--ink-muted); }
.wr-paygroup .wr-payrow { grid-template-columns: 44px 1fr auto auto 18px 18px; }
.wr-mini { padding: 2px 8px; border: 1px solid var(--navy); border-radius: var(--radius-full); background: #fff; color: var(--navy); font: inherit; font-size: 12px; font-weight: 700; cursor: pointer; white-space: nowrap; }
.wr-payform { display: grid; grid-template-columns: 1fr 86px 72px; gap: 6px; margin-top: 6px; }
.wr-payform input, .wr-payform select { min-width: 0; padding: 5px 8px; border: 1px solid var(--line); border-radius: var(--radius-sm); background: #fff; font: inherit; font-size: 13px; }
.wr-payform .wr-btn { justify-self: start; }
.wr-payform button[type='submit'] { grid-column: 2; }
/* 영수증·직접 입력 칸: 날짜|시각 / 가게 이름(한 줄 전체) / 금액|낸 사람 / 넣기|취소 */
.wr-payform--full { grid-template-columns: 1fr 1fr; }
.wr-payform--full input[aria-label='가게 이름'] { grid-column: 1 / -1; }
.wr-payform--full button[type='submit'] { grid-column: auto; justify-self: stretch; }
.wr-payform--full .wr-btn { justify-self: stretch; }
.wr-settle-msg { margin: 8px 0 0; color: var(--navy); font-size: 12px; font-weight: 700; }
:deep(.wr-pop__type) { display: inline-block; margin-bottom: 4px; padding: 1px 8px; border-radius: var(--radius-full); color: #fff; font-size: 11px; font-weight: 700; }
:deep(.wr-pop__feature) { margin-top: 2px; font-size: 12px; color: var(--ink-muted); }
:deep(.wr-pop__stars) { margin-top: 6px; color: #F2A33A; letter-spacing: 1px; }
:deep(.wr-pop__hint) { margin-top: 6px; font-size: 11px; color: var(--ink-muted); }
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
.wr-title__links { display: flex; gap: 6px; margin-bottom: 8px; }
.wr-sky { position: absolute; inset: 0; z-index: 1; pointer-events: none; transition: background 0.8s; }
.wr-payadd { display: flex; gap: 8px; margin: 6px 0 10px; }
.wr-unity { position: absolute; inset: 0; z-index: 1; } /* 카드·버튼(z-index 2)은 3D 위에 그대로 보임 */
.wr-unity-zoom { position: absolute; right: 10px; bottom: 96px; z-index: 3; display: flex; flex-direction: column; border-radius: var(--radius-sm); overflow: hidden; box-shadow: 0 2px 8px rgba(24, 33, 58, 0.18); }
.wr-unity-zoom button { width: 32px; height: 32px; border: 0; border-bottom: 1px solid var(--line); background: #fff; font-size: 18px; cursor: pointer; }
.wr-unity-zoom button:last-child { border-bottom: 0; }
.wr.is-unity :deep(.maplibregl-ctrl-bottom-right) { display: none; } /* 3D를 켜면 가려진 평면 지도의 +/− 대신 3D용 버튼을 씀 */
.wr-legend__dot { width: 12px !important; height: 12px !important; border: 2px solid #fff; border-radius: 50%; box-shadow: 0 0 0 1px rgba(24, 33, 58, 0.3); }
.wr-legend__photo { width: 14px !important; height: 14px !important; border: 2px solid #fff; border-radius: 50%; background: #F2A33A; box-shadow: 0 0 0 1px #F2A33A; }
.wr-player__barwrap { position: relative; flex: 1; min-width: 60px; padding-top: 24px; }
.wr-player__barwrap .wr-player__bar { width: 100%; }
.wr-player__sun { position: absolute; left: 0; right: 0; top: 18px; height: 6px; border-radius: var(--radius-full); }

.wr-float { position: absolute; left: 50%; top: calc(100% + 10px); transform: translateX(-50%); width: 260px; padding: 0; overflow: hidden; border: 3px solid #fff; border-radius: var(--radius-lg); background: #000; box-shadow: 0 8px 28px rgba(0, 0, 0, 0.35); cursor: zoom-in; }
.wr-float img, .wr-float video { display: block; width: 100%; height: 170px; object-fit: cover; }
.wr-float span { display: block; padding: 6px 10px; background: #fff; color: var(--ink); font-size: 13px; text-align: left; }
.wr-float-enter-active, .wr-float-leave-active { transition: opacity 0.3s, transform 0.3s; }
.wr-float-enter-from, .wr-float-leave-to { opacity: 0; }

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
  .wr-side { width: auto !important; border-left: 0; } /* 좁은 화면은 아래로 쌓여서 너비 조절 없음 */
  .wr-split { display: none; }
}
</style>
