<script setup>
// S13-e 길 안내 (⑤): 지금 위치 → 다음 장소까지 도보·버스·지하철·택시 길을 진짜 길찾기(GET /api/route, TMAP·ODsay)로 받아
//   카카오 지도에 경로선을 그리고, GPS가 바뀔 때마다 경로선 위 내 위치로 진행률·남은 시간·다음 안내를 바꿔요.
//   - 경로에서 60m 넘게 두 번 연속 벗어나면 지금 위치에서 길을 다시 찾아요 (30초에 한 번까지)
//   - 20초마다 위치를 저장(POST /live/track)하고, 도착 150m 안이면 도착을 기록한 뒤 여행 중 화면으로 돌아가요
//   - 가짜 GPS(개발자 모드, 시연 계정만): 진짜 GPS 대신 받은 경로선을 따라 내 위치가 움직여요 (11/06: ?demo=1 없앰)
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BottomSheet from '@/components/common/BottomSheet.vue'
import PageMessage from '@/components/common/PageMessage.vue'
import KakaoMap from '@/components/common/KakaoMap.vue'
import { getTrip, getLiveStatus, getRouteGuide, getTripList, sendTrack } from '@/api/trips'
import { useTripStore } from '@/stores/trip'
import { distanceMeters, pathLength, pointAlongPath, snapToPath } from '@/utils/geo'
import { toMinutes, toTime } from '@/utils/time'
import { goBack } from '@/utils/back'
import { showToast } from '@/utils/toast'
import { devMode, isFakeGps, setFakeGps } from '@/utils/devMode'

const MODES = [
  { key: 'walk', label: '도보', icon: 'i-walk' },
  { key: 'bus', label: '버스', icon: 'i-bus' },
  { key: 'subway', label: '지하철', icon: 'i-subway' },
  { key: 'car', label: '택시', icon: 'i-car' },
]
const STEP_ICON = { start: 'i-locate', straight: 'i-up', left: 'i-back', right: 'i-next', crosswalk: 'i-walk', stairs: 'i-up', walk: 'i-walk', bus: 'i-bus', subway: 'i-subway', getoff: 'i-busstop', car: 'i-car', end: 'i-flag' }
const OFF_ROUTE_M = 60 // 경로선에서 이만큼 벗어나면
const OFF_ROUTE_COUNT = 2 // 연속 이만큼이면 길을 다시 찾음
const REROUTE_GAP_MS = 30000
const ARRIVE_M = 150 // 서버 도착 판정 반경과 같게
const TRACK_GAP_MS = 20000
const PASSED_M = 15 // 안내 지점을 이만큼 지나면 다음 안내로
const WALK_FIRST_MAX_MIN = 20 // 원하는 수단이 안 될 때 걸어서 이 안이면 도보를 먼저 보여 줌
const DEMO_STEP_MS = 1500
const DEMO_STEP_COUNT = 24 // 시연: 경로를 몇 걸음에 가는지
const DEMO_START_OFFSET = 0.004 // 시연: 다녀온 곳이 없을 때 출발점을 도착지에서 이만큼(도) 떨어뜨림
const SEOUL_CITY_HALL = { lat: 37.5665, lng: 126.978 }
// 지도는 시트 뒤까지 화면 전체 (시트를 내리면 지도가 보여요). 아래 시트(404px)에 가리지 않는 곳에 맞춰요 (11/04)
const ROUTE_MAP_PADDING = { top: 120, right: 40, bottom: 444, left: 40 }

const route = useRoute()
const router = useRouter()
const tripStore = useTripStore()
const isDemo = ref(isFakeGps())

const phase = ref('loading') // loading / none / done / gps / gps-error / ready
const message = ref('')
const tripId = ref(null)
const destination = ref(null) // { id, no, name, lat, lng, moveMode }
const originName = ref('지금 위치')
const position = ref(null) // { lat, lng }
const guides = ref({}) // 수단 → /api/route 응답
const modeKey = ref('walk')
const isRerouting = ref(false)
const isArrived = ref(false)
let watchId = null
let demoTimer = null
let lastTrackAt = 0
let lastRerouteAt = 0
let offCount = 0
const hasJoined = ref(false) // 한 번이라도 경로선 위(60m 안)에 올라왔나. 그 전(출발점까지 가는 중)에는 이탈로 보지 않음

