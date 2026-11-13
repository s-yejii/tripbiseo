// ⑤ 웹 회고: 계절별 숲 색 (11/11)
// 숲 구역(산림청 임상도: 침엽수·활엽수·혼효림)을 여행 간 계절 + 그 시기 위성 NDVI로 칠해요
// 색 고르는 법: 계절로 "어떤 색 계열"(봄 연두, 여름 초록, 가을 단풍, 겨울 갈색)을 정하고, NDVI로 그 안에서 정함
//  - 봄·여름: NDVI가 클수록 짙은 초록
//  - 가을: 여름보다 NDVI가 얼마나 줄었나(감소율)로 단풍 단계. 서울 활엽수 숲 실제 값: 여름 0.86 → 10/28 0.83 (3.5% 감소, 물들기 시작)
//  - 겨울: 활엽수는 잎 진 갈색, 소나무 등 침엽수는 초록 (1/31 실제 값: 침엽수 0.47, 활엽수 0.31)
// NDVI가 없으면 계절 기본색

export const SEASONS = {
  spring: { name: '봄', months: [3, 4, 5] },
  summer: { name: '여름', months: [6, 7, 8] },
  autumn: { name: '가을', months: [9, 10, 11] },
  winter: { name: '겨울', months: [12, 1, 2] },
}

// 가을 단풍 단계: 여름 대비 NDVI 감소율 기준
export const AUTUMN_STAGES = [
  { id: 'green', name: '아직 초록', below: 0.04 },
  { id: 'turning', name: '물드는 중', below: 0.1 },
  { id: 'peak', name: '단풍 절정', below: 0.2 },
  { id: 'falling', name: '지는 중', below: Infinity },
]

/** "2026-10-31" → 'autumn' */
export function seasonOf(dateText) {
  const month = Number(dateText.slice(5, 7))
  for (const key in SEASONS) {
    if (SEASONS[key].months.includes(month)) return key
  }
  return 'summer'
}

/** 가을 감소율 → 단풍 단계 id. 감소율이 없으면 null */
export function autumnStageOf(drop) {
  if (drop === null) return null
  for (const stage of AUTUMN_STAGES) {
    if (drop < stage.below) return stage.id
  }
  return 'falling'
}

const BROADLEAF_AUTUMN = { green: '#7CB342', turning: '#F4B400', peak: '#E67E22', falling: '#B5532C' }
const MIXED_AUTUMN = { green: '#689F38', turning: '#C9A227', peak: '#C17A2B', falling: '#8D5A3B' }

// 활엽수: 계절 × NDVI
function broadleafColor(season, ndvi, drop) {
  if (season === 'spring') {
    if (ndvi === null) return '#8BC34A'
    if (ndvi < 0.45) return '#C5E1A5' // 새잎이 막 나옴 (4월 초 서울 0.44)
    return ndvi < 0.6 ? '#9CCC65' : '#66BB6A'
  }
  if (season === 'summer') return ndvi !== null && ndvi < 0.75 ? '#66BB6A' : '#2E7D32'
  if (season === 'autumn') return BROADLEAF_AUTUMN[autumnStageOf(drop)] || '#E67E22'
  return '#A1887F' // 겨울: 잎 진 갈색·회색
}

// 침엽수(소나무 등): 늘 푸름. 겨울·NDVI 낮으면 조금 어둡게
function coniferColor(season, ndvi) {
  if (season === 'winter' || (ndvi !== null && ndvi < 0.4)) return '#2F5D3A'
  return '#1B5E20'
}

function mixedColor(season, ndvi, drop) {
  if (season === 'autumn') return MIXED_AUTUMN[autumnStageOf(drop)] || '#C17A2B'
  if (season === 'winter') return '#6D6A4F'
  if (season === 'spring') return ndvi !== null && ndvi < 0.45 ? '#9CCC65' : '#43A047'
  return '#388E3C'
}

/**
 * 숲 구역 색. kind: conifer | broadleaf | mixed, ndvi: 그 시기 0~1 또는 null
 * drop: 가을에 여름 대비 감소율 (0.1 = 10% 줄어듦) 또는 null
 */
export function forestColor(kind, season, ndvi, drop) {
  if (kind === 'conifer') return coniferColor(season, ndvi)
  if (kind === 'broadleaf') return broadleafColor(season, ndvi, drop)
  return mixedColor(season, ndvi, drop)
}

// 속성 이름 ndvi_202610 → 달 번호(년×12 + 월)
function monthIndexOf(key) {
  return Number(key.slice(5, 9)) * 12 + Number(key.slice(9, 11))
}

/**
 * 여행 날짜와 가장 가까운 달의 NDVI 고르기
 * 숲 구역 속성에 ndvi_202610 처럼 달마다 값이 있으면 가장 가까운 달 (해가 달라도 같은 달이면 가깝게 봄)
 */
export function pickNdvi(properties, dateText) {
  const target = Number(dateText.slice(0, 4)) * 12 + Number(dateText.slice(5, 7))
  let best = null
  let bestGap = Infinity
  for (const key in properties) {
    if (!key.startsWith('ndvi_') || properties[key] === null) continue
    const gap = Math.abs(monthIndexOf(key) - target) % 12
    if (Math.min(gap, 12 - gap) < bestGap) {
      bestGap = Math.min(gap, 12 - gap)
      best = properties[key]
    }
  }
  return best
}

/** 여름(6~8월) NDVI. 없으면 null */
export function summerNdvi(properties) {
  for (const key in properties) {
    const month = Number(key.slice(9, 11))
    if (key.startsWith('ndvi_') && properties[key] !== null && month >= 6 && month <= 8) return properties[key]
  }
  return null
}

/** 가을 감소율 = (여름 − 그 시기) ÷ 여름. 둘 중 하나라도 없으면 null */
export function autumnDrop(properties, dateText) {
  const summer = summerNdvi(properties)
  const now = pickNdvi(properties, dateText)
  if (summer === null || now === null || summer <= 0) return null
  return (summer - now) / summer
}
