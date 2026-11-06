<script setup>
// 시연 진행 띠 (③, 11/06): 개발자 모드(시연 계정)에서만 여행 중 화면 위에 떠요.
// 장면 버튼 하나로 시연 시계·가짜 위치·비 예보를 한 번에 맞춰서, 실제 사용자가 그 순간 보는 화면(알림·대안)을 바로 보여 줘요.
//   1일차 출발 → ① 회식 출발 알림 → 숙소(밤 건너뛰기) → ② 2일차 아침 비 → ③ 한강에서 늦음 → 서울역 도착
// 진행 중인 여행이 없으면 "시연 여행 시작": 가장 최근 예정 여행을 복사해서 시작해요 (원본은 본편용으로 그대로)
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { clearDevRain, setDevClock, setDevRain } from '@/api/dev'
import { createTrip, getTrip, getTripList, startTrip } from '@/api/trips'
import { addDays } from '@/utils/dates'
import { devMode, setClockOffset } from '@/utils/devMode'
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
const RAIN_FROM = '13:00'
const SCENE_KEY = 'demoScene'

const sceneIndex = ref(readScene()) // 지금까지 끝낸 장면 수 (다음 장면 = 이 번호)
const isBusy = ref(false)
const isAuto = ref(false)

const trip = computed(() => props.live.trip.value)
const isOngoing = computed(() => props.live.phase.value !== 'none' && props.live.phase.value !== 'loading')
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
  setClockOffset(status.offsetMinutes) // 상태바·홈 시계도 같이
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
  // 자동 재생: 돌발 장면에서는 멈춰서 알림·대안을 보여 줌
  if (isAuto.value && nextScene.value && !lastScene.value.isIncident) runNext()
  else isAuto.value = false
}

function playAuto() {
  isAuto.value = true
  runNext()
}

// 시연 중에 화면을 새로 고치면 진짜 GPS를 기다리느라 멈춰요 → 마지막으로 도착한 칸 자리로 다시 옮겨 둠
watch(() => props.live.phase.value, async (phase) => {
  if ((phase !== 'gps' && phase !== 'gps-error') || !devMode.isOn || sceneIndex.value === 0 || !props.live.tripId.value) return
  try {
    const saved = await getTrip(props.live.tripId.value)
    const arrived = saved.stops.filter((stop) => stop.arrivedAt).sort((a, b) => a.arrivedAt.localeCompare(b.arrivedAt))
    const last = arrived[arrived.length - 1]
    if (last) await props.live.placeAt(last.lat, last.lng, false)
  } catch (e) {
    // 못 받으면 원래처럼 GPS를 기다림
  }
})

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
    const planned = (await getTripList()).filter((item) => item.status === 'planned').sort((a, b) => b.id - a.id)
    if (!planned.length) throw new Error('복사할 예정 여행이 없어요. 먼저 여행을 하나 저장해 주세요')
    const source = await getTrip(planned[0].id)
    const copy = await createTrip({ ...source, id: null, title: source.title + ' (연습)', stops: source.stops.map((stop) => ({ ...stop, id: null })) })
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
</script>

<template>
  <div v-if="devMode.isOn" class="demo-bar" role="region" aria-label="시연 진행">
    <template v-if="!isOngoing || !trip">
      <span class="demo-bar__text">진행 중인 여행이 없어요</span>
      <button type="button" class="btn btn--primary btn--sm" :disabled="isBusy" @click="startDemoTrip">시연 여행 시작</button>
    </template>
    <template v-else>
      <div class="demo-bar__steps" aria-hidden="true">
        <span v-for="(scene, i) in SCENES" :key="scene.label" class="demo-bar__dot" :class="{ 'is-done': i < sceneIndex, 'is-now': i === sceneIndex }"></span>
      </div>
      <p class="demo-bar__text">
        <b>{{ lastScene ? lastScene.label : '시작 전' }}</b>
        <span v-if="lastScene"> · 사용자에게는: {{ lastScene.user }}</span>
      </p>
      <div class="demo-bar__actions">
        <button v-if="nextScene" type="button" class="btn btn--primary btn--sm" :disabled="isBusy" @click="runNext">{{ isBusy ? '움직이는 중' : '다음: ' + nextScene.label }}</button>
        <button v-if="nextScene && !isAuto" type="button" class="btn btn--secondary btn--sm" :disabled="isBusy" aria-label="자동 재생 (돌발에서 멈춤)" @click="playAuto">자동</button>
        <button v-if="!nextScene" type="button" class="btn btn--secondary btn--sm" @click="saveScene(0)">처음부터</button>
      </div>
    </template>
  </div>
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
