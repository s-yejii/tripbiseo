<script setup>
// S10-a 일정 점검 (②): 만든 일정을 지도와 목록으로 보고, 미리 확인할 것을 살핀 뒤 저장해요
// 초안(store.draft)에 장소가 있으면 서버가 점검(POST /api/plans/check: 실제 이동 시간·영업시간·고정 일정 여유)하고
// "저장"은 POST /api/trips. 초안이 비어 있으면 시연용 가짜 일정(concertTrip)을 보여 줘요.
// 11/04: 여러 날이면 일차를 골라 그날만 점검(?day=N) · 목록 손잡이를 끌어 순서 바꾸기 → 시각을 비우고 다시 점검
//        · 지도는 점검 결과의 구간 경로(길찾기 API)로 진짜 길
//        · 오른쪽 위 버튼은 칸을 골랐을 때만 "○○ 편집"으로 떠서 그 칸 수정으로 가요 (순서는 목록에서 끌어서 바꿔요)
//        · 오른쪽 위 재생 버튼: 점이 일정대로 움직이고 시각이 흘러가요 (이동 미리보기, 화면에서만)
//        · 이동 줄을 눌러 도보·대중교통·택시를 고르면 그 칸 수단(mode)을 바꾸고 다시 점검 → 시간·경로가 진짜로 바뀜
import ChipScroller from '@/components/common/ChipScroller.vue'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TripPage from '@/components/trip/TripPage.vue'
import { stopWon, toWonText } from '@/utils/budget'
import { useStopPhotos } from '@/utils/useStopPhotos'
import { concertTrip } from '@/data/trips'
import { modes } from '@/data/places'
import { checkPlan, createTrip, deleteTrip} from '@/api/trips'
import { useTripStore } from '@/stores/trip'
import { addDays } from '@/utils/dates'
import { dayStopsOf, retime, setDayStops } from '@/utils/draftEdit'
import { toShortDate, toViewTrip } from '@/utils/tripView'
import { showToast } from '@/utils/toast'
import { showTrashToast } from '@/utils/trash'
import { useRouteSim } from '@/utils/useRouteSim'
import RouteSimButton from '@/components/trip/RouteSimButton.vue'

const route = useRoute()
const router = useRouter()
const store = useTripStore()

const check = ref(null) // 점검 결과 (고른 날)
const error = ref('')
const saving = ref(false)
const savedId = ref(null) // 다시 짜기로 방금 저장한 여행 번호 (있으면 바꿀지 묻는 창을 띄움)
const isReplacing = ref(false)
const day = ref(Number(route.query.day || 1))
const hasDraft = computed(() => store.draft.stops.length > 0)
// 점검 결과가 오기 전에는 초안 그대로 보여 줌 (가짜 일정으로 깜빡이지 않게)
const baseTrip = computed(() => (hasDraft.value ? toViewTrip(store.draft, check.value, day.value) : concertTrip))
// 카드 사진: 장소마다 진짜 사진 (홈 카드와 같은 함수, 일정 안에서 안 겹침). 없으면 종류별 기본 사진
const stopPhotos = useStopPhotos(() => (hasDraft.value ? store.draft.stops.filter((stop) => (stop.day || 1) === day.value) : []))
// 칸 오른쪽 아래 금액 글자 (숙소처럼 넣지 않는 곳은 없음, 저장 전 초안에만)
function costTextOf(stop) {
  const won = stopWon(stop)
  return hasDraft.value && won !== null && won !== undefined ? toWonText(won) : null
}
const trip = computed(() => ({
  ...baseTrip.value,
  stops: baseTrip.value.stops.map((stop) => ({ ...stop, img: stopPhotos.value[stop.name] || stop.img, photo: stopPhotos.value[stop.name] || null, costText: costTextOf(stop) })),
}))
// 일차 목록 [{ day: 1, label: '12/4(금)' }] (초안 칸의 가장 큰 day까지)
const dayList = computed(() => {
  let last = 1
  for (const stop of store.draft.stops) last = Math.max(last, stop.day || 1)
  const list = []
  for (let d = 1; d <= last; d++) list.push({ day: d, label: store.draft.startDate ? toShortDate(addDays(store.draft.startDate, d - 1)) : '' })
  return list
})

onMounted(runCheck)

// 이동 미리보기. 일정이 바뀌면(다시 점검) 처음으로
const sim = useRouteSim(() => trip.value)
watch(check, sim.reset)

// 꼭 갈 곳인데 일정에 못 넣은 곳 (③ generate 응답). "다시 넣기"는 지금 일차 끝에 붙이고 다시 점검
const missedList = computed(() => store.draft.missed || [])
const missedText = computed(() => missedList.value.length + '곳 못 넣었어요: ' + missedList.value.map((place) => place.name).join(', '))
function addMissed() {
  const unknown = []
  for (const place of missedList.value) {
    if (place.lat == null) unknown.push(place)
    else store.addDraftPlace(place, false, null, day.value)
  }
  store.draft.missed = unknown
  if (unknown.length) showToast(unknown.length + '곳은 위치를 몰라요')
  runCheck()
}

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

