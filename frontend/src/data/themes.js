// 테마로 떠나기 (③). 홈·로그인 전 홈의 동그라미와 테마 화면(S04-d)이 써요
// 11/13: 예전엔 방송 프로그램 이름(전참시·또간집 등)으로 7개를 두고 누르면 전부 "준비 중"이었어요.
//   이제 우리 DB에 장소가 충분한 진짜 코스 3개만 두고, 누르면 지역·종류가 채워진 채 AI 일정 만들기로 바로 시작해요
//   label: 동그라미 아래 이름 / img: 동그라미 사진 (public/img) / area: 여행 정보의 지역 / kinds·tags: "어디 위주로"·"이런 곳" 코드
//   (DB 장소 수 11/13 확인: 익선동 51 · 을지로 63 · 성수 180)

export const themeList = [
  {
    id: 'ikseon', label: '익선동 골목', title: '익선동 한옥 골목 산책', tagline: '한옥 사이 작은 밥집·카페를 천천히 걸어요', img: '/img/alley.jpg', badge: 'HOT',
    area: { name: '익선동', fullName: '서울 종로구 익선동', type: 'dong', lat: 37.5741, lng: 126.9901 },
    kinds: ['food', 'cafe', 'culture'], tags: ['mood'],
  },
  {
    id: 'euljiro', label: '을지로 노포', title: '을지로 노포·힙지로 한 바퀴', tagline: '오래된 가게와 새 가게가 섞인 을지로 저녁',  img: '/img/euljiro.jpg',
    area: { name: '을지로동', fullName: '서울 중구 을지로동', type: 'dong', lat: 37.5663, lng: 126.9919 },
    kinds: ['food', 'bar', 'cafe'], tags: [],
  },
  {
    id: 'seongsu', label: '성수 카페', title: '성수 카페·편집숍 거리', tagline: '카페 하나, 편집숍 하나씩 들르는 하루', img: '/img/cafe.jpg', badge: 'NEW',
    area: { name: '성수동', fullName: '서울 성동구 성수동', type: 'dong', lat: 37.5446, lng: 127.0557 },
    kinds: ['cafe', 'shop', 'food'], tags: ['mood'],
  },
]

// id로 테마 하나 찾기 (없으면 null. 예전 테마 주소로 와도 화면이 깨지지 않게 테마 목록을 보여 줘요)
export function findTheme(id) {
  return themeList.find((theme) => theme.id === id) || null
}
