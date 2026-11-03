// 여행 스타일 칩 코드 (11/03). 서버의 장소 종류(PlaceMapper kindCase)·리뷰 태그(PlaceTagReader)와 같은 코드를 써요
//   → 일정 만들기·나중의 재분석(member_style)이 같은 기준으로 비교할 수 있게
export const KIND_CHIPS = [
  { code: 'food', label: '맛집' },
  { code: 'cafe', label: '카페·디저트' },
  { code: 'bar', label: '술집' },
  // 관광·산책(tour)은 지금 관광지 데이터가 종로뿐이라 TourAPI로 서울 전체를 받은 뒤에 넣어요
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
