<script setup>
// 장소 상세 [일정에 담기] → "어디에 담을까요?" 시트 (②)
// 만들고 있는 일정(초안)이나 저장된 여행(여행 중·다가오는 여행)을 고르고, 며칠째에 넣을지 골라 담아요
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import BottomSheet from '@/components/common/BottomSheet.vue'
import { createStop, getTrip, getTripList } from '@/api/trips'
import { useTripStore } from '@/stores/trip'
import { addDays, daysBetween } from '@/utils/dates'
import { toMinutes, toTime } from '@/utils/time'
import { daysLeft, toShortDate, tripBadge, tripDetailLink } from '@/utils/tripView'
import { showToast } from '@/utils/toast'

const FIRST_START_MIN = 10 * 60 // 그날 칸이 없으면 10:00에 넣음
const GAP_MIN = 20 // 앞 칸이 끝나고 이동할 여유
const TIME_STEP_MIN = 10 // 시각은 10분 단위로 올림
const LAST_START_MIN = 23 * 60 // 이보다 늦게 시작하는 칸은 만들지 않음

const props = defineProps({ place: { type: Object, required: true } })
const emit = defineEmits(['close', 'new-trip'])

const router = useRouter()
const store = useTripStore()
const options = ref([]) // 고를 수 있는 일정 목록 (맨 위가 만들고 있는 일정)
const pickedKey = ref(null)
const day = ref(1)
const isLoading = ref(true)
const isWorking = ref(false)

const picked = computed(() => options.value.find((option) => option.key === pickedKey.value) || null)
const hasDraft = computed(() => options.value.some((option) => option.kind === 'draft'))
// 고른 일정의 일차 칩 [{ day, label }]. 하루짜리면 []라서 칩이 안 보임
const dayChips = computed(() => {
  if (!picked.value) return []
  const count = daysBetween(picked.value.startDate, picked.value.endDate) + 1
  if (count < 2) return []
  const chips = []
  for (let n = 1; n <= count; n++) {
    chips.push({ day: n, label: n + '일차 · ' + toShortDate(addDays(picked.value.startDate, n - 1)) })
  }
  return chips
})

onMounted(loadOptions)

// 고를 일정 목록 만들기: 만들고 있는 일정 → 저장된 여행(여행 중·다가오는 것만)
async function loadOptions() {
  const list = []
  if (store.draft.stops.length > 0) list.push(toOption('draft', 'draft', store.draft, { text: '만드는 중', cls: 'badge--navy' }))
  try {
    const trips = (await getTripList()).filter(isUpcomingTrip)
    // 목록 응답은 stops가 늘 []라서, 이 장소가 담겼는지 보려면 여행마다 상세를 받아야 함
    const details = await Promise.all(trips.map((trip) => getTrip(trip.id)))
    for (const trip of details) list.push(toOption('trip', 'trip-' + trip.id, trip, tripBadge(trip)))
  } catch (e) {
    showToast(e.message)
  }
  options.value = list
  isLoading.value = false
}

// 여행 중이거나 아직 끝나지 않은 계획만 (날짜가 지난 계획은 담아도 쓸 일이 없어서 뺌)
function isUpcomingTrip(trip) {
  if (trip.status === 'ongoing') return true
  return trip.status === 'planned' && daysLeft(trip.endDate) >= 0
}

// 일정 하나를 시트 한 줄 모양으로
function toOption(kind, key, trip, badge) {
  const stops = trip.stops.filter((stop) => stop.state !== 'skipped') // 빼 둔 칸은 없는 것으로
  return {
    kind,
    key,
    trip,
    stops,
    title: trip.title,
    startDate: trip.startDate,
    endDate: trip.endDate,
    badge,
    isIn: stops.some((stop) => stop.placeId != null && String(stop.placeId) === String(props.place.id)),
  }
}

function dateText(option) {
  if (option.startDate === option.endDate) return toShortDate(option.startDate)
  return toShortDate(option.startDate) + ' ~ ' + toShortDate(option.endDate)
}

function pick(option) {
  pickedKey.value = option.key
  day.value = 1 // 일정을 바꾸면 일차는 처음부터
}

