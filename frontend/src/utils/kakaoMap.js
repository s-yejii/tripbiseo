// 카카오 지도 SDK 불러오기: 서버(GET /api/config/public)에서 JS 키를 받아 <script>를 한 번만 붙여요.
// 키가 없거나 스크립트를 못 받으면 오류를 던져요 → 화면은 예전 지도 그림을 보여 줘요.
import { get } from '@/api/http'

let loading = null // 여러 화면이 불러도 SDK는 한 번만 받아요 (받는 중이면 같은 Promise를 돌려줌)

// 카카오 지도를 쓸 준비가 되면 window.kakao를 돌려줘요
export function loadKakaoMap() {
  if (!loading) {
    loading = startLoading().catch((error) => {
      loading = null // 실패하면 다음 화면에서 다시 시도
      throw error
    })
  }
  return loading
}

async function startLoading() {
  if (window.kakao && window.kakao.maps && window.kakao.maps.LatLng) return window.kakao
  const config = await get('/config/public')
  if (!config || !config.kakaoJsKey) throw new Error('카카오 지도 키(KAKAO_JS_KEY)가 없어요')
  // autoload=false: 스크립트를 받은 뒤 kakao.maps.load로 지도 기능을 준비해요 (Vue 화면이 뜬 뒤에 불러도 동작)
  await addScript('https://dapi.kakao.com/v2/maps/sdk.js?autoload=false&appkey=' + config.kakaoJsKey)
  await new Promise((resolve) => window.kakao.maps.load(resolve))
  return window.kakao
}

function addScript(src) {
  return new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = src
    script.onload = resolve
    script.onerror = () => reject(new Error('카카오 지도 스크립트를 못 받았어요 (카카오 콘솔에 사이트 도메인을 등록했는지 확인)'))
    document.head.appendChild(script)
  })
}
