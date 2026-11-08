// 서버 API 모음. 주소·요청·응답 모양은 docs/API.md 그대로예요.
// 화면은 이 함수들만 불러요 (주소를 화면마다 직접 쓰지 않게).
import { get, post, put, del, upload, postStream } from '@/api/http'

// ② 여행·일정
// 보통 화면의 여행 목록에는 보관한 여행을 넣지 않아요 (보관함 화면만 getArchivedTrips)
export const getTripList = async () => (await get('/trips')).filter((trip) => !trip.archived)
export const getArchivedTrips = async () => (await get('/trips')).filter((trip) => trip.archived)
export const getTrip = (tripId) => get(`/trips/${tripId}`)
export const createTrip = (trip) => post('/trips', trip)
export const updateTrip = (tripId, trip) => put(`/trips/${tripId}`, trip)
export const deleteTrip = (tripId) => del(`/trips/${tripId}`) // 지우지 않고 휴지통으로
export const getTrashTrips = () => get('/trips/trash')
export const restoreTrip = (tripId) => post(`/trips/${tripId}/restore`)
export const deleteTrashTrip = (tripId) => del(`/trips/${tripId}/trash`) // 영구 삭제
export const emptyTrash = () => del('/trips/trash')
export const startTrip = (tripId) => post(`/trips/${tripId}/start`)
export const finishTrip = (tripId) => post(`/trips/${tripId}/finish`)
export const archiveTrip = (tripId) => post(`/trips/${tripId}/archive`)
export const createStop = (tripId, stop) => post(`/trips/${tripId}/stops`, stop)
export const updateStop = (tripId, stopId, stop) => put(`/trips/${tripId}/stops/${stopId}`, stop)
export const deleteStop = (tripId, stopId) => del(`/trips/${tripId}/stops/${stopId}`)
// 일정 칸 전부 한 번에 저장 (목록에 없는 칸은 서버가 지움, 편집 화면 "완료") (11/04)
export const updateStops = (tripId, stops) => put(`/trips/${tripId}/stops`, stops)
export const getChatList = (tripId) => get(`/trips/${tripId}/chats`)
export const createChats = (tripId, messages) => post(`/trips/${tripId}/chats`, messages)
export const checkPlan = (trip, day = 1) => post(`/plans/check?day=${day}`, trip)

// ② 장소
export const searchPlaces = (q, lat, lng) => {
  const params = new URLSearchParams({ q })
  if (lat != null && lng != null) {
    params.set('lat', lat)
    params.set('lng', lng)
  }
  return get('/places/search?' + params)
}
export const getNearbyPlaces = (lat, lng, radius = 200) => get(`/places/nearby?lat=${lat}&lng=${lng}&radius=${radius}`)
// 한 일정의 칸들 사진 (같은 사진 안 겹침). 칸마다 이름으로 찾아서 장소 번호가 없는 칸도 돼요. 돌려받는 건 { 칸 이름: 사진 주소 }
export async function getTripPhotos(stops) {
  const list = stops.map((stop) => ({ key: stop.name, name: stop.name, address: stop.address, placeId: stop.placeId, category: stop.category, lat: stop.lat, lng: stop.lng }))
  const map = {}
  for (const photo of await post('/places/photos/trip', list)) map[photo.placeId] = photo.imageUrl
  return map
}
export const getPlacePhotos = (placeIds) => get('/places/photos?ids=' + placeIds.map(encodeURIComponent).join(','))
export const getPlaceDetail = (placeId) => get(`/places/${encodeURIComponent(placeId)}`)

// ② 어디로 가요?(서울 구·동) · 어디서 출발해요?(역·건물·주소) · 현재 위치 주소 — 카카오로 찾아요
export const searchAreas = (q) => get('/places/areas?' + new URLSearchParams({ q }))
export const searchLocations = (q, lat, lng) => {
  const params = new URLSearchParams({ q })
  if (lat != null && lng != null) {
    params.set('lat', lat)
    params.set('lng', lng)
  }
  return get('/places/locations?' + params)
}
export const getLocationHubs = (lat, lng) => get(`/places/locations/hubs?lat=${lat}&lng=${lng}`)
export const getLocationHere = (lat, lng) => get(`/places/locations/here?lat=${lat}&lng=${lng}`)
export const searchStays = (q) => get('/places/locations/stays?' + new URLSearchParams({ q })) // 숙소 위치 찍기: 숙소만

// ③ AI 일정
export const chatPlan = (message, conditions) => post('/plans/chat', { message, conditions })
export const generatePlan = (conditions, messages, lat, lng) => post('/plans/generate', { conditions, messages, lat, lng })
// 위와 같고 단계마다 onProgress(이름, { step, state, percent, text, detail })를 불러요 → 끝나면 초안 (11/04)
export const generatePlanStream = (conditions, messages, lat, lng, onProgress) =>
  postStream('/plans/generate/stream', { conditions, messages, lat, lng }, onProgress)