// 그날 마지막 칸이 끝난 뒤 + 20분을 10분 단위로 올린 시각(분). 그날 칸이 없으면 10:00
function nextStartMinutes(stops, dayNumber) {
  let lastEnd = null
  for (const stop of stops) {
    if ((stop.day || 1) !== dayNumber) continue
    const end = toMinutes(stop.time) + stop.stay
    if (lastEnd === null || end > lastEnd) lastEnd = end
  }
  if (lastEnd === null) return FIRST_START_MIN
  return Math.ceil((lastEnd + GAP_MIN) / TIME_STEP_MIN) * TIME_STEP_MIN
}

async function addPlace() {
  const option = picked.value
  if (!option || isWorking.value) return
  isWorking.value = true
  try {
    const link = option.kind === 'draft' ? addToDraft() : await addToTrip(option)
    if (link) {
      emit('close')
      showToast(option.title + ' ' + day.value + '일차에 넣었어요', { actionText: '보러 가기', action: () => router.push(link) })
    }
  } catch (e) {
    showToast(e.message)
  }
  isWorking.value = false
}

// 만들고 있는 일정에 넣기. 돌아갈 주소를 돌려줌
function addToDraft() {
  store.addDraftPlace(props.place, false, null, day.value)
  store.draft.stops[store.draft.stops.length - 1].stay = props.place.dur // store는 늘 60분으로 넣어서 방금 넣은 칸(맨 끝)만 고침
  return '/plan/check?day=' + day.value
}

// 저장된 여행에 넣기. 시간이 없으면 null
async function addToTrip(option) {
  const start = nextStartMinutes(option.stops, day.value)
  if (start > LAST_START_MIN) {
    showToast('그날은 더 넣을 시간이 없어요')
    return null
  }
  const place = props.place
  await createStop(option.trip.id, {
    day: day.value,
    placeId: place.id,
    name: place.name,
    category: place.category ?? null,
    address: place.address ?? null,
    lat: place.lat,
    lng: place.lng,
    time: toTime(start),
    stay: place.dur,
    fixed: false,
  })
  return tripDetailLink(option.trip)
}
</script>

<template>
  <div class="dim" style="z-index: 40"></div>
  <BottomSheet closable :on-close="() => emit('close')" class="ats-sheet" role="dialog" aria-modal="true" aria-labelledby="ats-title">
    <h2 id="ats-title" class="h-title">어디에 담을까요?</h2>

    <div class="ats-list">
      <p v-if="isLoading" class="t-cap">일정을 불러오는 중이에요</p>
      <div v-else class="group">
        <button v-for="option in options" :key="option.key" type="button" class="row" :class="{ 'is-picked': option.key === pickedKey }" :disabled="option.isIn" :aria-pressed="option.key === pickedKey" @click="pick(option)">
          <span class="row__body">
            <span class="row__title">{{ option.title }}</span>
            <span class="row__meta">{{ dateText(option) }} · {{ option.stops.length }}곳</span>
          </span>
          <span v-if="option.isIn" class="badge badge--neutral">담겨 있어요</span>
          <span v-else class="badge" :class="option.badge.cls">{{ option.badge.text }}</span>
          <i v-if="option.key === pickedKey" class="ic i-check c-navy" aria-hidden="true"></i>
        </button>
        <button v-if="!hasDraft" type="button" class="row" @click="emit('new-trip')">
          <i class="ic i-plus muted" aria-hidden="true"></i>
          <span class="row__body"><span class="row__title">새 여행 만들기</span></span>
        </button>
      </div>
    </div>

    <template v-if="dayChips.length > 0">
      <p class="t-strong">몇 일차에 넣을까요?</p>
      <div class="chips">
        <button v-for="chip in dayChips" :key="chip.day" type="button" class="chip chip--sm tn" :class="{ 'is-on': chip.day === day }" :aria-pressed="chip.day === day" @click="day = chip.day">{{ chip.label }}</button>
      </div>
    </template>

    <button type="button" class="btn btn--primary btn--lg btn--block" :disabled="!picked || isWorking" @click="addPlace">
      {{ picked ? day + '일차에 담기' : '담을 일정을 골라 주세요' }}
    </button>
  </BottomSheet>
</template>

<style>
.ats-sheet { z-index: 41; gap: 12px; padding: 8px 16px 24px; }
.ats-list { max-height: 320px; overflow-y: auto; }
.ats-sheet .row.is-picked { background: var(--navy-soft); }
.ats-sheet .row[disabled] { color: var(--ink-disabled); cursor: not-allowed; }
.ats-sheet .row[disabled] .row__meta { color: var(--ink-disabled); }
</style>
