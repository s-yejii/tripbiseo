// 여러 화면이 같이 쓰는 여행 상태 (pinia: 화면을 옮겨 다녀도 값이 남는 저장소)
//   draft    : 새로 만드는 여행 초안 (장소 고르기 → 일정 점검 → 저장)
//   tripId   : 지금 보고 있는 여행 번호 (여행 중 화면·회고가 씀)
//   editTrip : 저장된 여행을 편집하는 동안의 복사본 (GET /api/trips/{id}로 읽어 고치고, "완료"면 서버에 저장 · 끝나면 null) (11/04)
//   position : 여행 중 화면이 마지막으로 받은 GPS { lat, lng } (대안 화면이 같이 씀)
//   applied  : 방금 적용한 대안 결과 (live/apply 응답, 바뀐 일정 화면이 씀)
//   planConditions·planMessages : AI 일정 대화로 모은 조건과 사용자가 한 말 (③ 일정 만들기가 씀)
//   planStartDate·planEndDate : 여행 정보 달력에서 고른 날짜 '2026-11-14' (당일치기면 planEndDate는 null)
//   planKinds·planTags : 여행 정보의 "어디 위주로"(food·cafe·bar) · "이런 곳이면 더 좋아요"(mood·quiet·value·no_wait·solo) 코드
//                        (11/04: 처음엔 아무것도 안 골라진 상태. 사용자가 직접 골라요)
//   planPeople·planCompanion·planMoves : 여행 정보의 인원 · 동행(혼자·친구… 처음엔 null) · 이동 수단 이름 목록(['지하철','도보'], 처음엔 [])
//                        화면 안에만 두면 장소 검색에 다녀올 때마다 처음 값으로 돌아가서 저장소에 둬요 (11/04)
//   planStays : 여행 정보에서 담은 숙소 목록 [{ name, address, lat, lng, checkInDate, checkInTime, checkOutDate, checkOutTime }] (체크인 순)
//   planArea·planOrigin : 여행 정보에서 고른 지역 { name, fullName, type, lat, lng } · 출발지 { name, address, category, lat, lng } (없으면 null)
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getTripList } from '@/api/trips'
import { todayText } from '@/utils/dates'

// 오늘 (개발자 모드면 시연 시계의 날짜, 11/09: 시연 계정은 발표일 11/10이 기본으로 잡히게)
function today() {
  return todayText()
}

function emptyDraft() {
  return { title: '새 여행', startDate: today(), endDate: today(), people: 2, transports: ['subway', 'bus', 'walk'], stops: [] }
}

export const useTripStore = defineStore('trip', () => {
  const draft = ref(emptyDraft())
  const tripId = ref(null)
  const editTrip = ref(null)
  const position = ref(null)
  const applied = ref(null)
  const planConditions = ref(null)
  const planMessages = ref([])
  const planArea = ref(null)
  const planOrigin = ref(null)
  const planStays = ref([])
  const planKinds = ref([])
  const planTags = ref([])
  const planPeople = ref(2)
  const planCompanion = ref(null)
  const planMoves = ref([])
  const planStartDate = ref(today())
  const planEndDate = ref(null)
  const planStartTime = ref(null) // 직접 고르기: 첫날 시작 시각
  const planFromTime = ref(null) // 여행 정보: 하루 시작 시각 'HH:mm'
  const planToTime = ref(null) // 여행 정보: 하루 끝 시각 'HH:mm'
  const planAreas = ref([]) // 서울 구 지도에서 고른 구 [{ name, lat, lng, dongs: [{ name, code, geometry }] }]
  const planStep = ref(0) // 여행 정보에서 지금 묻는 칸 번호 (장소 검색에 다녀와도 그대로)
  const planAutoNext = ref(false) // 검색에서 고르고 돌아오면 다음 칸으로 넘길지
  const replanFrom = ref(null) // 다시 짜는 원래 여행 { id, title } (저장할 때 바꿀지 물어봄)

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
    planKinds.value = []
    planTags.value = []
    planPeople.value = 2
    planCompanion.value = null
    planMoves.value = []
    planStartDate.value = today()
    planEndDate.value = null
    planStep.value = 0
    planAreas.value = []
    planStartTime.value = null
    planFromTime.value = null
    planToTime.value = null
    planAutoNext.value = false
    replanFrom.value = null
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

  return { draft, tripId, editTrip, position, applied, planConditions, planMessages, planArea, planAreas, planStartTime, planFromTime, planToTime, planOrigin, planStays, planStartDate, planEndDate, planKinds, planTags, planPeople, planCompanion, planMoves, planStep, planAutoNext, replanFrom, addDraftPlace, resetDraft, resetPlan, setDraft, findOngoingTripId }
})
