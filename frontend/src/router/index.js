import { createRouter, createWebHistory } from 'vue-router'

// 화면 57개. 담당 번호(owner)별로 묶었어요. 화면 파일도 views/담당폴더 안에 있어요
//   ① member  회원·홈 밖 화면·알림·마이·잠금화면
//   ② trip    홈·내 여행·일정 점검/편집·여행 상세·장소 상세
//   ③ plan    새 여행·AI 일정 만들기·테마 코스·릴스 담기
//   ④ live    여행 중 판별(지연·대안·바뀐 일정)
//   ⑤ after   길 안내·결제 메모·회고·앨범·카드 등록
// meta.title: 화면 번호와 이름 / meta.overlay: 앞 화면 위에 뜨는 창 / meta.statusbar: 상태바 색
const routes = [
  // ① 회원·홈 밖 화면·알림·마이·잠금화면
  { path: '/onboarding', component: () => import('@/views/member/OnboardingView.vue'), meta: { owner: 1, title: 'S01 온보딩' } },
  { path: '/login', component: () => import('@/views/member/LoginView.vue'), meta: { owner: 1, title: 'S02-a 로그인' } },
  { path: '/find-account', component: () => import('@/views/member/FindAccountView.vue'), meta: { owner: 1, title: 'S02-c 아이디·비밀번호 찾기' } },
  { path: '/signup', component: () => import('@/views/member/SignupView.vue'), meta: { owner: 1, title: 'S02-b 회원가입' } },
  { path: '/permission', component: () => import('@/views/member/PermissionView.vue'), meta: { owner: 1, title: 'S03 권한 허용', overlay: true } },
  { path: '/permission/denied', component: () => import('@/views/member/PermissionDeniedView.vue'), meta: { owner: 1, title: 'S03-b 위치 권한 거부', overlay: true } },
  { path: '/guest', component: () => import('@/views/member/MainGuestView.vue'), meta: { owner: 1, title: 'S04-a0 홈 · 로그인 전' } },
  { path: '/notifications', component: () => import('@/views/member/NotificationsView.vue'), meta: { owner: 1, title: 'S20-a 알림 센터' } },
  { path: '/my', component: () => import('@/views/member/MyView.vue'), meta: { owner: 1, title: 'S21 마이페이지' } },
  { path: '/my/profile', component: () => import('@/views/member/ProfileEditView.vue'), meta: { owner: 1, title: 'S21-b 정보 수정' } },
  { path: '/my/style', component: () => import('@/views/member/StyleEditView.vue'), meta: { owner: 1, title: 'S21-f 내 여행 스타일' } },
  { path: '/my/friends', component: () => import('@/views/member/FriendInviteView.vue'), meta: { owner: 1, title: 'S21-c 친구 초대' } },
  { path: '/my/location', component: () => import('@/views/member/LocHistoryView.vue'), meta: { owner: 1, title: 'S21-e 내 위치 기록', overlay: true } },
  { path: '/lock/push', component: () => import('@/views/member/LockPushView.vue'), meta: { owner: 1, title: 'S20-b 잠금화면 알림', statusbar: 'lock' } },
  { path: '/lock/live', component: () => import('@/views/member/LockLiveView.vue'), meta: { owner: 1, title: 'S20-c 잠금화면 · 여행 중 배너', statusbar: 'lock' } },
  { path: '/lock/delay', component: () => import('@/views/member/LockDelayView.vue'), meta: { owner: 1, title: 'S20-d 잠금화면 · 지연 알림', statusbar: 'lock' } },
  { path: '/lock/pay', component: () => import('@/views/member/LockPayView.vue'), meta: { owner: 1, title: 'S20-e 잠금화면 · 결제 알림', statusbar: 'lock' } },
  { path: '/lock/stay', component: () => import('@/views/member/LockStayView.vue'), meta: { owner: 1, title: 'S20-f 잠금화면 · 들른 곳 확인', statusbar: 'lock' } },

  // ② 홈·내 여행·일정 점검/편집·여행 상세·장소 상세
  { path: '/', component: () => import('@/views/trip/MainView.vue'), meta: { owner: 2, title: 'S04-a 홈 · 여행 날' } },
  { path: '/start-check', component: () => import('@/views/trip/MainStartView.vue'), meta: { owner: 2, title: 'S04-a2 홈 · 여행 시간 확인' } },
  { path: '/trips', component: () => import('@/views/trip/TripsView.vue'), meta: { owner: 2, title: 'S04-b 내 여행' } },
  { path: '/trips/empty', component: () => import('@/views/trip/TripsEmptyView.vue'), meta: { owner: 2, title: 'S04-c 내 여행 · 빈 상태' } },
  { path: '/plan/manual', component: () => import('@/views/trip/PlanManualView.vue'), meta: { owner: 2, title: 'S09-a 지도에서 직접 고르기' } },
  { path: '/plan/check', component: () => import('@/views/trip/PlanCheckView.vue'), meta: { owner: 2, title: 'S10-a 일정 점검' } },
  { path: '/plan/edit', component: () => import('@/views/trip/PlanEditView.vue'), meta: { owner: 2, title: 'S10-a2 일정 편집 모드' } },
  { path: '/plan/edit/stop', component: () => import('@/views/trip/StopEditView.vue'), meta: { owner: 2, title: 'S10-b 일정 한 칸 수정', overlay: true } },
  { path: '/plan/edit/delete', component: () => import('@/views/trip/DeleteConfirmView.vue'), meta: { owner: 2, title: 'S10-c 일정 빼기 확인', overlay: true } },
  { path: '/trips/detail', component: () => import('@/views/trip/TripDetailView.vue'), meta: { owner: 2, title: 'S11 여행 상세 · 여행 전' } },
  { path: '/trips/today', component: () => import('@/views/trip/TripDetailTodayView.vue'), meta: { owner: 2, title: 'S11-b 여행 상세 · 여행 날' } },
  { path: '/place', component: () => import('@/views/trip/PlaceView.vue'), meta: { owner: 2, title: 'S12 장소 상세' } },

  // ③ 새 여행·AI 일정 만들기·테마 코스·릴스 담기
  { path: '/theme', component: () => import('@/views/plan/ThemeView.vue'), meta: { owner: 3, title: 'S04-d 테마 코스' } },
  { path: '/new', component: () => import('@/views/plan/NewTripView.vue'), meta: { owner: 3, title: 'S05 새 여행 · 방법 고르기' } },
  { path: '/plan/info', component: () => import('@/views/plan/PlanAiView.vue'), meta: { owner: 3, title: 'S06 여행 정보 입력' } },
  { path: '/plan/search', component: () => import('@/views/plan/PlaceSearchView.vue'), meta: { owner: 3, title: 'S06-b 장소 검색 · 시간 고정', overlay: true } },
  { path: '/plan/stay', component: () => import('@/views/plan/StayPinView.vue'), meta: { owner: 3, title: 'S06-c 숙소 위치 찍기' } },
  { path: '/plan/capture', component: () => import('@/views/plan/ReserveCaptureView.vue'), meta: { owner: 3, title: 'S06-d 예약 캡처로 추가' } },
  { path: '/plan/chat', component: () => import('@/views/plan/PlanChatView.vue'), meta: { owner: 3, title: 'S07 원하는 것 말하기' } },
  { path: '/plan/generating', component: () => import('@/views/plan/GeneratingView.vue'), meta: { owner: 3, title: 'S08 일정 만드는 중' } },
  { path: '/plan/fail', component: () => import('@/views/plan/GenFailView.vue'), meta: { owner: 3, title: 'S08-b 일정 생성 실패' } },
  { path: '/plan/trim', component: () => import('@/views/plan/GenTrimView.vue'), meta: { owner: 3, title: 'S08-c 한 곳 빼고 다시 만들기' } },
  { path: '/share', component: () => import('@/views/plan/ShareAddView.vue'), meta: { owner: 3, title: 'S09-b 인스타 릴스에서 담기', overlay: true, statusbar: 'light' } },

  // ④ 여행 중 판별(지연·대안·바뀐 일정)
  { path: '/live', component: () => import('@/views/live/LiveView.vue'), meta: { owner: 4, title: 'S13-a 여행 중' } },
  { path: '/live/late', component: () => import('@/views/live/LiveView.vue'), meta: { owner: 4, title: 'S13-a2 여행 중 · 지연 배너', late: true } },
  { path: '/live/pin', component: () => import('@/views/live/PinEditView.vue'), meta: { owner: 4, title: 'S13-b 핀 눌러 미리 바꾸기', overlay: true } },
  { path: '/live/gps', component: () => import('@/views/live/GpsLostView.vue'), meta: { owner: 4, title: 'S13-d GPS 끊김' } },
  { path: '/live/delay', component: () => import('@/views/live/LiveDelayView.vue'), meta: { owner: 4, title: 'S15 지연 감지', overlay: true } },
  { path: '/live/alt', component: () => import('@/views/live/AlternativesView.vue'), meta: { owner: 4, title: 'S16-a 대안 · 시간 줄이기' } },
  { path: '/live/alt/trim', component: () => import('@/views/live/AltTrimView.vue'), meta: { owner: 4, title: 'S16-a2 시간 줄이기 슬라이더', overlay: true } },
  { path: '/live/alt/drop', component: () => import('@/views/live/AltDropView.vue'), meta: { owner: 4, title: 'S16-b 대안 · 한 곳 빼기' } },
  { path: '/live/alt/swap', component: () => import('@/views/live/AltSwapView.vue'), meta: { owner: 4, title: 'S16-c 장소 바꾸기 (지연·비 공용)' } },
  { path: '/live/applied', component: () => import('@/views/live/AppliedView.vue'), meta: { owner: 4, title: 'S17 바뀐 일정' } },
  { path: '/live/reserve-late', component: () => import('@/views/live/LateModalView.vue'), meta: { owner: 4, title: 'S18 예약에 늦어요', overlay: true } },

  // ⑤ 길 안내·결제 메모·회고·앨범·카드 등록
  { path: '/live/route', component: () => import('@/views/after/RouteGuideView.vue'), meta: { owner: 5, title: 'S13-e 길 안내 · 버스 정류장' } },
  { path: '/live/pay', component: () => import('@/views/after/PayMemoView.vue'), meta: { owner: 5, title: 'S13-f 결제 메모', overlay: true } },
  { path: '/retro', component: () => import('@/views/after/RetroView.vue'), meta: { owner: 5, title: 'S19-a 지난 여행 다시보기' } },
  { path: '/album', component: () => import('@/views/after/AlbumView.vue'), meta: { owner: 5, title: 'S19-c 앨범' } },
  { path: '/my/card', component: () => import('@/views/after/CardAddView.vue'), meta: { owner: 5, title: 'S21-d 결제 카드 등록' } },

  // 개발용: 전체 화면 목록
  { path: '/screens', component: () => import('@/views/ScreenListView.vue'), meta: { title: '전체 화면 목록' } },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

export default router
