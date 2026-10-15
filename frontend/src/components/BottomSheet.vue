<script setup>
// 지도 위로 올라오는 시트. 손잡이를 끌거나 눌러 접기(peek)·반(half)·펼침(full)을 오갑니다.
import { computed, ref } from 'vue'

const state = defineModel('state', { default: 'half' })
const props = defineProps({ peek: { type: Number, default: 96 } })
const HEIGHTS = { half: 0.5, full: 0.9 }
const dragY = ref(null)
let startY = 0, startH = 0, host = null

const height = computed(() => {
  if (dragY.value != null) return `${dragY.value}px`
  return state.value === 'peek' ? `${props.peek}px` : `${HEIGHTS[state.value] * 100}%`
})

function down(e) {
  host = e.currentTarget.closest('.sheet').parentElement.getBoundingClientRect().height
  startY = e.clientY
  startH = e.currentTarget.closest('.sheet').getBoundingClientRect().height
  dragY.value = startH
  e.currentTarget.setPointerCapture(e.pointerId)
}
function move(e) {
  if (dragY.value == null) return
  dragY.value = Math.min(host * 0.92, Math.max(props.peek, startH + startY - e.clientY))
}
function up(e) {
  if (dragY.value == null) return
  const moved = Math.abs(e.clientY - startY) > 6
  const r = dragY.value / host
  dragY.value = null
  if (!moved) state.value = state.value === 'peek' ? 'half' : 'peek' // 눌렀을 때는 접기/펴기
  else state.value = r < 0.3 ? 'peek' : r < 0.7 ? 'half' : 'full'
}
</script>

<template>
  <section class="sheet" :style="{ height }" :class="{ dragging: dragY != null }">
    <div class="handle" @pointerdown="down" @pointermove="move" @pointerup="up" @pointercancel="up"><span /></div>
    <div class="sheet-body"><slot /></div>
  </section>
</template>

<style scoped>
.sheet { position: absolute; left: 0; right: 0; bottom: 0; z-index: 20; display: flex; flex-direction: column; background: #fff;
  border-radius: 22px 22px 0 0; box-shadow: 0 -6px 24px rgba(0, 0, 0, .12); transition: height .25s ease; }
.sheet.dragging { transition: none; }
.handle { flex: none; height: 26px; display: grid; place-items: center; touch-action: none; cursor: grab; }
.handle span { width: 40px; height: 5px; border-radius: 3px; background: #d1d5db; }
.sheet-body { flex: 1; min-height: 0; overflow-y: auto; padding: 0 18px 20px; }
</style>
