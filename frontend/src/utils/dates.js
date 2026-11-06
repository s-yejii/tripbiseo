// 날짜 글자('2026-11-14') 도우미. 날짜를 글자로 다루면 크기 비교('2026-11-14' < '2026-11-15')가 그대로 돼요
import { appNow } from '@/utils/devMode'

const DAY_MS = 24 * 60 * 60 * 1000

// Date → '2026-11-14' (내 PC 시간 기준)
export function toDateText(date) {
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${m}-${d}`
}

// 오늘 '2026-11-03'
export function todayText() {
  return toDateText(appNow()) // 개발자 모드면 시연 시계 (11/06)
}

// '2026-11-14' + 1일 → '2026-11-15'
export function addDays(dateText, days) {
  const date = new Date(dateText + 'T00:00:00')
  date.setDate(date.getDate() + days)
  return toDateText(date)
}

// 두 날짜 사이 날 수 ('2026-11-14' → '2026-11-16' = 2)
// '2026-12-05' → '12/5' (일차 칸처럼 좁은 곳에 쓰는 짧은 날짜)
export function toMonthDay(dateText) {
  const [, month, day] = dateText.split('-')
  return Number(month) + '/' + Number(day)
}

export function daysBetween(fromText, toText) {
  return Math.round((new Date(toText + 'T00:00:00') - new Date(fromText + 'T00:00:00')) / DAY_MS)
}
