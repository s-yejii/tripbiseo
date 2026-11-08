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
import { createTrip, getTrip, getTripList, startTrip } from '@/api/trips'
import { addDays, daysBetween } from '@/utils/dates'
import { isPracticeTrip, PRACTICE_MARK } from '@/utils/practice'
import { appNow, clearCommand, devMode, setClockOffset, syncClock } from '@/utils/devMode'
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
    wantsAutoplay.value = false
    playAuto()
  }
})

// ------------------------------------------------------------ 시연 여행 시작 (진행 중인 여행이 없을 때)
async function startDemoTrip() {
  if (isBusy.value) return
  isBusy.value = true
  try {
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
  if (day === lastDay()) {
    const station = list.filter((stop) => stop.fixed).pop()
    const park = list[list.indexOf(station) - 1] // ③ 서울역 바로 앞 칸에서 늦게까지 (11/08: 비 예보를 반영하면 한강이 오전으로 가서 실내 쇼핑몰)
    const parkPlan = plan.find((item) => item.stop === park)
    if (station && parkPlan && parkPlan.stop.time < station.time) {
      parkPlan.leave = Math.max(parkPlan.leave, toMinutes(station.time) - LATE_BEFORE_MIN)
      parkPlan.isLate = true
    }
  }
  return plan
}

function dateText(date) {
  return date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0') + '-' + String(date.getDate()).padStart(2, '0')
}

async function flowTick() {
  if (!devMode.isFlow || !trip.value || props.live.phase.value === 'none' || isPlacing || isBusy.value) return
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

async function moveForTime(day, plan, nowMin) {
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
    // ① 고정 일정(회식) 앞 칸: 떠날 시각 5분 전이 되면 "출발해야 해요" 알림이 보이게 시계를 멈춤
    if (next && next.stop.fixed && !item.isLate && nowMin >= item.leave - LEAVE_ALERT_BEFORE_MIN && nowMin < item.leave) {
      await placeOnce(item.stop)
      if (pausedAt.has('leave' + day)) return
      await props.live.refresh()
      return pauseFor('leave' + day)
    }
    if (item.isLate && nowMin >= item.arrive) {
      const result = await stayLate(item, next, nowMin)
      if (result !== 'gone') return result
      continue
    }
    if (nowMin >= item.arrive && nowMin < item.leave) return placeOnce(item.stop)
    if (next && nowMin >= item.leave && nowMin < Math.max(next.arrive, item.leave + 1)) {
      const travel = Math.max(5, next.arrive - item.leave - ARRIVE_EARLY_MIN)
      return walkBetween(item.stop, next.stop, (nowMin - item.leave) / travel)
    }
  }
  if (nowMin >= last.arrive) return placeOnce(last.stop) // 마지막 칸(서울역)에 도착 → 여행 끝
  if (nowMin < plan[0].arrive) return placeOnce(plan[0].stop, false) // 첫 장소로 가는 중
}

// ③ 마지막 야외(한강)에서 늦게까지: 서울역에 늦을 때까지(여유가 -3분 아래) 머물고, 늦으면 시계를 멈춰 "늦음·대안"을 보여 줌
//   다시 재생하면 서울역으로 떠남 (TRAVEL_MIN 동안 움직임)
let lateFrom = null
async function stayLate(item, next, nowMin) {
  if (lateFrom != null) {
    if (!next) return 'gone'
    if (nowMin >= lateFrom + TRAVEL_MIN) return 'gone'
    return walkBetween(item.stop, next.stop, (nowMin - lateFrom) / TRAVEL_MIN)
  }
  await placeOnce(item.stop)
  if (nowMin < item.leave) return 'stay'
  await props.live.refresh()
  const fixed = props.live.status.value && props.live.status.value.fixed
  if (fixed && fixed.slackMin != null && fixed.slackMin >= -LATE_TARGET_MIN) return 'stay' // 아직 안 늦음 → 더 머묾
  if (!pausedAt.has('late')) {
    await pauseFor('late')
    return 'stay'
  }
  lateFrom = nowMin // 재생하면 여기서부터 떠남
  return 'stay'
}

// 돌발 순간: 시계를 멈춰서 알림이 화면에 남게 (발표자가 대안을 보여 주고 설정의 "재생"으로 이어감)
async function pauseFor(name) {
  if (pausedAt.has(name)) return
  pausedAt.add(name)
  syncClock(await setDevSpeed(0))
}

async function placeOnce(stop, isArrive = true) {
  const key = stop.id + (isArrive ? ':in' : ':near')
  if (placedKey === key) return
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

onMounted(() => { flowTimer = setInterval(flowTick, FLOW_TICK_MS) })
onUnmounted(() => clearInterval(flowTimer))

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
  if (index === 4) return { day: days, minute: toMinutes(station.time) - LATE_BEFORE_MIN }
  return { day: days, minute: toMinutes(station.time) }
}

async function runCommand(command) {
  if (command.type === 'scene') {
    const target = sceneMinute(command.index)
    if (command.index < 3) { rainDay = 0; await clearDevRain() }
    pausedAt.clear()
    lateFrom = null
    await setClock(dateOf(target.day), target.minute)
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
    lateFrom = null
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
