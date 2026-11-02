// 서버 여행(Trip) → 일정 화면(TripPage·TripTimeline)이 쓰는 모양으로 바꾸기
// 화면 모양은 원래 가짜 데이터(data/trips.js)와 같아요: time, name, dur, img, meta, imp, wx, fixed, leg, x, y
import { imageFor, toMapPositions } from '@/utils/mapView'
import { toDuration } from '@/utils/time'

const WEEK = ['일', '월', '화', '수', '목', '금', '토']
const TRIP_MAP_BOX = { left: 30, top: 110, width: 330, height: 210 } // TripPage 지도 칸 (viewBox 390×344)
// 서버 Leg.mode(walk/transit/car) → 화면 이동 수단 이름
const MODE_OF_LEG = { walk: 'walk', transit: 'subway', car: 'taxi' }

// "2026-10-24" → "10/24(토)"
export function toShortDate(dateText) {
  if (!dateText) return ''
  const date = new Date(dateText + 'T00:00:00')
  return `${date.getMonth() + 1}/${date.getDate()}(${WEEK[date.getDay()]})`
}

// trip: 서버 Trip 또는 초안 / check: POST /plans/check 응답(없으면 null) / day: 몇째 날
export function toViewTrip(trip, check = null, day = 1) {
  const dayStops = check ? check.stops : trip.stops.filter((stop) => (stop.day || 1) === day && stop.state !== 'skipped')
  const positions = toMapPositions(dayStops, TRIP_MAP_BOX)
  const stops = dayStops.map((stop, i) => toViewStop(stop, positions[i]))
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
  return view
}

// 일정 한 칸 (check 결과 칸이면 arrive·issue·leg가 있음)
function toViewStop(stop, position) {
  const stay = stop.stay || 0
  return {
    id: stop.id ?? stop.placeId ?? stop.name,
    serverId: stop.id ?? null,
    placeId: stop.placeId ?? null,
    time: stop.time || stop.arrive || '',
    name: stop.name,
    dur: stay,
    img: imageFor(stop.category),
    meta: (stop.category ? stop.category + ' · ' : '') + toDuration(stay),
    metaIcon: stop.fixed ? 'i-lock' : stop.issue ? 'i-warning' : null,
    metaCls: stop.fixed ? 'c-navy' : stop.issue ? 'c-warning' : null,
    imp: 'maybe',
    wx: 'cloud',
    fixed: !!stop.fixed,
    warn: !!stop.issue,
    leg: stop.leg ? { mode: MODE_OF_LEG[stop.leg.mode] || 'walk', min: stop.leg.minutes, walk: stop.leg.minutes } : null,
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
