// 서버 API 모음. 주소·요청·응답 모양은 docs/API.md 그대로예요.
// 화면은 이 함수들만 불러요 (주소를 화면마다 직접 쓰지 않게).
import { get, post, put, del } from '@/api/http'

// ② 여행·일정
export const getTripList = () => get('/trips')
export const getTrip = (tripId) => get(`/trips/${tripId}`)
export const createTrip = (trip) => post('/trips', trip)
export const updateTrip = (tripId, trip) => put(`/trips/${tripId}`, trip)
export const deleteTrip = (tripId) => del(`/trips/${tripId}`)
export const startTrip = (tripId) => post(`/trips/${tripId}/start`)
export const finishTrip = (tripId) => post(`/trips/${tripId}/finish`)
export const createStop = (tripId, stop) => post(`/trips/${tripId}/stops`, stop)
export const updateStop = (tripId, stopId, stop) => put(`/trips/${tripId}/stops/${stopId}`, stop)
export const deleteStop = (tripId, stopId) => del(`/trips/${tripId}/stops/${stopId}`)
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
export const getPlaceDetail = (placeId) => get(`/places/${encodeURIComponent(placeId)}`)

// ③ AI 일정
export const chatPlan = (message, conditions) => post('/plans/chat', { message, conditions })
export const generatePlan = (conditions, messages, lat, lng) => post('/plans/generate', { conditions, messages, lat, lng })

// ④ 여행 중 (본문에 지금 위치 lat, lng가 항상 들어가요)
export const sendTrack = (tripId, lat, lng, accuracy) => post(`/trips/${tripId}/live/track`, { lat, lng, accuracy })
export const getLiveStatus = (tripId, lat, lng) => post(`/trips/${tripId}/live/status`, { lat, lng })
export const askAssist = (tripId, message, lat, lng) => post(`/trips/${tripId}/live/assist`, { message, lat, lng })
export const askRecommend = (tripId, lat, lng, radius, category, tags) => post(`/trips/${tripId}/live/recommend`, { lat, lng, radius, category, tags })
export const insertPlace = (tripId, placeId, lat, lng) => post(`/trips/${tripId}/live/insert`, { placeId, lat, lng })
export const getAlternatives = (tripId, lat, lng, reason = 'delay', request = null) => post(`/trips/${tripId}/live/alternatives`, { lat, lng, reason, request })
export const applyAlternative = (tripId, key, stops, lat, lng) => post(`/trips/${tripId}/live/apply`, { key, stops, lat, lng })

// ⑤ 여행 후
export const getRetro = (tripId) => get(`/trips/${tripId}/retro`)