onMounted(start)
onUnmounted(stopMoving)

async function start() {
  try {
    const trips = await getTripList()
    const ongoing = trips.find((trip) => trip.status === 'ongoing')
    if (!ongoing) {
      phase.value = 'none'
      return
    }
    tripId.value = ongoing.id
    tripStore.tripId = ongoing.id
  } catch (e) {
    phase.value = 'none'
    message.value = e.message
    return
  }
  if (isDemo.value) {
    await startDemo()
  } else {
    phase.value = 'gps'
    watchGps()
  }
}

function stopMoving() {
  if (watchId !== null && navigator.geolocation) navigator.geolocation.clearWatch(watchId)
  watchId = null
  clearInterval(demoTimer)
}

// ------------------------------------------------------------ 위치
function watchGps() {
  if (!navigator.geolocation) {
    message.value = '이 브라우저는 위치를 지원하지 않아요'
    phase.value = 'gps-error'
    return
  }
  watchId = navigator.geolocation.watchPosition(
    (pos) => onMove(pos.coords.latitude, pos.coords.longitude, pos.coords.accuracy),
    (e) => {
      message.value = e.code === 1 ? '위치 권한이 꺼져 있어요. 브라우저 주소창 옆 자물쇠에서 위치를 허용해 주세요.' : '지금 위치를 받지 못했어요.'
      if (!position.value) phase.value = 'gps-error'
    },
    { enableHighAccuracy: true, maximumAge: 5000, timeout: 20000 },
  )
}

// 위치를 못 받을 때 시연용 이동으로
function showDemo() {
  stopMoving()
  isDemo.value = true
  setFakeGps(true)
  startDemo()
}

function retryGps() {
  stopMoving()
  phase.value = 'gps'
  watchGps()
}

// 위치가 바뀔 때마다: 처음이면 목적지·길을 받고, 그다음부터 진행·이탈·도착을 확인
async function onMove(lat, lng, accuracy = null) {
  if (isArrived.value) return
  const isFirst = position.value === null
  position.value = { lat, lng }
  tripStore.position = position.value
  saveTrack(lat, lng, accuracy, false)
  if (isFirst) {
    await loadDestination(lat, lng)
    if (destination.value) await loadGuides(lat, lng)
    return
  }
  if (!destination.value) return
  if (distanceMeters(lat, lng, destination.value.lat, destination.value.lng) <= ARRIVE_M) {
    arrive(lat, lng)
    return
  }
  checkOffRoute()
}

async function saveTrack(lat, lng, accuracy, isForced) {
  const now = Date.now()
  if (!tripId.value || (!isForced && now - lastTrackAt < TRACK_GAP_MS)) return
  lastTrackAt = now
  try {
    await sendTrack(tripId.value, lat, lng, accuracy)
  } catch (e) {
    message.value = e.message
  }
}

// ------------------------------------------------------------ 목적지·길
// 지금 상황(POST /live/status)에서 다음에 갈 칸 (query stopId가 있으면 그 칸)
async function loadDestination(lat, lng) {
  try {
    const status = await getLiveStatus(tripId.value, lat, lng)
    applyStatus(status)
  } catch (e) {
    message.value = e.message
    phase.value = 'none'
  }
}

function applyStatus(status) {
  const stops = status.stops
  const wantedId = Number(route.query.stopId)
  let index = stops.findIndex((stop) => stop.id === wantedId && stop.reach !== 'done')
  if (index < 0) index = stops.findIndex((stop) => stop.reach !== 'done' && stop.reach !== 'here')
  if (index < 0) {
    phase.value = 'done'
    return null
  }
  const stop = stops[index]
  destination.value = { id: stop.id, no: index + 1, name: stop.name, lat: stop.lat, lng: stop.lng, moveMode: stop.moveMode, fixed: stop.fixed }
  const here = stops.find((item) => item.reach === 'here')
  originName.value = here ? here.name : '지금 위치'
  // 시연 출발점: 지금 있는 곳 → 마지막으로 다녀온 곳 → 도착지 근처
  let before = here
  for (let i = index - 1; i >= 0 && !before; i--) if (stops[i].reach === 'done') before = stops[i]
  return before ? { lat: before.lat, lng: before.lng } : { lat: stop.lat - DEMO_START_OFFSET, lng: stop.lng - DEMO_START_OFFSET }
}

