// 개발자 모드 (③, 11/06): 시연 계정만 쓰는 시연 시계. 주소는 docs/API.md "개발자 모드"
import { get, post, del } from '@/api/http'

export const getDevStatus = () => get('/dev/status')
export const setDevClock = (date, time) => post('/dev/clock', { date, time })
export const forwardDevClock = (minutes) => post('/dev/clock/forward', { minutes })
export const resetDevClock = () => del('/dev/clock')
export const setDevRain = (from) => post('/dev/rain', { from }) // 시연 비 예보 넣기 "13:00"
export const clearDevRain = () => del('/dev/rain')
export const getDevScenario = () => get('/dev/scenario') // 시연 계정: AI 일정 화면에 미리 채울 출발지·예약·숙소 (11/08)
