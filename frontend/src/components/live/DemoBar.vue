<script setup>
// 시연 엔진 (③, 11/06 → 11/08 화면에서 띠를 뺌): 개발자 모드(시연 계정)에서만, 여행 중 화면에 보이는 것은 없어요.
// 11/08 주형: "티 나면 안 됨" → 가짜 GPS가 시연 시계(빠르게 도는 시계)에 맞춰 일정 자리로 움직여서 여행이 저절로 흘러가요.
//   돌발 ① 회식 5분 전 출발 알림 · ③ 서울역 앞 칸에서 늦게까지 → 서울역 늦음, 은 그 시각이 되면 진짜 알림으로 떠요 (날씨는 돌발 아님: 일정 짤 때 반영, 11/08)
//   조작(배속·멈춤·장면 바로 가기·다음/이전 장소·처음부터)은 반투명 점(DevDot)의 설정에서 → devMode.command로 받음
// 장면 버튼 하나로 시연 시계·가짜 위치·비 예보를 한 번에 맞춰서, 실제 사용자가 그 순간 보는 화면(알림·대안)을 바로 보여 줘요.
//   1일차 출발 → ① 회식 출발 알림 → 숙소(밤 건너뛰기) → ② 2일차 아침 비 → ③ 한강에서 늦음 → 서울역 도착
// 진행 중인 여행이 없으면 "시연 여행 시작": 가장 최근 예정 여행을 복사해서 시작해요 (원본은 본편용으로 그대로)
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { clearDevRain, forwardDevClock, getDevStatus, setDevClock, setDevRain, setDevSpeed } from '@/api/dev'
import { createTrip, generatePlan, getTrip, getTripList, startTrip } from '@/api/trips'
import { getDevScenario } from '@/api/dev'
import { addDays, daysBetween } from '@/utils/dates'
import { isPracticeTrip, PRACTICE_MARK } from '@/utils/practice'
import { appNow, clearCommand, devMode, ensureDemoFlow, setClockOffset, syncClock } from '@/utils/devMode'
import { toMinutes, toTime } from '@/utils/time'
import { showToast } from '@/utils/toast'

const props = defineProps({
  live: { type: Object, required: true }, // useLiveTrip() 결과
})
const emit = defineEmits(['restart'])

const SCENES = [
  { label: '1일차 출발', user: '첫 장소에 닿으면 알아서 "다녀옴"으로 바뀌어요' },
  { label: '① 회식 출발', user: '떠날 시각 5분 전 "5분 뒤 출발해야 해요" 알림이 떠요', isIncident: true },
  { label: '숙소', user: '회식 뒤 숙소까지 가고 밤은 건너뛰어요' },
  { label: '② 아침 비', user: '아침에 "오후 비 소식" 알림 → 대안에서 한강을 먼저', isIncident: true },
  { label: '③ 한강에서 늦음', user: '"한강에서 더 머물렀어요" 알림 · 서울역 늦음 → 대안 보기', isIncident: true },
  { label: '서울역 도착', user: 'KTX를 타면 여행 끝 · 총 지출 → 회고' },
]
const WALK_STEPS = 4 // 장소 사이를 몇 번에 나눠 걸어가는 모습을 보여 줄지
const WALK_STEP_MS = 450
const LEAVE_ALERT_BEFORE_MIN = 5 // ① 떠날 시각 몇 분 전으로 맞출지
const LATE_BEFORE_MIN = 27 // ③ KTX 고정 시각 몇 분 전까지 한강에 남아 있을지 (18:18: 대중교통이면 몇 분 늦고, 택시로 가면 안 늦음)
const RAIN_CHECK_TIME = '09:30'
const LATE_TARGET_MIN = 3 // ③ 대중교통으로 가면 이만큼 늦게 (택시면 안 늦게)
const RAIN_FROM = '13:00'
const SCENE_KEY = 'demoScene'
const SCENE_PAUSE_MS = 4000 // 자동 재생: 장면 사이 사람이 읽을 시간
const INCIDENT_PAUSE_MS = 10000 // 자동 재생: 돌발 장면(알림·대안)은 더 오래 보여 줌

const sceneIndex = ref(readScene()) // 지금까지 끝낸 장면 수 (다음 장면 = 이 번호)
const isBusy = ref(false)
const isAuto = ref(false)
const scenario = ref(null) // 시연 시나리오 (출발지 가마산로 242·시작 15:00, 서버 DemoScenario.java)
let autoTimer = null

const trip = computed(() => props.live.trip.value)
const isOngoing = computed(() => props.live.phase.value !== 'none' && props.live.phase.value !== 'loading' && !!props.live.tripId.value)
const nextScene = computed(() => SCENES[sceneIndex.value] || null)
const lastScene = computed(() => SCENES[sceneIndex.value - 1] || null)

