<script setup>
// 시연 계정 "다음 장소로" (③, 11/08 주형이 다시 정함): 발표자가 이 작은 버튼을 누를 때만 다음 장소로 가요.
//   자동으로 하는 건 두 가지뿐이에요. ① 가짜 GPS가 실제 길(상황 응답의 다음 경로선)을 따라 움직이고 ② 시계가 이동 시간만큼 빨리 흘러요.
//   머무는 동안 시계는 실제 시간대로 흘러요. 발표자가 설정의 [+10분]으로 감으면 늦음 알림·대안이 다른 계정과 똑같이 떠요.
//   개발자 모드(시연 계정)이고 가짜 GPS가 켜져 있을 때만 보여요. 다른 계정은 이 컴포넌트가 아무것도 안 해요.
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { getDevScenario, setDevClock, setDevSpeed } from '@/api/dev'
import { getTrip } from '@/api/trips'
import { addDays } from '@/utils/dates'
import { appNow, devMode, ensureDemoFlow, syncClock } from '@/utils/devMode'
import { toMinutes, toTime } from '@/utils/time'
import { showToast } from '@/utils/toast'

const props = defineProps({
  live: { type: Object, required: true }, // useLiveTrip() 결과
})

const STAY_SPEED = 1 // 머무는 동안 시계는 실제 시간대로 (11/08 주형: 빨라지는 건 버튼으로 이동할 때만, 더 감으려면 설정의 +10분)
const LEAVE_LEAD_MIN = 5 // 나갈 시각 몇 분 전으로 감을지: "곧 출발해야 해요" 알림이 먼저 뜨게
const ALERT_SHOW_MS = 2500 // 출발 알림을 보여 주고 움직이기 시작할 때까지
const MOVE_MS = 6000 // 이동하는 모습을 보여 주는 실제 시간 (시계는 이 동안 이동 시간만큼 흐름)
const MOVE_STEPS = 12
const LEFT_STEP = 2 // 몇 번째 걸음에서 서버에 위치를 보내 "떠났다"를 알릴지
const WANDER_MS = 4000 // 머무는 동안 위치가 조금씩 흔들리는 간격 (화면만, 서버에는 안 보냄)
const WANDER_PARK_M = 50 // 공원·한강은 넓게
const WANDER_PLACE_M = 10 // 가게·건물 안은 조금만
const METERS_PER_DEGREE = 111000
const WAIT_MIN_PER_KM = 3 // 경로를 못 받을 때 이동 분 어림 (1km에 3분 + 기다림)
const WAIT_EXTRA_MIN = 8

const isBusy = ref(false)
const origin = ref(null) // 시나리오 출발지 (가마산로 242)
let wanderTimer = null

const trip = computed(() => props.live.trip.value)
const isShown = computed(() => devMode.isOn && devMode.isFakeGps && !!trip.value && props.live.phase.value !== 'none' && props.live.phase.value !== 'loading')

const wait = (ms) => new Promise((done) => setTimeout(done, ms))
const isVisited = (stop) => !!stop.arrivedAt || stop.state === 'done'
const dateOf = (day) => addDays(trip.value.startDate, day - 1)
function dateText(date) {
  return date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0') + '-' + String(date.getDate()).padStart(2, '0')
}
function minutesOf(date) {
  return date.getHours() * 60 + date.getMinutes()
}
// 빠진 칸을 뺀 일정 (날짜·시각 순)
function orderedStops() {
  return trip.value.stops.filter((stop) => stop.state !== 'skipped').sort((a, b) => a.day - b.day || a.time.localeCompare(b.time))
}
// 지금 있는 칸: 마지막으로 도착한 칸 (없으면 아직 출발지)
function currentStop() {
  let current = null
  for (const stop of orderedStops()) if (isVisited(stop)) current = stop
  return current
}

async function setClock(date, minutes) {
  syncClock(await setDevClock(date, toTime(minutes)))
}
async function setSpeed(speed) {
  syncClock(await setDevSpeed(speed))
}

