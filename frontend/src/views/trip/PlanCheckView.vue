<script setup>
// S10-a 일정 점검 (②): 만든 일정을 지도와 목록으로 보고, 미리 확인할 것을 살핀 뒤 저장해요
// 초안(store.draft)에 장소가 있으면 서버가 점검(POST /api/plans/check: 실제 이동 시간·영업시간·고정 일정 여유)하고
// "저장"은 POST /api/trips. 초안이 비어 있으면 시연용 가짜 일정(concertTrip)을 보여 줘요.
// 11/04: 여러 날이면 일차를 골라 그날만 점검(?day=N) · 목록 손잡이를 끌어 순서 바꾸기 → 시각을 비우고 다시 점검
//        · 지도는 점검 결과의 구간 경로(길찾기 API)로 진짜 길
//        · 오른쪽 위 버튼은 칸을 골랐을 때만 "○○ 편집"으로 떠서 그 칸 수정으로 가요 (순서는 목록에서 끌어서 바꿔요)
//        · 이동 줄을 눌러 도보·대중교통·택시를 고르면 그 칸 수단(mode)을 바꾸고 다시 점검 → 시간·경로가 진짜로 바뀜
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TripPage from '@/components/trip/TripPage.vue'
import { concertTrip } from '@/data/trips'
import { modes } from '@/data/places'
import { checkPlan, createTrip } from '@/api/trips'
import { useTripStore } from '@/stores/trip'
import { addDays } from '@/utils/dates'
import { dayStopsOf, retime, setDayStops } from '@/utils/draftEdit'
import { toShortDate, toViewTrip } from '@/utils/tripView'
import { showToast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const store = useTripStore()

const check = ref(null) // 점검 결과 (고른 날)
const error = ref('')
const saving = ref(false)
const day = ref(Number(route.query.day || 1))
const hasDraft = computed(() => store.draft.stops.length > 0)
// 점검 결과가 오기 전에는 초안 그대로 보여 줌 (가짜 일정으로 깜빡이지 않게)
const trip = computed(() => (hasDraft.value ? toViewTrip(store.draft, check.value, day.value) : concertTrip))
// 일차 목록 [{ day: 1, label: '12/4(금)' }] (초안 칸의 가장 큰 day까지)
const dayList = computed(() => {
  let last = 1
  for (const stop of store.draft.stops) last = Math.max(last, stop.day || 1)
  const list = []
  for (let d = 1; d <= last; d++) list.push({ day: d, label: store.draft.startDate ? toShortDate(addDays(store.draft.startDate, d - 1)) : '' })
  return list
})

onMounted(runCheck)

// 그날 초안 점검: 시각이 빈 칸은 서버가 앞 일정에 이어서 채워 줌 → 그 시각을 초안에 반영
async function runCheck() {
  if (!hasDraft.value) return
  try {
    check.value = await checkPlan(store.draft, day.value)
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

function pickDay(d) {
  if (d === day.value) return
  day.value = d
  check.value = null
  router.replace('/plan/check?day=' + d)
  runCheck()
}

// 목록에서 끌어 순서를 바꿈: order = 원래 번호를 새 순서로. 첫 시각·고정 칸만 두고 나머지는 비운 뒤 다시 점검
function reorder(order) {
  const list = dayStopsOf(store.draft, day.value)
  const startTime = list.length ? list[0].time : null
  const moved = order.map((i) => list[i])
  retime(moved, startTime)
  setDayStops(store.draft, day.value, moved)
  runCheck()
}

// 이동 수단을 바꿈: i번째 칸으로 오는 수단을 초안에 넣고, 시각을 비운 뒤 다시 점검 (시간·경로를 서버가 다시 계산)
async function changeMode(i, mode) {
  const list = dayStopsOf(store.draft, day.value)
  list[i].mode = mode
  retime(list, list[0].time)
  await runCheck()
  if (!error.value) showToast(modes[mode].label + '(으)로 바꿨어요')
}

// 고른 칸 편집 버튼: "성내동 카페 편집" → 그 칸 수정 (칸을 안 고르면 버튼이 없어요)
function editLabel(selected) {
  return trip.value.stops[selected].name.replace('천호 ', '') + ' 편집'
}
function editLink(selected) {
  if (!hasDraft.value) return '/plan/edit/stop?idx=' + selected
  return `/plan/edit/stop?day=${day.value}&idx=${selected}&back=${encodeURIComponent(route.fullPath)}` // 돌아올 곳 = 지금 주소 그대로
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
    <TripPage :trip="trip" back="/trips" :reorderable="hasDraft" :mode-editable="hasDraft" @reorder="reorder" @change-mode="changeMode">
      <template #right="{ selected }">
        <RouterLink v-if="selected !== null" :to="editLink(selected)" class="btn btn--sm btn--float"><i class="ic ic--s i-edit" aria-hidden="true"></i>{{ editLabel(selected) }}</RouterLink>
      </template>
      <template v-if="hasDraft && dayList.length > 1" #head>
        <div class="chips pc-days" role="tablist" aria-label="일차">
          <button v-for="d in dayList" :key="d.day" type="button" role="tab" class="chip chip--sm" :class="{ 'is-on': day === d.day }" :aria-selected="day === d.day" @click="pickDay(d.day)">
            {{ d.day }}일차<span v-if="d.label" class="t-cap tn" style="margin-left: 4px">{{ d.label }}</span>
          </button>
        </div>
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

<style>
.scr-PlanCheck .pc-days{flex-wrap:nowrap;overflow-x:auto;padding:0 16px 8px;margin:0}
.scr-PlanCheck .pc-days .chip.is-on .t-cap{color:inherit}
</style>