// 네 가지 수단 길을 한꺼번에 받음 (못 찾은 수단은 available=false + 이유)
async function loadGuides(lat, lng) {
  const to = destination.value
  const list = await Promise.all(MODES.map((mode) => getRouteGuide(lat, lng, to.lat, to.lng, mode.key).catch((e) => ({ mode: mode.key, available: false, message: e.message }))))
  const next = {}
  for (let i = 0; i < MODES.length; i++) next[MODES[i].key] = list[i]
  guides.value = next
  modeKey.value = pickMode()
  phase.value = 'ready'
}

// 처음 고를 수단: 서버가 다음 칸에 쓴 수단(walk / transit / car) → 쓸 수 있는 것 중 가장 빠른 것
function pickMode() {
  const wanted = { walk: ['walk'], transit: ['subway', 'bus'], car: ['car'] }[destination.value.moveMode] || ['walk']
  for (const key of wanted) if (guides.value[key] && guides.value[key].available) return key
  const walk = guides.value.walk
  if (walk && walk.available && walk.minutes <= WALK_FIRST_MAX_MIN) return 'walk'
  let best = null
  for (const mode of MODES) {
    const guide = guides.value[mode.key]
    if (guide && guide.available && (!best || guide.minutes < guides.value[best].minutes)) best = mode.key
  }
  return best || 'walk'
}

// 지금 수단으로 길을 다시 찾음 (경로를 벗어났을 때)
async function reroute() {
  if (isRerouting.value || !position.value) return
  isRerouting.value = true
  lastRerouteAt = Date.now()
  offCount = 0
  hasJoined.value = false
  const { lat, lng } = position.value
  try {
    const guide = await getRouteGuide(lat, lng, destination.value.lat, destination.value.lng, modeKey.value)
    if (guide.available) {
      guides.value = { ...guides.value, [modeKey.value]: guide }
      showToast('길을 벗어나서 지금 위치에서 다시 찾았어요')
    }
  } catch (e) {
    showToast(e.message)
  }
  isRerouting.value = false
}

function checkOffRoute() {
  if (!snap.value) return
  if (snap.value.off <= OFF_ROUTE_M) hasJoined.value = true
  if (!hasJoined.value) return
  offCount = snap.value.off > OFF_ROUTE_M ? offCount + 1 : 0
  if (offCount >= OFF_ROUTE_COUNT && Date.now() - lastRerouteAt > REROUTE_GAP_MS) reroute()
}

// 도착: 도착 판정은 서버가 150m 안 두 번 연속으로 해서 두 번 보냄 → 여행 중 화면으로
async function arrive(lat, lng) {
  isArrived.value = true
  stopMoving()
  await saveTrack(lat, lng, null, true)
  await saveTrack(lat, lng, null, true)
  setTimeout(() => {
    router.replace('/live')
    showToast(destination.value.name + '에 도착했어요 · 도착 시각을 기록했어요')
  }, 1200)
}

// ------------------------------------------------------------ 시연용 이동
async function startDemo() {
  phase.value = 'gps'
  try {
    const status = await getLiveStatus(tripId.value, SEOUL_CITY_HALL.lat, SEOUL_CITY_HALL.lng)
    const origin = applyStatus(status)
    if (!origin) return
    position.value = origin
    await loadGuides(origin.lat, origin.lng)
    walkDemo()
  } catch (e) {
    message.value = e.message
    phase.value = 'none'
  }
}

// 고른 수단 경로선을 따라 내 위치를 옮김 (수단을 바꾸면 지금 자리에서 그 경로로 다시)
function walkDemo() {
  clearInterval(demoTimer)
  const path = guide.value && guide.value.available ? guide.value.coords : []
  if (path.length < 2) return
  const total = pathLength(path)
  let along = snap.value ? snap.value.along : 0
  demoTimer = setInterval(() => {
    along = Math.min(total, along + total / DEMO_STEP_COUNT)
    const [lat, lng] = pointAlongPath(path, along)
    onMove(lat, lng, 10)
  }, DEMO_STEP_MS)
}
watch(modeKey, () => {
  hasJoined.value = false
  offCount = 0
  if (isDemo.value && phase.value === 'ready') walkDemo()
})