// 경로선 위 rate(0~1) 지점 (선 길이 비율로)
function pointAlong(path, rate) {
  const lengths = [0]
  for (let i = 1; i < path.length; i++) {
    lengths.push(lengths[i - 1] + Math.hypot(path[i][0] - path[i - 1][0], path[i][1] - path[i - 1][1]))
  }
  const target = lengths[lengths.length - 1] * rate
  let i = 1
  while (i < path.length - 1 && lengths[i] < target) i++
  const part = lengths[i] - lengths[i - 1] || 1
  const ratio = (target - lengths[i - 1]) / part
  return [path[i - 1][0] + (path[i][0] - path[i - 1][0]) * ratio, path[i - 1][1] + (path[i][1] - path[i - 1][1]) * ratio]
}
// 경로를 못 받을 때 이동 분 어림 (직선 거리)
function guessMinutes(from, to) {
  const km = (Math.hypot(from.lat - to.lat, (from.lng - to.lng) * Math.cos((from.lat * Math.PI) / 180)) * METERS_PER_DEGREE) / 1000
  return Math.round(km * WAIT_MIN_PER_KM + WAIT_EXTRA_MIN)
}

// "다음 장소로"
async function goNext() {
  if (isBusy.value) return
  isBusy.value = true
  try {
    await moveToNext()
  } catch (e) {
    showToast(e.message)
  } finally {
    isBusy.value = false
  }
}

async function moveToNext() {
  const list = orderedStops()
  const current = currentStop()
  const next = list[current ? list.indexOf(current) + 1 : 0]
  if (!next) return showToast('마지막 장소예요')
  const isNextDay = !!current && next.day !== current.day
  if (current && !isNextDay) await showLeaveAlert(current)

  // 이동 시간: 실제 길찾기(상황 응답의 다음 경로), 다음 날이면 거리로 어림
  await props.live.refresh()
  const status = props.live.status.value
  const route = !isNextDay && status && status.next && status.next.id === next.id ? status.next : null
  const from = props.live.position.value || (current ? { lat: current.lat, lng: current.lng } : origin.value)
  const travelMin = Math.max(1, route ? route.minutes : guessMinutes(from, next))
  // 떠나는 시각: 다음 장소 계획 시각에 맞춰 도착하게 (그보다 늦었으면 지금 바로 → 늦게 도착). 그때까지는 시계를 감음
  const nowDate = dateText(appNow())
  const planDepart = toMinutes(next.time) - travelMin
  const isLateNow = !isNextDay && nowDate === dateOf(next.day) && minutesOf(appNow()) >= planDepart
  const departDate = isLateNow ? nowDate : dateOf(next.day)
  const departMin = isLateNow ? minutesOf(appNow()) : planDepart
  if (!isLateNow) await setClock(departDate, departMin)

  // 이동: 경로선을 따라 움직이고, 그동안 시계는 이동 시간만큼 흐름
  const path = route && route.coords && route.coords.length > 1 ? route.coords : [[from.lat, from.lng], [next.lat, next.lng]]
  await setSpeed(Math.max(1, Math.round((travelMin * 60 * 1000) / MOVE_MS)))
  for (let step = 1; step < MOVE_STEPS; step++) {
    const [lat, lng] = pointAlong(path, step / MOVE_STEPS)
    if (step === LEFT_STEP) await props.live.placeAt(lat, lng, false) // 서버에도 "떠났다" → 화면이 이동 중으로
    else props.live.showAt(lat, lng)
    await wait(MOVE_MS / MOVE_STEPS)
  }

  // 도착: 시계를 정확히 도착 시각으로, 다시 천천히
  await setSpeed(STAY_SPEED)
  await setClock(departDate, departMin + travelMin)
  await props.live.placeAt(next.lat, next.lng, true)
  await props.live.refresh()
}

