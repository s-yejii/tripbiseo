// 가짜 데이터: 테마 코스 (③). 홈·내 여행 빈 화면의 테마 카드와 테마 코스 화면(S04-d)이 써요
// 백엔드가 붙으면 theme_course, theme_course_place 테이블에서 읽어요
// stops 모양은 data/trips.js 설명과 같아요

export const themes = {
  alley: {
    id: 'alley',
    title: '천호 골목 걷기',
    short: '천호 골목',
    img: '/img/alley.jpg',
    who: '하루',
    move: 40,
    count: '5곳 · 하루',
    stops: [
      { id: 'alley', time: '11:00', name: '천호 골목 산책', img: '/img/alley.jpg', meta: '산책 · 40분', imp: 'maybe', wx: 'sun', x: 70, y: 190 },
      { id: 'dessert', time: '11:47', name: '천호 디저트 카페', img: '/img/dessert.jpg', meta: '카페 · 50분', imp: 'maybe', wx: 'sun', x: 118, y: 212, leg: { mode: 'walk', min: 7, walk: 7 } },
      { id: 'daelim', time: '12:46', name: '성내동 카페', img: '/img/daelim.jpg', meta: '카페 · 1시간 20분', imp: 'must', wx: 'sun', x: 215, y: 205, leg: { mode: 'walk', min: 9, walk: 9 } },
      { id: 'gallery', time: '14:12', name: '천호 작은 갤러리숍', img: '/img/gallery.jpg', meta: '전시 · 40분', imp: 'maybe', wx: 'sun', x: 175, y: 118, leg: { mode: 'walk', min: 6, walk: 6 } },
      { id: 'sopum', time: '15:10', name: '천호 소품 편집숍', img: '/img/sopum.jpg', meta: '쇼핑 · 30분', imp: 'maybe', wx: 'cloud', x: 252, y: 236, leg: { mode: 'walk', min: 18, walk: 18 } },
    ],
  },
  forest: {
    id: 'forest',
    title: '천호공원 반나절',
    short: '천호공원 반나절',
    img: '/img/seoulforest.jpg',
    who: '반나절',
    move: 15,
    count: '3곳 · 3시간',
    stops: [
      { id: 'forest', time: '10:00', name: '강풀만화거리 산책', img: '/img/alley.jpg', meta: '산책 · 1시간', imp: 'must', wx: 'sun', x: 140, y: 170 },
      { id: 'fshop', time: '11:06', name: '천호공원 상점가', img: '/img/forestshop.jpg', meta: '쇼핑 · 40분', imp: 'maybe', wx: 'sun', x: 98, y: 140, leg: { mode: 'walk', min: 6, walk: 6 } },
      { id: 'dessert', time: '11:55', name: '천호 디저트 카페', img: '/img/dessert.jpg', meta: '카페 · 50분', imp: 'maybe', wx: 'sun', x: 118, y: 212, leg: { mode: 'walk', min: 9, walk: 9 } },
    ],
  },
  nopo: {
    id: 'nopo',
    title: '천호 노포 한 바퀴',
    short: '천호 노포',
    img: '/img/euljiro.jpg',
    who: '하루',
    move: 67,
    count: '4곳 · 하루',
    badge: '노포',
    stops: [
      { id: 'nopo', time: '12:00', name: '천호 40년 순댓국', img: '/img/euljiro.jpg', meta: '노포·한식 · 50분', imp: 'must', wx: 'sun', x: 330, y: 280 },
      { id: 'gamja', time: '13:20', name: '천호 쭈꾸미골목', img: '/img/gamjatang.jpg', meta: '점심 · 1시간 10분', imp: 'maybe', wx: 'sun', x: 90, y: 250, leg: { mode: 'walk', min: 30, walk: 30 } },
      { id: 'shoes', time: '14:59', name: '성내동 소품샵', img: '/img/sopum.jpg', meta: '소품샵 · 1시간', imp: 'maybe', wx: 'sun', x: 290, y: 140, leg: { mode: 'walk', min: 29, walk: 29 } },
      { id: 'under', time: '16:07', name: '천호 로데오거리', img: '/img/understand.jpg', meta: '올리브영·다이소 · 40분', imp: 'maybe', wx: 'cloud', x: 300, y: 250, leg: { mode: 'walk', min: 8, walk: 8 } },
    ],
  },
}