function readScene() {
  try {
    return Number(localStorage.getItem(SCENE_KEY)) || 0
  } catch (e) {
    return 0
  }
}
function saveScene(index) {
  sceneIndex.value = index
  try {
    localStorage.setItem(SCENE_KEY, String(index))
  } catch (e) {
    // 저장소를 못 쓰면 이번 화면에서만 기억
  }
}

// ------------------------------------------------------------ 일정 칸 고르기
function stopsOf(day) {
  return trip.value.stops.filter((stop) => stop.day === day && stop.state !== 'skipped').sort((a, b) => a.time.localeCompare(b.time))
}
function dateOf(day) {
  return addDays(trip.value.startDate, day - 1)
}
function lastDay() {
  let last = 1
  for (const stop of trip.value.stops) last = Math.max(last, stop.day || 1)
  return last
}
const isOutdoor = (stop) => /한강|공원|숲|산책/.test(stop.name + ' ' + (stop.category || ''))

// ------------------------------------------------------------ 시계·이동
async function setClock(date, minutes) {
  const status = await setDevClock(date, toTime(minutes))
  syncClock(status) // 상태바·홈 시계도 같이 (배속까지)
}

// 앞 위치에서 그 칸까지 걸어가는 모습 → 도착 시각으로 맞추고 도착
async function visit(stop, date) {
  const from = props.live.position.value
  if (from) {
    for (let step = 1; step < WALK_STEPS; step++) {
      const rate = step / WALK_STEPS
      props.live.showAt(from.lat + (stop.lat - from.lat) * rate, from.lng + (stop.lng - from.lng) * rate)
      await wait(WALK_STEP_MS)
    }
  }
  await setClock(date, toMinutes(stop.time))
  await props.live.placeAt(stop.lat, stop.lng, true)
}
const wait = (ms) => new Promise((done) => setTimeout(done, ms))
const isVisited = (stop) => !!stop.arrivedAt || stop.state === 'done'

// 그날 칸 중 아직 안 간 곳을 until 칸 전(또는 포함)까지 차례로
async function visitUntil(day, until, isIncluded) {
  for (const stop of stopsOf(day)) {
    const isLast = stop.id === until.id
    if (isLast && !isIncluded) break
    if (!isVisited(stop)) await visit(stop, dateOf(day))
    if (isLast) break
  }
}

// ------------------------------------------------------------ 장면
const RUNS = [
  async () => { // 1일차 출발
    const first = stopsOf(1)[0]
    await visit(first, dateOf(1))
  },
  async () => { // ① 회식 출발: 고정 일정(회식) 바로 앞 칸에서, 떠날 시각 5분 전
    const day1 = stopsOf(1)
    const dinner = day1.find((stop) => stop.fixed) || day1[day1.length - 1]
    const before = day1[day1.indexOf(dinner) - 1] || day1[0]
    await visitUntil(1, before, true)
    await setClock(dateOf(1), toMinutes(before.time) + (before.stay || 0) - LEAVE_ALERT_BEFORE_MIN)
    await props.live.refresh()
  },
  async () => { // 숙소: 회식 → 숙소, 밤은 건너뜀
    const day1 = stopsOf(1)
    await visitUntil(1, day1[day1.length - 1], true)
  },
  async () => { // ② 2일차 아침 + 13:00 비
    await setClock(dateOf(2), toMinutes(RAIN_CHECK_TIME))
    await setDevRain(RAIN_FROM)
    await props.live.refresh()
  },
  async () => { // ③ 점심 뒤 야외(한강)에서 늦게까지
    await clearDevRain()
    const days = lastDay()
    const list = stopsOf(days)
    const station = list.filter((stop) => stop.fixed).pop() || list[list.length - 1]
    const park = list.filter((stop) => stop !== station && isOutdoor(stop)).pop() || list[list.length - 2]
    await visitUntil(days, park, true)
    await setClock(dateOf(days), toMinutes(station.time) - LATE_BEFORE_MIN)
    await props.live.refresh()
    // 실제 길찾기 시간은 시간대마다 달라서(밤엔 빨라짐) 아직 안 늦으면, 3분쯤 늦는 시각까지 더 머무름
    const fixed = props.live.status.value && props.live.status.value.fixed
    if (fixed && fixed.slackMin != null && fixed.slackMin >= 0) {
      await forwardDevClock(fixed.slackMin + LATE_TARGET_MIN)
      setClockOffset((await getDevStatus()).offsetMinutes)
      await props.live.refresh()
    }
  },
  async () => { // 서울역 도착
    const days = lastDay()
    const list = stopsOf(days)
    const station = list.filter((stop) => stop.fixed).pop() || list[list.length - 1]
    await visitUntil(days, station, true)
  },
]

