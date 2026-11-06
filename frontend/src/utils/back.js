// 뒤로가기 (앱 흐름 기준, 프로토타입과 같게)
//   - 보통: 화면마다 정해 둔 돌아갈 곳(router meta.parent)으로. 지나온 기록을 되감지 않아요
//     (예: 여행을 시작한 뒤 여행 중 화면에서 뒤로가기 → 홈. 일정 만들기 단계로 되돌아가지 않음)
//   - meta.history가 있는 상세 화면(장소 상세·알림·일정 편집)은 들어온 곳으로
//   - 주소에 ?back=/live 처럼 돌아갈 곳을 붙여 들어왔으면 거기로 (예: 여행 중 지도 → 내 일정 → 뒤로가기 → 지도)
//   - 둘 다 없으면 fallback
import router from '@/router'

// path로 돌아가기 (11/04): 바로 전 화면이 path면 뒤로가기(기록이 쌓이지 않게), 아니면 path로 바꾸기
//   예: 편집 → 한 칸 수정 → 저장하면 편집으로 "뒤로" 가서, 편집에서 다시 뒤로가기 하면 상세로 가요
export function goBackTo(path) {
  const previous = window.history.state && window.history.state.back
  if (previous === path) {
    router.back()
    return
  }
  router.replace(path)
}

export function goBack(fallback = '/') {
  const queryBack = router.currentRoute.value.query.back
  const isInnerPath = typeof queryBack === 'string' && queryBack.startsWith('/') && !queryBack.startsWith('//') // 다른 사이트 주소는 막음
  if (isInnerPath) {
    router.replace(queryBack)
    return
  }
  const meta = router.currentRoute.value.meta
  const previous = window.history.state && window.history.state.back
  // 바로 전 기록이 지금 화면과 같으면 되감아도 그대로예요 (예: 알림 → 상세 → 알림으로 돌아온 뒤 뒤로가기)
  const hasPrevious = previous && previous.split('?')[0] !== router.currentRoute.value.path
  if (meta.history && hasPrevious) {
    router.back()
    return
  }
  router.replace(meta.parent || fallback)
}
