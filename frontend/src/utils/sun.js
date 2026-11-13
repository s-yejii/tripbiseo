// ⑤ 해 뜨고 지는 시각 · 해의 높이와 방향 (웹 회고의 일출·일몰 표현, 11/10)
// 외부 API 없이 천문 공식으로 계산해요 (SunCalc 라이브러리와 같은 공식, 오차 1~2분)
// 시각은 모두 한국 시각 기준 "그날 0시부터 몇 분" (예: 06:58 → 418)

const RAD = Math.PI / 180              // 도 → 라디안
const DAY_MS = 86400000                // 하루(밀리초)
const J1970 = 2440588                  // 1970-01-01의 율리우스일 (천문학에서 쓰는 날짜 번호)
const J2000 = 2451545                  // 2000-01-01 12시의 율리우스일
const J0 = 0.0009
const EARTH_TILT = 23.4397 * RAD       // 지구 자전축 기울기
const KST_OFFSET_MINUTES = 9 * 60      // 한국 시각 = 세계 표준시 + 9시간
const SUNRISE_ANGLE = -0.833           // 해가 지평선에 걸치는 높이(도). 대기 굴절 때문에 0보다 조금 아래
const TWILIGHT_ANGLE = -6              // 시민 박명: 해가 져도 이 높이까지는 하늘이 밝음
const GOLDEN_ANGLE = 6                 // 해가 이보다 낮으면 노을빛(골든 아워)

// Date → 2000년 1월 1일 12시부터 며칠째 (소수)
function toDays(date) {
  return date.valueOf() / DAY_MS - 0.5 + J1970 - J2000
}

// 율리우스일 → Date
function fromJulian(julian) {
  return new Date((julian + 0.5 - J1970) * DAY_MS)
}

// 태양의 평균 근점 이각 (해가 궤도에서 어디쯤인지)
function meanAnomaly(days) {
  return RAD * (357.5291 + 0.98560028 * days)
}

// 황경 (해가 하늘의 길(황도)에서 어디쯤인지)
function eclipticLongitude(anomaly) {
  const center = RAD * (1.9148 * Math.sin(anomaly) + 0.02 * Math.sin(2 * anomaly) + 0.0003 * Math.sin(3 * anomaly))
  const perihelion = RAD * 102.9372
  return anomaly + center + perihelion + Math.PI
}

// 적위 (해가 하늘 적도에서 남북으로 얼마나 떨어졌는지)
function declination(longitude) {
  return Math.asin(Math.sin(EARTH_TILT) * Math.sin(longitude))
}

// 적경 (하늘 적도를 따라 잰 해의 위치)
function rightAscension(longitude) {
  return Math.atan2(Math.sin(longitude) * Math.cos(EARTH_TILT), Math.cos(longitude))
}

/** 이 순간 해의 높이(도, 지평선 0 · 머리 위 90)와 방향(도, 북쪽 0 · 동쪽 90) */
export function sunPosition(date, lat, lng) {
  const westLng = RAD * -lng
  const phi = RAD * lat
  const days = toDays(date)
  const longitude = eclipticLongitude(meanAnomaly(days))
  const dec = declination(longitude)
  const hourAngle = RAD * (280.16 + 360.9856235 * days) - westLng - rightAscension(longitude)
  const altitude = Math.asin(Math.sin(phi) * Math.sin(dec) + Math.cos(phi) * Math.cos(dec) * Math.cos(hourAngle))
  const azimuth = Math.atan2(Math.sin(hourAngle), Math.cos(hourAngle) * Math.sin(phi) - Math.tan(dec) * Math.cos(phi))
  return { altitude: altitude / RAD, azimuth: (azimuth / RAD + 180) % 360 }
}

// 해가 이 높이(angle)를 지나는 두 시각 (뜰 때, 질 때). 그날 그 높이까지 안 가면 null
function crossingTimes(noonDays, westLng, phi, angle) {
  const anomaly = meanAnomaly(noonDays)
  const longitude = eclipticLongitude(anomaly)
  const dec = declination(longitude)
  const cosHour = (Math.sin(angle * RAD) - Math.sin(phi) * Math.sin(dec)) / (Math.cos(phi) * Math.cos(dec))
  if (cosHour < -1 || cosHour > 1) return null
  const cycle = Math.round(noonDays - J0 - westLng / (2 * Math.PI))
  const transit = (hourAngle) => J0 + (hourAngle + westLng) / (2 * Math.PI) + cycle
  const fix = 0.0053 * Math.sin(anomaly) - 0.0069 * Math.sin(2 * longitude) // 지구 궤도가 타원이라 생기는 차이
  const noon = J2000 + transit(0) + fix
  const set = J2000 + transit(Math.acos(cosHour)) + fix
  return { rise: fromJulian(noon - (set - noon)), set: fromJulian(set) }
}

// Date → 한국 시각으로 그날 0시부터 몇 분
function toKoreanMinutes(date) {
  const minutes = date.getTime() / 60000 + KST_OFFSET_MINUTES
  return Math.round(((minutes % 1440) + 1440) % 1440)
}