async function runNext() {
  if (isBusy.value || !nextScene.value || !trip.value) return
  isBusy.value = true
  try {
    await RUNS[sceneIndex.value]()
    saveScene(sceneIndex.value + 1)
  } catch (e) {
    showToast(e.message)
    isAuto.value = false
  } finally {
    isBusy.value = false
  }
  // 자동 재생 (11/08 주형: 안 눌러도 끝까지): 장면마다 읽을 시간만큼 기다렸다가 다음. 돌발 장면은 더 오래
  if (!isAuto.value || !nextScene.value) {
    isAuto.value = false
    return
  }
  const pause = lastScene.value && lastScene.value.isIncident ? INCIDENT_PAUSE_MS : SCENE_PAUSE_MS
  autoTimer = setTimeout(() => {
    if (isAuto.value) runNext()
  }, pause)
}

function playAuto() {
  isAuto.value = true
  runNext()
}

// 멈춤: 지금 장면에서 그대로 (다시 "자동"이나 "다음"으로 이어서)
function stopAuto() {
  isAuto.value = false
  clearTimeout(autoTimer)
}
onUnmounted(() => clearTimeout(autoTimer))

// 시연 중엔 진짜 GPS를 기다리지 않음: 위치 권한이 없거나(발표 PC) 새로 고쳐도 마지막으로 도착한 칸(없으면 첫 칸) 자리로 옮겨 둠
watch(() => props.live.phase.value, async (phase) => {
  if ((phase !== 'gps' && phase !== 'gps-error') || !devMode.isOn || !props.live.tripId.value) return
  try {
    const saved = await getTrip(props.live.tripId.value)
    const arrived = saved.stops.filter((stop) => stop.arrivedAt).sort((a, b) => a.arrivedAt.localeCompare(b.arrivedAt))
    const ordered = saved.stops.slice().sort((a, b) => a.day - b.day || a.time.localeCompare(b.time))
    if (!arrived.length && devMode.isFlow) { // 아직 아무 데도 안 갔으면 출발지(가마산로 242)에 놓고 시작 → 위치 권한이 없어도 화면이 열림 (11/08)
      const setup = scenario.value || (await getDevScenario().catch(() => null))
      if (setup && setup.originLat != null) return props.live.placeAt(setup.originLat, setup.originLng, false)
    }
    const spot = arrived[arrived.length - 1] || ordered[0]
    if (spot) await props.live.placeAt(spot.lat, spot.lng, false)
  } catch (e) {
    // 못 받으면 원래처럼 GPS를 기다림
  }
}, { immediate: true })

// 여행 상세의 "시연 재생"으로 들어오면(?autoplay=1): 여행이 없으면 시작하고, 준비되면 자동 재생
const route = useRoute()
const wantsAutoplay = ref(route.query.autoplay === '1')
onMounted(() => {
  if (!wantsAutoplay.value || !devMode.isOn) return
  saveScene(0)
})
watch(() => props.live.phase.value, async (phase) => {
  if (!wantsAutoplay.value || !devMode.isOn) return
  if (phase === 'none') {
    await startDemoTrip()
  } else if (phase === 'ready' && trip.value) {
    wantsAutoplay.value = false // 11/08: 장면 자동 재생 대신 시계가 흐르며 일정대로 (조작은 설정에서)
  }
})

// ------------------------------------------------------------ 시연 여행 시작 (진행 중인 여행이 없을 때)
async function startDemoTrip() {
  if (isBusy.value) return
  isBusy.value = true
  try {
    const today0 = (await getDevStatus()).now.slice(0, 10)
    if (scenario.value) { // 시연 계정: 시나리오 일정을 오늘부터 새로 만들어 시작 (예전 86 복사 대신, 11/08)
      const draft = await generatePlan({ startDate: today0, endDate: today0, people: 5, companion: '워크숍' })
      const made = await createTrip({ title: draft.title + ' ' + PRACTICE_MARK, startDate: draft.startDate, endDate: draft.endDate, people: draft.people, transports: draft.transports, stops: draft.stops })
      await setClock(made.startDate, toMinutes(scenario.value.startTime))
      await startTrip(made.id)
      saveScene(0)
      emit('restart')
      return
    }
    const planned = (await getTripList()).filter((item) => item.status === 'planned' && !isPracticeTrip(item)).sort((a, b) => b.id - a.id)
    if (!planned.length) throw new Error('복사할 예정 여행이 없어요. 먼저 여행을 하나 저장해 주세요')
    const source = await getTrip(planned[0].id)
    // 복사본은 오늘부터로 옮겨요: 원본 날짜가 지났어도(발표 날) 만들고 시작할 수 있고, 날씨도 오늘 예보로 나와요 (11/07 점검)
    const today = (await getDevStatus()).now.slice(0, 10) // 서버가 보는 오늘 (시연 시계를 맞춰 뒀으면 그 날짜)
    const shift = daysBetween(source.startDate, today)
    const copy = await createTrip({ ...source, id: null, title: source.title + ' ' + PRACTICE_MARK, startDate: today, endDate: addDays(source.endDate, shift), stops: source.stops.map((stop) => ({ ...stop, id: null })) })
    const first = copy.stops.slice().sort((a, b) => a.day - b.day || a.time.localeCompare(b.time))[0]
    await setClock(copy.startDate, toMinutes(first.time) - 10)
    await startTrip(copy.id)
    saveScene(0)
    showToast('"' + source.title + '"을 복사해서 시작했어요 · 원본은 그대로예요')
    emit('restart')
  } catch (e) {
    showToast(e.message)
  } finally {
    isBusy.value = false
  }
}

