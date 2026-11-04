<script setup>
// 테마로 떠나기 (③): 동그란 테마 사진을 옆으로 넘겨 보고, 누르면 테마 화면으로
// 예) <ThemeCircles /> · 로그인 전 홈: <ThemeCircles to="/login" />
// 지금은 가짜 테마(data/themes.js)라 테마 화면에는 "준비 중"만 나와요
import { onUnmounted, ref } from 'vue'
import { themeList } from '@/data/themes'

const props = defineProps({
  title: { type: String, default: '테마로 떠나기' },
  to: { type: String, default: '' }, // 정해 두면 모든 동그라미가 이 주소로 (예: 로그인 전 → /login)
  exceptId: { type: String, default: '' }, // 테마 화면에서 지금 보는 테마는 빼고
})

const DRAG_START_PX = 5
const box = ref(null)
const themes = themeList.filter((theme) => theme.id !== props.exceptId)
let drag = null // 마우스로 끄는 중: { startX, startScroll, isMoved }

function linkOf(theme) {
  return props.to || '/theme?id=' + theme.id
}

// 마우스로 끌어서 옆으로 넘기기 (휴대폰 손가락은 브라우저가 알아서 넘겨 줌)
function onPointerDown(e) {
  if (e.pointerType !== 'mouse' || e.button !== 0) return
  e.preventDefault() // 끄는 동안 글자가 선택되지 않게
  drag = { startX: e.clientX, startScroll: box.value.scrollLeft, isMoved: false }
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp)
}
function onPointerMove(e) {
  if (!drag) return
  const moved = e.clientX - drag.startX
  if (!drag.isMoved && Math.abs(moved) < DRAG_START_PX) return
  drag.isMoved = true
  box.value.scrollLeft = drag.startScroll - moved
}
function onPointerUp() {
  removeListeners()
  if (!drag) return
  const wasMoved = drag.isMoved
  drag = null
  if (!wasMoved) return
  // 끈 뒤 손을 뗀 자리의 동그라미가 눌리지 않게 잠깐 막음
  const block = (event) => {
    event.stopPropagation()
    event.preventDefault()
  }
  box.value.addEventListener('click', block, true)
  setTimeout(() => box.value && box.value.removeEventListener('click', block, true), 60)
}
function removeListeners() {
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
}
onUnmounted(removeListeners)

// 마우스 휠(위아래)도 옆으로
function onWheel(e) {
  if (Math.abs(e.deltaY) <= Math.abs(e.deltaX)) return
  const canScroll = box.value.scrollWidth > box.value.clientWidth
  if (!canScroll) return
  e.preventDefault()
  box.value.scrollLeft += e.deltaY
}
</script>

<template>
  <section class="theme-circles" aria-labelledby="theme-circles-title">
    <h2 id="theme-circles-title" class="h-title">{{ title }}</h2>
    <div ref="box" class="theme-circles__row" @pointerdown="onPointerDown" @wheel="onWheel" @dragstart.prevent>
      <RouterLink v-for="theme in themes" :key="theme.id" :to="linkOf(theme)" class="theme-circle" :aria-label="theme.title">
        <span class="theme-circle__photo">
          <img :src="theme.img" alt="" />
          <span v-if="theme.badge" class="theme-circle__badge" :class="{ 'is-new': theme.badge === 'NEW' }">{{ theme.badge }}</span>
        </span>
        <span class="theme-circle__label">{{ theme.label }}</span>
      </RouterLink>
    </div>
  </section>
</template>

<style>
.theme-circles { display: flex; flex-direction: column; gap: 12px; margin-bottom: -8px; } /* 아래 인기 장소와 붙여 보이게 (칸 사이 기본 24px → 16px) */
.theme-circles__row { display: flex; gap: 12px; overflow-x: auto; margin: 0 -16px; padding: 2px 16px; scrollbar-width: none; scroll-snap-type: x proximity; scroll-padding: 0 16px; cursor: grab; user-select: none; }
.theme-circles__row::-webkit-scrollbar { display: none; }
.theme-circle { flex: none; width: 72px; display: flex; flex-direction: column; align-items: center; gap: 6px; text-decoration: none; color: var(--ink); scroll-snap-align: start; -webkit-user-drag: none; }
.theme-circle__photo { position: relative; width: 64px; height: 64px; }
.theme-circle__photo img { width: 100%; height: 100%; border-radius: 50%; object-fit: cover; border: 1px solid var(--line); background: var(--surface-sunken); -webkit-user-drag: none; }
.theme-circle:hover img, .theme-circle:focus-visible img { border-color: var(--navy); }
.theme-circle__badge { position: absolute; top: -2px; right: -6px; padding: 0 5px; border-radius: var(--radius-full); background: var(--coral-soft); color: var(--coral-ink); font-size: 10px; font-weight: 700; line-height: 16px; border: 1px solid var(--surface-raised); }
.theme-circle__badge.is-new { background: var(--navy-soft); color: var(--navy); }
.theme-circle__label { font-size: 12px; line-height: 16px; color: var(--ink-muted); text-align: center; white-space: pre-line; word-break: keep-all; }
</style>
