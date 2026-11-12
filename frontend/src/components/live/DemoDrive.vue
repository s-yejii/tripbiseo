<script setup>
// 시연 계정 "다음 장소로" (③, 11/08 주형이 다시 정함): 발표자가 이 작은 버튼을 누를 때만 다음 장소로 가요.
//   자동으로 하는 건 두 가지뿐이에요. ① 가짜 GPS가 실제 길(상황 응답의 다음 경로선)을 따라 움직이고 ② 시계가 이동 시간만큼 빨리 흘러요.
//   머무는 동안 시계는 실제 시간대로 흘러요. 발표자가 설정의 [+10분]으로 감으면 늦음 알림·대안이 다른 계정과 똑같이 떠요.
//   개발자 모드(시연 계정)이고 가짜 GPS가 켜져 있을 때만 보여요. 다른 계정은 이 컴포넌트가 아무것도 안 해요.
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showLiveNotice } from '@/utils/liveBanner'
import { getDevScenario, setDevClock, setDevSpeed } from '@/api/dev'
import { getTrip } from '@/api/trips'
import { useReplanStore } from '@/stores/replan'
import { addDays } from '@/utils/dates'
import { appNow, clearCommand, devMode, ensureDemoFlow, syncClock } from '@/utils/devMode'
import { toMinutes, toTime } from '@/utils/time'
import { showToast } from '@/utils/toast'
import { demoRoute } from '@/utils/demoRoute'
import { incidentClockMin, isBoardingPlace, lateMinutesFor, planDeparture } from '@/utils/departure'

const props = defineProps({
  live: { type: Object, required: true }, // useLiveTrip() 결과
})

const STAY_SPEED = 1 // 머무는 동안 시계는 실제 시간대로 (11/08 주형: 빨라지는 건 버튼으로 이동할 때만, 더 감으려면 설정의 +10분)
const ALERT_SHOW_MS = 2500 // 다음 날 아침 안내를 보여 줄 시간
const MOVE_MS = 6000 // 이동하는 모습을 보여 주는 실제 시간 (시계는 이 동안 이동 시간만큼 흐름)
const MOVE_STEPS = 12
const FOLLOW_START_MS = 1000 // 출발 전 지도 전환(내 위치로 다가가며 확대)을 기다리는 시간 (11/09 ①)
const LEFT_STEP = 5 // 경로의 4/12·5/12 자리를 서버에 보내 "떠났다"를 알림 (출발 판정은 장소에서 200m 밖 점 두 개)
const WANDER_MS = 4000 // 머무는 동안 위치가 조금씩 흔들리는 간격 (화면만, 서버에는 안 보냄)
const WANDER_PARK_M = 50 // 공원·한강은 넓게
const WANDER_PLACE_M = 10 // 가게·건물 안은 조금만
const METERS_PER_DEGREE = 111000
const WAIT_MIN_PER_KM = 3 // 경로를 못 받을 때 이동 분 어림 (1km에 3분 + 기다림)
const WAIT_EXTRA_MIN = 8
const MORNING_LEAD_MIN = 15
const NOTICE_MS = 2000 // 출발 안내를 보여 주고 움직이기 시작할 때까지
const NEARBY_M = 200 // 이만큼 가까우면(같은 건물·바로 옆) 길 이동을 안 보여 주고 걸어서 몇 분
const INSIDE_WALK_MIN = 2 // 다음 날 아침: 첫 장소로 떠날 시각 몇 분 전에 일어난 걸로

const isBusy = ref(false)
const replan = useReplanStore()
const router = useRouter()
const MUTE_AGAIN_MIN = 10 // 여행 중 화면 MUTE_AGAIN_MIN과 같음
const TIGHT_SLACK_MIN = 5 // 예약까지 여유가 이 이하면 돌발 (서버 "지금 나가지 않으면 늦어요"와 같은 기준)
const origin = ref(null) // 시나리오 출발지 (가마산로 242)
const scenario = ref(null) // 시연 시나리오 (조금 더 머무는 곳 등)
let wanderTimer = null

const trip = computed(() => props.live.trip.value)
const isShown = computed(() => devMode.isOn && devMode.isFakeGps && !!trip.value && props.live.phase.value !== 'none' && props.live.phase.value !== 'loading')

