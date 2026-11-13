// ⑤ 웹 회고 풍경 분석 3단계: 구간 성격 분류 (11/11)
// 지금은 "규칙 분류기"(사람이 정한 기준)로 시작해요. 나중에 직접 만든 모델이나 AI로 바꿀 때는
// classifySegments(list, 새분류기)처럼 분류기만 바꿔 끼우면 돼요. 분류기 모양: (특징값, 구간) → { type, confidence, reasons }
// 규칙 분류기는 모델을 만들 때 "기준 답(베이스라인)"으로도 써요 (모델이 이보다 나은지 비교)

/** 구간 성격 (정해진 목록. 분류기는 이 중에서만 고름) */
export const LANDSCAPE_TYPES = [
  { id: 'forest', name: '숲길 산책', color: '#2E8B57' },
  { id: 'water', name: '수변 산책·휴식', color: '#1E88E5' },
  { id: 'hill', name: '언덕 골목', color: '#B5651D' },
  { id: 'heritage', name: '궁궐·유적', color: '#8E44AD' },
  { id: 'shopping', name: '상점가', color: '#E8603F' },
  { id: 'city', name: '도심 걷기', color: '#7C8496' },
  { id: 'transit', name: '대중교통·차 이동', color: '#3B5BDB' },
  { id: 'indoor', name: '실내 머묾', color: '#F2A33A' },
]

const TRANSIT_KMH = 12          // 이보다 빠르면 걸은 게 아니라 탄 것
const HILL_SLOPE = 0.06         // 오르내린 높이 ÷ 길이가 6% 넘으면 언덕
const HILL_GAIN_M = 10          // 한 구간(약 250m)에서 이만큼 넘게 오르면 언덕
const GREEN_RATIO = 0.4         // 토지피복 숲·풀 비율이 이보다 크면 숲길
const GREEN_NDVI = 0.45         // NDVI가 이보다 크면 숲길
const WATER_RATIO = 0.2         // 토지피복 물 비율이 이보다 크면 수변
const SHOP_PLACE_COUNT = 6      // 근처 음식점·가게가 이만큼 넘으면 상점가

// 장소 이름·분류에 들어 있으면 그 성격으로 보는 말
const HERITAGE_WORDS = /(궁$|궁 |궁과|고궁|궁궐|종묘|사적|유적|박물관|성곽|향교|서원|왕릉|문화재|한옥마을)/
const WATER_WORDS = /(한강|강변|호수|저수지|수변|청계천|천변|분수|폭포|해변)/
const FOREST_WORDS = /(숲|공원|수목원|둘레길|정원|후원|산책로|자연휴양|[가-힣]산$)/
const SHOP_WORDS = /(시장|쇼핑|백화점|아울렛|몰|상가|상점|거리|타임스퀘어)/
const INDOOR_WORDS = /(숙소|호텔|모텔|게스트하우스|카페|커피|디저트|음식|식당|한식|중식|일식|양식|EAT|이자카야|술집|베이커리)/

function typeOf(id) {
  for (const type of LANDSCAPE_TYPES) {
    if (type.id === id) return type
  }
  return LANDSCAPE_TYPES[5]
}

function answer(id, confidence, reasons) {
  return { type: typeOf(id), confidence: confidence, reasons: reasons }
}

// 근처 장소 이름·분류 중 이 말이 든 것 (가까운 것부터)
function placeWith(places, words) {
  for (const place of places) {
    if (words.test(place.name) || words.test(place.category)) return place
  }
  return null
}

function countShops(places) {
  let count = 0
  for (const place of places) {
    if (SHOP_WORDS.test(place.name) || INDOOR_WORDS.test(place.category) || INDOOR_WORDS.test(place.name)) count++
  }
  return count
}

function percent(value) {
  return Math.round(value * 100) + '%'
}

/** 규칙 분류기: 걸은 구간 */
function classifyMove(features) {
  if (features.speedKmh >= TRANSIT_KMH) return answer('transit', 0.9, ['평균 ' + Math.round(features.speedKmh) + 'km/h'])
  if (features.avgSlope !== null && (features.avgSlope >= HILL_SLOPE || features.gainM >= HILL_GAIN_M)) {
    return answer('hill', 0.8, ['오른 높이 ' + features.gainM + 'm', '평균 경사 ' + percent(features.avgSlope)])
  }
  if (features.water !== null && features.water >= WATER_RATIO) return answer('water', 0.85, ['물 ' + percent(features.water)])
  if (features.green !== null && features.green >= GREEN_RATIO) return answer('forest', 0.85, ['숲·풀 ' + percent(features.green)])
  if (features.ndvi !== null && features.ndvi >= GREEN_NDVI) return answer('forest', 0.8, ['NDVI ' + features.ndvi.toFixed(2)])
  return classifyByPlaces(features.places, features)
}

/** 규칙 분류기: 머문 곳 (일정 칸 이름·분류가 있으면 그걸 먼저) */
function classifyStay(features, segment) {
  const own = { name: segment.name || '', category: segment.category || '' }
  const places = segment.name ? [own].concat(features.places) : features.places
  const byPlace = classifyByPlaces(places, features)
  if (byPlace.type.id !== 'city' && byPlace.type.id !== 'shopping') return byPlace
  if (INDOOR_WORDS.test(own.name + ' ' + own.category)) return answer('indoor', 0.75, [own.category || own.name])
  if (byPlace.type.id === 'shopping') return byPlace
  return answer('indoor', 0.4, ['머문 ' + segment.minutes + '분 · 주변 장소로 추정'])
}

// 근처 장소로 판단 (궁궐·물가·숲·상점가 순서)
function classifyByPlaces(places, features) {
  const heritage = placeWith(places, HERITAGE_WORDS)
  if (heritage) return answer('heritage', 0.7, ['근처 ' + heritage.name])
  const water = placeWith(places, WATER_WORDS)
  if (water) return answer('water', 0.6, ['근처 ' + water.name])
  const forest = placeWith(places, FOREST_WORDS)
  if (forest) return answer('forest', 0.55, ['근처 ' + forest.name])
  const shops = countShops(places)
  if (shops >= SHOP_PLACE_COUNT) return answer('shopping', 0.6, ['가게·음식점 ' + shops + '곳'])
  return answer('city', 0.4, features.lengthM > 0 ? [features.lengthM + 'm 걸음'] : ['특징 없음'])
}

/** 규칙 분류기 (기본) */
export function classifyByRules(features, segment) {
  if (segment.kind === 'stay') return classifyStay(features, segment)
  return classifyMove(features)
}

/**
 * 구간 목록 분류. list: [{ segment, features }] → 같은 순서로 결과를 붙여 돌려줌
 * classifier를 바꾸면 모델·AI 분류기로 바뀜 (Promise를 돌려줘도 됨)
 */
export async function classifySegments(list, classifier) {
  const use = classifier || classifyByRules
  const result = []
  for (const item of list) {
    const verdict = await use(item.features, item.segment)
    result.push(Object.assign({}, item, verdict))
  }
  return result
}