// ------------------------------------------------------------ 일정대로 흐르기 (11/08): 시연 시각에 맞는 자리로 "나"를 옮김
const FLOW_TICK_MS = 1000
const NIGHT_WAKE_BEFORE_MIN = 30 // 밤을 건너뛰고 다음 날 첫 장소 몇 분 전으로
const TRAVEL_MIN = 20 // ③ 늦게 떠난 뒤 서울역까지 걸리는 시간 (화면에서 움직이는 모습용)
let flowTimer = null
let placedKey = ''
let isPlacing = false
let rainDay = 0
let lastSentAt = 0
const visitedIds = new Set() // 이번 흐름에서 도착 처리한 칸 (장면을 건너뛰어도 앞 칸을 먼저 다녀오게)
const WALK_SEND_MS = 3000 // 이동 중에도 이만큼마다 위치를 서버에 보냄 (떠남 판정·늦음 계산)
const ARRIVE_EARLY_MIN = 10 // 이동은 다음 칸 시각보다 이만큼 일찍 끝나게 (서버의 남은 시간 계산이 늦음으로 안 보이게)
const pausedAt = new Set() // 돌발마다 한 번만 시계를 멈춤 ('leave' · 'rain' · 'late')

// 그날 칸마다 [도착, 떠남] 분. 마지막 날 서울역 앞 야외(한강)는 늦을 때까지 머무름(돌발 ③)
function planOf(day) {
  const list = stopsOf(day)
  const plan = []
  for (const stop of list) plan.push({ stop, arrive: toMinutes(stop.time), leave: toMinutes(stop.time) + (stop.stay || 0) })
  // 돌발 (DEMO.md): 시나리오가 정한 칸(2일차 테디뵈르하우스)에서 계획보다 N분(24분) 더 머묾 → KTX에 늦음
  const setup = scenario.value
  if (setup && setup.overstayDay === day) {
    const item = plan.find((one) => one.stop.name === setup.overstayName)
    if (item) {
      item.plannedLeave = item.leave
      item.leave += setup.overstayMin || 0 // 0이면 "늦어지는 순간까지" (아래 stayUntilLate)
      item.isOverstay = true
    }
  }
  return plan
}

function dateText(date) {
  return date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0') + '-' + String(date.getDate()).padStart(2, '0')
}

async function flowTick() {
  if (!devMode.isFlow || !trip.value || props.live.phase.value === 'none' || isPlacing || isBusy.value) return
  if (!devMode.isAutoSpeed) return manualTick() // 직접 조작: 시계만 흐르고 "다음 장소로"를 누를 때만 움직임
  const now = appNow()
  const day = daysBetween(trip.value.startDate, dateText(now)) + 1
  if (day < 1 || day > lastDay()) return
  const nowMin = now.getHours() * 60 + now.getMinutes()
  const plan = planOf(day)
  if (!plan.length) return
  isPlacing = true
  try {
    await moveForTime(day, plan, nowMin)
  } catch (e) {
    // 한 번 못 하면 다음 틱에 다시
  } finally {
    isPlacing = false
  }
}

// 자동 배속 (11/08 주형: 평가자에게 필요한 부분만 천천히): 도착하고 잠깐은 보통, 오래 머무는 중은 아주 빠르게,
//   이동은 빠르게, 돌발(회식 출발 알림·서울역 늦음) 10분 앞부터는 천천히
const SPEED_SLOW = 15
const SPEED_NORMAL = 60
const SPEED_MOVE = 120
const SPEED_FAST = 480
const SHOW_ARRIVAL_MIN = 8 // 도착하고 이만큼은 보통 속도로 보여 줌
const OVERSTAY_SLOW_AFTER_MIN = 20 // 나갈 시각 뒤 이만큼(늦어지는 순간 포함)은 천천히
const SLOW_BEFORE_MIN = 5 // 돌발 이만큼 앞부터 천천히 (11/08: 10 → 5분, 전체 5분 안쪽으로)
function wantedSpeed(day, plan, nowMin) {
  for (let i = 0; i < plan.length; i++) {
    const item = plan[i]
    const next = plan[i + 1]
    // 돌발 칸: 나갈 시각 5분 전부터, 늦어져서 멈출 때까지 천천히
    if (item.isOverstay && overstayLeftAt == null && nowMin >= item.leave - SLOW_BEFORE_MIN && nowMin < item.leave + OVERSTAY_SLOW_AFTER_MIN) return SPEED_SLOW
    if (nowMin >= item.arrive && nowMin < item.arrive + SHOW_ARRIVAL_MIN) return SPEED_NORMAL
    if (nowMin >= item.arrive && nowMin < item.leave) return SPEED_FAST
  }
  return SPEED_MOVE
}
let lastSpeedSetAt = 0
async function applyAutoSpeed(day, plan, nowMin) {
  if (!devMode.isAutoSpeed || devMode.speed === 0 || Date.now() - lastSpeedSetAt < 1500) return // 멈춤(돌발)은 사람이 재생할 때까지 그대로
  const wanted = wantedSpeed(day, plan, nowMin)
  if (wanted === devMode.speed) return
  lastSpeedSetAt = Date.now()
  syncClock(await setDevSpeed(wanted))
}

