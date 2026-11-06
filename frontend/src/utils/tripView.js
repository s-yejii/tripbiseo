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

// trip: 서버 Trip 또는 초안 / check: POST /plans/check 응답(없으면 null) / day: 몇째 날
export function toViewTrip(trip, check = null, day = 1) {
  const dayStops = check ? check.stops : trip.stops.filter((stop) => (stop.day || 1) === day && stop.state !== 'skipped')
  const positions = toMapPositions(dayStops, TRIP_MAP_BOX)
  const stops = dayStops.map((stop, i) => toViewStop(stop, positions[i], findMemo(trip, stop)))
  let move = 0
  for (const stop of stops) move += stop.leg ? stop.leg.min : 0
  const view = {
    id: trip.id,
    title: trip.title,
    date: toShortDate(trip.startDate),
    who: (trip.people || 1) + '명',
    move,
    stops,
    checks: check ? toChecks(check) : [],
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

// 칸 메모 (점검 결과 칸에는 memo가 없어서 원래 일정에서 이름·날짜로 찾음). AI 일정이면 고른 이유·리뷰 근거
function findMemo(trip, stop) {
  if (stop.memo) return stop.memo
  const original = trip.stops.find((s) => s.name === stop.name && (s.day || 1) === (stop.day || 1))
  return original ? original.memo : null
}

// 일정 한 칸 (check 결과 칸이면 arrive·issue·leg가 있음)
function toViewStop(stop, position, memo = null) {
  const stay = stop.stay || 0
  return {
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
    leg: stop.leg ? { mode: MODE_OF_LEG[stop.leg.mode] || 'walk', min: stop.leg.minutes, walkMin: stop.leg.walkMinutes || 0 } : null,
    x: position.x,
    y: position.y,
  }
}

// 점검 결과의 문제 줄 → "미리 확인할 것"
function toChecks(check) {
  const items = []
  for (const stop of check.stops) {
    if (stop.issue) items.push({ kind: 'late', icon: 'i-warning', title: stop.name, desc: stop.issue })
  }
  if (check.fixed) {
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