const wait = (ms) => new Promise((done) => setTimeout(done, ms))
const isVisited = (stop) => !!stop.arrivedAt || stop.state === 'done'
const dateOf = (day) => addDays(trip.value.startDate, day - 1)
function dateText(date) {
  return date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0') + '-' + String(date.getDate()).padStart(2, '0')
}
const hhmm = (date) => toTime(date.getHours() * 60 + date.getMinutes()) // 알림에 붙일 시각 "18:15"
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
  if (isNextDay) await startNextMorning(current, next)

  // 같은 건물·바로 옆(200m 안)은 길 이동 없이 걸어서 몇 분 (11/08 주형: 더현대 → 지하 1층 테디뵈르)
  const from = props.live.position.value || (current ? { lat: current.lat, lng: current.lng } : origin.value)
  if (current && !isNextDay && distanceMeters(current, next) < NEARBY_M) return walkInside(current, next)

  // 이동 시간: 실제 길찾기(상황 응답의 다음 경로), 없으면 거리로 어림
  await props.live.refresh()
  const status = props.live.status.value
  const route = status && status.next && status.next.id === next.id ? status.next : null
  // 이동 시간: 여행 중 화면이 보여 주는 예상 도착(서버 계산)과 같게 → 늦음 판단·카드·도착 시각이 한 숫자 (11/09: 길찾기 두 번 결과가 달라 늦음 카드 없이 떠나던 문제)
  const nextStatus = status && status.stops ? status.stops.find((stop) => stop.id === next.id) : null
  const etaTravel = nextStatus && nextStatus.eta && !isNextDay ? toMinutes(nextStatus.eta) - Math.max(minutesOf(appNow()), current ? stayEndOf(current) : 0) : 0
  const travelMin = Math.max(1, etaTravel > 0 ? etaTravel : route ? route.minutes : guessMinutes(from, next))

  // 시나리오 돌발: 예약 바로 앞 장소에서 처음 누르면 더 머문 걸로(호텔에서 쉬다가 · 팀장님 배탈) 시계를 옮기고 카드부터
  if (await startIncident(current, next, status)) return

  // 떠나는 시각: 예약(고정)이면 그 시각에 맞춰, 느슨한 곳이면 지금 장소에서 머무는 시간이 끝나면 (계획보다 밀려도 괜찮음)
  const departDate = isNextDay ? dateOf(next.day) : dateText(appNow())
  const plan = planDeparture({
    nowMin: minutesOf(appNow()),
    stayEndMin: current ? stayEndOf(current) : null,
    extraMin: current ? extraStayOf(current) : 0,
    travelMin,
    nextTimeMin: toMinutes(next.time),
    isNextFixed: !!next.fixed,
    isNextDay,
  })
  if (await isBlocked(plan, status)) return // 예약까지 여유 5분 이하·늦음이면 출발하지 않고 카드부터 (대안을 고른 뒤 이동)
  const departMin = plan.departMin
  if (departMin > minutesOf(appNow())) await setClock(departDate, departMin)
  await showNotice(next, travelMin)

  // 이동: 경로선을 따라 움직이고, 그동안 시계는 이동 시간만큼 흐름
  const path = route && route.coords && route.coords.length > 1 ? route.coords : [[from.lat, from.lng], [next.lat, next.lng]]
  demoRoute.value = path // 이동하는 동안 지도의 남색 점선도 이 길로 고정 (11/09 ①: 중간에 다시 받은 길과 어긋나지 않게)
  await wait(FOLLOW_START_MS) // 지도가 내 위치로 다가가며 확대하는 동안 점은 출발점에서 기다림 (11/09 ①)
  // 떠나는 그 시각에 서버에 "떠났다"를 먼저 알림 (점 두 개가 칸 밖이어야 출발로 봄) → 늦게 떠났으면 서버가 뒤 일정을 밀거나 줄임
  for (let step = 1; step <= LEFT_STEP; step++) {
    const [lat, lng] = pointAlong(path, step / MOVE_STEPS)
    if (step >= LEFT_STEP - 1) await props.live.placeAt(lat, lng, false) // 마지막 두 걸음은 서버에도 (장소에서 200m 밖)
    else props.live.showAt(lat, lng)
    await wait(MOVE_MS / MOVE_STEPS) // 첫 걸음들도 같은 간격으로 (기다림 없이 찍혀 점이 순간이동하던 문제, 11/09 ①)
  }
  // 떠난 뒤 다시 계산해서 예약에 늦게 되면 그 자리에서 멈춤 (시계도 멈춤) → 카드에서 고른 뒤 다시 [다음 장소로] (11/09 주형: 시간도 공간도 stop)
  const after = props.live.status.value
  const isNowTight = next.fixed && after && after.fixed && after.fixed.slackMin <= TIGHT_SLACK_MIN && !replan.isMutedFor(props.live.tripId.value, after.fixed.name, Math.max(0, -after.fixed.slackMin))
  if (isIncident(after) || isNowTight) {
    blockedKey = after.alert ? after.alert.key : blockedKey
    demoRoute.value = null // 멈추면 서버가 지금 자리에서 다시 잰 길을 보여 줌
    await setSpeed(0)
    return
  }
  await setSpeed(Math.max(1, Math.round((travelMin * 60 * 1000) / MOVE_MS)))
  for (let step = LEFT_STEP + 1; step < MOVE_STEPS; step++) {
    const [lat, lng] = pointAlong(path, step / MOVE_STEPS)
    props.live.showAt(lat, lng)
    await wait(MOVE_MS / MOVE_STEPS)
  }
  await arrive(next, departDate, departMin + travelMin)
}