async function moveForTime(day, plan, nowMin) {
  await applyAutoSpeed(day, plan, nowMin)
  const last = plan[plan.length - 1]
  if (nowMin >= last.leave && day < lastDay()) { // 밤: 다음 날 아침으로 건너뜀
    const next = planOf(day + 1)[0]
    if (next) {
      await setClock(dateOf(day + 1), next.arrive - NIGHT_WAKE_BEFORE_MIN)
      await props.live.refresh()
    }
    return
  }
  for (let i = 0; i < plan.length; i++) {
    const item = plan[i]
    const next = plan[i + 1]
    // 돌발: 나갈 시각이 지나도 그 자리에 머묾 → 예약(서울역) 도착 목표보다 늦어지는 순간 시계 멈춤 → 재생하면 떠남
    if (item.isOverstay && nowMin >= item.arrive) {
      const result = await stayUntilLate(item, next, nowMin)
      if (result !== 'gone') return result
      continue
    }
    if (nowMin >= item.arrive && nowMin < item.leave) return placeOnce(item.stop)
    const travel = next ? Math.max(5, next.arrive - item.leave - ARRIVE_EARLY_MIN) : 0
    if (next && nowMin >= item.leave && nowMin < Math.max(next.arrive, item.leave + travel)) {
      return walkBetween(item.stop, next.stop, (nowMin - item.leave) / travel)
    }
  }
  if (nowMin >= last.arrive) return placeOnce(last.stop) // 마지막 칸(서울역)에 도착 → 여행 끝
  if (nowMin < plan[0].arrive) return walkFromOrigin(day, plan[0], nowMin) // 첫 장소로 가는 중
}

// 1일차 첫 장소 전: 출발지(가마산로 242)에서 시작 시각(15:00)부터 첫 장소까지 걸어가는 모습 (11/08: 첫 장소에 미리 도착해 있지 않게)
async function walkFromOrigin(day, first, nowMin) {
  const setup = scenario.value
  if (day !== 1 || !setup || setup.originLat == null) return placeOnce(first.stop, false)
  const origin = { id: 'origin', lat: setup.originLat, lng: setup.originLng }
  const start = toMinutes(setup.startTime)
  if (nowMin <= start) return placeOnce(origin, false)
  return walkBetween(origin, first.stop, (nowMin - start) / Math.max(1, first.arrive - start - ARRIVE_EARLY_MIN))
}

let overstayLeftAt = null // 돌발 뒤 재생해서 떠난 앱 시각(분)
const OVERSTAY_TRAVEL_MIN = 25 // 떠난 뒤 서울역까지 움직이는 모습 (지하철 기준)
async function stayUntilLate(item, next, nowMin) {
  if (overstayLeftAt == null) {
    if (nowMin < item.leave) return placeOnce(item.stop)
    await placeOnce(item.stop)
    if (!pausedAt.has('overstay')) {
      await props.live.refresh()
      const fixed = props.live.status.value && props.live.status.value.fixed
      if (fixed && fixed.slackMin != null && fixed.slackMin <= WARN_SLACK_MIN) await pauseFor('overstay')
      return 'stay'
    }
    overstayLeftAt = nowMin // 멈췄다가 재생함 → 지금 떠남
  }
  if (next && nowMin < overstayLeftAt + OVERSTAY_TRAVEL_MIN) return walkBetween(item.stop, next.stop, (nowMin - overstayLeftAt) / OVERSTAY_TRAVEL_MIN)
  if (next) return placeOnce(next.stop) // 서울역 도착 → 여행 끝
  return 'gone'
}

// 돌발 순간: 시계를 멈춰서 알림이 화면에 남게 (발표자가 대안을 보여 주고 설정의 "재생"으로 이어감)
async function pauseFor(name) {
  if (pausedAt.has(name)) return
  pausedAt.add(name)
  syncClock(await setDevSpeed(0))
}

