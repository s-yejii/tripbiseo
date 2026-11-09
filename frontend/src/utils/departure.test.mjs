// 실행: node src/utils/departure.test.mjs (테스트 도구 없이 node 하나로)
import assert from 'node:assert/strict'
import { arrivalGapText, incidentClockMin, isBoardingPlace, lateMinutesFor, missTrainTitle, planDeparture } from './departure.js'

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
  // 11/09 KTX: 서울역 칸 = 19:00 출발. 배탈로 18:20 출발 → 지하철 42분 → 19:02, 2분 늦음(놓침) → 카드
  { name: 'KTX 지하철 18:20 → 19:02 놓침', input: { nowMin: at('18:20'), stayEndMin: at('17:28'), travelMin: 42, nextTimeMin: at('19:00'), isNextFixed: true }, slack: -2, isTight: true },
  // 택시 26분이면 18:46 도착, 14분 여유 → 바로 출발
  { name: 'KTX 택시 18:20 → 18:46', input: { nowMin: at('18:20'), stayEndMin: at('17:28'), travelMin: 26, nextTimeMin: at('19:00'), isNextFixed: true }, slack: 14, isTight: false },
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
// 돌발 시계와 문구
const checks = [
  ['서울역은 타는 곳', isBoardingPlace('서울역'), true],
  ['문래 골목집은 타는 곳 아님', isBoardingPlace('문래 골목집'), false],
  ['KTX 돌발은 2분', lateMinutesFor('서울역'), 2],
  ['회식 돌발은 12분', lateMinutesFor('문래 골목집'), 12],
  // 타임스퀘어 나갈 시각 17:28, 지하철 42분이면 여유 = 19:00 - 18:10 = 50 → 돌발 시계 17:28 + 50 + 2 = 18:20
  ['KTX 돌발 시계 18:20', incidentClockMin(at('17:28'), 50, 2), at('18:20')],
  ['카드 제목', missTrainTitle('서울역', '19:02', '19:00'), '지금대로 가면 서울역 19:02 도착 · 19:00 KTX를 놓쳐요'],
  ['끝 시트 택시', arrivalGapText('서울역', at('18:46'), at('19:00'), '19:00'), 'KTX 19:00 출발 14분 전 도착'],
  ['끝 시트 놓침', arrivalGapText('서울역', at('19:02'), at('19:00'), '19:00'), 'KTX 19:00 출발 2분 뒤 도착 · 놓침'],
  ['끝 시트 회식', arrivalGapText('문래 골목집', at('18:42'), at('18:30'), '18:30'), '목표 18:30보다 12분 늦게 도착'],
]
for (const [name, actual, expected] of checks) {
  if (actual === expected) console.log('통과', name)
  else { failed++; console.log('실패', name, actual, '기대', expected) }
}
process.exit(failed ? 1 : 0)
