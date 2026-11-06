// 이동 미리보기: 일정 순서대로 점이 길을 따라 움직이고 시각이 흘러가요. 화면에서만 계산해요 (서버 저장·실제 위치 없음)
// 하루를 약 25초에 봐요. 머무는 시간은 빠르게 건너뛰고, 이동 시간이 긴 구간일수록 오래 보여 줘요.
import { computed, onUnmounted, ref } from 'vue'
import { toMinutes, toTime } from '@/utils/time'

const DAY_MS = 25000 // 하루 전체를 보는 시간
const STAY_MS = 1200 // 장소 한 곳에 머무는 동안
const MIN_MOVE_MS = 1500 // 아주 짧은 이동도 이만큼은 보여 줌
const TICK_MS = 40 // 화면을 다시 그리는 간격
const END_HOLD_MS = 1200 // 끝난 뒤 잠깐 두었다가 원래대로
const DAY_MIN = 24 * 60

// 구간 길: 앞 장소 → 길찾기 좌표 → 뒤 장소 [[위도, 경도], ...]
function pathOf(leg, from, to) {
  const coords = leg && leg.coords ? leg.coords : []
  return [[from.lat, from.lng], ...coords, [to.lat, to.lng]]
}

// 일정 → 차례대로 보여 줄 단계 목록 (머무름 · 이동 · 머무름 · 이동 …)
function buildSteps(trip) {
  const steps = []
  let moveTotal = 0
  trip.stops.forEach((stop, i) => {
    const start = toMinutes(stop.time)
    const end = start + (stop.dur || 0)
    steps.push({ index: i, name: stop.name, from: start, to: end, ms: STAY_MS, path: null })
    const next = trip.stops[i + 1]
    if (!next) return
    let arrive = toMinutes(next.time)
    if (arrive < end) arrive += DAY_MIN // 자정을 넘긴 칸
    moveTotal += arrive - end
    steps.push({ index: i + 1, name: next.name + ' 가는 중', from: end, to: arrive, ms: 0, path: pathOf((trip.legs || [])[i], stop, next) })
  })
  const moveMs = Math.max(0, DAY_MS - STAY_MS * trip.stops.length)
  for (const step of steps) {
    if (step.path) step.ms = Math.max(MIN_MOVE_MS, moveTotal ? (moveMs * (step.to - step.from)) / moveTotal : MIN_MOVE_MS)
  }
  return steps
}

// 두 점 사이 길이 (비교용이라 지도 평면으로 간단히 계산)
function lengthOf(a, b) {
  const dy = a[0] - b[0]
  const dx = (a[1] - b[1]) * Math.cos((a[0] * Math.PI) / 180)
  return Math.sqrt(dy * dy + dx * dx)
}

// 길 위에서 ratio(0~1)만큼 간 지점
function pointOnPath(path, ratio) {
  let total = 0
  for (let i = 1; i < path.length; i++) total += lengthOf(path[i - 1], path[i])
  let left = total * ratio
  for (let i = 1; i < path.length; i++) {
    const piece = lengthOf(path[i - 1], path[i])
    if (left <= piece && piece > 0) {
      const part = left / piece
      return { lat: path[i - 1][0] + (path[i][0] - path[i - 1][0]) * part, lng: path[i - 1][1] + (path[i][1] - path[i - 1][1]) * part }
    }
    left -= piece
  }
  const last = path[path.length - 1]
  return { lat: last[0], lng: last[1] }
}

export function useRouteSim(getTrip) {
  const isOn = ref(false) // 미리보기 중 (일시정지 포함)
  const isPlaying = ref(false)
  const me = ref(null) // 움직이는 점 { lat, lng }
  const clock = ref('') // 흘러가는 시각
  const label = ref('') // 지금 있는 곳 또는 가는 곳
  const activeStop = ref(null) // 강조할 칸 번호
  let steps = []
  let stepIndex = 0
  let stepElapsed = 0
  let timer = null
  let endTimer = null

  // 칸마다 좌표와 시각이 있고 두 곳 이상이어야 볼 수 있어요
  const canPlay = computed(() => {
    const stops = getTrip().stops
    return stops.length > 1 && stops.every((stop) => stop.lat != null && stop.time)
  })

  function show() {
    const step = steps[stepIndex]
    const ratio = Math.min(1, stepElapsed / step.ms)
    clock.value = toTime(Math.round(step.from + (step.to - step.from) * ratio) % DAY_MIN)
    label.value = step.name
    activeStop.value = step.path ? null : step.index
    if (step.path) {
      me.value = pointOnPath(step.path, ratio)
    } else {
      const stop = getTrip().stops[step.index]
      me.value = { lat: stop.lat, lng: stop.lng }
    }
  }

  function tick() {
    stepElapsed += TICK_MS
    if (stepElapsed >= steps[stepIndex].ms) {
      if (stepIndex === steps.length - 1) return finish()
      stepIndex++
      stepElapsed = 0
    }
    show()
  }

  function pause() {
    clearInterval(timer)
    isPlaying.value = false
  }

  function finish() {
    pause()
    endTimer = setTimeout(reset, END_HOLD_MS)
  }

  // 처음 상태로 (점·강조를 지움)
  function reset() {
    pause()
    clearTimeout(endTimer)
    isOn.value = false
    me.value = null
    activeStop.value = null
  }

  // 재생 ↔ 일시정지
  function toggle() {
    if (isPlaying.value) return pause()
    if (!isOn.value) {
      steps = buildSteps(getTrip())
      stepIndex = 0
      stepElapsed = 0
      isOn.value = true
      show()
    }
    isPlaying.value = true
    timer = setInterval(tick, TICK_MS)
  }

  onUnmounted(reset)

  return { isOn, isPlaying, me, clock, label, activeStop, canPlay, toggle, reset }
}
