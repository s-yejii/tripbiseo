// 가짜 데이터: 여행 중 화면(④)에 쓰는 오늘 일정
// 백엔드가 붙으면 GET /api/trips/{id}/live 응답으로 바꿔요
// 시간은 "0시부터 몇 분"(780 = 13:00)

// 오늘 일정 6곳 (지도 핀 위치 x, y 포함)
export const todayStops = [
  { no: 1, id: 'gamja', name: '천호 쭈꾸미골목', img: '/img/gamjatang.jpg', start: 780, x: 90, y: 330 },
  { no: 2, id: 'forest', name: '강풀만화거리 산책', img: '/img/alley.jpg', start: 850, x: 140, y: 250 },
  { no: 3, id: 'daelim', name: '성내동 카페', img: '/img/daelim.jpg', start: 892, x: 215, y: 285 },
  { no: 4, id: 'shoes', name: '성내동 소품샵', img: '/img/sopum.jpg', start: 967, x: 290, y: 200 },
  { no: 5, id: 'under', name: '천호 로데오거리', img: '/img/understand.jpg', start: 1035, x: 300, y: 330 },
  { no: 6, id: 'gogi', name: '잔나비 콘서트', img: '/img/concert.svg', start: 1110, x: 220, y: 400, fixed: true },
]

// 장소 사이 이동 (앞 장소 → 이 장소). 키는 도착하는 장소 번호
export const legs = {
  2: { mode: 'walk', min: 10, dist: '700m' },
  3: { mode: 'walk', min: 12, dist: '850m' },
  4: { mode: 'walk', min: 15, dist: '1.1km' },
  5: { mode: 'walk', min: 8, dist: '560m' },
  6: { mode: 'subway', min: 20, dist: '6.2km' },
}

// 공연장 예약 시각과 원래 도착 예정 시각 → 여유 15분
export const reserveTime = 1110 // 18:30
export const planArrive = 1095 // 18:15

// 늦은 상황(16:21, 아직 성내동 카페)의 남은 일정 도착 예상
export const lateArrive = { 4: 996, 5: 1064, 6: 1124 } // 16:36, 17:44, 18:44
export const lateMinutes = 14 // 공연 입장에 14분 늦음

// 다녀온 곳 핀을 눌렀을 때 보이는 정보
export const visitedInfo = {
  1: { name: '천호 쭈꾸미골목', time: '13:00–14:00 다녀옴', pay: 32000, x: 90, y: 330 },
  2: { name: '강풀만화거리 산책', time: '14:10–14:40 다녀옴', pay: 0, x: 140, y: 250 },
}
