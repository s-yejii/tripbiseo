// 일정 편집 도우미 (11/04): 일정 점검 → 편집 → 한 칸 수정·빼기가 같이 써요
// trip = 지금 고치는 여행. 새로 짠 초안(store.draft)이거나, 저장된 여행 복사본(store.editTrip) → editingTripOf
// 순서·머무는 시간·빼기를 바꾸면 그날 시각을 다시 맞춰요(retime): 첫 칸·시간 고정 칸·숙소·다녀온 칸은 시각을 그대로 두고,
// 나머지 칸은 시각을 비워서 일정 점검(POST /api/plans/check)이 "앞 일정에 이어서" 다시 채우게 해요
// 저장된 여행은 "완료"를 누르면 빈 시각을 점검으로 채운 뒤 칸 전부를 한 번에 저장해요 (PUT /api/trips/{id}/stops)
// 끌어서 순서 바꾸기: 보통 칸끼리는 자리 바꾸기, 고정 칸(예약) 위에 놓으면 그 앞이나 뒤에 끼워 넣기 (dropPlaceOf)
import { checkPlan, getTrip, updateStops } from '@/api/trips'
import { imageFor, isStayStop } from '@/utils/mapView'
import { toMinutes, toTime } from '@/utils/time'

const MOVE_GUESS_MIN = 20 // 시작 시각을 앞당길 때 칸 사이 이동 시간 어림값(분). 정확한 시각은 점검이 다시 계산해요
const EARLIEST_START_MIN = 7 * 60 // 아무리 앞당겨도 아침 7시부터

let backup = null // 편집에 들어올 때 칸 복사본 (편집 취소하면 되돌림)

// 주소에 여행 번호(?id=)가 있으면 저장된 여행 복사본, 없으면 새로 짠 초안
export function editingTripOf(store, tripId) {
  return tripId ? store.editTrip : store.draft
}

// 화면에 보이는 칸 (여행 중 뺀 칸 skipped는 안 보여요)
function visibleStops(trip) {
  return trip.stops.filter((stop) => stop.state !== 'skipped')
}

// 그날 칸 목록 (여행 순서 그대로, 칸 객체는 여행 것 그대로라 고치면 여행이 바뀜)
export function dayStopsOf(trip, day) {
  return visibleStops(trip).filter((stop) => (stop.day || 1) === day)
}

// 그날 칸을 새 목록으로 바꿔 넣기 (다른 날 칸·skipped 칸은 그대로, 날짜 순서 유지)
export function setDayStops(trip, day, list) {
  const others = trip.stops.filter((stop) => (stop.day || 1) !== day || stop.state === 'skipped')
  // sort는 같은 날끼리는 넣은 순서를 지켜요 (안정 정렬)
  trip.stops = [...others, ...list].sort((a, b) => (a.day || 1) - (b.day || 1))
}

// 시각 다시 맞추기: startTime(그날 첫 시각)은 첫 칸에, 고정·숙소(체크인 시각)·다녀온 칸은 그대로, 나머지는 비움 → 일정 점검이 채움
export function retime(list, startTime) {
  const start = startBeforeFixed(list, startTime)
  list.forEach((stop, i) => {
    if (stop.fixed || isStayStop(stop) || stop.state === 'done') return
    stop.time = i === 0 ? start : null
  })
}

// 첫 고정 칸 앞에 다른 칸을 넣었으면, 그 칸들이 고정 시각 전에 끝나게 시작 시각을 앞당김
//   예: 12:00 예약 앞에 카페(60분)를 넣으면 12:00 − (60 + 이동 20) = 10:40 시작. 원래 시작이 더 이르면 그대로
function startBeforeFixed(list, startTime) {
  let fixedIndex = -1
  for (let i = 0; i < list.length; i++) {
    if (list[i].fixed && list[i].time) {
      fixedIndex = i
      break
    }
  }
  if (fixedIndex <= 0) return startTime
  let need = 0
  for (let i = 0; i < fixedIndex; i++) need += (list[i].stay || 0) + MOVE_GUESS_MIN
  const latest = Math.max(toMinutes(list[fixedIndex].time) - need, EARLIEST_START_MIN)
  if (startTime && toMinutes(startTime) <= latest) return startTime
  return toTime(latest)
}