// 머무는 동안에도 조금씩 걸어 다니는 모습 (11/08 주형: 공원이어도 움직이는 게 보여야): 지도 위에만, 서버엔 안 보냄
const WANDER_PARK_M = 70 // 공원·한강은 넓게
const WANDER_PLACE_M = 12 // 가게·건물 안은 조금만
const METERS_PER_DEGREE = 111000
function wander(stop) {
  const radius = isOutdoor(stop) ? WANDER_PARK_M : WANDER_PLACE_M
  const t = Date.now() / 4000 // 4초에 한 바퀴쯤 천천히
  const dLat = (radius * Math.sin(t)) / METERS_PER_DEGREE
  const dLng = (radius * Math.sin(t * 0.7) * Math.cos(t / 3)) / (METERS_PER_DEGREE * Math.cos((stop.lat * Math.PI) / 180))
  props.live.showAt(stop.lat + dLat, stop.lng + dLng)
}

async function placeOnce(stop, isArrive = true) {
  const key = stop.id + (isArrive ? ':in' : ':near')
  if (placedKey === key) {
    if (isArrive) wander(stop)
    return
  }
  placedKey = key
  if (isArrive) await visitEarlier(stop)
  await props.live.placeAt(stop.lat, stop.lng, isArrive)
  if (isArrive) visitedIds.add(stop.id)
  await props.live.refresh()
}

// 장면을 건너뛰어 왔으면 그 앞 칸(앞날 포함)을 차례로 먼저 다녀옴 → 서버가 "아직 안 간 곳"으로 늦음 계산을 하지 않게
async function visitEarlier(target) {
  for (let day = 1; day <= target.day; day++) {
    for (const stop of stopsOf(day)) {
      if (stop.id === target.id) return
      if (visitedIds.has(stop.id) || isVisited(stop)) continue
      await props.live.placeAt(stop.lat, stop.lng, true)
      visitedIds.add(stop.id)
    }
  }
}

// 이동 중: 지도 위 위치는 매번, 서버에는 몇 초마다 보내서 앞 칸 "떠남"과 남은 이동 시간이 맞게
async function walkBetween(from, to, rate) {
  const ratio = Math.min(1, Math.max(0, rate))
  const lat = from.lat + (to.lat - from.lat) * ratio
  const lng = from.lng + (to.lng - from.lng) * ratio
  placedKey = 'walk:' + from.id
  if (Date.now() - lastSentAt < WALK_SEND_MS) return props.live.showAt(lat, lng)
  lastSentAt = Date.now()
  await props.live.placeAt(lat, lng, false)
}

onMounted(async () => {
  ensureDemoFlow()
  flowTimer = setInterval(flowTick, FLOW_TICK_MS)
  if (devMode.isOn) scenario.value = await getDevScenario().catch(() => null)
})
// 여행 중 화면을 떠나면(대안·길 안내 등) 시연 시계를 멈춤: 화면 밖에서 시간만 흘러 일정이 밀리지 않게 (11/08: 대안 보는 사이 22:31까지 흐른 문제)
onUnmounted(() => {
  clearInterval(flowTimer)
  if (devMode.isOn && devMode.isFlow && devMode.speed > 0) {
    devMode.pausedByLeave = true
    setDevSpeed(0).then(syncClock).catch(() => {})
  }
})
onMounted(async () => {
  if (!devMode.pausedByLeave) return
  devMode.pausedByLeave = false
  syncClock(await setDevSpeed(SPEED_NORMAL).catch(() => ({ offsetMinutes: devMode.clockOffsetMs / 60000, speed: devMode.speed })))
})

// ------------------------------------------------------------ 직접 조작 (11/08 주형: 발표자가 회색 점을 누를 때마다 다음 장소로)
const MANUAL_SPEED = 60 // 직접 조작일 때 시계가 흐르는 배속 (화면 시계가 움직이는 게 보이게)
const MOVE_STEPS = 8 // 이동하는 모습을 몇 번에 나눠 보여 줄지
const LEFT_STEP = 2 // 이동 몇 번째 걸음에서 서버에 "떠났다"를 보낼지 (장소 반경 밖)
const MOVE_STEP_MS = 450 // 약 3.6초 동안 이동
const ALERT_SHOW_MS = 2500 // 출발 알림을 보여 주고 움직이기 시작할 때까지 (60배속이라 앱 시계로 약 2~3분)
const LEAVE_LEAD_MIN = 5 // 나갈 시각 몇 분 전으로 감을지 (11/08 주형: 알림이 먼저, GPS는 그 뒤에)
const WARN_SLACK_MIN = 5 // 돌발 장소에서 고정 일정 여유가 이 이하가 되면 멈춤 ("지금 안 나가면 늦어요" 알림과 같은 기준)
const LATE_TRAVEL_MIN = 20 // 돌발 뒤 대안(지하철)으로 서울역까지 걸리는 분 (대략, 실제 대안 값은 화면에)
let overstayArmed = false // 돌발 장소에서 "다음 장소로"를 눌러 나갈 시각이 된 상태 (늦어지면 멈춤)

