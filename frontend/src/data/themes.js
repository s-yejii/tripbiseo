// 가짜 데이터: 테마로 떠나기 (③). 홈·로그인 전 홈의 동그라미와 테마 화면(S04-d)이 써요
// 아직 코스가 없어서 누르면 "준비 중" 화면만 보여 줘요. 코스를 만들면 theme · theme_stop 테이블(docs/DB.md)에서 읽어요
//   label: 동그라미 아래 이름 (\n에서 줄바꿈) / img: 동그라미 사진 (public/img) / badge: HOT · NEW (없어도 됨)

export const themeList = [
  { id: 'jeonchamsi', label: '전참시', title: '전참시 매니저 맛집 코스', tagline: '매니저가 아껴 둔 단골집만 골라 가요', img: '/img/gogi.jpg', badge: 'HOT' },
  { id: 'demonhunters', label: '데몬헌터스', title: '데몬헌터스 서울 성지순례', tagline: '화면 속 그 서울, 직접 걸어 봐요', img: '/img/understand.jpg' },
  { id: 'ttoganjip', label: '또간집', title: '또간집 동네 찐맛집', tagline: '동네 사람이 또 간 집만 모았어요', img: '/img/gamjatang.jpg' },
  { id: 'saengsaeng', label: '생생정보통', title: '생생정보통 가성비 맛집', tagline: '줄 서서 먹는 착한 가격 맛집', img: '/img/euljiro.jpg' },
  { id: 'hometown', label: '6시 내고향', title: '6시 내고향 서울 시장 한 바퀴', tagline: '정 넘치는 전통시장 먹거리 투어', img: '/img/shoes.jpg' },
  { id: 'koreatrip', label: '한국기행', title: '한국기행 느린 골목 산책', tagline: '천천히 걸으며 만나는 옛 골목', img: '/img/alley.jpg' },
  { id: 'welcome', label: '어서와', title: '어서와 서울은 처음이지', tagline: '외국 친구에게 꼭 보여 주고 싶은 서울', img: '/img/jeonju.jpg', badge: 'NEW' },
]

// id로 테마 하나 찾기 (없으면 null)
export function findTheme(id) {
  return themeList.find((theme) => theme.id === id) || null
}
