// 시간 계산 도우미. 화면에서는 "하루 0시부터 몇 분"(예: 13:00 → 780)으로 계산하고 보여 줄 때 글자로 바꿔요

// 780 → '13:00'
export function toTime(minutes) {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return String(h).padStart(2, '0') + ':' + String(m).padStart(2, '0')
}

// '13:00' → 780
export function toMinutes(time) {
  const [h, m] = time.split(':').map(Number)
  return h * 60 + m
}

// 90 → '1시간 30분', 40 → '40분'
export function toDuration(minutes) {
  if (minutes < 60) return minutes + '분'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return m ? h + '시간 ' + m + '분' : h + '시간'
}

// 32000 → '32,000원'
export function toWon(amount) {
  return amount.toLocaleString('ko-KR') + '원'
}
