// 실행: node src/utils/timeRecommend.test.mjs
import { recommendTimeFor } from './timeRecommend.js'

const at = (hhmm) => Number(hhmm.slice(0, 2)) * 60 + Number(hhmm.slice(3))
const cases = [
  ['오늘이 아니면 기본 11:00~20:00', recommendTimeFor(false, at('11:43')), { start: '11:00', end: '20:00', isTomorrow: false }],
  ['오늘 아침 9시면 기본 그대로', recommendTimeFor(true, at('09:00')), { start: '11:00', end: '20:00', isTomorrow: false }],
  ['오늘 11:43이면 12:00~20:00 (내일로 안 넘김)', recommendTimeFor(true, at('11:43')), { start: '12:00', end: '20:00', isTomorrow: false }],
  ['오늘 18:50이면 19:30~21:30 (최소 2시간)', recommendTimeFor(true, at('18:50')), { start: '19:30', end: '21:30', isTomorrow: false }],
  ['오늘 21:40이면 내일로', recommendTimeFor(true, at('21:40')), { start: '11:00', end: '20:00', isTomorrow: true }],
]
let failed = 0
for (const [name, actual, expected] of cases) {
  const ok = JSON.stringify(actual) === JSON.stringify(expected)
  if (!ok) failed++
  console.log(ok ? '통과' : '실패', name, ok ? '' : JSON.stringify(actual))
}
process.exit(failed ? 1 : 0)