// "2026-11-07" → 그날 한국 시각 정오의 Date
function koreanNoon(dateText) {
  const [year, month, day] = dateText.split('-').map(Number)
  return new Date(Date.UTC(year, month - 1, day, 12) - KST_OFFSET_MINUTES * 60000)
}

/** 그날의 해 시각: 새벽 밝아짐(dawn)·일출·일몰·어두워짐(dusk), 모두 0시부터 분 */
export function sunTimes(dateText, lat, lng) {
  const noonDays = toDays(koreanNoon(dateText))
  const westLng = RAD * -lng
  const phi = RAD * lat
  const sun = crossingTimes(noonDays, westLng, phi, SUNRISE_ANGLE)
  const twilight = crossingTimes(noonDays, westLng, phi, TWILIGHT_ANGLE)
  if (!sun || !twilight) return null
  return {
    dawn: toKoreanMinutes(twilight.rise),
    sunrise: toKoreanMinutes(sun.rise),
    sunset: toKoreanMinutes(sun.set),
    dusk: toKoreanMinutes(twilight.set),
  }
}

/** "2026-11-07" + 그날 0시부터 분 → 그 순간의 Date (한국 시각) */
export function koreanDate(dateText, minutes) {
  const [year, month, day] = dateText.split('-').map(Number)
  return new Date(Date.UTC(year, month - 1, day, 0, minutes) - KST_OFFSET_MINUTES * 60000)
}

/** 해 높이로 지금이 어떤 때인지: night(밤) · twilight(해 뜨기 전·진 뒤) · golden(노을빛) · day(낮) */
export function sunPhase(altitude) {
  if (altitude < TWILIGHT_ANGLE) return 'night'
  if (altitude < SUNRISE_ANGLE) return 'twilight'
  if (altitude < GOLDEN_ANGLE) return 'golden'
  return 'day'
}

// 해 높이별 하늘 색 (높이가 이 사이면 두 색을 섞음)
const SKY_STEPS = [
  { altitude: -12, sky: '#0B1430', horizon: '#1E2B52', shade: 'rgba(6, 12, 36, 0.52)', light: '#7A8CC8', intensity: 0.12 },
  { altitude: -6, sky: '#26305E', horizon: '#6A4E7E', shade: 'rgba(20, 22, 60, 0.36)', light: '#9C8FC8', intensity: 0.2 },
  { altitude: -1, sky: '#5D5A8E', horizon: '#F08A5D', shade: 'rgba(70, 40, 80, 0.2)', light: '#FF9A6A', intensity: 0.3 },
  { altitude: 4, sky: '#8DA9D8', horizon: '#FFC48A', shade: 'rgba(255, 140, 60, 0.12)', light: '#FFC48A', intensity: 0.4 },
  { altitude: 12, sky: '#A9C8EF', horizon: '#E9F0FA', shade: 'rgba(255, 255, 255, 0)', light: '#FFFFFF', intensity: 0.5 },
]

// "#A9C8EF" → [169, 200, 239]
function hexToRgb(hex) {
  return [parseInt(hex.slice(1, 3), 16), parseInt(hex.slice(3, 5), 16), parseInt(hex.slice(5, 7), 16)]
}

// 두 색을 rate(0~1)만큼 섞기
function mixHex(from, to, rate) {
  const a = hexToRgb(from)
  const b = hexToRgb(to)
  let text = '#'
  for (let i = 0; i < 3; i++) text += Math.round(a[i] + (b[i] - a[i]) * rate).toString(16).padStart(2, '0')
  return text
}

// "rgba(6, 12, 36, 0.52)" 두 개를 섞기
function mixRgba(from, to, rate) {
  const a = from.match(/[\d.]+/g).map(Number)
  const b = to.match(/[\d.]+/g).map(Number)
  const value = []
  for (let i = 0; i < 4; i++) value.push(a[i] + (b[i] - a[i]) * rate)
  return 'rgba(' + Math.round(value[0]) + ', ' + Math.round(value[1]) + ', ' + Math.round(value[2]) + ', ' + value[3].toFixed(2) + ')'
}

/** 해 높이 → 하늘 색·지평선 색·화면 덮개 색·건물 비추는 빛 색과 세기 */
export function skyAt(altitude) {
  const first = SKY_STEPS[0]
  const last = SKY_STEPS[SKY_STEPS.length - 1]
  if (altitude <= first.altitude) return first
  if (altitude >= last.altitude) return last
  let i = 0
  while (SKY_STEPS[i + 1].altitude < altitude) i++
  const from = SKY_STEPS[i]
  const to = SKY_STEPS[i + 1]
  const rate = (altitude - from.altitude) / (to.altitude - from.altitude)
  return {
    sky: mixHex(from.sky, to.sky, rate),
    horizon: mixHex(from.horizon, to.horizon, rate),
    shade: mixRgba(from.shade, to.shade, rate),
    light: mixHex(from.light, to.light, rate),
    intensity: from.intensity + (to.intensity - from.intensity) * rate,
  }
}
