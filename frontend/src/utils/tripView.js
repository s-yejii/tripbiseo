// 서버 여행(Trip) → 일정 화면(TripPage·TripTimeline)이 쓰는 모양으로 바꾸기
// 화면 모양은 원래 가짜 데이터(data/trips.js)와 같아요: time, name, dur, img, meta, imp, wx, fixed, leg, x, y
import { imageFor, isStayStop, toMapPositions } from '@/utils/mapView'
import { toDuration } from '@/utils/time'
import { appNow } from '@/utils/devMode'

const WEEK = ['일', '월', '화', '수', '목', '금', '토']
const TRIP_MAP_BOX = { left: 30, top: 110, width: 330, height: 210 } // TripPage 지도 칸 (viewBox 390×344)
// 서버 Leg.mode(walk/transit/car) → 화면 이동 수단 이름 (Stop.mode와 같은 이름: walk/transit/taxi)
const MODE_OF_LEG = { walk: 'walk', transit: 'transit', car: 'taxi' }

// "2026-10-24" → "10/24(토)"
export function toShortDate(dateText) {
  if (!dateText) return ''
  const date = new Date(dateText + 'T00:00:00')
  return `${date.getMonth() + 1}/${date.getDate()}(${WEEK[date.getDay()]})`
}

// 서버 이동 수단 → 화면 글자
const TRANSPORT_NAMES = { subway: '지하철', bus: '버스', walk: '도보', taxi: '택시', car: '자동차' }
const DAY_MS = 86400000 // 하루(밀리초)

// "2026-10-24" → 오늘부터 며칠 남았는지 (오늘 0, 지난 날은 음수)
export function daysLeft(dateText) {
  const today = appNow() // 개발자 모드면 시연 시계 (11/06)
  today.setHours(0, 0, 0, 0)
  return Math.round((new Date(dateText + 'T00:00:00') - today) / DAY_MS)
}

// 오늘이 여행 기간 안인지
export function isTodayTrip(trip) {
  return daysLeft(trip.startDate) <= 0 && daysLeft(trip.endDate) >= 0
}

// 여행 배지: 여행 중 / 오늘 / D-3
export function tripBadge(trip) {
  if (trip.status === 'ongoing') return { text: '여행 중', cls: 'badge--coral' }
  if (isTodayTrip(trip)) return { text: '오늘', cls: 'badge--coral' }
  if (daysLeft(trip.endDate) < 0) return { text: '지난 일정', cls: 'badge--neutral' } // 안 떠나고 날짜가 지난 여행
  return { text: 'D-' + daysLeft(trip.startDate), cls: 'badge--navy' }
}

// ["subway","walk"] → "지하철·도보"
export function toTransportText(transports) {
  const names = []
  for (const key of transports || []) names.push(TRANSPORT_NAMES[key] || key)
  return names.join('·')
}

// 홈에 크게 보여 줄 여행 하나: 여행 중 → 오늘 여행 → 가장 가까운 다가오는 여행 (없으면 null)
// trips는 서버가 시작 날짜 최신순으로 줘요
export function pickHomeTrip(trips) {
  let next = null
  for (const trip of trips) {
    if (trip.status === 'ongoing') return trip
    if (trip.status !== 'planned') continue
    if (isTodayTrip(trip)) return trip
    if (daysLeft(trip.startDate) > 0 && (!next || trip.startDate < next.startDate)) next = trip
  }
  return next
}

// 여행 상세 화면 주소 (오늘·여행 중이면 오늘 화면, 아니면 여행 전 화면)
export function tripDetailLink(trip) {
  if (trip.status === 'ongoing' || isTodayTrip(trip)) return '/trips/today?id=' + trip.id
  return '/trips/detail?id=' + trip.id
}

const LOCATION_UNKNOWN_MEMO = '위치 확인 필요' // ③이 가게 위치를 못 찾은 예약 칸에 붙이는 메모 앞머리 (PlanServiceImpl UNLOCATED_MEMO)

// 가게 위치를 못 찾아 여행 지역 가운데에 둔 칸인지 (지금은 메모로 판단. ③이 따로 값을 주면 여기만 바꿈)
export function isLocationUnknown(stop) {
  return (stop.memo || '').startsWith(LOCATION_UNKNOWN_MEMO)
}

// trip: 서버 Trip 또는 초안 / check: POST /plans/check 응답(없으면 null) / day: 몇째 날
export function toViewTrip(trip, check = null, day = 1) {
  const dayStops = check ? check.stops : trip.stops.filter((stop) => (stop.day || 1) === day && stop.state !== 'skipped')
  const positions = toMapPositions(dayStops, TRIP_MAP_BOX)
  const stops = dayStops.map((stop, i) => toViewStop(stop, positions[i], findMemo(trip, stop)))
  markUnknownLegs(stops)
  // 화면마다 출발지가 있거나 없어서 합계가 달라지지 않게, 장소 사이 이동만 센다 (그날 첫 칸의 leg = 출발지·숙소에서 오는 길은 뺌)
  // 위치를 모르는 칸을 오가는 구간은 가짜 좌표로 잰 시간이라 합계에서도 뺌
  let move = 0
  for (let i = 1; i < stops.length; i++) {
    const leg = stops[i].leg
    if (leg && !leg.isUnknown) move += leg.min
  }
  const view = {
    id: trip.id,
    title: trip.title,
    date: toShortDate(trip.startDate),
    who: (trip.people || 1) + '명',
    move,
    stops,
    checks: check ? toChecks(check, stops) : [],
    pack: [],
  }
  if (check && check.slackMin != null) view.slack = check.slackMin
  // 구간별 실제 경로선 (점검 결과의 legs: 길찾기 API 좌표, 실패하면 두 점) → 지도에 구불구불한 길 (11/04)
  // from·to: 구간 앞뒤 장소 좌표 → 대중교통 선이 역·정류장에서 끝나면 장소까지 걷는 점으로 이어요 (KakaoMap)
  view.legs = check && check.legs ? check.legs.map((leg, i) => toViewLeg(leg, check.stops[i], check.stops[i + 1])) : []
  return view
}

