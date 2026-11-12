// 여행 상세 화면 공통 (②): 주소의 ?id= 여행을 서버에서 읽고(GET /api/trips/{id}) 일정 점검(POST /plans/check)까지 붙여
// 일정 화면(TripPage) 모양으로 바꿔요. "use…" 함수: 여러 화면이 같은 상태·동작을 같이 쓰게 묶은 함수(Vue composable)
import { ref } from 'vue'
import { checkPlan, getTrip } from '@/api/trips'
import { useTripStore } from '@/stores/trip'
import { toViewTrip } from '@/utils/tripView'

export function useTripDetail() {
  const store = useTripStore()
  const saved = ref(null) // 서버 여행 그대로
  const view = ref(null) // TripPage가 쓰는 모양
  const isLoading = ref(true) // 처음엔 "불러오는 중" (안내 문구가 잠깐 깜빡이지 않게)
  const error = ref('')

  // tripId가 없으면 불러오지 않음 (화면이 "여행을 찾을 수 없어요"를 보여 줌)
  async function load(tripId) {
    if (!tripId) {
      isLoading.value = false
      return
    }
    isLoading.value = true
    error.value = ''
    try {
      saved.value = await getTrip(tripId)
      store.tripId = saved.value.id
      view.value = toViewTrip(saved.value, await checkFirstDay(saved.value))
    } catch (e) {
      error.value = e.message
    }
    isLoading.value = false
  }

  // 일정이 있으면 첫째 날 점검 (점검이 실패해도 일정은 보여 줌)
  async function checkFirstDay(trip) {
    if (!trip.stops.length) return null
    try {
      return await checkPlan(trip, 1)
    } catch {
      return null
    }
  }

  // 다른 날 보기 (여러 날 여행, 11/06): 그날 점검을 붙여 그날 칸으로 바꿈
  async function loadDay(day) {
    if (!saved.value) return
    let check = null
    try {
      check = await checkPlan(saved.value, day)
    } catch {
      check = null
    }
    view.value = toViewTrip(saved.value, check, day)
  }

  return { saved, view, isLoading, error, load, loadDay }
}
