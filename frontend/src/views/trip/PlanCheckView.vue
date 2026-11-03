<script setup>
// S10-a 일정 점검 (②): 만든 일정을 지도와 목록으로 보고, 미리 확인할 것을 살핀 뒤 저장해요
// 초안(store.draft)에 장소가 있으면 서버가 점검(POST /api/plans/check: 실제 이동 시간·영업시간·고정 일정 여유)하고
// "저장"은 POST /api/trips. 초안이 비어 있으면 시연용 가짜 일정(concertTrip)을 보여 줘요.
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import TripPage from '@/components/trip/TripPage.vue'
import { concertTrip } from '@/data/trips'
import { checkPlan, createTrip } from '@/api/trips'
import { useTripStore } from '@/stores/trip'
import { toViewTrip } from '@/utils/tripView'
import { showToast } from '@/utils/toast'

const router = useRouter()
const store = useTripStore()

const check = ref(null) // 점검 결과
const error = ref('')
const saving = ref(false)
const hasDraft = computed(() => store.draft.stops.length > 0)
const trip = computed(() => (hasDraft.value && check.value ? toViewTrip(store.draft, check.value) : concertTrip))

onMounted(runCheck)

// 초안 점검: 시각이 빈 칸은 서버가 앞 일정에 이어서 채워 줌 → 그 시각을 초안에 반영
async function runCheck() {
  if (!hasDraft.value) return
  try {
    check.value = await checkPlan(store.draft, 1)
    for (let i = 0; i < check.value.stops.length; i++) {
      const checked = check.value.stops[i]
      const stop = store.draft.stops.find((s) => s.name === checked.name && (s.day || 1) === checked.day)
      if (stop && !stop.time) stop.time = checked.time
    }
    error.value = ''
  } catch (e) {
    error.value = e.message
  }
}

// 편집 버튼: 고른 칸이 있으면 "성내동 카페 편집"처럼 보여요
function editLabel(selected) {
  if (selected === null) return '편집'
  return trip.value.stops[selected].name.replace('천호 ', '') + ' 편집'
}

async function save() {
  if (!hasDraft.value) {
    router.replace('/trips/today')
    showToast('일정을 저장했어요')
    return
  }
  saving.value = true
  try {
    const saved = await createTrip(store.draft)
    store.tripId = saved.id
    store.resetDraft()
    store.resetPlan()
    showToast('일정을 저장했어요')
    router.replace('/trips/today?id=' + saved.id)
  } catch (e) {
    showToast(e.message)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="scr scr-PlanCheck">
    <TripPage :trip="trip" back="/trips">
      <template #right="{ selected }">
        <RouterLink to="/plan/edit" class="btn btn--sm btn--float"><i class="ic ic--s i-edit" aria-hidden="true"></i>{{ editLabel(selected) }}</RouterLink>
      </template>
      <template v-if="error" #below>
        <p class="t-cap c-danger" style="padding: 12px 16px">{{ error }}</p>
      </template>
      <template #cta>
        <div class="cta__row">
          <RouterLink to="/plan/manual" class="btn btn--secondary btn--lg"><i class="ic i-plus" aria-hidden="true"></i>장소 추가</RouterLink>
          <button type="button" class="btn btn--primary btn--lg" :disabled="saving" @click="save">이 일정으로 저장</button>
        </div>
      </template>
    </TripPage>
  </div>
</template>
