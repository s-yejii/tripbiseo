// 장소 카드 사진 (④ 11/13): GET /api/places/photos (DB 사진 → 없으면 카카오 블로그 썸네일)로 진짜 사진을 받아요.
// 받기 전이나 못 찾으면 종류별 기본 그림(imageFor). 한 번 받은 곳은 기억해서 다시 안 불러요 (장소 화면 PlaceView와 같은 API)
import { reactive } from 'vue'
import { getPlacePhotos } from '@/api/trips'
import { imageFor } from '@/utils/mapView'

const photos = reactive({}) // 장소 id → 사진 주소 (못 찾았으면 '')

/** 이 장소 사진을 받아 둠 (이미 받았거나 받는 중이면 그대로) */
export async function loadPhoto(placeId) {
  if (!placeId || placeId in photos) return
  photos[placeId] = '' // 받는 중 표시 (두 번 안 부르게)
  try {
    const found = await getPlacePhotos([placeId])
    if (found.length && found[0].imageUrl) photos[placeId] = found[0].imageUrl
  } catch (e) {
    // 사진을 못 받아도 카드는 기본 그림으로 보여 줌
  }
}

const MAX_IDS = 12 // GET /api/places/photos는 한 번에 12곳까지

/** 여러 장소 사진을 한 번에 받아 둠 (결과 카드 줄). 이미 받았거나 받는 중인 곳은 빼고 */
export async function loadPhotos(placeIds) {
  const ids = placeIds.filter((id) => id && !(id in photos)).slice(0, MAX_IDS)
  if (ids.length === 0) return
  for (const id of ids) photos[id] = '' // 받는 중 표시
  try {
    for (const photo of await getPlacePhotos(ids)) {
      if (photo.imageUrl) photos[photo.placeId] = photo.imageUrl
    }
  } catch (e) {
    // 사진을 못 받아도 카드는 기본 그림으로 보여 줌
  }
}

/** 카드에 쓸 사진: 받은 사진 → 없으면 종류별 기본 그림 */
export function photoFor(place) {
  return photos[place.id] || imageFor(place.category)
}