// ------------------------------------------------------------ 화면 값
const guide = computed(() => guides.value[modeKey.value] || null)
const path = computed(() => (guide.value && guide.value.available ? guide.value.coords : []))
const total = computed(() => pathLength(path.value))
const snap = computed(() => (position.value && path.value.length > 1 ? snapToPath(path.value, position.value.lat, position.value.lng) : null))
const along = computed(() => (snap.value ? snap.value.along : 0))
const progress = computed(() => (total.value ? Math.min(100, Math.round((along.value / total.value) * 100)) : 0))
const minutesLeft = computed(() => {
  if (!guide.value || !guide.value.available) return null
  if (!total.value) return guide.value.minutes
  return Math.max(1, Math.ceil((guide.value.minutes * (total.value - along.value)) / total.value))
})
const eta = computed(() => {
  if (minutesLeft.value === null) return '--:--'
  const now = new Date()
  return toTime(toMinutes(String(now.getHours()).padStart(2, '0') + ':' + String(now.getMinutes()).padStart(2, '0')) + minutesLeft.value)
})

// 안내마다 경로선 위 위치(처음부터 몇 m) → 지난 안내 / 다음 안내
const stepAlongs = computed(() => {
  const list = []
  let last = 0
  for (const step of guide.value && guide.value.available ? guide.value.steps : []) {
    if (step.lat != null && path.value.length > 1) last = snapToPath(path.value, step.lat, step.lng).along
    list.push(last)
  }
  return list
})
const nextStepIndex = computed(() => {
  const list = stepAlongs.value
  for (let i = 0; i < list.length; i++) if (list[i] > along.value + PASSED_M) return i
  return list.length - 1
})
const steps = computed(() => {
  if (!guide.value || !guide.value.available) return []
  return guide.value.steps.map((step, i) => ({
    ...step,
    icon: STEP_ICON[step.kind] || 'i-route',
    isPassed: i < nextStepIndex.value - 1,
    isNext: i === nextStepIndex.value,
    distance: stepAlongs.value[i] - along.value,
  }))
})

// 다음 안내가 바뀌면 목록에서 그 줄이 보이게 스크롤
const stepList = ref(null)
watch(() => nextStepIndex.value, async () => {
  await nextTick()
  const element = stepList.value && stepList.value.querySelector('.is-next')
  if (element) element.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
})

function tabText(mode) {
  const item = guides.value[mode.key]
  if (!item) return mode.label
  return item.available ? mode.label + ' ' + item.minutes + '분' : mode.label + ' –'
}
function distText(meters) {
  if (meters <= 0) return ''
  return meters >= 1000 ? (meters / 1000).toFixed(1) + 'km 앞' : Math.round(meters / 10) * 10 + 'm 앞'
}
const fareText = computed(() => {
  if (!guide.value || !guide.value.available || guide.value.fare == null) return ''
  return (modeKey.value === 'car' ? '예상 택시비 ' : '요금 ') + guide.value.fare.toLocaleString() + '원'
})

const APP_OPEN_WAIT_MS = 1200 // 앱이 열렸는지 기다리는 시간
const isTaxi = computed(() => modeKey.value === 'car' && !!guide.value && guide.value.available)
const taxiFare = computed(() => (guide.value && guide.value.fare != null ? '약 ' + guide.value.fare.toLocaleString() + '원' : '-'))

// 도착지 주소: 지금 상황(live/status) 칸에는 주소가 없어서, 택시를 고르면 여행에서 찾아 둠 (누른 뒤 받으면 복사가 막히는 브라우저가 있어요)
const taxiAddress = ref('')
watch([isTaxi, destination], async ([taxi, place]) => {
  taxiAddress.value = ''
  if (!taxi || !place) return
  try {
    const trip = await getTrip(tripId.value)
    const stop = trip.stops.find((item) => item.id === place.id)
    taxiAddress.value = stop && stop.address ? stop.address : ''
  } catch {
    taxiAddress.value = ''
  }
})