// 출발 전 확인 (11/08 주형: 대안을 고른 다음에 움직여야지): 예약까지 여유 5분 이하·늦음 알림(late-)이면 시계를 멈추고 출발 안 함
//   → 가운데 카드에서 대안을 고르면(안 늦게 되면) 다음에 누를 때 출발. "나중에"·"그냥 갈게요" 뒤 한 번 더 누르면 그대로 출발
// 돌발(가운데 카드가 뜨는 늦음 알림)인지. 대안을 골랐거나 "그냥 갈게요"면 10분 더 늦기 전까지는 아님 (여행 중 화면과 같은 기준)
function isIncident(status) {
  if (!status || !status.alert || !status.fixed || !/^late-/.test(status.alert.key || '')) return false
  const lateMin = Math.max(0, -status.fixed.slackMin)
  return !replan.isMutedFor(props.live.tripId.value, status.fixed.name, lateMin)
}
// 출발 안내와 같은 계산(planDeparture)으로 여유 5분 이하면 막음. 대안을 골랐거나 그냥 가기로 한 늦음(10분 더 늦기 전), 카드에서 [나중에] 뒤 다시 누름은 통과
async function isBlocked(plan, status) {
  // 늦음(가운데 카드)인데 아직 대안도 "그냥 갈게요"도 안 골랐으면 [나중에] 뒤라도 떠나지 않음 → 늦음 화면으로
  //   (11/09 주형: 고르기 전에 떠나면 대안이 호텔이 아니라 가는 길 중간에서 시작했음. 시간도 공간도 멈춤)
  if (isIncident(status)) {
    await setSpeed(0)
    showToast('대안을 고르거나 [그냥 갈게요]를 눌러 주세요')
    router.push('/live/delay')
    return true
  }
  if (!plan.isTight) return false
  const fixedName = status && status.fixed ? status.fixed.name : ''
  if (replan.isMutedFor(props.live.tripId.value, fixedName, Math.max(0, -plan.slack))) return false
  const key = status && status.alert && /^late-/.test(status.alert.key || '') ? status.alert.key : 'tight-' + plan.departMin
  if (blockedKey === key) return false
  blockedKey = key
  await setSpeed(0)
  return true
}
// 시나리오에서 계획보다 조금 더 머무는 분 (사람들이 실제로 그러듯, 서버 DemoScenario EXTRA_STAYS)
function extraStayOf(stop) {
  const list = (scenario.value && scenario.value.extraStays) || []
  const found = list.find((item) => item.day === stop.day && item.name === stop.name)
  return found ? found.stay : 0
}
// 지금 칸에서 머무는 시간이 끝나는 분 (예약·숙소는 일찍 와도 계획 시각부터, 여행 중 화면·서버와 같은 기준)
function stayEndOf(stop) {
  const fresh = (props.live.trip.value.stops || []).find((item) => item.id === stop.id) || stop
  const arrived = fresh.arrivedAt ? toMinutes(fresh.arrivedAt.slice(11, 16)) : toMinutes(fresh.time)
  const isFromPlan = fresh.fixed || /숙소|호텔|숙박|모텔|게스트하우스/.test(fresh.category || '')
  return (isFromPlan ? Math.max(arrived, toMinutes(fresh.time)) : arrived) + (fresh.stay || 0)
}

