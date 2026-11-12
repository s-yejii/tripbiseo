// ③ 내 장소 API (주소·요청·응답은 docs/API.md 그대로)
import { del, get, post } from '@/api/http'

export const getMyPlaceList = () => get('/my/places') // [{ placeId, name, group: 식당·카페·놀거리, ... }]
export const saveMyPlace = (body) => post('/my/places', body) // { placeId } 또는 { name, fromName }
export const deleteMyPlace = (placeId) => del('/my/places/' + encodeURIComponent(placeId))
