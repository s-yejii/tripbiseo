// 가짜 데이터: 여행 중 지도의 "근처 찾기"와 화장실·쓰레기통 칩(⑤)
// 백엔드가 붙으면 PostGIS 반경 검색(ST_DWithin) 결과로 바꿔요
// x, y: 지도 그림 위 위치(px). 지도 1px ≈ 4m

export const nearPlaces = [
  { id: 'n1', kind: 'cafe', name: '천호 디저트 카페', img: '/img/dessert.jpg', x: 260, y: 320, rate: 4.7, cat: '카페·디저트', hours: '10:00–21:00' },
  { id: 'n2', kind: 'cafe', name: '성내동 로스터리', img: '/img/cafe.jpg', x: 230, y: 350, rate: 4.5, cat: '카페', hours: '09:00–20:00' },
  { id: 'n3', kind: 'cafe', name: '천호공원 커피바', img: '/img/forestshop.jpg', x: 150, y: 384, rate: 4.4, cat: '카페', hours: '08:00–19:00' },
  { id: 'n4', kind: 'cafe', name: '성내동 카페거리 브루잉', img: '/img/gallery.jpg', x: 330, y: 176, rate: 4.6, cat: '카페', hours: '11:00–22:00' },
  { id: 'n5', kind: 'cafe', name: '천호 라떼 하우스', img: '/img/dessert.jpg', x: 305, y: 420, rate: 4.2, cat: '카페', hours: '10:00–20:00' },
  { id: 'n6', kind: 'cafe', name: '천호 골목 커피', img: '/img/alley.jpg', x: 112, y: 214, rate: 4.3, cat: '카페', hours: '09:00–18:00' },
  { id: 'n10', kind: 'cafe', name: '한강 뷰 카페', img: '/img/cafe.jpg', x: 30, y: 470, rate: 4.6, cat: '카페', hours: '10:00–22:00' },
  { id: 'n7', kind: 'food', name: '천호 국밥집', img: '/img/gamjatang.jpg', x: 262, y: 352, rate: 4.4, cat: '한식', hours: '07:00–21:00' },
  { id: 'n8', kind: 'food', name: '천호 파스타 키친', img: '/img/gogi.jpg', x: 178, y: 410, rate: 4.5, cat: '양식', hours: '11:30–21:30' },
  { id: 'n9', kind: 'pub', name: '천호 수제맥주 펍', img: '/img/euljiro.jpg', x: 255, y: 230, rate: 4.4, cat: '술집', hours: '17:00–01:00' },
]

// 검색어 → 종류 (커피 → cafe)
export function kindOf(word) {
  const q = word.toLowerCase()
  if (/커피|카페|cafe|coffee|라떼|디저트|빵/.test(q)) return 'cafe'
  if (/밥|맛집|식당|국밥|파스타|점심|저녁|한식|양식/.test(q)) return 'food'
  if (/술|맥주|펍|바|이자카야/.test(q)) return 'pub'
  return null
}

// 화장실·쓰레기통 위치 [x, y, 이름, 거리(m)]
export const amenities = {
  toilet: { icon: 'i-toilet', label: '화장실', points: [[238, 262, '성내동 카페 1층', 30], [176, 318, '천호역 3번 출구 옆', 220]] },
  trash: { icon: 'i-trash', label: '쓰레기통', points: [[196, 268, '골목 입구', 40], [262, 300, '편의점 앞', 110]] },
}
