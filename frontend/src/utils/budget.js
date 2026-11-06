// 1인 예상 금액 (③). 인원으로 나누지 않고 1인 기준으로 더해요. 실제 가격이 아니라 예상이에요
// 우리 장소 DB(retrip_place)에는 가격·메뉴가·입장료 칸이 없어서, 아래 기본값만 써요
// 확인한 날: 2026-11-04. "추정"은 공식 통계로 확인하지 못한 값

// 한 끼: (서울 냉면 12,615원 + 서울 김치찌개 백반 8,654원) ÷ 2. 참가격(한국소비자원) 2026년 조사를 인용한 보도 수치 (원자료는 못 봄)
const MEAL_WON = 10600
// 술집 1인: 추정 (안주 + 술 한두 잔)
const BAR_WON = 20000
// 카페 1인: 추정 (아메리카노 저가 1,700~1,800원, 스타벅스 톨 4,700원 사이. 보도 수치)
const CAFE_WON = 4000
// 관광지·거리·공원: 추정 (입장료 정보가 없어 무료로 봄. 고궁처럼 유료인 곳도 무료로 나와요)
const TOUR_WON = 0
// 전시·공연 같은 문화시설: 추정
const CULTURE_WON = 5000
// 쇼핑: 구경만 하는 걸로 (추정)
const SHOP_WON = 0
// 대중교통 한 구간: 서울 지하철 교통카드 1,550원. 서울시 요금 안내 기준일 2025-06-28 (news.seoul.go.kr/traffic/traffic_price)
// 금액은 그 페이지 이미지에 있어 글자로 확인 못 했고, 1,550원은 요금표 정리 글 기준. 시내버스 1,500원은 같은 페이지 글에서 확인
// 수단이 지하철·버스로 나뉘지 않아 더 비싼 지하철 요금을 써요. 환승 할인은 안 뺌
const TRANSIT_WON = 1550
// 택시 한 구간: 서울 중형 기본요금 4,800원 (같은 서울시 페이지, 2023-02-01 조정). 거리 요금은 안 더함
const TAXI_WON = 4800

const WON_UNIT = 1000
const MAN_WON = 10000

// 서버 PlaceMapper.kindCase와 같은 기준으로 분류 글자 → 종류
export function kindOf(category) {
  const text = category || ''
  if (text === '숙소' || text === '숙박') return 'stay'
  if (/관광지|축제|레포츠/.test(text)) return 'tour'
  if (/문화시설/.test(text)) return 'culture'
  if (/쇼핑/.test(text)) return 'shop'
  if (/카페|커피|디저트|베이커리|케이크|도넛|와플|아이스크림|베이글|크레페|^차$/.test(text)) return 'cafe'
  if (/이자카야|주점|맥주|호프|포장마차|바\(BAR\)|와인/.test(text)) return 'bar'
  return 'food'
}

const WON_OF_KIND = { food: MEAL_WON, bar: BAR_WON, cafe: CAFE_WON, tour: TOUR_WON, culture: CULTURE_WON, shop: SHOP_WON, stay: null }

// 장소 한 곳의 1인 금액. 숙소처럼 넣지 않는 곳은 null
export function stopWon(stop) {
  return WON_OF_KIND[kindOf(stop.category)]
}

// 구간 한 번의 교통비 (view 칸의 leg: { mode: walk·transit·taxi })
function legWon(leg) {
  if (!leg) return 0
  if (leg.mode === 'transit') return TRANSIT_WON
  if (leg.mode === 'taxi') return TAXI_WON
  return 0
}

// 일정 전체 1인 금액: { meal, cafe, entry, transit, total } (식사에 술집 포함, 입장료에 문화시설 포함)
export function tripBudget(stops) {
  const sum = { meal: 0, cafe: 0, entry: 0, transit: 0 }
  for (const stop of stops) {
    const kind = kindOf(stop.category)
    const won = stopWon(stop) || 0
    if (kind === 'food' || kind === 'bar') sum.meal += won
    else if (kind === 'cafe') sum.cafe += won
    else sum.entry += won
    sum.transit += legWon(stop.leg)
  }
  return { ...sum, total: sum.meal + sum.cafe + sum.entry + sum.transit }
}

// 금액 글자: 천원 단위 반올림. 1만원 이상 "약 4.2만원", 미만 "약 8천원", 0이면 "무료"
export function toWonText(won) {
  const rounded = Math.round(won / WON_UNIT) * WON_UNIT
  if (rounded === 0) return '무료'
  if (rounded >= MAN_WON) return '약 ' + rounded / MAN_WON + '만원'
  return '약 ' + rounded / WON_UNIT + '천원'
}
