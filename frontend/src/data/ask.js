// 가짜 데이터: 여행 중 AI 채팅 (④)
// 지금은 낱말 규칙으로 흉내만 내요. Gemini는 부르지 않아요
// 백엔드가 붙으면 아래 함수 대신 API를 불러요 (응답 모양은 docs/API.md와 같아요)
//   askAssist(message)          → POST /api/trips/{id}/live/assist
//   askRecommend(category, ...) → POST /api/trips/{id}/live/recommend
import { nearPlaces } from '@/data/near'
import { todayStops, reserveTime, planArrive, lateArrive, lateMinutes } from '@/data/live'
import { toTime } from '@/utils/time'

// 입력창 아래 예시 칩
export const examples = ['근처 카페 추천해 줘', '비 와서 실내로 바꿔 줘', '다음 일정 뭐야?']

// 반경 칩 (m). API는 100~3000m까지 받아요
export const radiusOptions = [300, 500, 1000]

// 추천 종류: 이 낱말이 들어가면 그 종류를 추천해요
const CATEGORIES = [
  { category: '카페', kind: 'cafe', words: /카페|커피|디저트|빵|라떼|앉/ },
  { category: '식당', kind: 'food', words: /밥|맛집|식당|배고|점심|저녁|먹/ },
  { category: '술집', kind: 'pub', words: /술|맥주|펍/ },
]
const REPLAN_WORDS = /비|실내|우산|늦|바꿔|더 있|다시/
const RAIN_WORDS = /비|실내|우산/
const NEXT_WORDS = /다음|어디/
const TIME_WORDS = /남았|여유|몇 시|공연/

const MAP_METERS_PER_PX = 4 // 지도 그림 1px ≈ 4m
const TOP_COUNT = 3 // 추천은 평점 상위 3곳

// 비·실내 요청인지 (비 요청은 "비 올 때 바꾸기" 화면으로 보내요, 10/30 결정)
export function isRainRequest(message) {
  return RAIN_WORDS.test(message)
}

// 사용자 말 → assist 응답 { kind, ... }
// kind: 'ask-radius'(추천) / 'alternatives'(일정 바꾸기) / 'text'(그 밖의 질문)
export function askAssist(message, late) {
  for (const item of CATEGORIES) {
    if (item.words.test(message)) return { kind: 'ask-radius', category: item.category }
  }
  if (message.includes('추천')) return { kind: 'ask-radius', category: '카페' }
  if (REPLAN_WORDS.test(message)) return { kind: 'alternatives', data: { title: '남은 일정 대안' } }
  return { kind: 'text', text: answerQuestion(message, late) }
}

// 일정 질문 답: 서버가 가진 오늘 일정 안에서만 답해요
function answerQuestion(message, late) {
  if (NEXT_WORDS.test(message)) {
    if (late) return `다음은 ${todayStops[3].name}이에요. ${toTime(lateArrive[4])}에 도착할 것 같아요.`
    return `다음은 ${todayStops[0].name}이에요. 걸어서 5분, 13:00에 도착해요.`
  }
  if (TIME_WORDS.test(message)) {
    if (late) return `이대로면 공연 입장에 ${lateMinutes}분 늦어요. '대안 보기'로 남은 일정을 줄일 수 있어요.`
    return `공연 입장(18:30)까지 ${reserveTime - planArrive}분 여유가 있어요.`
  }
  return '지금은 근처 장소 추천, 일정 바꾸기, 오늘 일정 질문을 도와드릴 수 있어요.'
}

// 추천 → recommend 응답 { places, closedSkipped }
// places 항목은 API의 장소 카드 모양 + 화면용 x, y, img (진짜 API는 lat, lng)
export function askRecommend(category, radius, me) {
  const kind = CATEGORIES.find((item) => item.category === category).kind
  const found = []
  for (const place of nearPlaces) {
    if (place.kind !== kind) continue
    const distance = distanceFrom(me, place)
    if (distance > radius) continue
    found.push({
      id: place.id,
      name: place.name,
      category: place.cat,
      rating: place.rate,
      distance: distance,
      open: true,
      closeAt: place.hours.split('–')[1],
      img: place.img,
      x: place.x,
      y: place.y,
    })
  }
  found.sort((a, b) => b.rating - a.rating)
  return { places: found.slice(0, TOP_COUNT), closedSkipped: 0 }
}

// 지금 위치에서 거리(m), 10m 단위
function distanceFrom(me, place) {
  const px = Math.hypot(place.x - me.x, place.y - me.y)
  return Math.round((px * MAP_METERS_PER_PX) / 10) * 10
}