// 구간 하나: 경로선 + 수단 + 앞뒤 장소 좌표 [위도, 경도] (없으면 null)
function toViewLeg(leg, fromStop, toStop) {
  return {
    coords: leg.coords || [],
    mode: leg.mode,
    from: fromStop && fromStop.lat != null ? [fromStop.lat, fromStop.lng] : null,
    to: toStop && toStop.lat != null ? [toStop.lat, toStop.lng] : null,
  }
}

// 위치를 모르는 칸으로 오는 구간·그 칸에서 다음 칸으로 가는 구간에 isUnknown 표시 → 화면에 "이동 시간 확인 필요"
function markUnknownLegs(stops) {
  for (let i = 0; i < stops.length; i++) {
    const before = stops[i - 1]
    const isNearUnknown = stops[i].locationUnknown || (before && before.locationUnknown)
    if (stops[i].leg && isNearUnknown) stops[i].leg.isUnknown = true
  }
}

// 칸 메모 (점검 결과 칸에는 memo가 없어서 원래 일정에서 이름·날짜로 찾음). AI 일정이면 고른 이유·리뷰 근거
function findMemo(trip, stop) {
  if (stop.memo) return stop.memo
  const original = trip.stops.find((s) => s.name === stop.name && (s.day || 1) === (stop.day || 1))
  return original ? original.memo : null
}

// 일정 한 칸 (check 결과 칸이면 arrive·issue·leg가 있음)
function toViewStop(stop, position, memo = null) {
  const stay = stop.stay || 0
  const viewStop = {
    id: stop.id ?? stop.placeId ?? stop.name,
    serverId: stop.id ?? null,
    placeId: stop.placeId ?? null,
    time: stop.time || stop.arrive || '',
    name: stop.name,
    category: stop.category,
    address: stop.address,
    dur: stay,
    img: imageFor(stop.category),
    meta: (stop.category ? stop.category + ' · ' : '') + toDuration(stay),
    metaIcon: isStayStop(stop) ? 'i-home' : stop.fixed ? 'i-lock' : stop.issue ? 'i-warning' : null,
    metaCls: isStayStop(stop) || stop.fixed ? 'c-navy' : stop.issue ? 'c-warning' : null,
    isStay: isStayStop(stop), // 숙소 칸 → 지도에 숙소 핀 (11/04)
    movable: !stop.fixed && !isStayStop(stop), // 목록에서 끌어 순서를 바꿀 수 있는 칸 (고정·숙소는 그대로)
    memo,
    imp: null, // 중요도·날씨는 서버에 아직 없어서 비워 둠 (화면에서 안 보임)
    wx: null,
    fixed: !!stop.fixed,
    lat: stop.lat,
    lng: stop.lng,
    warn: !!stop.issue,
    // walkMin: 그중 걷는 시간 (대중교통이면 역·정류장까지·갈아탈 때, 11/04)
    leg: stop.leg ? { mode: MODE_OF_LEG[stop.leg.mode] || 'walk', min: stop.leg.minutes, walkMin: stop.leg.walkMinutes || 0, from: stop.leg.fromName || '' } : null,
    x: position.x,
    y: position.y,
  }
  viewStop.locationUnknown = isLocationUnknown(viewStop) // 점검 칸엔 memo가 없어서, findMemo로 찾은 memo가 담긴 화면 칸으로 판단
  return viewStop
}

const FIXED_CHECK_SLACK_MIN = 10 // 예약까지 여유가 이보다 적어야 "미리 확인할 것"에 넣음

// 점검 결과의 문제 줄 → "미리 확인할 것" (stops: 화면 칸, 위치를 모르는 칸을 알려 주려고)
function toChecks(check, stops) {
  const items = []
  for (const stop of check.stops) {
    if (stop.issue) items.push({ kind: 'late', icon: 'i-warning', title: stop.name, desc: stop.issue })
  }
  // 위치를 모르는 칸: 늦음이 아니라 확인할 일이라 kind는 'late'가 아닌 'place' (빨간 "문제 있음"으로 안 바뀜)
  for (const stop of stops) {
    if (stop.locationUnknown) items.push({ kind: 'place', icon: 'i-pin', title: stop.name, desc: '가게 위치를 못 찾았어요. 위치를 확인하면 이동 시간이 맞아져요' })
  }
  // 예약(시간 고정)은 빠듯하거나(여유 10분 미만) 늦을 때만 (11/09 주형: 여유 있는데 왜 말해줌?). 여유가 넉넉하면 넣지 않음
  if (check.fixed && check.fixed.slack < FIXED_CHECK_SLACK_MIN) {
    const slack = check.fixed.slack
    items.push({
      kind: slack < 0 ? 'late' : 'event',
      icon: 'i-lock',
      title: `${check.fixed.name} ${check.fixed.time}`,
      desc: slack < 0 ? `지금 일정대로면 ${-slack}분 늦어요` : `${slack}분 여유가 있어요`,
    })
  }
  return items
}
