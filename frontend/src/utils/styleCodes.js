// 여행 스타일 칩 코드 (11/03). 서버의 장소 종류(PlaceMapper kindCase)·리뷰 태그(PlaceTagReader)와 같은 코드를 써요
//   → 일정 만들기·나중의 재분석(member_style)이 같은 기준으로 비교할 수 있게
export const KIND_CHIPS = [
  { code: 'food', label: '맛집' },
  { code: 'cafe', label: '카페·디저트' },
  { code: 'bar', label: '술집' },
  // 관광·전시·쇼핑은 TourAPI 서울 데이터(db/06_retrip_seed_tour_seoul.sql)를 넣어야 종로 밖에서도 걸려요
  { code: 'tour', label: '관광·산책' },
  { code: 'culture', label: '전시·문화' },
  { code: 'shop', label: '쇼핑' },
]
export const TAG_CHIPS = [
  { code: 'mood', label: '분위기 좋은' },
  { code: 'quiet', label: '조용한' },
  { code: 'value', label: '가성비' },
  { code: 'no_wait', label: '웨이팅 없는' },
  { code: 'solo', label: '혼밥 편한' },
]
// 화면 이동 수단 → 서버 코드
export const MOVE_CODES = { 지하철: 'subway', 버스: 'bus', 도보: 'walk', 자동차: 'car' }
