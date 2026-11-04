<script setup>
// 일정 목록 (시간 · 장소 카드 · 사이 이동). 일정 점검·여행 상세·테마 코스가 같이 써요
// 이동 줄을 누르면 도보·버스·지하철·택시 중에 고를 수 있어요
// 메모가 있으면 카드 아래 한 줄 (AI 일정: 고른 이유 · 리뷰 근거 "조용해요 · 리뷰 10개 중 4개")
// reorderable이면 카드 오른쪽 손잡이를 끌어 다른 칸에 놓으면 두 칸 자리가 바뀌어요 (고정·숙소 칸은 손잡이 없음) → reorder(새 순서의 원래 번호 목록) (11/04)
import { onUnmounted, ref, watch } from 'vue'
import { importance, modes } from '@/data/places'
import { minutesBy } from '@/data/trips'
import { showToast } from '@/utils/toast'

const props = defineProps({
  stops: { type: Array, required: true },
  selected: { type: Number, default: null },
  reorderable: { type: Boolean, default: false },
})
const emit = defineEmits(['select', 'reorder'])

// 끌기: 다른 칸은 그대로 두고, 끄는 칸만 손가락을 따라 움직여요. 놓은 자리의 칸과 둘이 자리를 바꿔요 (11/04)
const dragFrom = ref(null) // 끄는 칸 번호 (안 끌면 null)
const dropTo = ref(null) // 놓으면 자리를 바꿀 칸 번호 (없으면 null)
const dragY = ref(0) // 끄는 칸이 손가락을 따라 움직인 거리(px)
let startY = 0
let scale = 1 // 폰 화면을 줄여 보여 줄 때 손가락 거리 → 화면 거리 비율