// 저장 끝: 초안·조건을 비우고 새 여행으로
function finishSave(id) {
  store.resetDraft()
  store.resetPlan()
  showToast('일정을 저장했어요')
  router.replace('/trips/today?id=' + id)
}

// 다시 짜기로 저장한 뒤: 옛 여행을 지우고 바꾸기 (지우기 실패해도 새 일정은 이미 저장됨)
async function replaceOld() {
  isReplacing.value = true
  const oldId = store.replanFrom.id
  let isMoved = false
  try {
    await deleteTrip(oldId)
    isMoved = true
  } catch (e) {
    showToast(e.message)
  }
  isReplacing.value = false
  finishSave(savedId.value)
  if (isMoved) showTrashToast(oldId) // 옛 여행은 휴지통으로 (되돌리면 두 여행 다 남아요)
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
    if (store.replanFrom) {
      savedId.value = saved.id // 다시 짜기: 새 일정이 저장된 뒤에만 옛 여행을 어떻게 할지 물어봄
      return
    }
    finishSave(saved.id)
  } catch (e) {
    showToast(e.message)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="scr scr-PlanCheck">
    <TripPage :trip="trip" back="/trips" :show-budget="hasDraft" :sim-me="sim.me.value" :active-stop="sim.activeStop.value" :reorderable="hasDraft" :mode-editable="hasDraft" @reorder="reorder" @change-mode="changeMode">
      <template #right="{ selected }">
        <RouteSimButton v-if="hasDraft" :sim="sim" />
        <RouterLink v-if="selected !== null && !sim.isOn.value" :to="editLink(selected)" class="btn btn--sm btn--float"><i class="ic ic--s i-edit" aria-hidden="true"></i>{{ editLabel(selected) }}</RouterLink>
      </template>
      <template v-if="hasDraft && (dayList.length > 1 || missedList.length)" #head>
        <div v-if="missedList.length" class="pc-missed">
          <i class="ic ic--s i-warning" aria-hidden="true"></i>
          <span class="pc-missed__text">{{ missedText }}</span>
          <button type="button" class="btn btn--secondary btn--sm" @click="addMissed">다시 넣기</button>
        </div>
        <ChipScroller v-if="dayList.length > 1" class="pc-days" role="tablist" aria-label="일차">
          <button v-for="d in dayList" :key="d.day" type="button" role="tab" class="chip chip--sm" :class="{ 'is-on': day === d.day }" :aria-selected="day === d.day" @click="pickDay(d.day)">
            {{ d.day }}일차<span v-if="d.label" class="t-cap tn" style="margin-left: 4px">{{ d.label }}</span>
          </button>
        </ChipScroller>
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
    <Teleport v-if="savedId" to=".scr-PlanCheck .app">
      <div class="dim" style="z-index: 40"></div>
      <section class="pc-replace" role="alertdialog" aria-modal="true" aria-label="지금 여행 바꾸기">
        <h2 class="h-title" style="margin: 0">지금 여행을 이걸로 바꿀까요?</h2>
        <p class="t-cap" style="margin: 0 0 12px">{{ store.replanFrom.title }}</p>
        <div class="cta__row">
          <button type="button" class="btn btn--secondary btn--lg" :disabled="isReplacing" @click="finishSave(savedId)">둘 다 두기</button>
          <button type="button" class="btn btn--primary btn--lg" :disabled="isReplacing" @click="replaceOld">{{ isReplacing ? '바꾸는 중' : '바꾸기' }}</button>
        </div>
      </section>
    </Teleport>
  </div>
</template>

<style>
.scr-PlanCheck .pc-missed{display:flex;align-items:center;gap:8px;margin:0 16px 8px;padding:8px 12px;border-radius:10px;font-size:13px;font-weight:600;color:var(--warning);background:var(--warning-soft)}
.scr-PlanCheck .pc-missed .ic{flex:none}
.scr-PlanCheck .pc-missed__text{flex:1;min-width:0}
.scr-PlanCheck .pc-missed .btn{flex:none}
.scr-PlanCheck .pc-replace{position:absolute;left:0;right:0;bottom:0;z-index:41;display:flex;flex-direction:column;gap:8px;padding:24px 16px;border-radius:20px 20px 0 0;background:#fff}
.scr-PlanCheck .pc-replace .btn{flex:1}
.scr-PlanCheck .pc-days{padding:0 16px 8px}
.scr-PlanCheck .pc-days .chip.is-on .t-cap{color:inherit}
</style>
