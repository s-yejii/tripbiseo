// 뒤로가기: 앞에 본 화면이 있으면 거기로, 없으면(주소로 바로 들어온 경우) fallback 화면으로
import router from '@/router'

export function goBack(fallback = '/') {
  if (window.history.state && window.history.state.back) {
    router.back()
  } else {
    router.push(fallback)
  }
}