// 끌어 놓을 때 어떻게 넣을지 (rows: 화면 칸 목록, from: 끄는 칸, to: 손가락 아래 칸)
//   'swap' 두 칸 자리 바꾸기 · 'before'/'after' 고정 칸 앞/뒤에 끼우기 · null 여기엔 못 놓음
//   고정 칸(예약)은 자기 자리는 그대로지만 앞뒤에 다른 칸을 넣을 수 있어요. 숙소는 앞에만, 다녀온 칸 자리엔 못 놓아요
export function dropPlaceOf(rows, from, to) {
  if (to === null || to === from) return null
  const target = rows[to]
  if (target.movable) return 'swap'
  if (target.isDone) return null
  if (target.isStay) return 'before'
  return from > to ? 'before' : 'after'
}

// 놓은 뒤 순서: 원래 번호를 새 순서대로 담은 목록 (예: [0, 2, 1, 3])
export function orderAfterDrop(count, from, to, place) {
  const order = []
  for (let i = 0; i < count; i++) order.push(i)
  if (place === 'swap') {
    order[from] = to
    order[to] = from
    return order
  }
  order.splice(from, 1) // 끄는 칸을 빼고
  const at = order.indexOf(to) + (place === 'after' ? 1 : 0)
  order.splice(at, 0, from) // 고정 칸 앞(뒤)에 넣기
  return order
}

// 순서를 바꿔도 되는 칸인지 (시간 고정·숙소·이미 다녀온 칸은 자리를 안 바꿔요)
export function isMovable(stop) {
  return !stop.fixed && !isStayStop(stop) && stop.state !== 'done'
}

// 편집 화면에 보일 칸 모양
export function toEditRow(stop) {
  return {
    key: stop.id ?? stop.name, // 순서를 바꿔도 같은 칸으로 보게 (몇 번째인지를 넣으면 끌 때마다 새로 그려짐)
    name: stop.name,
    time: stop.time || '',
    dur: stop.stay || 0,
    fixed: !!stop.fixed,
    isStay: isStayStop(stop),
    isDone: stop.state === 'done', // 여행 중 이미 다녀온 칸 (못 빼고 못 옮김)
    movable: isMovable(stop),
    img: imageFor(stop.category),
    category: stop.category || '',
    imp: stop.importance || 'maybe',
  }
}

// 편집 시작·취소·끝: 시작할 때 칸을 복사해 두고, 취소하면 그걸로 되돌림
export function startEdit(trip) {
  if (!backup) backup = JSON.parse(JSON.stringify(trip.stops)) // 깊은 복사 (칸 객체까지 새로)
}
export function cancelEdit(trip) {
  if (backup && trip) trip.stops = backup
  backup = null
}
export function finishEdit() {
  backup = null
}

// 저장된 여행 편집 시작: 서버에서 읽어 store.editTrip에 (같은 여행을 이미 고치는 중이면 그대로 → 한 칸 수정에서 돌아와도 유지)
export async function loadEditTrip(store, tripId) {
  if (store.editTrip && String(store.editTrip.id) === String(tripId)) return
  backup = null
  store.editTrip = await getTrip(tripId)
}

// 저장된 여행 편집 저장: 시각이 빈 날은 점검으로 채우고 → 칸 전부 한 번에 저장 → 저장된 여행(Trip)
export async function saveEditTrip(store) {
  const trip = store.editTrip
  for (const day of daysWithEmptyTime(trip)) {
    await fillTimes(trip, day)
  }
  const saved = await updateStops(trip.id, visibleStops(trip))
  store.editTrip = null
  backup = null
  return saved
}

// 시각이 빈 칸이 있는 날 목록
function daysWithEmptyTime(trip) {
  const days = []
  for (const stop of visibleStops(trip)) {
    const day = stop.day || 1
    if (!stop.time && !days.includes(day)) days.push(day)
  }
  return days
}

// 그날 점검 → 서버가 앞 일정에 이어서 정한 시각을 빈 칸에 넣기 (점검 결과는 보낸 순서 그대로라 같은 번호끼리 짝)
async function fillTimes(trip, day) {
  const check = await checkPlan({ ...trip, stops: visibleStops(trip) }, day)
  const list = dayStopsOf(trip, day)
  for (let i = 0; i < list.length; i++) {
    if (!list[i].time && check.stops[i]) list[i].time = check.stops[i].time
  }
}
