<script setup>
// S10-a2 일정 편집 모드 (②): 손잡이로 순서 바꾸기, 빨간 버튼으로 빼기, 칸을 눌러 한 칸 수정
// 고정 일정(예약)·숙소는 자리를 안 바꿔요
// 11/04: 일정 점검에서 들어오면(?day=N) 초안(store.draft)의 그날 칸을 진짜로 고쳐요.
//   순서를 바꾸면 첫 칸·고정 칸 시각만 두고 나머지는 비워서, 일정 점검이 다시 계산해 채워요 (utils/draftEdit)
//   "완료"는 그대로 두고 점검으로, ✕는 들어오기 전으로 되돌려요
//   저장된 여행(?id=)에서 들어오면 아직 시연용 가짜 일정이에요 (서버 수정 API 연결 전)
import { computed, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { concertTrip } from '@/data/trips'
import { importance } from '@/data/places'
import { useTripStore } from '@/stores/trip'
import { goBack } from '@/utils/back'
import { cancelEdit, dayStopsOf, finishEdit, retime, setDayStops, startEdit, toEditRow } from '@/utils/draftEdit'
import { toDuration } from '@/utils/time'
import { showToast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const store = useTripStore()
const day = Number(route.query.day || 1)
const isDraft = !route.query.id && store.draft.stops.length > 0 // 초안 편집 (아니면 시연용)
const backTo = '/plan/edit?day=' + day // 한 칸 수정·빼기에서 돌아올 곳

if (isDraft) startEdit(store)
const demoList = ref(concertTrip.stops.map((stop) => ({ ...stop, key: stop.id, movable: !stop.fixed })))
const rows = computed(() => (isDraft ? dayStopsOf(store, day).map(toEditRow) : demoList.value))
const dragIndex = ref(null) // 끌고 있는 칸 번호
const dropTo = ref(null) // 놓으면 자리를 바꿀 칸 번호
const dragY = ref(0) // 끄는 칸이 손가락을 따라 움직인 거리(px)
let startY = 0
let scale = 1 // 폰 화면을 줄여 보여 줄 때 손가락 거리 → 화면 거리 비율

// 두 칸 자리 바꾸기 (초안이면 그날 칸 순서를 바꿔 넣고 시각을 다시 맞춤)
function swapRows(from, to) {
  const list = isDraft ? [...dayStopsOf(store, day)] : demoList.value
  const startTime = isDraft && list.length ? list[0].time : null
  const moved = list[from]
  list[from] = list[to]
  list[to] = moved
  if (!isDraft) return
  retime(list, startTime)
  setDayStops(store, day, list)
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
  const isOk = target !== null && target !== dragIndex.value && rows.value[target].movable
  dropTo.value = isOk ? target : null
}
function endDrag() {
  stopListening()
  const from = dragIndex.value
  const to = dropTo.value
  dragIndex.value = null
  dropTo.value = null
  dragY.value = 0
  if (from !== null && to !== null) swapRows(from, to)
}

// 키보드 위아래 화살표로도 옆 칸과 바꿔요
function moveBy(i, step) {
  const target = i + step
  if (target < 0 || target >= rows.value.length || !rows.value[target].movable) return
  swapRows(i, target)
}

function openStop(i) {
  router.push(isDraft ? `/plan/edit/stop?day=${day}&idx=${i}&back=${encodeURIComponent(backTo)}` : '/plan/edit/stop?idx=' + i)
}
function openDelete(i) {
  router.push(isDraft ? `/plan/edit/delete?day=${day}&idx=${i}&back=${encodeURIComponent(backTo)}` : '/plan/edit/delete?idx=' + i)
}

function cancel() {
  if (isDraft) cancelEdit(store)
  goBack('/plan/check')
}
function done() {
  if (isDraft) finishEdit()
  showToast(isDraft ? '일정을 바꿨어요 · 시간을 다시 계산해요' : '일정을 바꿨어요')
  router.replace(isDraft ? '/plan/check?day=' + day : '/plan/check')
}
</script>

<template>
  <div class="scr scr-PlanEdit">
    <div class="app app--white">
      <header class="topbar" style="padding-right: 16px">
        <button type="button" class="iconbtn" aria-label="편집 취소" @click="cancel"><i class="ic ic--l i-close" aria-hidden="true"></i></button>
        <h1 class="topbar__title">일정 편집{{ isDraft && day > 1 ? ' · ' + day + '일차' : '' }}</h1>
        <button type="button" class="btn btn--primary btn--sm" @click="done">완료</button>
      </header>

      <div class="scroll" style="padding-top: 4px; gap: 10px; bottom: 34px">
        <div style="display: flex; align-items: center; justify-content: space-between; gap: 8px">
          <p class="t-cap" style="display: flex; align-items: center; gap: 4px"><i class="ic ic--s i-grip" aria-hidden="true"></i>손잡이를 끌어 순서를 바꿔요{{ isDraft ? ' · 시각은 다시 계산해요' : '' }}</p>
          <span v-if="!isDraft" class="badge badge--success tn" aria-live="polite"><i class="ic ic--s i-check" aria-hidden="true"></i>예약 여유 {{ concertTrip.slack }}분</span>
        </div>

        <ol class="elist">
          <li
            v-for="(stop, i) in rows"
            :key="stop.key"
            class="erow"
            :class="{ 'is-drag': dragIndex === i, 'is-target': dropTo === i }"
            :style="dragIndex === i ? { transform: 'translateY(' + dragY + 'px)' } : null"
            :data-idx="i"
          >
            <button type="button" class="delbtn" :aria-label="stop.name + ' 빼기'" @click="openDelete(i)"><i class="ic ic--s i-minus" aria-hidden="true"></i></button>
            <button type="button" class="stop" :class="{ 'is-fixed': stop.fixed }" style="grid-template-columns: 1fr" @click="openStop(i)">
              <span class="stop__card">
                <img class="thumb" :src="stop.img" alt="" />
                <span class="stop__body">
                  <span class="stop__name">{{ stop.name }}</span>
                  <span v-if="stop.fixed" class="stop__meta c-navy">{{ stop.time }} · 시간 고정</span>
                  <span v-else-if="stop.isStay" class="stop__meta c-navy"><i class="ic ic--s i-home" aria-hidden="true"></i>숙소</span>
                  <span v-else class="stop__meta">{{ stop.time || '시각 다시 계산' }} · {{ toDuration(stop.dur) }}</span>
                </span>
                <span v-if="!stop.fixed && !stop.isStay" class="imp" :class="'imp--' + stop.imp">{{ importance[stop.imp] }}</span>
              </span>
            </button>
            <span v-if="!stop.movable" class="grip" style="opacity: 0.35; cursor: default" :aria-label="stop.isStay ? '숙소는 순서가 그대로예요' : '고정 일정은 순서가 그대로예요'">
              <i class="ic ic--l" :class="stop.isStay ? 'i-home' : 'i-clock'" aria-hidden="true"></i>
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
        <RouterLink to="/plan/manual" class="btn btn--secondary btn--md btn--block" style="margin-top: 4px"><i class="ic i-plus" aria-hidden="true"></i>장소 추가</RouterLink>
      </div>
    </div>
  </div>
</template>

<style>
.scr-PlanEdit .elist { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px; }
.scr-PlanEdit .grip { touch-action: none; }
.scr-PlanEdit .erow.is-drag { position: relative; z-index: 5; pointer-events: none; }
.scr-PlanEdit .erow.is-drag .stop__card { border-color: var(--navy); background: #fff; box-shadow: 0 8px 20px rgba(24, 33, 58, 0.22); }
.scr-PlanEdit .erow.is-target .stop__card { border: 2px dashed var(--navy); background: var(--navy-soft); }
</style>
