// 일정 다시 짜기 화면들이 같이 쓰는 상태 (④): 지연 감지 → 시간 줄이기 · 한 곳 빼기 · 장소 바꾸기 → 바뀐 일정
// 숫자는 전부 서버 계산이에요.
//   base     : 그대로 가면 (POST /live/preview, 남은 칸을 안 바꾸고 보냄) → 몇 분 늦는지
//   options  : 서버 추천 대안 A 시간 단축 · B 한 곳 빼기 · C 장소 변경 (POST /live/alternatives) → 탭 여유, 추천
//   trimPlan : 시간 줄이기에서 사용자가 정한 머묾 { 칸 id: 분 } (0이면 뺌)
//   before   : 적용 직전 원래 칸 (되돌리기에 씀)
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { applyAlternative, deleteStop, getAlternatives, getLiveStatus, getTrip, previewPlan, updateStop } from '@/api/trips'
import { useTripStore } from '@/stores/trip'

const CACHE_MS = 60000 // 탭을 옮겨 다닐 때 1분 안에 받은 값은 다시 안 받음
const UNDO_LIMIT_MS = 5 * 60000 // 적용 후 5분 안에만 되돌리기
const GPS_TIMEOUT_MS = 10000

// 브라우저 GPS 한 번 (여행 중 화면을 안 거치고 바로 들어왔을 때)
function readGps() {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) return reject(new Error('이 브라우저는 위치를 지원하지 않아요'))
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve({ lat: pos.coords.latitude, lng: pos.coords.longitude }),
      () => reject(new Error('위치 권한을 허용해 주세요')),
      { enableHighAccuracy: true, timeout: GPS_TIMEOUT_MS },
    )
  })
}

export const useReplanStore = defineStore('replan', () => {
  const tripStore = useTripStore()
  const tripId = ref(null)
  const position = ref(null)
  const trip = ref(null) // GET /trips/{id}
  const status = ref(null) // POST /live/status
  const options = ref(null) // POST /live/alternatives
  const base = ref(null) // 그대로 가면 (preview)
  const trimPlan = ref({})
  const before = ref(null)
  const applied = ref(null) // POST /live/apply 응답
  const plan = ref(null) // 적용한 계산 결과 (칸마다 이동 분 moveMin, 바뀐 일정 화면이 씀)
  const appliedAt = ref(0)
  const mutedLate = ref(null) // "그냥 갈게요"를 누른 때 늦는 분 (10분 더 늦어지면 다시 알림)
  const isLoading = ref(false)
  const error = ref('')
  let loadedAt = 0

  // 남은 칸 (그대로 가면 계산 결과의 칸: eta·stay·fixed·importance·moveMin)
  const stops = computed(() => (base.value ? base.value.stops : []))
  const lateMin = computed(() => (base.value && base.value.slackMin < 0 ? -base.value.slackMin : 0))
  // 기준 칸: 첫 고정 일정, 없으면 마지막 칸 (서버 계산과 같은 기준)
  const basisStop = computed(() => stops.value.find((stop) => stop.fixed) || stops.value[stops.value.length - 1] || null)
  const currentStop = computed(() => {
    if (!trip.value || !status.value || !status.value.currentStopId) return null
    return trip.value.stops.find((stop) => stop.id === status.value.currentStopId) || null
  })

  // 진행 중인 여행·위치·상황·추천 대안·그대로 가면을 받아 옴 (force면 1분 안이어도 다시)
  async function load(force = false) {
    if (!force && base.value && Date.now() - loadedAt < CACHE_MS) return
    isLoading.value = true
    error.value = ''
    try {
      tripId.value = tripStore.tripId || (await tripStore.findOngoingTripId())
      if (!tripId.value) throw new Error('진행 중인 여행이 없어요')
      position.value = tripStore.position || (await readGps())
      await loadPlan()
      loadedAt = Date.now()
    } catch (e) {
      error.value = e.message
    }
    isLoading.value = false
  }

  async function loadPlan() {
    const { lat, lng } = position.value
    trip.value = await getTrip(tripId.value)
    status.value = await getLiveStatus(tripId.value, lat, lng)
    options.value = await getAlternatives(tripId.value, lat, lng)
    // 시간 단축(A)은 항상 만들어지고 남은 칸이 순서대로 다 들어 있어서, 그 id로 "그대로 가면"을 계산
    const trim = findOption('시간 단축') || options.value.alternatives[0]
    base.value = await previewPlan(tripId.value, trim.stops.map((stop) => ({ id: stop.id })), lat, lng)
  }

  // 서버 추천 대안 하나 (short: "시간 단축" · "한 곳 빼기" · "장소 변경"), 없으면 null
  function findOption(shortName) {
    if (!options.value) return null
    return options.value.alternatives.find((option) => option.short === shortName) || null
  }

  // 남은 칸 목록을 고친 대로 계산만 (저장 안 함). list: [{ id, stay }] 또는 새 장소 칸
  function preview(list) {
    return previewPlan(tripId.value, list, position.value.lat, position.value.lng)
  }

  // trimPlan을 남은 칸 목록으로 (0분은 뺌)
  function trimList(plan) {
    const list = []
    for (const stop of stops.value) {
      const stay = plan[stop.id]
      if (stay === 0) continue
      list.push(stay == null ? { id: stop.id } : { id: stop.id, stay })
    }
    return list
  }

  // 계산된 대안(서버 응답)을 그대로 적용. 되돌리기를 위해 원래 칸을 먼저 기억
  async function apply(alternative) {
    const ids = stops.value.map((stop) => stop.id)
    before.value = trip.value.stops.filter((stop) => ids.includes(stop.id))
    applied.value = await applyAlternative(tripId.value, alternative.key, alternative.stops, position.value.lat, position.value.lng)
    plan.value = alternative
    tripStore.applied = applied.value
    appliedAt.value = Date.now()
    trimPlan.value = {}
    loadedAt = 0
  }

  // 되돌릴 수 있나 (적용 후 5분 안). 시각이 계속 바뀌어서 computed 대신 부를 때마다 계산
  function canUndo() {
    return before.value !== null && Date.now() - appliedAt.value < UNDO_LIMIT_MS
  }

  // 되돌리기: 원래 칸은 원래 시각·머묾·갈 곳으로, 적용하면서 새로 생긴 칸은 지움 (②의 일정 칸 API)
  // 칸마다 따로 저장돼서, 중간에 실패하면 거기까지만 바뀌어요 → 오류를 그대로 알려 줌
  async function undo() {
    const now = await getTrip(tripId.value)
    const originalIds = before.value.map((stop) => stop.id)
    for (const stop of before.value) {
      const form = { time: stop.time, stay: stop.stay, state: 'todo', mode: stop.mode, memo: stop.memo, importance: stop.importance }
      await updateStop(tripId.value, stop.id, form)
    }
    for (const stop of now.stops) {
      const isAdded = !originalIds.includes(stop.id) && stop.state === 'todo' && isAddedByApply(stop)
      if (isAdded) await deleteStop(tripId.value, stop.id)
    }
    before.value = null
    applied.value = null
    loadedAt = 0
  }

  // 적용 응답에서 "새로 추가"로 표시된 칸인지
  function isAddedByApply(stop) {
    if (!applied.value) return false
    return applied.value.stops.some((row) => row.id === stop.id && row.tag === 'added')
  }

  return {
    tripId, position, trip, status, options, base, trimPlan, before, applied, plan, mutedLate, isLoading, error,
    stops, lateMin, basisStop, currentStop, canUndo,
    load, findOption, preview, trimList, apply, undo,
  }
})