// 카카오 T 앱으로 택시 부르기 (kakaot:// 앱 주소). 앱이 없어서 화면이 그대로면 카카오맵 길찾기 웹으로
function callTaxi() {
  const { name, lat, lng } = destination.value
  window.location.href = `kakaot://taxi?dest_lat=${lat}&dest_lng=${lng}`
  setTimeout(() => {
    if (document.visibilityState === 'visible') window.open(`https://map.kakao.com/link/to/${encodeURIComponent(name)},${lat},${lng}`, '_blank', 'noopener')
  }, APP_OPEN_WAIT_MS)
}

// 기사님께 보여 줄 도착지 주소 복사 (주소가 없으면 장소 이름). http에서는 clipboard가 없을 수 있어 옛날 방식으로
async function copyAddress() {
  const text = taxiAddress.value || destination.value.name
  try {
    await navigator.clipboard.writeText(text)
  } catch {
    const box = document.createElement('textarea')
    box.value = text
    document.body.appendChild(box)
    box.select()
    const isCopied = document.execCommand('copy')
    box.remove()
    if (!isCopied) return showToast(text)
  }
  showToast('주소를 복사했어요')
}

// 택시로 가다 도착하면 한 번 더 알려 줌
watch(isArrived, (arrived) => {
  if (arrived && modeKey.value === 'car') showToast('두고 내린 물건 없는지 확인해요')
})

const mapPins = computed(() => {
  if (!destination.value) return []
  const pins = [{ key: 'dest', lat: destination.value.lat, lng: destination.value.lng, text: destination.value.no, icon: destination.value.fixed ? 'i-lock' : '', cls: ' pin--sel', label: destination.value.name, route: false }]
  for (const step of guide.value && guide.value.available ? guide.value.steps : []) {
    if (step.lat == null || !['bus', 'subway', 'getoff'].includes(step.kind)) continue
    pins.push({ key: 'step-' + step.lat + step.lng + step.kind, lat: step.lat, lng: step.lng, icon: step.kind === 'subway' ? 'i-subway' : 'i-busstop', cls: ' pin--res', label: step.text, route: false })
  }
  return pins
})
const statusText = computed(() => {
  if (isArrived.value) return '도착했어요 · 기록하는 중…'
  if (isRerouting.value) return '길을 다시 찾는 중…'
  if (snap.value && snap.value.off > OFF_ROUTE_M && !hasJoined.value) return '출발 지점까지 ' + Math.round(snap.value.off / 10) * 10 + 'm 걸어가세요'
  if (snap.value && snap.value.off > OFF_ROUTE_M) return '경로에서 벗어났어요 · ' + Math.round(snap.value.off) + 'm'
  return '이동 중 · ' + minutesLeft.value + '분 남음'
})
</script>

