// 여행 시간 추천 (③, 11/06): "시간 추천" 버튼이 쓰는 시작·끝 시각
// TODO: 방문 시간 통계(정리 중)가 오면 장소 종류·요일별 많이 다니는 시간으로 바꾸기. 지금은 자리만 잡은 기본값이에요
const RECOMMENDED = { start: '11:00', end: '20:00' } // 점심부터 저녁까지 (임시 기본값, 통계 아님)

// 추천 시작·끝 시각 { start, end }
export function recommendTime() {
  return { ...RECOMMENDED }
}

// ---- 11/09: 오늘 여행이면 추천 시작을 지금 이후로 (추천 11:00인데 지금 11:43이면 내일로 넘기던 문제) ----
export const PREP_MIN = 15 // 나갈 준비 시간: 지금부터 최소 이만큼 뒤에 시작
export const STEP_MIN = 30 // 시각은 30분 단위
export const MIN_SPAN_MIN = 120 // 최소 2시간은 놀 수 있게
export const LAST_START_MIN = 21 * 60 // 이보다 늦게 시작해야 하면 오늘은 못 놀아서 내일로
const DAY_END_MIN = 24 * 60

const toMinutes = (time) => Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5))
const toTime = (minutes) => String(Math.floor(minutes / 60)).padStart(2, '0') + ':' + String(minutes % 60).padStart(2, '0')

// 오늘인지·지금(분)에 맞춘 추천 { start, end, isTomorrow }. isTomorrow면 날짜를 내일로 (그때만 "늦어서 내일로")
export function recommendTimeFor(isToday, nowMin) {
  const base = recommendTime()
  const baseStart = toMinutes(base.start)
  if (!isToday || baseStart >= nowMin + PREP_MIN) return { ...base, isTomorrow: false }
  const start = Math.ceil((nowMin + PREP_MIN) / STEP_MIN) * STEP_MIN
  if (start > LAST_START_MIN) return { ...base, isTomorrow: true }
  const end = Math.min(DAY_END_MIN, Math.max(toMinutes(base.end), start + MIN_SPAN_MIN))
  return { start: toTime(start), end: toTime(end), isTomorrow: false }
}
