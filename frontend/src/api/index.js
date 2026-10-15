import { get, post, put, del } from './http'

const q = (params) => new URLSearchParams(Object.entries(params).filter(([, v]) => v != null && v !== '')).toString()

// 로그인
export const auth = {
  me: () => get('/auth/me'),
  login: (username, password) => post('/auth/login', { username, password }),
  signup: (username, password, nickname) => post('/auth/signup', { username, password, nickname }),
  logout: () => post('/auth/logout'),
}

// 장소 (우리 DB: retrip_place / retrip_review)
export const places = {
  search: (keyword, lat, lng) => get('/places/search?' + q({ q: keyword, lat, lng })),
  nearby: (lat, lng, radius = 200) => get('/places/nearby?' + q({ lat, lng, radius })),
  detail: (id) => get('/places/' + encodeURIComponent(id)),
}

// 일정 만들기 (저장 전 초안)
export const plans = {
  chat: (message, conditions) => post('/plans/chat', { message, conditions }),
  generate: (conditions, messages, pos) => post('/plans/generate', { conditions, messages, lat: pos?.lat, lng: pos?.lng }),
  check: (draft, day = 1) => post('/plans/check?day=' + day, draft),
}

// 여행
export const trips = {
  list: () => get('/trips'),
  get: (id) => get('/trips/' + id),
  create: (draft) => post('/trips', draft),
  update: (id, body) => put('/trips/' + id, body),
  remove: (id) => del('/trips/' + id),
  start: (id) => post(`/trips/${id}/start`),
  finish: (id) => post(`/trips/${id}/finish`),
  addStop: (id, stop) => post(`/trips/${id}/stops`, stop),
  updateStop: (id, stopId, stop) => put(`/trips/${id}/stops/${stopId}`, stop),
  removeStop: (id, stopId) => del(`/trips/${id}/stops/${stopId}`),
  retro: (id) => get(`/trips/${id}/retro`),
  chats: (id) => get(`/trips/${id}/chats`),
  addChats: (id, messages) => post(`/trips/${id}/chats`, messages),
}

// 여행 중
export const live = {
  status: (id, lat, lng) => post(`/trips/${id}/live/status`, { lat, lng }),
  track: (id, lat, lng, accuracy) => post(`/trips/${id}/live/track`, { lat, lng, accuracy }),
  assist: (id, message, lat, lng) => post(`/trips/${id}/live/assist`, { message, lat, lng }),
  recommend: (id, lat, lng, radius, category) => post(`/trips/${id}/live/recommend`, { lat, lng, radius, category }),
  insert: (id, placeId, lat, lng) => post(`/trips/${id}/live/insert`, { placeId, lat, lng }),
  alternatives: (id, lat, lng, reason, request) => post(`/trips/${id}/live/alternatives`, { lat, lng, reason, request }),
  apply: (id, alternative, lat, lng) => post(`/trips/${id}/live/apply`, { ...alternative, lat, lng }),
}

// 설정
export const config = {
  public: () => get('/config/public'),
  keys: () => get('/admin/keys'),
  saveKeys: (keys) => put('/admin/keys', { keys }),
}
