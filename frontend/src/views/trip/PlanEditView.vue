<script setup>
// S10-a2 일정 편집 모드 (②): 손잡이로 순서 바꾸기, 빨간 버튼으로 빼기, 칸을 눌러 한 칸 수정
// 고정 일정(예약)은 자리를 안 바꿔요
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { concertTrip } from '@/data/trips'
import { importance } from '@/data/places'
import { goBack } from '@/utils/back'
import { toDuration } from '@/utils/time'
import { showToast } from '@/utils/toast'

const router = useRouter()
const list = ref(concertTrip.stops.map((stop) => ({ ...stop }))) // 편집용 복사본
const dragIndex = ref(null) // 끌고 있는 칸 번호

// 손잡이를 누르고 위아래로 움직이면, 손가락 아래 칸 자리로 옮겨요
function startDrag(i, e) {
  dragIndex.value = i
  e.currentTarget.setPointerCapture(e.pointerId)
}
function onDrag(e) {
  if (dragIndex.value === null) return
  const row = document.elementFromPoint(e.clientX, e.clientY)?.closest('.erow')
  if (!row) return
  const target = Number(row.dataset.idx)
  if (target === dragIndex.value || list.value[target].fixed) return
  const [moved] = list.value.splice(dragIndex.value, 1)
  list.value.splice(target, 0, moved)
  dragIndex.value = target
}
function endDrag() {
  dragIndex.value = null
}

// 키보드 위아래 화살표로도 옮겨요
function moveBy(i, step) {
  const target = i + step
  if (target < 0 || target >= list.value.length || list.value[target].fixed) return
  const [moved] = list.value.splice(i, 1)
  list.value.splice(target, 0, moved)
}

function done() {
  showToast('일정을 바꿨어요')
  goBack('/plan/check')
}
</script>

<template>
  <div class="scr scr-PlanEdit">
    <div class="app app--white">
      <header class="topbar" style="padding-right: 16px">
        <button type="button" class="iconbtn" aria-label="편집 취소" @click="goBack('/plan/check')"><i class="ic ic--l i-close" aria-hidden="true"></i></button>
        <h1 class="topbar__title">일정 편집</h1>
        <button type="button" class="btn btn--primary btn--sm" @click="done">완료</button>
      </header>

      <div class="scroll" style="padding-top: 4px; gap: 10px; bottom: 34px">
        <div style="display: flex; align-items: center; justify-content: space-between; gap: 8px">
          <p class="t-cap" style="display: flex; align-items: center; gap: 4px"><i class="ic ic--s i-grip" aria-hidden="true"></i>손잡이를 끌어 순서를 바꿔요</p>
          <span class="badge badge--success tn" aria-live="polite"><i class="ic ic--s i-check" aria-hidden="true"></i>예약 여유 {{ concertTrip.slack }}분</span>
        </div>

        <ol class="elist">
          <li v-for="(stop, i) in list" :key="stop.id" class="erow" :class="{ 'is-drag': dragIndex === i }" :data-idx="i">
            <button type="button" class="delbtn" :aria-label="stop.name + ' 빼기'" @click="router.push('/plan/edit/delete?idx=' + i)"><i class="ic ic--s i-minus" aria-hidden="true"></i></button>
            <button type="button" class="stop" :class="{ 'is-fixed': stop.fixed }" style="grid-template-columns: 1fr" @click="router.push('/plan/edit/stop?idx=' + i)">
              <span class="stop__card">
                <img class="thumb" :src="stop.img" alt="" />
                <span class="stop__body">
                  <span class="stop__name">{{ stop.name }}</span>
                  <span v-if="stop.fixed" class="stop__meta c-navy"><i class="ic ic--s i-lock" aria-hidden="true"></i>{{ stop.time }} 고정</span>
                  <span v-else class="stop__meta">{{ stop.time }} · {{ toDuration(stop.dur) }}</span>
                </span>
                <span v-if="!stop.fixed" class="imp" :class="'imp--' + stop.imp">{{ importance[stop.imp] }}</span>
              </span>
            </button>
            <span v-if="stop.fixed" class="grip" style="opacity: 0.35; cursor: default" aria-label="고정 일정은 순서가 그대로예요"><i class="ic ic--l i-lock" aria-hidden="true"></i></span>
            <button
              v-else
              type="button"
              class="grip"
              :aria-label="stop.name + ' 순서 바꾸기, 위아래 화살표로도 움직여요'"
              @pointerdown="startDrag(i, $event)"
              @pointermove="onDrag"
              @pointerup="endDrag"
              @pointercancel="endDrag"
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
</style>
