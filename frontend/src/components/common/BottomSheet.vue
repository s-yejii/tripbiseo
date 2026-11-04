<script setup>
// 아래에서 올라오는 시트 (프로토타입과 같은 동작)
//   - 손잡이·시트 머리(.sheet__head, data-drag 붙은 곳)를 위아래로 끌면 따라 움직이고,
//     놓으면 펼침 / 손잡이만 남기기 중 가까운 쪽으로 붙어요
//   - 손잡이를 누르거나(Enter) 머리 위에서 휠을 굴려도 펼치고 내려요
//   - 같은 화면(.app)의 data-sheet-follow 붙은 것(지도 위 버튼·칩)은 시트와 같이 움직여요
// 예) <BottomSheet class="sheet--peek" style="top: 440px"> <div data-drag>머리</div> 내용 </BottomSheet>
//     내용이 짧은 시트는 <BottomSheet data-drag>처럼 시트 자체에 붙이면 어디를 잡아도 끌려요 (버튼·링크는 그대로 눌림)
import { onUnmounted, ref } from 'vue'

const HANDLE_LEFT_PX = 34 // 내렸을 때 남겨 둘 손잡이 높이
const MOVE_START_PX = 6 // 이만큼 움직여야 끌기로 봄 (그 전엔 누르기)
const SNAP_FROM_OPEN = 0.3 // 펼친 상태에서 끌 때: 이 비율 넘게 내리면 내림
const SNAP_FROM_CLOSED = 0.65 // 내린 상태에서 끌 때: 이 비율보다 위로 올리면 펼침
const ANIMATION = 'transform .28s cubic-bezier(.2,.8,.2,1)'
const GRAB_AREA = '.handle, .sheet__head, .trip-head, [data-drag]'
const NOT_GRAB = 'a, button, input, textarea, label, select'

const sheet = ref(null)
const isCollapsed = ref(false)
let drag = null // 끄는 중: { startY, scale, base, offset, max, isMoved, isHandle }

// 가장 많이 내릴 수 있는 거리(px): 아래 버튼 줄(.cta)이 있으면 그 위까지
function maxOffset() {
  const app = sheet.value.parentElement
  const cta = app.querySelector(':scope > .cta')
  const bottom = cta ? cta.offsetTop : app.offsetHeight
  return Math.max(0, bottom - HANDLE_LEFT_PX - sheet.value.offsetTop)
}

// 시트와 같이 움직일 것들 + 어둡게 덮은 막(.dim)
function moveWith(offset, isAnimated) {
  const app = sheet.value.parentElement
  for (const element of app.querySelectorAll('[data-sheet-follow]')) {
    element.style.transition = isAnimated ? ANIMATION : 'none'
    element.style.transform = offset ? `translateY(${offset}px)` : ''
  }
  const dim = app.querySelector(':scope > .dim')
  if (dim) {
    dim.style.transition = 'opacity .28s'
    dim.style.opacity = offset ? '0' : ''
    dim.style.pointerEvents = offset ? 'none' : ''
  }
}

function setOffset(offset, isAnimated) {
  sheet.value.style.transition = isAnimated ? ANIMATION : 'none'
  sheet.value.style.transform = offset ? `translateY(${offset}px)` : ''
  moveWith(offset, isAnimated)
}

function setCollapsed(collapsed) {
  isCollapsed.value = collapsed
  setOffset(collapsed ? maxOffset() : 0, true)
}

function toggle() {
  setCollapsed(!isCollapsed.value)
}

function onPointerDown(e) {
  const grab = e.target.closest(GRAB_AREA)
  if (!grab || !sheet.value.contains(grab)) return
  if (e.target.closest(NOT_GRAB)) return // 머리 안의 버튼·링크는 그대로 눌리게
  const scale = sheet.value.getBoundingClientRect().height / sheet.value.offsetHeight || 1 // 화면이 축소돼 보일 때 보정
  const max = maxOffset()
  drag = { startY: e.clientY, scale, base: isCollapsed.value ? max : 0, offset: 0, max, isMoved: false, isHandle: grab.classList.contains('handle') }
  e.preventDefault()
  window.addEventListener('pointermove', onPointerMove, { passive: false })
  window.addEventListener('pointerup', onPointerUp)
  window.addEventListener('pointercancel', onPointerUp)
}

function onPointerMove(e) {
  if (!drag) return
  const moved = (e.clientY - drag.startY) / drag.scale
  if (!drag.isMoved && Math.abs(moved) < MOVE_START_PX) return
  drag.isMoved = true
  drag.offset = Math.min(drag.max, Math.max(0, drag.base + moved))
  setOffset(drag.offset, false)
  e.preventDefault()
}

function onPointerUp() {
  removeListeners()
  if (!drag) return
  const done = drag
  drag = null
  if (!done.isMoved) {
    if (done.isHandle) toggle() // 손잡이를 그냥 누름
    return
  }
  const limit = done.base ? done.max * SNAP_FROM_CLOSED : done.max * SNAP_FROM_OPEN
  setCollapsed(done.offset > limit)
  // 끌기를 끝낸 순간 손가락 밑 카드가 눌리지 않게 잠깐 막음
  const block = (event) => {
    event.stopPropagation()
    event.preventDefault()
  }
  sheet.value.addEventListener('click', block, true)
  setTimeout(() => sheet.value && sheet.value.removeEventListener('click', block, true), 60)
}

// 머리 위에서 휠: 아래로 굴리면 내리고, 위로 굴리면 펼침
function onWheel(e) {
  if (!e.target.closest(GRAB_AREA)) return
  if (e.deltaY > 0 && !isCollapsed.value) setCollapsed(true)
  else if (e.deltaY < 0 && isCollapsed.value) setCollapsed(false)
}

function removeListeners() {
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
  window.removeEventListener('pointercancel', onPointerUp)
}

onUnmounted(removeListeners)
</script>

<template>
  <section ref="sheet" class="sheet" :class="{ 'is-collapsed': isCollapsed }" @pointerdown="onPointerDown" @wheel.passive="onWheel">
    <span
      class="handle"
      role="button"
      tabindex="0"
      :aria-label="isCollapsed ? '시트 펼치기' : '시트 내리기'"
      :aria-expanded="!isCollapsed"
      @keydown.enter.prevent="toggle"
      @keydown.space.prevent="toggle"
    ></span>
    <slot />
  </section>
</template>

<style>
.sheet [data-drag], .sheet[data-drag] { touch-action: none; cursor: grab; }
.sheet .handle { touch-action: none; cursor: grab; }
</style>
