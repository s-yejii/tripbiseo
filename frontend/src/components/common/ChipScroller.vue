<script setup>
// 가로로 넘기는 칩 줄 (공통): 손가락으로 밀기 + 마우스로 끌기 + 세로 휠을 가로로. 더 있으면 오른쪽 끝이 옅어져요
import { nextTick, onMounted, onUnmounted, ref } from 'vue'

const DRAG_START_PX = 5 // 이만큼 움직여야 끌기로 봄 (그 전엔 칩 누르기)
const END_GAP_PX = 2 // 끝까지 갔다고 볼 여유

const box = ref(null)
const isMore = ref(false) // 오른쪽에 더 있는지
let drag = null // 끄는 중: { startX, startScroll, isMoved }
let observer = null

function updateMore() {
  const el = box.value
  if (el) isMore.value = el.scrollLeft + el.clientWidth < el.scrollWidth - END_GAP_PX
}

onMounted(async () => {
  await nextTick()
  updateMore()
  // ResizeObserver: 칸 크기가 바뀌면 알려 줘요 (칩이 늘거나 화면이 바뀔 때)
  observer = new ResizeObserver(updateMore)
  observer.observe(box.value)
})

function onPointerDown(e) {
  if (e.pointerType !== 'mouse' || e.button !== 0) return
  drag = { startX: e.clientX, startScroll: box.value.scrollLeft, isMoved: false }
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp)
}
function onPointerMove(e) {
  if (!drag) return
  const moved = e.clientX - drag.startX
  if (!drag.isMoved && Math.abs(moved) < DRAG_START_PX) return
  drag.isMoved = true
  box.value.style.scrollSnapType = 'none' // 끄는 동안은 칩에 붙지 않게
  box.value.scrollLeft = drag.startScroll - moved
}
function removeListeners() {
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
}
function onPointerUp() {
  removeListeners()
  if (!drag) return
  const wasMoved = drag.isMoved
  drag = null
  if (!wasMoved) return
  box.value.style.scrollSnapType = ''
  // 끈 뒤 손을 뗀 자리의 칩이 눌리지 않게 잠깐 막음
  const block = (event) => {
    event.stopPropagation()
    event.preventDefault()
  }
  box.value.addEventListener('click', block, true)
  setTimeout(() => box.value && box.value.removeEventListener('click', block, true), 60)
}
function onWheel(e) {
  const el = box.value
  if (Math.abs(e.deltaY) <= Math.abs(e.deltaX) || el.scrollWidth <= el.clientWidth) return
  e.preventDefault()
  el.scrollLeft += e.deltaY
}
onUnmounted(() => {
  removeListeners()
  if (observer) observer.disconnect()
})
</script>

<template>
  <div class="chip-scroller" :class="{ 'is-more': isMore }">
    <div ref="box" class="chip-scroller__row" @scroll.passive="updateMore" @pointerdown="onPointerDown" @wheel="onWheel" @dragstart.prevent>
      <slot />
    </div>
  </div>
</template>

<style>
.chip-scroller { position: relative; min-width: 0; }
.chip-scroller__row { display: flex; gap: 8px; padding-block: 2px; overflow-x: auto; scrollbar-width: none; scroll-snap-type: x proximity; cursor: grab; user-select: none; -webkit-user-select: none; }
.chip-scroller__row::-webkit-scrollbar { display: none; }
.chip-scroller__row > * { flex: none; scroll-snap-align: start; }
.chip-scroller.is-more .chip-scroller__row { -webkit-mask-image: linear-gradient(to right, #000 calc(100% - 32px), transparent); mask-image: linear-gradient(to right, #000 calc(100% - 32px), transparent); }
</style>