<template>
  <div class="scr scr-RouteGuide">
    <div class="app">
      <template v-if="phase !== 'ready'">
        <header class="topbar">
          <button type="button" class="iconbtn" aria-label="뒤로가기" @click="goBack('/live')"><i class="ic ic--l i-back" aria-hidden="true"></i></button>
          <h1 class="topbar__title">길 안내</h1>
          <span style="width: 44px"></span>
        </header>
        <div class="msg-wrap">
        <PageMessage v-if="phase === 'loading'" is-loading title="다음 장소를 찾는 중이에요" />
        <PageMessage v-else-if="phase === 'none'" icon="i-route" title="진행 중인 여행이 없어요" :text="message || '여행을 시작하면 다음 장소까지 길을 안내해 드려요'" to="/trips" button="내 여행 보기" />
        <PageMessage v-else-if="phase === 'done'" icon="i-flag" title="남은 일정이 없어요" text="오늘 갈 곳을 모두 다녀왔어요" to="/live" button="여행 중 화면으로" />
        <section v-else-if="phase === 'gps-error'" class="page-msg">
          <span class="ico-tile ico-tile--warn" style="width: 56px; height: 56px"><i class="ic ic--xl i-locate" aria-hidden="true"></i></span>
          <h2 class="h-title">지금 위치를 받을 수 없어요</h2>
          <p class="t-cap">{{ message }}</p>
          <button type="button" class="btn btn--primary btn--lg" @click="retryGps">다시 시도</button>
          <button v-if="devMode.isOn" type="button" class="btn btn--secondary btn--lg" @click="showDemo">시연용 이동으로 보기</button>
        </section>
        <PageMessage v-else is-loading title="지금 위치를 찾는 중이에요" text="위치를 받으면 다음 장소까지 길을 찾아요" />
        </div>
      </template>

      <template v-else>
        <KakaoMap :pins="mapPins" :me="position" :path="path" follow :padding="ROUTE_MAP_PADDING" />

        <header class="topbar topbar--float">
          <button type="button" class="iconbtn iconbtn--float" aria-label="뒤로가기" @click="goBack('/live')"><i class="ic ic--l i-back" aria-hidden="true"></i></button>
          <span class="card livebar"><b class="t-strong" style="flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap">{{ originName }} → {{ destination.name }}</b></span>
        </header>

        <BottomSheet class="sheet--peek" style="top: 440px; padding: 8px 16px 0; gap: 12px">
          <div class="seg" role="tablist" aria-label="이동 방법">
            <button v-for="mode in MODES" :key="mode.key" type="button" role="tab" :class="{ 'is-on': mode.key === modeKey }" :aria-selected="mode.key === modeKey" @click="modeKey = mode.key">
              <i class="ic ic--s" :class="mode.icon" aria-hidden="true"></i>{{ tabText(mode) }}
            </button>
          </div>
          <h1 class="sr">{{ destination.name }}까지 가는 길</h1>

          <p v-if="!guide || !guide.available" class="t-cap" style="display: flex; gap: 6px; margin: 0">
            <i class="ic ic--s i-info" aria-hidden="true"></i>{{ guide && guide.message ? guide.message : '이 수단으로는 길을 찾지 못했어요' }}
          </p>
          <!-- 택시: 길은 기사님이 아니까 도착 시각·요금만 크게 -->
          <section v-else-if="isTaxi" class="rg-taxi" aria-label="택시 예상">
            <div class="rg-taxi__main">
              <div class="rg-taxi__cols">
                <div class="rg-taxi__col"><b class="tn">{{ eta }}</b><span>도착 · {{ guide.minutes }}분 · {{ (guide.meters / 1000).toFixed(1) }}km</span></div>
                <i class="rg-taxi__line" aria-hidden="true"></i>
                <div class="rg-taxi__col"><b class="tn">{{ taxiFare }}</b><span>TMAP 기준 예상</span></div>
              </div>
            </div>
            <div class="rg-taxi__actions">
              <button type="button" class="btn btn--lg rg-taxi__call" @click="callTaxi"><i class="ic ic--s i-car" aria-hidden="true"></i>카카오 T로 부르기</button>
              <button type="button" class="btn btn--secondary btn--lg rg-taxi__copy" @click="copyAddress">주소 복사</button>
            </div>
            <p class="rg-taxi__tip"><i class="ic ic--s i-info" aria-hidden="true"></i>내릴 때 두고 내린 물건 없는지 확인해요</p>
          </section>

          <template v-else>
            <p v-if="fareText || guide.source" class="t-cap tn" style="margin: 0">{{ (guide.meters / 1000).toFixed(1) }}km{{ fareText ? ' · ' + fareText : '' }} · {{ guide.source === 'odsay' ? 'ODsay' : 'TMAP' }} 길찾기</p>
            <ol ref="stepList" class="rg-steps">
              <li v-for="(step, i) in steps" :key="i" :class="{ 'is-key': step.key, 'is-next': step.isNext, 'is-passed': step.isPassed }">
                <span class="rg-dot"><i class="ic ic--s" :class="step.icon" aria-hidden="true"></i></span>
                <span>
                  <b>{{ step.text }}</b>
                  <small v-if="step.detail || (step.isNext && distText(step.distance))">{{ [step.isNext ? distText(step.distance) : '', step.detail].filter(Boolean).join(' · ') }}</small>
                </span>
              </li>
            </ol>
          </template>
        </BottomSheet>

        <div class="cta cta--white">
          <div class="rg-status">
            <span v-if="!isTaxi" class="rg-eta tn"><b>{{ eta }}</b><small>도착 예상</small></span>
            <span style="flex: 1; display: flex; flex-direction: column; gap: 6px">
              <span class="t-cap rg-moving" :class="{ 'c-warning': snap && snap.off > OFF_ROUTE_M && hasJoined }"><i class="ic ic--s i-locate" aria-hidden="true"></i>{{ statusText }}</span>
              <span class="bar"><i :style="{ width: progress + '%' }"></i></span>
            </span>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<style>
