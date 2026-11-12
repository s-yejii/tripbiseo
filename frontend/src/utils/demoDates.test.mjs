// 실행: node --experimental-default-type=module src/utils/demoDates.test.mjs (별칭 @ 없이 직접 계산해서 확인)
import assert from 'node:assert/strict'
const DAY_MS = 86400000
const toText = (d) => d.toISOString().slice(0, 10)
const addDays = (text, n) => toText(new Date(Date.parse(text + 'T00:00:00Z') + n * DAY_MS))
const daysBetween = (a, b) => Math.round((Date.parse(b) - Date.parse(a)) / DAY_MS)
function shiftToDemoDate(trip, demoDate) {
  if (!trip || !demoDate || trip.startDate === demoDate) return null
  return { startDate: demoDate, endDate: addDays(demoDate, daysBetween(trip.startDate, trip.endDate)) }
}
assert.deepEqual(shiftToDemoDate({ startDate: '2026-11-14', endDate: '2026-11-15' }, '2026-11-10'), { startDate: '2026-11-10', endDate: '2026-11-11' })
assert.deepEqual(shiftToDemoDate({ startDate: '2026-11-20', endDate: '2026-11-22' }, '2026-11-10'), { startDate: '2026-11-10', endDate: '2026-11-12' })
assert.equal(shiftToDemoDate({ startDate: '2026-11-10', endDate: '2026-11-11' }, '2026-11-10'), null)
assert.deepEqual(shiftToDemoDate({ startDate: '2026-11-09', endDate: '2026-11-09' }, '2026-11-10'), { startDate: '2026-11-10', endDate: '2026-11-10' })
console.log('통과 4개 (11/14~15 → 11/10~11, 2박 → 11/10~12, 이미 11/10 → 그대로, 당일치기 → 11/10)')
