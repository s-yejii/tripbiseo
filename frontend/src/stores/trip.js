// 여러 화면이 같이 쓰는 여행 상태 (pinia: 화면을 옮겨 다녀도 값이 남는 저장소)
//   draft    : 새로 만드는 여행 초안 (장소 고르기 → 일정 점검 → 저장)
//   tripId   : 지금 보고 있는 여행 번호 (여행 중 화면·회고가 씀)
//   position : 여행 중 화면이 마지막으로 받은 GPS { lat, lng } (대안 화면이 같이 씀)
//   applied  : 방금 적용한 대안 결과 (live/apply 응답, 바뀐 일정 화면이 씀)
//   planConditions·planMessages : AI 일정 대화로 모은 조건과 사용자가 한 말 (③ 일정 만들기가 씀)
//   planStartDate·planEndDate : 여행 정보 달력에서 고른 날짜 '2026-11-14' (당일치기면 planEndDate는 null)
//   planKinds·planTags : 여행 정보의 "어디 위주로"(food·cafe·bar) · "이런 곳이면 더 좋아요"(mood·quiet·value·no_wait·solo) 코드
//   planStays : 여행 정보에서 담은 숙소 목록 [{ name, address, lat, lng, checkInDate, checkInTime, checkOutDate, checkOutTime }] (체크인 순)
//   planArea·planOrigin : 여행 정보에서 고른 지역 { name, fullName, type, lat, lng } · 출발지 { name, address, category, lat, lng } (없으면 null)
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getTripList } from '@/api/trips'

function today() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function emptyDraft() {
  return { title: '새 여행', startDate: today(), endDate: today(), people: 2, transports: ['subway', 'bus', 'walk'], stops: [] }
}

export const useTripStore = defineStore('trip', () => {
  const draft = ref(emptyDraft())
  const tripId = ref(null)
  const position = ref(null)
  const applied = ref(null)
  const planConditions = ref(null)
  const planMessages = ref([])
  const planArea = ref(null)
  const planOrigin = ref(null)
  const planStays = ref([])
  const planKinds = ref(['food', 'cafe'])
  const planTags = ref([])
  const planStartDate = ref(today())
  const planEndDate = ref(null)

  // 초안에 장소 카드 하나 추가 (time은 비워 두면 일정 점검이 앞 일정에 이어서 정해 줌, day는 며칠째 · 기본 1일차)
  function addDraftPlace(place, isFixed = false, time = null, day = 1) {
    draft.value.stops.push({
      day,
      placeId: place.id ?? null,
      name: place.name,
      category: place.category ?? null,
      address: place.address ?? null,
      lat: place.lat,
      lng: place.lng,
      time,
      stay: isFixed ? 120 : 60,
      fixed: isFixed,
      mode: null,
      memo: null,
    })
  }

  function resetDraft() {
    draft.value = emptyDraft()
  }

  // 새 여행 정보·대화 조건 비우기 (일정을 저장하면 다음 새 여행에 꼭 갈 곳·고정 일정이 남지 않게)
  function resetPlan() {
    planConditions.value = null
    planMessages.value = []
    planArea.value = null
    planOrigin.value = null
    planStays.value = []
    planKinds.value = ['food', 'cafe']
    planTags.value = []
    planStartDate.value = today()
    planEndDate.value = null
  }

  // 서버의 초안(③ generate 응답)을 그대로 초안으로
  function setDraft(trip) {
    draft.value = { ...emptyDraft(), ...trip, stops: trip.stops || [] }
  }

  // 진행 중인 여행 번호 (없으면 오늘 여행 → 없으면 null). 서버가 꺼져 있으면 null
  async function findOngoingTripId() {
    try {
      const trips = await getTripList()
      const ongoing = trips.find((trip) => trip.status === 'ongoing')
      const todayTrip = trips.find((trip) => trip.startDate <= today() && today() <= trip.endDate)
      tripId.value = (ongoing || todayTrip)?.id ?? null
    } catch {
      tripId.value = null
    }
    return tripId.value
  }

  return { draft, tripId, position, applied, planConditions, planMessages, planArea, planOrigin, planStays, planStartDate, planEndDate, planKinds, planTags, addDraftPlace, resetDraft, resetPlan, setDraft, findOngoingTripId }
})