.scr-RouteGuide .msg-wrap{padding-top:96px}
.scr-RouteGuide .rg-moving{display:flex;align-items:center;gap:4px;color:var(--success)}
.scr-RouteGuide .rg-steps{list-style:none;margin:0;padding:0 0 110px;display:flex;flex-direction:column;gap:2px;overflow-y:auto;min-height:0;flex:1}
.scr-RouteGuide .rg-steps li{display:flex;gap:12px;align-items:flex-start;padding:8px 0;position:relative}
.scr-RouteGuide .rg-steps li+li::before{content:"";position:absolute;left:15px;top:-10px;height:16px;border-left:2px dotted var(--line-strong)}
.scr-RouteGuide .rg-steps li.is-key{background:var(--success-soft);border-radius:var(--radius-md);padding:8px;margin:0 -8px}
.scr-RouteGuide .rg-steps li.is-next{background:var(--navy-soft);box-shadow:inset 3px 0 0 var(--navy);border-radius:var(--radius-md);padding:8px;margin:0 -8px}
.scr-RouteGuide .rg-steps li.is-passed{opacity:.45}
.scr-RouteGuide .rg-steps b{display:block;font-size:15px;line-height:22px}
.scr-RouteGuide .rg-steps small{display:block;font-size:13px;line-height:18px;color:var(--ink-muted)}
.scr-RouteGuide .rg-dot{width:32px;height:32px;border-radius:50%;background:var(--navy-soft);color:var(--navy);display:flex;align-items:center;justify-content:center;flex:none}
.scr-RouteGuide .is-key .rg-dot{background:var(--success);color:#fff}
.scr-RouteGuide .rg-taxi{display:flex;flex-direction:column;gap:10px;flex:1;min-height:0;overflow-y:auto;padding-bottom:96px}
.scr-RouteGuide .rg-taxi__main{flex:none;padding:16px 8px;border-radius:18px;background:linear-gradient(135deg,var(--navy),var(--navy-pressed));box-shadow:0 8px 20px rgba(31,58,104,.22)}
.scr-RouteGuide .rg-taxi__cols{display:flex;align-items:center}
.scr-RouteGuide .rg-taxi__col{flex:1;min-width:0;display:flex;flex-direction:column;align-items:center;gap:2px;color:#fff;text-align:center}
.scr-RouteGuide .rg-taxi__col b{font-size:26px;line-height:32px;font-weight:800;letter-spacing:-.5px;white-space:nowrap}
.scr-RouteGuide .rg-taxi__col span{font-size:12px;opacity:.78}
.scr-RouteGuide .rg-taxi__line{flex:none;width:1px;height:40px;background:rgba(255,255,255,.22)}
.scr-RouteGuide .rg-taxi__actions{flex:none;display:flex;gap:8px}
.scr-RouteGuide .rg-taxi__call{flex:1;min-width:0;gap:6px;color:#191600;background:#FEE500;border:0}
.scr-RouteGuide .rg-taxi__call:hover{background:#F5DC00}
.scr-RouteGuide .rg-taxi__copy{flex:none;padding:0 14px}
.scr-RouteGuide .rg-taxi__tip{flex:none;display:flex;align-items:center;justify-content:center;gap:6px;margin:0;padding:10px;border-radius:12px;font-size:13px;font-weight:600;color:var(--coral-ink);background:var(--coral-soft)}
.scr-RouteGuide .rg-status{display:flex;align-items:center;gap:12px;min-height:52px}
.scr-RouteGuide .rg-eta{display:flex;flex-direction:column;justify-content:center;min-width:64px}
.scr-RouteGuide .rg-eta b{font-size:20px;line-height:24px}
.scr-RouteGuide .rg-eta small{font-size:12px;color:var(--ink-muted)}
.scr-RouteGuide .seg button{font-size:12px;padding:0 2px;white-space:nowrap;gap:2px}
.scr-RouteGuide .seg button .ic{width:14px;height:14px}
</style>
