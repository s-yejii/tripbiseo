import { config } from '@/api'

// 지도 엔진 불러오기: 카카오 지도 → 실패하면 오픈스트리트맵(Leaflet)
// 카카오 JS 키는 서버의 API 키 설정(api_key 테이블)에서 받아옵니다.
let kakaoPromise, leafletPromise

function script(src, timeoutMs) {
  return new Promise((resolve, reject) => {
    const el = document.createElement('script')
    const timer = setTimeout(() => reject(new Error('timeout')), timeoutMs)
    el.src = src
    el.onload = () => (clearTimeout(timer), resolve())
    el.onerror = () => (clearTimeout(timer), reject(new Error('load failed')))
    document.head.appendChild(el)
  })
}

export function loadKakao() {
  if (window.kakao?.maps?.LatLng) return Promise.resolve(window.kakao)
  kakaoPromise ??= (async () => {
    const { kakaoJsKey } = await config.public()
    if (!kakaoJsKey) throw new Error('카카오 JS 키 없음')
    await script(`https://dapi.kakao.com/v2/maps/sdk.js?appkey=${kakaoJsKey}&autoload=false&libraries=services`, 6000)
    // 키가 틀리거나 도메인 미등록이면 load 콜백이 오지 않음 → 시간 초과로 판단
    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('카카오 지도 초기화 실패')), 6000)
      window.kakao.maps.load(() => (clearTimeout(timer), resolve()))
    })
    return window.kakao
  })().catch((e) => {
    kakaoPromise = null
    throw e
  })
  return kakaoPromise
}

export function loadLeaflet() {
  if (window.L?.map) return Promise.resolve(window.L)
  leafletPromise ??= (async () => {
    const css = document.createElement('link')
    css.rel = 'stylesheet'
    css.href = 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css'
    document.head.appendChild(css)
    await script('https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js', 8000)
    return window.L
  })()
  return leafletPromise
}
