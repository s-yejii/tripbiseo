// 가짜 데이터: 시연에 쓰는 장소들 (천호·성내동)
// 백엔드 장소 API가 붙으면 이 값 대신 서버에서 받은 값을 써요
// x, y: 프로토타입 지도 그림 위 위치(px) / dur: 기본 머무는 시간(분) / rate: 별점 / brk: 브레이크 타임
export const places = {
  gamja: { name: '천호 쭈꾸미골목', img: '/img/gamjatang.jpg', cat: '점심', dur: 60, x: 90, y: 250, rate: 4.3, hours: '11:00–22:00', brk: '15:00–17:00' },
  forest: { name: '강풀만화거리 산책', img: '/img/alley.jpg', cat: '산책', dur: 30, x: 140, y: 170, rate: 4.7, outdoor: true },
  daelim: { name: '성내동 카페', img: '/img/daelim.jpg', cat: '카페', dur: 60, x: 215, y: 205, rate: 4.6, hours: '11:00–22:00' },
  shoes: { name: '성내동 소품샵', img: '/img/sopum.jpg', cat: '소품샵', dur: 60, x: 290, y: 140, rate: 4.2, hours: '10:00–19:00' },
  under: { name: '천호 로데오거리', img: '/img/understand.jpg', cat: '올리브영·다이소', dur: 40, x: 300, y: 250, rate: 4.4, hours: '11:00–20:00' },
  gogi: { name: '잔나비 콘서트', img: '/img/concert.svg', cat: '공연 · 잠실실내체육관', dur: 150, x: 220, y: 290, rate: 4.8, hours: '18:00 입장 · 19:00 공연' },
  gallery: { name: '천호 작은 갤러리숍', img: '/img/gallery.jpg', cat: '전시', dur: 40, x: 175, y: 118, rate: 4.3, hours: '12:00–20:00' },
  sopum: { name: '천호 소품 편집숍', img: '/img/sopum.jpg', cat: '쇼핑', dur: 30, x: 252, y: 236, rate: 4.5, hours: '12:00–21:00' },
  dessert: { name: '천호 디저트 카페', img: '/img/dessert.jpg', cat: '카페', dur: 50, x: 118, y: 212, rate: 4.7, hours: '10:00–21:00' },
  fshop: { name: '천호공원 상점가', img: '/img/forestshop.jpg', cat: '쇼핑', dur: 40, x: 98, y: 140, rate: 4.1, hours: '11:00–20:00' },
  roof: { name: '천호 루프탑 카페', img: '/img/cafe.jpg', cat: '카페', dur: 50, x: 330, y: 190, rate: 2.8, hours: '12:00–22:00' },
  alley: { name: '천호 골목 산책', img: '/img/alley.jpg', cat: '산책', dur: 40, x: 70, y: 190, rate: 4.4, outdoor: true },
  pub: { name: '천호 수제맥주 펍', img: '/img/euljiro.jpg', cat: '술집', dur: 60, x: 262, y: 300, rate: 4.4, hours: '17:00–01:00' },
  izaka: { name: '천호 골목 이자카야', img: '/img/gamjatang.jpg', cat: '술집', dur: 70, x: 330, y: 230, rate: 4.2, hours: '18:00–02:00' },
  nopo: { name: '천호 40년 순댓국', img: '/img/euljiro.jpg', cat: '노포·한식', dur: 50, x: 330, y: 280, rate: 4.6, hours: '07:00–21:00', nopo: true },
}

// 이동 수단 이름과 아이콘
export const modes = {
  walk: { label: '도보', icon: 'i-walk' },
  bus: { label: '버스', icon: 'i-bus' },
  subway: { label: '지하철', icon: 'i-subway' },
  taxi: { label: '택시', icon: 'i-car' },
}

// 중요도 이름
export const importance = { must: '꼭', maybe: '가능하면', drop: '빼도 됨' }
