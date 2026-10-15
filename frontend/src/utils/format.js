// 시간·날짜·거리 표시
export const toMin = (hhmm) => {
  const [h, m] = String(hhmm || '0:0').split(':').map(Number)
  return h * 60 + m
}
export const toHhmm = (min) => {
  const m = ((Math.round(min) % 1440) + 1440) % 1440
  return `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`
}
export const duration = (min) => {
  const m = Math.max(0, Math.round(min))
  if (m < 60) return `${m}분`
  return m % 60 ? `${Math.floor(m / 60)}시간 ${m % 60}분` : `${m / 60}시간`
}
const WEEK = ['일', '월', '화', '수', '목', '금', '토']
export const dateLabel = (iso) => {
  if (!iso) return ''
  const d = new Date(iso + 'T00:00:00')
  return `${d.getMonth() + 1}월 ${d.getDate()}일 (${WEEK[d.getDay()]})`
}
export const shortDate = (iso) => (iso ? `${Number(iso.slice(5, 7))}.${Number(iso.slice(8, 10))}` : '')
export const dayDate = (start, day) => {
  const d = new Date(start + 'T00:00:00')
  d.setDate(d.getDate() + day - 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
export const today = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
// 지금 시각 "YYYY-MM-DDTHH:mm:ss" (서버에 그대로 저장되는 한국 시각)
export const stamp = () => `${today()}T${new Date().toTimeString().slice(0, 8)}`
export const dayCount = (start, end) => Math.round((new Date(end) - new Date(start)) / 864e5) + 1
export const metres = (m) => (m >= 1000 ? `${(m / 1000).toFixed(1)}km` : `${Math.round(m)}m`)

export const TRANSPORTS = [
  { key: 'bus', label: '버스', icon: '🚌' },
  { key: 'subway', label: '지하철', icon: '🚇' },
  { key: 'walk', label: '도보', icon: '🚶' },
  { key: 'taxi', label: '택시', icon: '🚕' },
  { key: 'car', label: '자가용', icon: '🚗' },
]
export const transportText = (keys = []) =>
  TRANSPORTS.filter((t) => keys.includes(t.key)).map((t) => t.label).join(' · ') || '선택 안 함'

// 장소 분류 → 아이콘
export function placeIcon(category = '') {
  if (/카페|디저트|베이커리|커피/.test(category)) return '☕'
  if (/숙박|호텔|게스트/.test(category)) return '🛏'
  if (/공원|산|숲|강/.test(category)) return '🌳'
  if (/공연|극장|콘서트|아레나|공연장/.test(category)) return '🎫'
  if (/궁|박물관|미술관|문화|관광|한옥|유적/.test(category)) return '🏯'
  if (/쇼핑|시장|몰|백화점/.test(category)) return '🛍'
  if (/식|요리|고기|분식|면|국|찌개|구이|회|초밥|피자|버거|햄버거|주점|술집|음식/.test(category)) return '🍽'
  return '📍'
}
