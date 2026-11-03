// 뒤로가기 (앱 흐름 기준, 프로토타입과 같게)
//   - 보통: 화면마다 정해 둔 돌아갈 곳(router meta.parent)으로. 지나온 기록을 되감지 않아요
//     (예: 여행을 시작한 뒤 여행 중 화면에서 뒤로가기 → 홈. 일정 만들기 단계로 되돌아가지 않음)
//   - meta.history가 있는 상세 화면(장소 상세·알림·일정 편집)은 들어온 곳으로
//   - 주소에 ?back=/live 처럼 돌아갈 곳을 붙여 들어왔으면 거기로 (예: 여행 중 지도 → 내 일정 → 뒤로가기 → 지도)
//   - 둘 다 없으면 fallback
import router from '@/router'

export function goBack(fallback = '/') {
  const queryBack = router.currentRoute.value.query.back
  const isInnerPath = typeof queryBack === 'string' && queryBack.startsWith('/') && !queryBack.startsWith('//') // 다른 사이트 주소는 막음
  if (isInnerPath) {
    router.replace(queryBack)
    return
  }
  const meta = router.currentRoute.value.meta
  const hasPrevious = window.history.state && window.history.state.back
  if (meta.history && hasPrevious) {
    router.back()
    return
  }
  router.replace(meta.parent || fallback)
}
