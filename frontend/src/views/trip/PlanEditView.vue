<script setup>
// S10-a2 일정 편집 모드 (②): 손잡이로 순서 바꾸기, 빨간 버튼으로 빼기, 칸을 눌러 한 칸 수정
// 고정 일정(예약)·숙소·이미 다녀온 칸은 자리를 안 바꿔요
// 두 가지로 들어와요 (11/04)
//   - 일정 점검에서(?day=N): 새로 짠 초안(store.draft)을 고치고, "완료"면 점검으로 돌아가 시각을 다시 계산
//   - 저장된 여행 상세에서(?id=12): 서버에서 읽은 복사본(store.editTrip)을 고치고, "완료"면 서버에 한 번에 저장
//     (빈 시각은 점검으로 채운 뒤 PUT /api/trips/{id}/stops)
// 순서를 바꾸면 첫 칸·고정 칸 시각만 두고 나머지는 비워요 (utils/draftEdit). ✕는 들어오기 전으로 되돌려요
// 고정 칸(예약) 위에 놓으면 그 앞·뒤에 끼워 넣어요 (예약 전에 다른 곳 먼저 가기)
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { importance } from '@/data/places'
import { useTripStore } from '@/stores/trip'
import { goBackTo } from '@/utils/back'
import { addDays } from '@/utils/dates'
import { cancelEdit, dayStopsOf, dropPlaceOf, editingTripOf, finishEdit, loadEditTrip, orderAfterDrop, retime, saveEditTrip, setDayStops, startEdit, toEditRow } from '@/utils/draftEdit'
import { toDuration } from '@/utils/time'
import { toShortDate, tripDetailLink } from '@/utils/tripView'
import { showToast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const store = useTripStore()
const tripId = route.query.id || null // 있으면 저장된 여행 편집, 없으면 새로 짠 초안 편집
const day = ref(Number(route.query.day || 1))
const isLoading = ref(!!tripId)
const isSaving = ref(false)
const loadError = ref('')

const trip = computed(() => editingTripOf(store, tripId))
const hasTrip = computed(() => !!trip.value && trip.value.stops.length > 0)
const rows = computed(() => (hasTrip.value ? dayStopsOf(trip.value, day.value).map(toEditRow) : []))
// 일차 목록 [{ day: 1, label: '12/4(금)' }] (여행 날짜 수만큼, 하루면 안 보여 줌)
const dayList = computed(() => {
  if (!hasTrip.value) return []
  let last = countDays(trip.value)
  for (const stop of trip.value.stops) last = Math.max(last, stop.day || 1)
  const list = []
  for (let d = 1; d <= last; d++) list.push({ day: d, label: trip.value.startDate ? toShortDate(addDays(trip.value.startDate, d - 1)) : '' })
  return list
})
const dragIndex = ref(null) // 끌고 있는 칸 번호
const dropTo = ref(null) // 놓을 칸 번호
const dropPlace = ref(null) // 'swap' 자리 바꾸기 / 'before'·'after' 고정 칸 앞·뒤에 끼우기
const dragY = ref(0) // 끄는 칸이 손가락을 따라 움직인 거리(px)
let startY = 0
let scale = 1 // 폰 화면을 줄여 보여 줄 때 손가락 거리 → 화면 거리 비율

onMounted(async () => {
  router.replace(hereLink()) // 주소에 day를 늘 붙여 둠 (한 칸 수정에서 "뒤로"로 돌아올 때 주소가 같게)
  if (!tripId) {
    if (hasTrip.value) startEdit(trip.value)
    return
  }
  try {
    await loadEditTrip(store, tripId)
    startEdit(store.editTrip)
  } catch (e) {
    loadError.value = e.message
  }
  isLoading.value = false
})

// 여행 날짜 수 (시작일 ~ 끝날 포함, 날짜가 없으면 1)
function countDays(t) {
  if (!t.startDate || !t.endDate) return 1
  return Math.round((new Date(t.endDate) - new Date(t.startDate)) / 86400000) + 1
}

// 지금 편집 화면 주소 (한 칸 수정·빼기에서 돌아올 곳)
function hereLink() {
  return '/plan/edit?' + (tripId ? 'id=' + tripId + '&' : '') + 'day=' + day.value
}
// 다른 날 고르기
function pickDay(d) {
  day.value = d
  router.replace(hereLink())
}

// 끌어 놓기 결과 반영: 그날 칸을 새 순서로 바꿔 넣고 시각을 다시 맞춤
function moveRow(from, to, place) {
  const list = dayStopsOf(trip.value, day.value)
  const startTime = list[0].time
  const moved = orderAfterDrop(list.length, from, to, place).map((i) => list[i])
  retime(moved, startTime)
  setDayStops(trip.value, day.value, moved)
}
// 끌기: 다른 칸은 그대로, 끄는 칸만 손가락을 따라 움직이고 놓은 자리의 칸과 자리를 바꿔요 (11/04)
// 움직임·놓기는 화면 전체(window)에서 받아요 (손가락이 손잡이 밖으로 나가도 계속)
function startDrag(i, e) {
  e.preventDefault()
  const row = e.currentTarget.closest('.erow')
  scale = row.getBoundingClientRect().height / row.offsetHeight || 1
  dragIndex.value = i
  dropTo.value = null
  dragY.value = 0
  startY = e.clientY
  window.addEventListener('pointermove', onDrag)
  window.addEventListener('pointerup', endDrag)
  window.addEventListener('pointercancel', endDrag)
}
function stopListening() {
  window.removeEventListener('pointermove', onDrag)
  window.removeEventListener('pointerup', endDrag)
  window.removeEventListener('pointercancel', endDrag)
}
onUnmounted(stopListening)
function onDrag(e) {
  if (dragIndex.value === null) return
  dragY.value = (e.clientY - startY) / scale
  const row = document.elementFromPoint(e.clientX, e.clientY)?.closest('.erow')
  const target = row ? Number(row.dataset.idx) : null
  dropPlace.value = dropPlaceOf(rows.value, dragIndex.value, target)
  dropTo.value = dropPlace.value ? target : null
}
function endDrag() {
  stopListening()
  const from = dragIndex.value
  const to = dropTo.value
  const place = dropPlace.value
  dragIndex.value = null
  dropTo.value = null
  dropPlace.value = null
  dragY.value = 0
  if (from !== null && to !== null) moveRow(from, to, place)
}

// 키보드 위아래 화살표로도 옆 칸과 바꿔요 (고정 칸이면 그 너머로)
function moveBy(i, step) {
  const to = i + step
  if (to < 0 || to >= rows.value.length) return
  const place = dropPlaceOf(rows.value, i, to)
  if (place) moveRow(i, to, place)
}

// 한 칸 수정·빼기 열기 (몇째 날 몇 번째 칸, 돌아올 곳)
function sheetLink(path, i) {
  return `${path}?${tripId ? 'id=' + tripId + '&' : ''}day=${day.value}&idx=${i}&back=${encodeURIComponent(hereLink())}`
}
function openStop(i) {
  router.push(sheetLink('/plan/edit/stop', i))
}
function openDelete(i) {
  router.push(sheetLink('/plan/edit/delete', i))
}

// 저장된 여행이면 상세로, 초안이면 일정 점검으로
function exitLink() {
  if (!tripId) return '/plan/check?day=' + day.value
  return trip.value ? tripDetailLink(trip.value) : '/trips'
}

function cancel() {
  const link = exitLink() // 복사본을 지우기 전에 돌아갈 곳부터 정함
  cancelEdit(trip.value)
  if (tripId) store.editTrip = null
  goBackTo(link)
}

async function done() {
  if (!tripId) {
    finishEdit()
    showToast('일정을 바꿨어요 · 시간을 다시 계산해요')
    goBackTo(exitLink())
    return
  }
  const link = exitLink()
  isSaving.value = true
  try {
    await saveEditTrip(store)
    showToast('일정을 저장했어요')
    goBackTo(link) // 상세로 돌아가면 상세가 서버에서 다시 읽어요
  } catch (e) {
    showToast(e.message)
  } finally {
    isSaving.value = false
  }
}
</script>

<template>
  <div class="scr scr-PlanEdit">
    <div class="app app--white">
      <header class="topbar" style="padding-right: 16px">
        <button type="button" class="iconbtn" aria-label="편집 취소" @click="cancel"><i class="ic ic--l i-close" aria-hidden="true"></i></button>
        <h1 class="topbar__title">일정 편집{{ dayList.length > 1 ? ' · ' + day + '일차' : '' }}</h1>
        <button type="button" class="btn btn--primary btn--sm" :disabled="isLoading || isSaving || !hasTrip" @click="done">{{ isSaving ? '저장 중' : '완료' }}</button>
      </header>

      <div class="scroll" style="padding-top: 4px; gap: 10px; bottom: 34px">
        <p v-if="isLoading" class="t-cap">일정을 불러오는 중이에요</p>
        <p v-else-if="loadError" class="t-cap c-warning">{{ loadError }}</p>
        <p v-else-if="!hasTrip" class="t-cap">고칠 일정이 없어요</p>
        <template v-else>
        <div v-if="dayList.length > 1" class="pe-days" role="tablist" aria-label="며칠째">
          <button v-for="d in dayList" :key="d.day" type="button" role="tab" class="chip chip--sm" :class="{ 'is-on': d.day === day }" :aria-selected="d.day === day" @click="pickDay(d.day)">{{ d.day }}일차<span class="t-cap tn">{{ d.label }}</span></button>
        </div>
        <p class="t-cap" style="display: flex; align-items: center; gap: 4px"><i class="ic ic--s i-grip" aria-hidden="true"></i>손잡이를 끌어 순서를 바꿔요 · 시각은 다시 계산해요</p>

        <ol class="elist">
          <li
            v-for="(stop, i) in rows"
            :key="stop.key"
            class="erow"
            :class="{ 'is-drag': dragIndex === i, 'is-target': dropTo === i && dropPlace === 'swap', 'is-before': dropTo === i && dropPlace === 'before', 'is-after': dropTo === i && dropPlace === 'after' }"
            :style="dragIndex === i ? { transform: 'translateY(' + dragY + 'px)' } : null"
            :data-idx="i"
          >
            <button v-if="!stop.isDone" type="button" class="delbtn" :aria-label="stop.name + ' 빼기'" @click="openDelete(i)"><i class="ic ic--s i-minus" aria-hidden="true"></i></button>
            <span v-else class="delbtn" style="visibility: hidden" aria-hidden="true"></span>
            <button type="button" class="stop" :class="{ 'is-fixed': stop.fixed }" style="grid-template-columns: 1fr" @click="openStop(i)">
              <span class="stop__card">
                <img class="thumb" :src="stop.img" alt="" />
                <span class="stop__body">
                  <span class="stop__name">{{ stop.name }}</span>
                  <span v-if="stop.isDone" class="stop__meta c-success"><i class="ic ic--s i-check" aria-hidden="true"></i>{{ stop.time }} · 다녀왔어요</span>
                  <span v-else-if="stop.fixed" class="stop__meta c-navy">{{ stop.time }} · 시간 고정</span>
                  <span v-else-if="stop.isStay" class="stop__meta c-navy"><i class="ic ic--s i-home" aria-hidden="true"></i>숙소</span>
                  <span v-else class="stop__meta">{{ stop.time || '시각 다시 계산' }} · {{ toDuration(stop.dur) }}</span>
                </span>
                <span v-if="!stop.fixed && !stop.isStay" class="imp" :class="'imp--' + stop.imp">{{ importance[stop.imp] }}</span>
              </span>
            </button>
            <span v-if="!stop.movable" class="grip" style="opacity: 0.35; cursor: default" :aria-label="stop.isDone ? '다녀온 곳은 순서가 그대로예요' : stop.isStay ? '숙소는 순서가 그대로예요' : '고정 일정은 순서가 그대로예요'">
              <i class="ic ic--l" :class="stop.isDone ? 'i-check' : stop.isStay ? 'i-home' : 'i-clock'" aria-hidden="true"></i>
            </span>
            <button
              v-else
              type="button"
              class="grip"
              :aria-label="stop.name + ' 순서 바꾸기, 위아래 화살표로도 움직여요'"
              @pointerdown="startDrag(i, $event)"
              @keydown.up.prevent="moveBy(i, -1)"
              @keydown.down.prevent="moveBy(i, 1)"
            >
              <i class="ic ic--l i-grip" aria-hidden="true"></i>
            </button>
          </li>
        </ol>
        <!-- 장소 추가는 새로 짜는 초안에만 담겨요 (저장된 여행에 추가는 아직 연결 전이라 숨김) -->
        <RouterLink v-if="!tripId" to="/plan/manual" class="btn btn--secondary btn--md btn--block" style="margin-top: 4px"><i class="ic i-plus" aria-hidden="true"></i>장소 추가</RouterLink>
        </template>
      </div>
    </div>
  </div>
</template>

<style>
.scr-PlanEdit .elist { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px; }
.scr-PlanEdit .grip { touch-action: none; }
.scr-PlanEdit .pe-days { display: flex; gap: 6px; overflow-x: auto; }
.scr-PlanEdit .pe-days .chip { gap: 4px; flex: none; }
.scr-PlanEdit .pe-days .chip.is-on .t-cap { color: inherit; }
.scr-PlanEdit .erow.is-drag { position: relative; z-index: 5; pointer-events: none; }
.scr-PlanEdit .erow.is-drag .stop__card { border-color: var(--navy); background: #fff; box-shadow: 0 8px 20px rgba(24, 33, 58, 0.22); }
.scr-PlanEdit .erow.is-target .stop__card { border: 2px dashed var(--navy); background: var(--navy-soft); }
.scr-PlanEdit .erow.is-before .stop__card { background: var(--navy-soft); box-shadow: inset 0 4px 0 var(--navy); }
.scr-PlanEdit .erow.is-after .stop__card { background: var(--navy-soft); box-shadow: inset 0 -4px 0 var(--navy); }
</style>