// ③ 추천 게시물 [{ platform, title, area, url, imageUrl }]
// 이동 시간이 짧은 순서 후보 ([동선 최적화])
export const getRouteOptions = (stops, date, transports) => post('/plans/route-options', { stops, date, transports })
// 하루 일정의 새 순서 ([동선 정리])
export const getRouteOrder = (stops, date, startLat, startLng) => post('/plans/route-order', { stops, date, startLat, startLng })
// 한 구의 행정동 특색 (동 지도 색칠). dongs: [{ code, geometry }]
export const getDongTypes = (dongs) => post('/plans/dong-types', dongs)
export const getRecommendedLinks = () => get('/plans/links/recommended')
// ③ 추천 지역 [{ kind, name, placeCount, reviewCount, lat, lng, imageUrl }]
export const getAreaStats = () => get('/plans/areas')
// ③ 인스타 링크 → { source, places: [장소 카드], missed: [못 찾은 이름] } (저장 안 함). 422면 게시물 글(caption)을 같이 보내요
export const getManualPlaces = (lat, lng, kind) => get(`/plans/places?lat=${lat}&lng=${lng}&kind=${kind}`)
export const findLinkPlaces = (url, caption) => post('/plans/links', { url, caption })
// ③ 숙소 예약 캡처 → { name, address, checkInDate, checkInTime, checkOutDate, checkOutTime, lat, lng } (저장 안 함)
export function readStayCapture(file) {
  const form = new FormData()
  form.append('image', file)
  return upload('/plans/capture/stay', form)
}

export function readCapture(file) {
  const form = new FormData()
  form.append('image', file)
  return upload('/plans/capture', form)
}

// ④ 여행 중 (본문에 지금 위치 lat, lng가 항상 들어가요)
export const sendTrack = (tripId, lat, lng, accuracy) => post(`/trips/${tripId}/live/track`, { lat, lng, accuracy })
export const getLiveStatus = (tripId, lat, lng) => post(`/trips/${tripId}/live/status`, { lat, lng })
export const askAssist = (tripId, message, lat, lng) => post(`/trips/${tripId}/live/assist`, { message, lat, lng })
export const askRecommend = (tripId, lat, lng, radius, category, tags) => post(`/trips/${tripId}/live/recommend`, { lat, lng, radius, category, tags })
export const insertPlace = (tripId, placeId, lat, lng) => post(`/trips/${tripId}/live/insert`, { placeId, lat, lng })
export const getAlternatives = (tripId, lat, lng, reason = 'delay', request = null) => post(`/trips/${tripId}/live/alternatives`, { lat, lng, reason, request })
export const applyAlternative = (tripId, key, stops, lat, lng) => post(`/trips/${tripId}/live/apply`, { key, stops, lat, lng })
// 링크로 찾은 장소들(장소 카드)을 남은 일정에 넣어 보기 → { fits, fixedName, message, best, alternatives } (저장 안 함)
export const fitLinkPlaces = (tripId, places, lat, lng) =>
  post(`/trips/${tripId}/live/link-fit`, { lat, lng, places: places.map((place) => ({ placeId: place.id, name: place.name, category: place.category, address: place.address, lat: place.lat, lng: place.lng })) })
export const previewPlan =(tripId, stops, lat, lng) => post(`/trips/${tripId}/live/preview`, { stops, lat, lng })
export const getSwapCandidates = (tripId, stopId, lat, lng) => get(`/trips/${tripId}/live/swap-candidates?stopId=${stopId}&lat=${lat}&lng=${lng}`)

// ⑤ 여행 후
export const getRetro = (tripId) => get(`/trips/${tripId}/retro`)

// ⑤ 길 안내: mode는 walk / bus / subway / car. 못 찾으면 available=false + message
export const getRouteGuide = (fromLat, fromLng, toLat, toLng, mode) =>
  get(`/route?fromLat=${fromLat}&fromLng=${fromLng}&toLat=${toLat}&toLng=${toLng}&mode=${mode}`)

// ② 요즘 인기 장소 (lat·lng 없으면 시범 지역 전체, radius 없으면 3km → 5km → 전체로 넓힘)
export const getPopularPlaces = (lat, lng, limit = 6, radius = null) => {
  const params = new URLSearchParams({ limit })
  if (lat != null && lng != null) {
    params.set('lat', lat)
    params.set('lng', lng)
  }
  if (radius != null) params.set('radius', radius)
  return get('/places/popular?' + params)
}
