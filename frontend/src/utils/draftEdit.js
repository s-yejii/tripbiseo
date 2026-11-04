// 일정 초안(store.draft) 한 날 편집 도우미 (11/04): 일정 점검 → 편집 → 한 칸 수정·빼기가 같이 써요
// 순서·머무는 시간·빼기를 바꾸면 그날 시각을 다시 맞춰요(retime): 첫 칸과 시간 고정 칸은 시각을 그대로 두고,
// 나머지 칸은 시각을 비워서 일정 점검(POST /api/plans/check)이 "앞 일정에 이어서" 다시 채우게 해요
import { imageFor, isStayStop } from '@/utils/mapView'

let backup = null // 편집에 들어올 때 초안 칸 복사본 (편집 취소하면 되돌림)

// 그날 칸 목록 (초안 순서 그대로, 칸 객체는 초안 것 그대로라 고치면 초안이 바뀜)
export function dayStopsOf(store, day) {
  return store.draft.stops.filter((stop) => (stop.day || 1) === day)
}

// 그날 칸을 새 목록으로 바꿔 넣기 (다른 날 칸은 그대로, 날짜 순서 유지)
export function setDayStops(store, day, list) {
  const others = store.draft.stops.filter((stop) => (stop.day || 1) !== day)
  // sort는 같은 날끼리는 넣은 순서를 지켜요 (안정 정렬)
  store.draft.stops = [...others, ...list].sort((a, b) => (a.day || 1) - (b.day || 1))
}

// 시각 다시 맞추기: startTime(그날 첫 시각)은 첫 칸에, 고정 칸·숙소 칸(체크인 시각)은 그대로, 나머지는 비움 → 일정 점검이 채움
export function retime(list, startTime) {
  list.forEach((stop, i) => {
    if (stop.fixed || isStayStop(stop)) return
    stop.time = i === 0 ? startTime : null
  })
}

// 순서를 바꿔도 되는 칸인지 (시간 고정·숙소는 자리를 안 바꿔요)
export function isMovable(stop) {
  return !stop.fixed && !isStayStop(stop)
}

// 편집 화면에 보일 칸 모양
export function toEditRow(stop, i) {
  return {
    key: stop.name, // 순서를 바꿔도 같은 칸으로 보게 (번호를 넣으면 끌 때마다 새로 그려짐)
    name: stop.name,
    time: stop.time || '',
    dur: stop.stay || 0,
    fixed: !!stop.fixed,
    isStay: isStayStop(stop),
    movable: isMovable(stop),
    img: imageFor(stop.category),
    category: stop.category || '',
    imp: stop.importance || 'maybe',
  }
}

export function startEdit(store) {
  if (!backup) backup = JSON.parse(JSON.stringify(store.draft.stops)) // 깊은 복사 (칸 객체까지 새로)
}
export function cancelEdit(store) {
  if (backup) store.draft.stops = backup
  backup = null
}
export function finishEdit() {
  backup = null
}