// 출발 안내를 먼저 보여 주고 잠깐 기다림 (예약이면 "지금 출발해야 해요", 느슨하면 부드럽게)
async function showNotice(next, travelMin) {
  const move = '이동 ' + travelMin + '분'
  showLiveNotice(next.fixed
    ? { key: 'drive-' + next.id, at: hhmm(appNow()), title: '지금 출발해야 해요', body: next.name + ' ' + next.time + ' 예약 · ' + move, warn: true }
    : { key: 'drive-' + next.id, at: hhmm(appNow()), title: '다음 ' + next.name + '(으)로 이동해요', body: move, warn: false })
  await wait(NOTICE_MS)
}

// 도착: 시계를 정확히 도착 시각으로, 다시 실제 시간으로
async function arrive(next, date, minutes) {
  demoRoute.value = null // 도착하면 고정한 길을 풀어요
  await setSpeed(STAY_SPEED)
  await setClock(date, minutes)
  await props.live.placeAt(next.lat, next.lng, true)
  await props.live.refresh()
}

// 같은 건물·바로 옆: 경로 이동 없이 몇 분 뒤 바로 도착
async function walkInside(current, next) {
  const departMin = Math.max(minutesOf(appNow()), stayEndOf(current) + extraStayOf(current))
  const date = dateText(appNow())
  await setClock(date, departMin)
  const isSameBuilding = (next.memo || '').includes('같은 건물') // 일정 메모에 적힌 대로 (예: 테디뵈르 "같은 건물 지하 1층")
  const title = isSameBuilding ? next.memo.split('·')[0].trim() + '이에요' : '바로 옆이에요'
  showLiveNotice({ key: 'drive-' + next.id, at: hhmm(appNow()), title, body: next.name + ' · 걸어서 ' + INSIDE_WALK_MIN + '분', warn: false })
  await wait(NOTICE_MS)
  await arrive(next, date, departMin + INSIDE_WALK_MIN)
}

function distanceMeters(a, b) {
  return Math.hypot(a.lat - b.lat, (a.lng - b.lng) * Math.cos((a.lat * Math.PI) / 180)) * METERS_PER_DEGREE
}

