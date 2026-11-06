// 개발자 모드 (③, 11/06): 시연 계정만 쓰는 시연 시계. 주소는 docs/API.md "개발자 모드"
import { get, post, del } from '@/api/http'

export const getDevStatus = () => get('/dev/status')
export const setDevClock = (date, time) => post('/dev/clock', { date, time })
export const forwardDevClock = (minutes) => post('/dev/clock/forward', { minutes })
export const resetDevClock = () => del('/dev/clock')
export const setDevRain = (from) => post('/dev/rain', { from }) // 시연 비 예보 넣기 "13:00"
export const clearDevRain = () => del('/dev/rain')
