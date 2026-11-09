// 시연 계정 여행 날짜 옮기기 (③ 11/09 주형: 어떤 여행을 만들든 발표에서는 무조건 11/10부터)
import { addDays, daysBetween } from '@/utils/dates'

// 여행을 시연 날짜부터로: 일수는 그대로 (1박 2일이면 11/10~11/11). 이미 그 날이면 null
export function shiftToDemoDate(trip, demoDate) {
  if (!trip || !demoDate || trip.startDate === demoDate) return null
  return { startDate: demoDate, endDate: addDays(demoDate, daysBetween(trip.startDate, trip.endDate)) }
}

// 두 기간이 겹치는지 (yyyy-MM-dd 문자열)
export function isOverlap(a, b) {
  return a.startDate <= b.endDate && a.endDate >= b.startDate
}