function orderedStops() {
  return trip.value.stops.filter((stop) => stop.state !== 'skipped').sort((a, b) => a.day - b.day || a.time.localeCompare(b.time))
}
// 지금 있는 칸: 마지막으로 도착한 칸 (없으면 출발지)
function currentStop() {
  const list = orderedStops()
  let current = null
  for (const stop of list) if (visitedIds.has(stop.id) || isVisited(stop)) current = stop
  return current
}

async function manualTick() {
  if (devMode.speed === 1) syncClock(await setDevSpeed(MANUAL_SPEED)) // 여행 중 화면에서는 시계가 흐르게
  if (!overstayArmed || pausedAt.has('overstay')) return
  isPlacing = true
  try {
    await props.live.refresh()
    const fixed = props.live.status.value && props.live.status.value.fixed
    if (fixed && fixed.slackMin != null && fixed.slackMin <= WARN_SLACK_MIN) await pauseFor('overstay') // 돌발: 늦기 전에 먼저 경고하는 순간
  } finally {
    isPlacing = false
  }
}

// "다음 장소로": 지금 칸을 떠날 시각으로 감기(출발 알림이 제때 뜨게) → 몇 초 동안 걸어가는 모습 → 다음 칸 시각에 도착
// 고정 일정(서울역)까지 지금 걸리는 분: 화면이 보여 주는 예상 도착(대안 적용 뒤면 택시·지하철 값)과 맞춤
async function travelToFixed() {
  await props.live.refresh()
  const status = props.live.status.value
  if (!status || !status.fixed || !status.fixed.eta || !status.now) return LATE_TRAVEL_MIN
  return toMinutes(status.fixed.eta) - toMinutes(status.now)
}

async function goNextStop() {
  const list = orderedStops()
  const current = currentStop()
  const next = list[current ? list.indexOf(current) + 1 : 0]
  if (!next) return showToast('마지막 장소예요')
  const setup = scenario.value
  const isOverstayStop = current && setup && current.day === setup.overstayDay && current.name === setup.overstayName
  const now = appNow()
  const nowMin = now.getHours() * 60 + now.getMinutes()
  const isSameDayNow = current && dateText(now) === dateOf(current.day)
  const leaveMin = current ? toMinutes(current.time) + (current.stay || 0) - 1 : 0
  const isPastLeave = isSameDayNow && nowMin >= leaveMin
  if (isOverstayStop && !pausedAt.has('overstay') && !isPastLeave) { // 돌발 장소: 서울역으로 바로 가지 않고 나갈 시각부터 그대로 머묾 → 늦어지면 멈춤
    overstayArmed = true
    await setClock(dateOf(current.day), leaveMin)
    await props.live.refresh()
    return
  }
  if (isOverstayStop && overstayArmed && !pausedAt.has('overstay')) return // 늦어지기를 기다리는 중 (다시 눌러도 시계를 되돌리지 않음)
  const alertMin = leaveMin + 1 - LEAVE_LEAD_MIN
  if (current && !(isSameDayNow && nowMin >= alertMin)) { // 나갈 시각 몇 분 전으로 (이미 지났으면 되돌리지 않음): "곧 출발해야 해요"가 먼저 뜨고, 그다음 움직임
    await setClock(dateOf(current.day), alertMin)
    await props.live.refresh()
    await wait(ALERT_SHOW_MS)
  }
  const from = props.live.position.value || (current ? { lat: current.lat, lng: current.lng } : null)
  const isAfterIncident = isOverstayStop && isPastLeave // 돌발 장소를 늦게 떠남: 화면의 예상 도착(대안 적용 뒤 값)대로 도착
  const travelMin = isAfterIncident && next.fixed ? await travelToFixed() : LATE_TRAVEL_MIN
  const isLateLeave = isAfterIncident || (isSameDayNow && next.day === current.day && nowMin + travelMin > toMinutes(next.time)) // 늦게 떠남: 지금부터 이동 시간만큼 뒤에 도착
  const arriveMin = isLateLeave ? nowMin + travelMin : toMinutes(next.time) - 1
  if (from) {
    for (let step = 1; step < MOVE_STEPS; step++) {
      const rate = step / MOVE_STEPS
      const lat = from.lat + (next.lat - from.lat) * rate
      const lng = from.lng + (next.lng - from.lng) * rate
      // 떠난 걸 서버에도 알림 (11/08 주형: 이동 중인데 "나갈 시간이에요"가 계속 떠 있던 문제) → 화면이 "이동 중"으로 바뀜
      if (step === LEFT_STEP) await props.live.placeAt(lat, lng, false)
      else props.live.showAt(lat, lng)
      await wait(MOVE_STEP_MS)
    }
  }
  await setClock(dateOf(next.day), arriveMin) // 도착할 때 시계를 도착 시각으로 (11/08: 움직이기 전에 감아서 "출발 시간 지남"이 뜨던 문제)
  await visitEarlier(next)
  await props.live.placeAt(next.lat, next.lng, true)
  visitedIds.add(next.id)
  placedKey = next.id + ':in'
  if (devMode.speed === 0) syncClock(await setDevSpeed(MANUAL_SPEED)) // 돌발로 멈춘 시계: 대안·공유 화면을 보는 동안은 멈춰 있다가, 다음 장소에 도착하면 다시 흐름
  await props.live.refresh()
}