// 나갈 시각 몇 분 전으로 감아서 "곧 출발해야 해요"가 먼저 뜨게 (이미 그 뒤면 시계를 되돌리지 않음)
async function showLeaveAlert(current) {
  const now = appNow()
  const fresh = (props.live.trip.value.stops || []).find((stop) => stop.id === current.id) || current
  const arrived = fresh.arrivedAt ? toMinutes(fresh.arrivedAt.slice(11, 16)) : toMinutes(fresh.time)
  const isFromPlan = fresh.fixed || /숙소|호텔|숙박|모텔|게스트하우스/.test(fresh.category || '') // 여행 중 화면·서버와 같은 기준
  const start = isFromPlan ? Math.max(arrived, toMinutes(fresh.time)) : arrived
  const alertMin = start + (fresh.stay || 0) - LEAVE_LEAD_MIN
  const isSameDay = dateText(now) === dateOf(current.day)
  if (isSameDay && minutesOf(now) >= alertMin) return
  await setClock(dateOf(current.day), alertMin)
  await props.live.refresh()
  await wait(ALERT_SHOW_MS)
}

// 머무는 동안: 그 자리에서 조금씩 움직이는 모습 (화면만)
function wander() {
  if (!isShown.value || isBusy.value) return
  const stop = currentStop()
  const center = stop || origin.value
  if (!center) return
  const isWide = stop && /한강|공원|숲|산책/.test(stop.name + ' ' + (stop.category || ''))
  const meters = isWide ? WANDER_PARK_M : WANDER_PLACE_M
  const angle = Math.random() * Math.PI * 2
  const degree = (meters * Math.random()) / METERS_PER_DEGREE
  props.live.showAt(center.lat + Math.sin(angle) * degree, center.lng + Math.cos(angle) * degree)
}

// 여행 중 화면에 들어오면: 가짜 GPS 켜기, 시계가 멈춰 있거나 1배면 천천히 흐르게, 위치가 없으면 지금 칸(또는 출발지)에 두기
async function setUp() {
  if (!devMode.isOn) return
  ensureDemoFlow()
  const setup = await getDevScenario().catch(() => null)
  if (setup) origin.value = { lat: setup.originLat, lng: setup.originLng }
  if (devMode.speed !== STAY_SPEED) await setSpeed(STAY_SPEED).catch(() => {})
  for (let i = 0; i < 20 && !props.live.tripId.value; i++) await wait(500) // 진행 중인 여행을 찾을 때까지
  if (!props.live.tripId.value || props.live.position.value) return
  // 여행 중 화면은 첫 위치가 있어야 여행을 읽어요 → 여행을 직접 읽어 지금 칸(없으면 출발지)에 둠
  const saved = await getTrip(props.live.tripId.value).catch(() => null)
  let place = origin.value
  if (saved) {
    for (const stop of saved.stops) if (isVisited(stop) && stop.state !== 'skipped') place = stop
  }
  if (place && !props.live.position.value) await props.live.placeAt(place.lat, place.lng, false)
}

onMounted(() => {
  setUp()
  wanderTimer = setInterval(wander, WANDER_MS)
})
onUnmounted(() => clearInterval(wanderTimer))
</script>

<template>
  <button v-if="isShown" type="button" class="demo-next" :disabled="isBusy" @click="goNext">
    <i class="ic ic--s i-next" aria-hidden="true"></i>{{ isBusy ? '이동 중' : '다음 장소로' }}
  </button>
</template>

<style>
.demo-next { position: absolute; right: 16px; top: 124px; z-index: 7; display: inline-flex; align-items: center; gap: 4px; height: 34px; padding: 0 12px 0 10px; border: 0; border-radius: 17px; background: #fff; color: var(--navy); font-size: 13px; font-weight: 700; box-shadow: 0 2px 8px rgba(24, 33, 58, 0.16); cursor: pointer; }
.demo-next:disabled { opacity: 0.7; cursor: default; }
</style>
