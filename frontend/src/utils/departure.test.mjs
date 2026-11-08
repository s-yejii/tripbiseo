// 실행: node src/utils/departure.test.mjs (테스트 도구 없이 node 하나로)
import assert from 'node:assert/strict'
import { planDeparture } from './departure.js'

const at = (hhmm) => Number(hhmm.slice(0, 2)) * 60 + Number(hhmm.slice(3))
const cases = [
  // 11/09 주형 화면: 타임스퀘어 17:48, 서울역까지 41분, 예약 18:30 → 여유 1분 → 카드(출발 안 함)
  { name: '타임스퀘어 17:48 + 41분 → 서울역 18:30', input: { nowMin: at('17:48'), stayEndMin: at('17:27'), travelMin: 41, nextTimeMin: at('18:30'), isNextFixed: true }, slack: 1, isTight: true },
  // 나갈 시각 전에 누르면 17:27에 떠나 18:08 도착, 여유 22분 → 출발
  { name: '타임스퀘어 17:00 (나갈 시각 전)', input: { nowMin: at('17:00'), stayEndMin: at('17:27'), travelMin: 41, nextTimeMin: at('18:30'), isNextFixed: true }, slack: 22, isTight: false },
  // 호텔 16:30에 누르면 18:00(머묾 끝)에 떠나 18:15 도착, 여유 15분 → 출발
  { name: '호텔 → 문래 (머묾 끝나고 출발)', input: { nowMin: at('16:30'), stayEndMin: at('18:00'), travelMin: 15, nextTimeMin: at('18:30'), isNextFixed: true }, slack: 15, isTight: false },
  // 호텔 18:12 → 18:27 도착, 여유 3분 → 카드
  { name: '호텔 18:12 → 문래 여유 3분', input: { nowMin: at('18:12'), stayEndMin: at('18:00'), travelMin: 15, nextTimeMin: at('18:30'), isNextFixed: true }, slack: 3, isTight: true },
  // 호텔에서 18:20까지 머묾 → 15분 → 18:35 도착, 5분 늦음 → 카드
  { name: '호텔 18:20 → 문래 5분 늦음', input: { nowMin: at('18:20'), stayEndMin: at('18:00'), travelMin: 15, nextTimeMin: at('18:30'), isNextFixed: true }, slack: -5, isTight: true },
  // 느슨한 곳 → 느슨한 곳: 여유 계산 안 함
  { name: '무탄 → 한강 (느슨한 곳)', input: { nowMin: at('12:10'), stayEndMin: at('12:10'), extraMin: 20, travelMin: 14, nextTimeMin: at('12:30'), isNextFixed: false }, slack: null, isTight: false },
]
let failed = 0
for (const c of cases) {
  const result = planDeparture(c.input)
  try {
    assert.equal(result.slack, c.slack)
    assert.equal(result.isTight, c.isTight)
    console.log('통과', c.name, '→ 출발', result.departMin, '여유', result.slack)
  } catch (e) {
    failed++
    console.log('실패', c.name, JSON.stringify(result), '기대 여유', c.slack, c.isTight)
  }
}
process.exit(failed ? 1 : 0)
