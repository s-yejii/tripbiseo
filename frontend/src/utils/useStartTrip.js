// 여행 시작 공통: 시작해 보고, 진행 중인 다른 여행이 있으면(409) 그 여행을 알려 줘서 시트를 띄워요
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { getTripList, startTrip } from '@/api/trips'
import { showToast } from '@/utils/toast'
import { devMode, prepareTripStart } from '@/utils/devMode'

const CONFLICT_STATUS = 409

export function useStartTrip() {
  const router = useRouter()
  const conflict = ref(null) // 진행 중인 다른 여행. 있으면 시트가 떠요
  let waitingTripId = null // 시트에서 처리한 뒤 시작할 여행

  // 여행 시작 → 여행 중 화면
  async function start(tripId) {
    waitingTripId = tripId
    try {
      if (devMode.isOn) await prepareTripStart(tripId) // 시연 계정: 시계를 그 여행 1일차 출발 시각으로 → 언제 눌러도 바로 시작
      await startTrip(tripId)
      conflict.value = null
      router.replace('/live')
    } catch (e) {
      if (e.status !== CONFLICT_STATUS) return showToast(e.message)
      const trips = await getTripList()
      conflict.value = trips.find((trip) => trip.status === 'ongoing') || null
      if (!conflict.value) showToast(e.message)
    }
  }

  // 시트에서 보관·삭제가 끝나면 다시 시작
  function retry() {
    conflict.value = null
    return start(waitingTripId)
  }

  return { conflict, start, retry }
}
