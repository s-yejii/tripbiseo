// ⑤ 로드뷰를 보고 하나씩 적은 건물 모습 (11/12)
// 건물은 보통 규칙(용도·높이)으로 그리지만, 여기 적힌 건물은 실제와 닮게 벽 색·무늬·1층 모습을 따로 정해요
// 조사 방법: 카카오맵 로드뷰를 걸은 경로를 따라 열어 보고, 화면에 보이는 건물의 색과 재질을 눈으로 적음 (사진은 저장하지 않음)
//   로드뷰 카메라 위치·방향으로 "화면 그쪽에 있는 건물"을 우리 건물 자료에서 찾아, 그 건물 가운데 좌표를 lng·lat에 적음
// 칸 뜻
//   lng, lat : 건물 안의 한 점 (이 점이 들어 있는 건물에 적용)
//   wall     : 벽 색
//   pattern  : 벽 무늬 — 0 작은 타일 · 1 벽돌 · 2 미장(무늬 없음) · 3 돌판·패널 · 4 유리벽
//   ground   : 1층 모습 — 0 필로티 주차장 · 1 가게(간판 + 진열창) · 2 벽 + 문 · 3 유리 로비
//   sign     : 간판·차양 색 (ground가 1일 때)
//   note     : 무엇을 보고 적었는지
// 유니티 쪽: MapData.cs의 LookData, FacadePainter.cs의 CustomUpper·CustomGround
export const BUILDING_LOOKS = [
  // 퇴계로 (충무로역 → 한옥마을 입구)
  { lng: 126.994814, lat: 37.561653, wall: '#8A4A3A', pattern: 1, ground: 1, sign: '#4A3528', note: '퇴계로 북쪽 붉은 벽돌 2~3층 식당, 짙은 나무색 간판' },
  { lng: 126.994829, lat: 37.561153, wall: '#6E7274', pattern: 3, ground: 1, sign: '#22242A', note: '퇴계로 남쪽 짙은 회색 돌판 건물, 초록빛 유리, 1층 검은 간판 가게' },
  { lng: 126.99342, lat: 37.560245, wall: '#B89E8C', pattern: 3, ground: 2, sign: '', note: '퇴계로34길 왼쪽 분홍빛 화강석 4층' },
  { lng: 126.993195, lat: 37.560223, wall: '#9AA0A2', pattern: 3, ground: 1, sign: '#2E7D6B', note: '퇴계로34길 오른쪽 회색 패널 건물, 1층 편의점 청록 차양' },
  { lng: 126.992292, lat: 37.560198, wall: '#8C8A82', pattern: 1, ground: 2, sign: '', note: '퇴계로30길 왼쪽 낮은 옛집, 회색 돌벽 (벽화 담장)' },
  { lng: 126.992279, lat: 37.560372, wall: '#C9C0AE', pattern: 3, ground: 3, sign: '', note: '퇴계로30길 오른쪽 베이지 돌판 4~5층, 1층 불투명 유리' },
  { lng: 126.990781, lat: 37.560031, wall: '#4E8F86', pattern: 4, ground: 0, sign: '', note: '삼일대로2길 초록 유리벽 건물, 1층 트인 주차장' },
  { lng: 126.988827, lat: 37.559723, wall: '#CDBFA6', pattern: 0, ground: 2, sign: '', note: '남산골한옥마을 주차장 쪽 베이지 타일 건물' },
  // 소파로·퇴계로20길 (목멱산방 가는 길)
  { lng: 126.987145, lat: 37.558412, wall: '#E6E4DE', pattern: 2, ground: 1, sign: '#3C7A5A', note: '소파로 오른쪽 흰 2층 상가, 초록 차양' },
  { lng: 126.986722, lat: 37.557569, wall: '#4F6FA8', pattern: 2, ground: 1, sign: '#EDEDED', note: '퇴계로20길 파란 나무판 사진관, 흰 간판' },
  { lng: 126.986168, lat: 37.557625, wall: '#C8C6C0', pattern: 3, ground: 2, sign: '', note: '숭의여자대학교, 밝은 회색 화강석' },
  // 후암동 (콤포타블 커피 남산점 둘레)
  { lng: 126.983313, lat: 37.549266, wall: '#D9D6CE', pattern: 4, ground: 3, sign: '', note: '콤포타블 커피 남산점: 콘크리트 담 위 유리 건물' },
  { lng: 126.983101, lat: 37.549183, wall: '#ECECE8', pattern: 2, ground: 2, sign: '', note: '카페 맞은편 흰 미장 건물 (유리블록 벽)' },
  { lng: 126.983114, lat: 37.549059, wall: '#9C4A38', pattern: 1, ground: 0, sign: '', note: '그 옆 붉은 벽돌 빌라' },
  // 회현동·남대문 (백범광장 → 가메골손왕만두)
  { lng: 126.976978, lat: 37.556312, wall: '#C9938F', pattern: 2, ground: 2, sign: '', note: '소파로 분홍 페인트 콘크리트 건물' },
  { lng: 126.977063, lat: 37.556344, wall: '#B07A3A', pattern: 0, ground: 1, sign: '#B83A30', note: '그 옆 주황갈색 타일 건물, 1층 붉은 가게' },
  { lng: 126.976607, lat: 37.557329, wall: '#D8C8A8', pattern: 2, ground: 2, sign: '', note: '소월로 오른쪽 베이지 건물 (노란 담)' },
  { lng: 126.975781, lat: 37.55834, wall: '#4C8C92', pattern: 4, ground: 1, sign: '#2A4FA0', note: '소월로 왼쪽 청록 유리 사무소, 1층 파란 간판 가게' },
  { lng: 126.97734, lat: 37.558204, wall: '#8C8278', pattern: 2, ground: 1, sign: '#C8352E', note: '남대문시장길 왼쪽 갈회색 5층, 붉은 차양' },
  { lng: 126.977101, lat: 37.558391, wall: '#BDB3A2', pattern: 2, ground: 1, sign: '#F2C200', note: '남대문시장길 오른쪽 낮은 상가, 노란 간판 (가메골손왕만두 둘레)' },
  { lng: 126.977562, lat: 37.55862, wall: '#A89C8C', pattern: 2, ground: 1, sign: '#1E4FB0', note: '남대문시장길 왼쪽 낮은 상가, 파란 간판' },
  { lng: 126.978575, lat: 37.558412, wall: '#5A5C60', pattern: 3, ground: 3, sign: '', note: '남대문시장 큰길 호텔, 짙은 회색 패널' },
  { lng: 126.978074, lat: 37.558718, wall: '#A8623A', pattern: 1, ground: 1, sign: '#F2C200', note: '남대문시장 큰길 주황갈색 벽돌 3층, 노란 간판' },
  { lng: 126.978001, lat: 37.55865, wall: '#A8623A', pattern: 1, ground: 1, sign: '#C8352E', note: '그 옆 벽돌 상가, 붉은 차양' },
  { lng: 126.979654, lat: 37.559155, wall: '#DEDCD6', pattern: 0, ground: 1, sign: '#5A4638', note: '퇴계로 회현 쪽 흰 타일 낮은 상가' },
  { lng: 126.979749, lat: 37.559143, wall: '#DEDCD6', pattern: 0, ground: 1, sign: '#5A4638', note: '그 옆 흰 타일 상가' },
  // 1박 2일 시연 일정 (11/13)
  { lng: 126.982732, lat: 37.5616253, wall: '#E6E3DC', pattern: 3, ground: 3, sign: '', note: '명동8나길 르메르디앙 서울 명동: 흰 석재 외벽, 창이 깊이 들어감, 1층 유리 로비와 화분 (로드뷰 2026.06)' },
]