// 숙소에서 다음 날로: 밤을 건너뛰어 다음 날 아침, 첫 장소로 떠나기 조금 전으로 (11/08: 바로 떠날 시각으로 감으면
//   서버가 계산한 이동 시간과 어림값이 달라서 "출발 시각이 4분 지났어요"가 잠깐 보이던 문제). 그다음은 같은 날 이동과 같음
async function startNextMorning(current, next) {
  const guess = guessMinutes(current, next)
  await setClock(dateOf(next.day), toMinutes(next.time) - guess - MORNING_LEAD_MIN)
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
  scenario.value = setup
  if (devMode.speed > 1) await setSpeed(STAY_SPEED).catch(() => {}) // 멈춰 있으면(돌발) 그대로 둠
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

// 돌발: 예약(회식·서울역)까지 여유가 5분 이하가 되거나 늦게 되는 순간 시연 시계를 멈춤 → 발표자가 알림을 눌러 대안을 보고 고를 시간 (시연 계정만)
//   대안을 적용하고 여행 중 지도로 돌아오면(안 늦게 되면) 다시 실제 시간으로 흐름. 늦음 알림·대안 자체는 다른 계정과 같은 코드
let pausedFor = null // 멈춘 고정 일정 이름
let blockedKey = '' // 출발을 한 번 막은(카드를 띄운) 알림 key
watch(() => props.live.status.value, async (status) => {
  if (!devMode.isOn || isBusy.value || !status || !status.fixed) return
  // 늦기 직전("지금 나가지 않으면 늦어요 · 여유 N분")이나 늦은 순간 = 가운데 카드가 뜨는 알림(late-)일 때 멈춤
  //   (11/08 주형 ①: 늦기 전에 멈춰야 지하철·택시 대안이 둘 다 안 늦게 나와요)
  const isLate = isIncident(status)
  if (isLate && pausedFor !== status.fixed.name && devMode.speed > 0) {
    pausedFor = status.fixed.name
    blockedKey = status.alert.key // 카드가 이미 떴으니 "나중에" 뒤 [다음 장소로]는 바로 출발
    await setSpeed(0).catch(() => {})
  } else if (!isLate && devMode.speed === 0) { // 대안을 적용하고 지도로 돌아오면 다시 흐름 (대안·바뀐 일정·카톡 공유 동안은 멈춰 있어서 도착 시각이 화면과 같음)
    pausedFor = null
    await setSpeed(STAY_SPEED).catch(() => {})
  }
})

// 설정의 [늦게 만들기]: 지금 장소에서 다음 예약까지 원래 수단으로 가면 LATE_TARGET_MIN분 늦는 시각으로 시계를 옮김
//   (여유 N분 = 예약 시각 − (떠나는 시각 + 이동). 떠나는 시각을 여유 + 12분만큼 늦추면 12분 늦음)
//   11/09: KTX(타는 곳)는 출발 시각을 2분 넘기게 = 놓침, 회식 같은 예약은 12분 (utils/departure.js lateMinutesFor)
// 2일차 KTX 돌발 시각 (11/09 ① 상재: 대본 숫자). 택시 23분이면 18:45 도착·여유 15분, 지하철 40분이면 19:02 도착·KTX 놓침
//   이동 분은 서버 DemoRoutes(타임스퀘어 → 서울역)와 같은 값이에요
const TRAIN_INCIDENT_CLOCK = '18:22'
async function makeLate(status, current) {
  const base = Math.max(minutesOf(appNow()), stayEndOf(current))
  const clock = isBoardingPlace(status.fixed.name) ? toMinutes(TRAIN_INCIDENT_CLOCK) : incidentClockMin(base, status.fixed.slackMin, lateMinutesFor(status.fixed.name))
  await setClock(dateText(appNow()), clock)
  await props.live.refresh()
}
watch(() => devMode.command, async (command) => {
  if (!command || command.type !== 'late' || !props.live.tripId.value) return
  clearCommand()
  await props.live.refresh()
  const status = props.live.status.value
  const current = currentStop()
  if (!status || !status.fixed || !current) return showToast('예약 전 장소에서 눌러 주세요')
  if (status.fixed.id) markIncidentDone(status.fixed.id)
  await makeLate(status, current)
})

// 시나리오 돌발 (11/09 주형: 대안을 안 고르고 그대로 가면 확실히 늦어야 함)
//   예약(회식·서울역) 바로 앞 장소에서 [다음 장소로]를 처음 누르면, 그 자리에서 더 머문 걸로 LATE_TARGET_MIN분 늦는 시각까지 시계를 옮김
//   → 가운데 늦음 카드가 뜨고 시계가 멈춤. 대안을 고르면 그 수단으로, "그냥 갈게요"면 원래 수단으로 늦게 도착
//   예약마다 한 번만 (이미 [늦게 만들기]를 눌렀거나 이미 늦었으면 그대로)
const INCIDENT_STORE_KEY = 'demo-incident-done'
function readIncidents() {
  try { return JSON.parse(sessionStorage.getItem(INCIDENT_STORE_KEY) || '[]') } catch (e) { return [] }
}
function isIncidentDone(stopId) {
  return readIncidents().includes(stopId)
}
function markIncidentDone(stopId) {
  const list = readIncidents()
  if (!list.includes(stopId)) list.push(stopId)
  try { sessionStorage.setItem(INCIDENT_STORE_KEY, JSON.stringify(list)) } catch (e) { /* 저장 못 해도 진행 */ }
}
async function startIncident(current, next, status) {
  if (!current || !next.fixed || next.day !== current.day || isIncidentDone(next.id)) return false
  markIncidentDone(next.id)
  if (!status || !status.fixed || status.fixed.id !== next.id || status.fixed.slackMin <= 0) return false // 이미 늦었으면 카드가 이미 떠 있음
  await makeLate(status, current)
  return true
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
