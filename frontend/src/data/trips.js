// 가짜 데이터: 내 여행과 일정 (②)
// 백엔드가 붙으면 GET /api/trips, GET /api/trips/{id} 응답으로 바꿔요
//
// 일정 칸(stop) 모양
//   time: 시작 시각 / dur: 머무는 시간(분) / meta: 카드 둘째 줄 / metaIcon·metaCls: 둘째 줄 앞 아이콘과 색
//   imp: 중요도(must 꼭 · maybe 가능하면 · drop 빼도 됨) / wx: 날씨(sun · cloud · rain)
//   fixed: 예약된 고정 일정 / warn: 지도 핀을 주황으로(비 오는 야외)
//   leg: 앞 장소에서 오는 이동 { mode, min, walk(걸으면 몇 분), rush(퇴근길 혼잡) }
//   x, y: 지도 그림 위 위치(px)

// 오늘 여행: 잔나비 공연 데이트
export const concertTrip = {
  id: 'plan',
  title: '잔나비 공연 데이트',
  date: '10/24(토)',
  who: '연인 2명',
  move: 65,
  slack: 15,
  stops: [
    { id: 'gamja', time: '13:00', name: '천호 쭈꾸미골목', dur: 60, img: '/img/gamjatang.jpg', meta: '점심 · 1시간', imp: 'maybe', wx: 'sun', x: 90, y: 250 },
    { id: 'forest', time: '14:10', name: '강풀만화거리 산책', dur: 30, img: '/img/alley.jpg', meta: '비 60% · 30분', metaIcon: 'i-rain', metaCls: 'c-warning', imp: 'drop', wx: 'rain', warn: true, x: 140, y: 170, leg: { mode: 'walk', min: 10, walk: 10 } },
    { id: 'daelim', time: '14:52', name: '성내동 카페', dur: 60, img: '/img/daelim.jpg', meta: '카페 · 1시간', imp: 'must', wx: 'rain', x: 215, y: 205, leg: { mode: 'walk', min: 12, walk: 12 } },
    { id: 'shoes', time: '16:07', name: '성내동 소품샵', dur: 60, img: '/img/sopum.jpg', meta: '소품샵 · 1시간', imp: 'maybe', wx: 'cloud', x: 290, y: 140, leg: { mode: 'walk', min: 15, walk: 15 } },
    { id: 'under', time: '17:15', name: '천호 로데오거리', dur: 40, img: '/img/understand.jpg', meta: '올리브영·다이소 · 40분', imp: 'must', wx: 'cloud', x: 300, y: 250, leg: { mode: 'walk', min: 8, walk: 8 } },
    { id: 'gogi', time: '18:30', name: '잔나비 콘서트', dur: 150, img: '/img/concert.svg', meta: '예약 · 고정', metaIcon: 'i-lock', metaCls: 'c-navy', fixed: true, wx: 'cloud', x: 220, y: 290, leg: { mode: 'subway', min: 20, walk: 25, rush: true } },
  ],
  // 미리 확인할 것 (rain 칸에는 "실내로 바꾸기" 버튼)
  checks: [
    { kind: 'rain', icon: 'i-rain', title: '강풀만화거리 산책 · 비 60%', desc: '실내 장소로 바꿀 수 있어요' },
    { kind: 'rush', icon: 'i-users', title: '17:55 퇴근길 · 8호선 혼잡', desc: '이동 시간에 5분 여유를 넣었어요 · 구간을 눌러 택시·도보로 바꿀 수 있어요' },
    { kind: 'event', icon: 'i-flag', title: '잔나비 공연 입장 18시부터', desc: '종합운동장역 주변이 붐벼요 · 이동에 5분을 더 넣었어요' },
  ],
  pack: [
    { text: '신분증', done: true },
    { text: '보조배터리', done: false },
    { text: '우산', done: false, why: '14–16시 비' },
    { text: '알러지약', done: false },
  ],
}

// 다가오는 여행: 성내동 카페 2차
export const cafeTrip = {
  id: 'plan2',
  title: '성내동 카페 2차',
  date: '11/1(토)',
  who: '친구 2명',
  move: 40,
  stops: [
    { id: 'alley', time: '11:00', name: '천호 골목 산책', dur: 40, img: '/img/alley.jpg', meta: '산책 · 40분', imp: 'drop', wx: 'sun', x: 70, y: 190 },
    { id: 'dessert', time: '11:47', name: '천호 디저트 카페', dur: 50, img: '/img/dessert.jpg', meta: '카페 · 50분', imp: 'maybe', wx: 'sun', x: 118, y: 212, leg: { mode: 'walk', min: 7, walk: 7 } },
    { id: 'daelim', time: '12:46', name: '성내동 카페', dur: 80, img: '/img/daelim.jpg', meta: '카페 · 1시간 20분', imp: 'must', wx: 'sun', x: 215, y: 205, leg: { mode: 'walk', min: 9, walk: 9 } },
    { id: 'gallery', time: '14:12', name: '천호 작은 갤러리숍', dur: 40, img: '/img/gallery.jpg', meta: '전시 · 40분', imp: 'maybe', wx: 'sun', x: 175, y: 118, leg: { mode: 'walk', min: 6, walk: 6 } },
    { id: 'sopum', time: '15:10', name: '천호 소품 편집숍', dur: 30, img: '/img/sopum.jpg', meta: '쇼핑 · 30분', imp: 'maybe', wx: 'cloud', x: 252, y: 236, leg: { mode: 'walk', min: 18, walk: 18 } },
  ],
  checks: [],
  pack: [
    { text: '신분증', done: false },
    { text: '보조배터리', done: false },
  ],
}

// 이동 수단마다 걸리는 시간 (걸어서 걸리는 시간으로 대충 계산한 시연용 값)
export function minutesBy(mode, walk) {
  if (mode === 'bus') return Math.round(walk * 0.55) + 6
  if (mode === 'subway') return Math.round(walk * 0.45) + 9
  if (mode === 'taxi') return Math.round(walk * 0.3) + 4
  return walk
}