// 움직임·놓기는 화면 전체(window)에서 받아요 (손가락이 손잡이 밖으로 나가도 계속)
function startDrag(i, e) {
  e.preventDefault()
  const row = e.currentTarget.closest('[data-row]')
  scale = row.getBoundingClientRect().height / row.offsetHeight || 1
  dragFrom.value = i
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
// 끄는 중에 목록이 바뀌면(점검 결과가 새로 옴) 끌기를 그만둠
watch(() => props.stops, () => {
  if (dragFrom.value !== null) cancelDrag()
})
function cancelDrag() {
  stopListening()
  dragFrom.value = null
  dropTo.value = null
  dragY.value = 0
}
// 손가락 아래 칸이 자리를 바꿀 수 있는 칸이면 표시 (끄는 칸은 pointer-events: none이라 아래 칸이 잡힘)
function onDrag(e) {
  if (dragFrom.value === null) return
  dragY.value = (e.clientY - startY) / scale
  const row = document.elementFromPoint(e.clientX, e.clientY)?.closest('[data-row]')
  const target = row ? Number(row.dataset.row) : null
  const isOk = target !== null && target !== dragFrom.value && props.stops[target].movable
  dropTo.value = isOk ? target : null
}
// 놓으면 두 칸 자리만 바꿔서 알림 (점검 화면이 초안 순서를 바꾸고 시간을 다시 계산)
function endDrag() {
  const from = dragFrom.value
  const to = dropTo.value
  cancelDrag()
  if (from === null || to === null) return
  const result = props.stops.map((stop, k) => k)
  result[from] = to
  result[to] = from
  emit('reorder', result)
}
// 키보드 위아래 화살표로도 한 칸씩
function moveBy(i, step) {
  const target = i + step
  if (target < 0 || target >= props.stops.length || !props.stops[target].movable) return
  const result = props.stops.map((stop, k) => k)
  result.splice(target, 0, result.splice(i, 1)[0])
  emit('reorder', result)
}

const openLeg = ref(null) // 이동 수단 고르기를 연 줄 번호
const pickedMode = ref({}) // 줄 번호 → 고른 이동 수단

const weather = {
  sun: { icon: 'i-sun', label: '맑음' },
  cloud: { icon: 'i-cloud', label: '흐림' },
  rain: { icon: 'i-rain', label: '비 60%' },
}

function modeOf(i, leg) {
  return pickedMode.value[i] || leg.mode
}
function minutesOf(i, leg) {
  const mode = modeOf(i, leg)
  return mode === leg.mode ? leg.min : minutesBy(mode, leg.walk)
}
function toggleLeg(i) {
  openLeg.value = openLeg.value === i ? null : i
}
function pickMode(i, mode) {
  pickedMode.value[i] = mode
  openLeg.value = null
  showToast(modes[mode].label + '(으)로 바꿨어요 · 시간을 다시 계산했어요')
}
</script>

<template>
  <ol class="tl" aria-label="일정" style="list-style: none; margin: 0; padding: 0">
    <template v-for="(stop, i) in stops" :key="stop.id + '-' + i">
      <!-- 앞 장소에서 오는 이동 -->
      <li v-if="stop.leg" class="leg">
        <span></span>
        <span>
          <button type="button" class="legbtn" :class="{ 'is-open': openLeg === i }" :aria-expanded="openLeg === i" @click="toggleLeg(i)">
            <i class="ic ic--s" :class="modes[modeOf(i, stop.leg)].icon" aria-hidden="true"></i>{{ modes[modeOf(i, stop.leg)].label }} {{ minutesOf(i, stop.leg) }}분<i class="ic ic--s i-down" aria-hidden="true"></i>
          </button>
          <b v-if="stop.leg.rush" class="legwarn"><i class="ic ic--s i-users" aria-hidden="true"></i>퇴근길 혼잡</b>
        </span>
      </li>
      <li v-if="stop.leg && openLeg === i" class="legpick" role="radiogroup" aria-label="이동 수단">
        <button
          v-for="(m, key) in modes"
          :key="key"
          type="button"
          role="radio"
          :aria-checked="key === modeOf(i, stop.leg)"
          class="chip chip--sm"
          :class="{ 'is-on': key === modeOf(i, stop.leg) }"
          @click="pickMode(i, key)"
        >
          <i class="ic ic--s" :class="m.icon" aria-hidden="true"></i>{{ m.label }} {{ key === stop.leg.mode ? stop.leg.min : minutesBy(key, stop.leg.walk) }}분
        </button>
      </li>

      <!-- 장소 카드 -->
      <li
        :data-row="i"
        :class="{ 'tl-row': reorderable, 'is-drag': dragFrom === i, 'is-target': dropTo === i }"
        :style="dragFrom === i ? { transform: 'translateY(' + dragY + 'px)' } : null"
      >
        <button type="button" class="stop" :class="{ 'is-fixed': stop.fixed, 'is-sel': selected === i }" :data-stop="i" @click="emit('select', i)">
          <span class="stop__time">{{ stop.time }}</span>
          <span class="stop__card">
            <img class="thumb" :src="stop.img" alt="" />
            <span class="stop__body">
              <span class="stop__name">{{ stop.name }}</span>
              <span class="stop__meta" :class="stop.metaCls"><i v-if="stop.metaIcon" class="ic ic--s" :class="stop.metaIcon" aria-hidden="true"></i>{{ stop.meta }}</span>
              <span v-if="stop.memo" class="stop__memo">{{ stop.memo }}</span>
            </span>
            <span class="stop__side">
              <span v-if="!stop.fixed && stop.imp" class="imp" :class="'imp--' + stop.imp">{{ importance[stop.imp] }}</span>
              <span v-if="stop.wx" class="stop__wx" :class="{ 'is-rain': stop.wx === 'rain' }"><i class="ic ic--s" :class="weather[stop.wx].icon" aria-hidden="true"></i>{{ weather[stop.wx].label }}</span>
            </span>
          </span>
        </button>
        <template v-if="reorderable">
          <button
            v-if="stop.movable"
            type="button"
            class="tl-grip"
            :aria-label="stop.name + ' 순서 바꾸기, 위아래 화살표로도 움직여요'"
            @pointerdown.stop="startDrag(i, $event)"
            @keydown.up.prevent="moveBy(i, -1)"
            @keydown.down.prevent="moveBy(i, 1)"
          >
            <i class="ic i-grip" aria-hidden="true"></i>
          </button>
          <span v-else class="tl-grip" aria-hidden="true"></span>
        </template>
      </li>
    </template>
  </ol>
</template>

<style>
.tl .tl-row{display:flex;align-items:stretch;gap:2px}
.tl .tl-row>.stop{flex:1;min-width:0}
.tl .tl-grip{width:28px;flex:none;border:0;padding:0;background:none;color:var(--ink-subtle);display:flex;align-items:center;justify-content:center;touch-action:none;cursor:grab}
.tl .tl-row.is-drag{position:relative;z-index:5;pointer-events:none}
.tl .tl-row.is-drag .stop__card{border-color:var(--navy);background:#fff;box-shadow:0 8px 20px rgba(24,33,58,.22)}
.tl .tl-row.is-target .stop__card{border:2px dashed var(--navy);background:var(--navy-soft)}
.tl .stop__memo{font-size:12px;line-height:17px;color:var(--ink-muted);display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;white-space:normal;text-align:left}
</style>
