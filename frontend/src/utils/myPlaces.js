// ③ 내 장소 저장 상태를 화면끼리 같이 써요 (친구 소식 시트의 "저장" 버튼 여러 개가 목록을 한 번만 읽게)
import { ref } from 'vue'
import { deleteMyPlace, getMyPlaceList, saveMyPlace } from '@/api/myPlaces'

export const myPlaces = ref(null) // null: 아직 못 읽음
let loading = null

export function loadMyPlaces(isFresh = false) {
  if (!isFresh && myPlaces.value) return Promise.resolve(myPlaces.value)
  if (!loading) {
    loading = getMyPlaceList()
      .then((list) => (myPlaces.value = list))
      .finally(() => (loading = null))
  }
  return loading
}

// placeId가 있으면 id로, 없으면 이름으로 찾아요 (친구 소식은 이름만 있어서)
export function findMyPlace(placeId, name) {
  return (myPlaces.value || []).find((place) => (placeId ? place.placeId === placeId : place.name === name)) || null
}

export async function addMyPlace(body) {
  const saved = await saveMyPlace(body)
  if (!findMyPlace(saved.placeId)) myPlaces.value = [saved, ...(myPlaces.value || [])]
  return saved
}

export async function removeMyPlace(placeId) {
  await deleteMyPlace(placeId)
  myPlaces.value = (myPlaces.value || []).filter((place) => place.placeId !== placeId)
}
