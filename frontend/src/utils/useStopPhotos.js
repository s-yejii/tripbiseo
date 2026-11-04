// 일정 칸들의 사진 (홈 카드·일정 결과·상세·편집 공통). "use…" 함수: 여러 화면이 같은 상태·동작을 같이 쓰게 묶은 것
// stopsOf: 지금 칸 목록을 돌려주는 함수. 돌려주는 photos: { 칸 이름: 사진 주소 } (못 찾은 칸은 없음)
import { ref, watch } from 'vue'
import { getTripPhotos } from '@/api/trips'

const MAX_STOPS = 12

export function useStopPhotos(stopsOf) {
  const photos = ref({})
  watch(() => stopsOf().slice(0, MAX_STOPS).map((stop) => stop.name).join('|'), async (names) => {
    if (!names) return
    try {
      photos.value = await getTripPhotos(stopsOf().slice(0, MAX_STOPS))
    } catch {
      photos.value = {}
    }
  }, { immediate: true })
  return photos
}