// ------------------------------------------------------------ 설정(DevDot)에서 보낸 일
// 장면 바로 가기: 그 장면 시각으로 시계를 옮기면 위 흐르기가 자리를 맞춤
function sceneMinute(index) {
  const day1 = stopsOf(1)
  const days = lastDay()
  const list = stopsOf(days)
  const station = list.filter((stop) => stop.fixed).pop() || list[list.length - 1]
  const dinner = day1.find((stop) => stop.fixed) || day1[day1.length - 1]
  const before = day1[day1.indexOf(dinner) - 1] || day1[0]
  if (index === 0) return { day: 1, minute: toMinutes(day1[0].time) - 40 }
  if (index === 1) return { day: 1, minute: toMinutes(before.time) + (before.stay || 0) - LEAVE_ALERT_BEFORE_MIN - 1 }
  if (index === 2) return { day: 1, minute: toMinutes(day1[day1.length - 1].time) }
  if (index === 3) return { day: days, minute: toMinutes(RAIN_CHECK_TIME) }
  if (index === 4) { // 돌발 장소 도착 10분 전 (도착을 제 시각에 해야 서버가 나갈 시각을 맞게 계산)
    const setup = scenario.value
    const cafe = setup && stopsOf(setup.overstayDay).find((stop) => stop.name === setup.overstayName)
    if (cafe) return { day: setup.overstayDay, minute: toMinutes(cafe.time) - 10 }
    return { day: days, minute: toMinutes(station.time) - LATE_BEFORE_MIN }
  }
  return { day: days, minute: toMinutes(station.time) }
}

async function runCommand(command) {
  if (command.type === 'scene') {
    const target = sceneMinute(command.index)
    if (command.index < 3) { rainDay = 0; await clearDevRain() }
    pausedAt.clear()
    overstayLeftAt = null
    overstayArmed = false
    await setClock(dateOf(target.day), target.minute)
  } else if (command.type === 'stop' && command.step > 0 && !devMode.isAutoSpeed) {
    await goNextStop()
  } else if (command.type === 'stop') {
    const now = appNow()
    const day = Math.max(1, Math.min(lastDay(), daysBetween(trip.value.startDate, dateText(now)) + 1))
    const plan = planOf(day)
    const nowMin = now.getHours() * 60 + now.getMinutes()
    let index = plan.findIndex((item) => item.arrive > nowMin) // 다음 장소
    if (index < 0) index = plan.length
    const target = command.step > 0 ? plan[index] : plan[Math.max(0, index - 2)]
    if (target) await setClock(dateOf(day), target.arrive)
  } else if (command.type === 'restart') {
    rainDay = 0
    visitedIds.clear()
    pausedAt.clear()
    overstayLeftAt = null
    overstayArmed = false
    await clearDevRain()
    const day1 = stopsOf(1)
    await setClock(dateOf(1), toMinutes(day1[0].time) - 40)
  }
  placedKey = ''
  await props.live.refresh()
}

watch(() => [devMode.command, props.live.phase.value, trip.value && trip.value.id], async ([command]) => {
  if (!command || !trip.value || props.live.phase.value === 'none') return
  clearCommand()
  try {
    await runCommand(command)
  } catch (e) {
    showToast(e.message)
  }
}, { immediate: true })
</script>

<template>
  <!-- 화면에 보이는 것 없음 (11/08: 시연이 티 나지 않게). 조작은 DevDot -->
  <span v-if="false"></span>
</template>

<style>
.demo-bar { position: absolute; left: 10px; right: 10px; top: 96px; z-index: 6; display: flex; flex-wrap: wrap; align-items: center; gap: 6px 8px; padding: 8px 10px; border-radius: 12px; background: rgba(21, 30, 48, 0.88); color: #fff; font-size: 12px; }
.demo-bar__steps { display: flex; gap: 4px; flex: none; }
.demo-bar__dot { width: 6px; height: 6px; border-radius: 50%; background: rgba(255, 255, 255, 0.3); }
.demo-bar__dot.is-done { background: rgba(255, 255, 255, 0.85); }
.demo-bar__dot.is-now { background: #ffb547; }
.demo-bar__text { flex: 1; min-width: 0; margin: 0; line-height: 1.4; }
.demo-bar__actions { display: flex; gap: 6px; flex: none; }
</style>
