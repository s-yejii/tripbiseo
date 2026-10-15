import { toHhmm, toMin } from './format'

// 장소 분류별 기본 체류 시간(분)
export function defaultStay(category = '') {
  if (/궁|박물관|미술관|공원|한강|산/.test(category)) return 90
  if (/공연|극장|콘서트/.test(category)) return 150
  return 60
}

// DB 장소 → 일정 한 칸
export function toStop(place, day, prev) {
  const time = prev ? toHhmm(Math.min(toMin(prev.time) + prev.stay + 30, 23 * 60 + 30)) : '10:00' // 자정을 넘기면 맨 앞으로 정렬되므로 23:30에서 멈춤
  return {
    day, placeId: place.id ?? null, name: place.name, category: place.category || '', address: place.address || '',
    lat: place.lat, lng: place.lng, time, stay: defaultStay(place.category), fixed: false, memo: '',
  }
}

// 하루 일정을 시간순으로 정렬하고 seq 다시 매기기
export function sortDay(stops, day) {
  const others = stops.filter((s) => s.day !== day)
  const mine = stops.filter((s) => s.day === day).sort((a, b) => toMin(a.time) - toMin(b.time)).map((s, i) => ({ ...s, seq: i + 1 }))
  return [...others, ...mine].sort((a, b) => a.day - b.day || (a.seq ?? 0) - (b.seq ?? 0))
}
