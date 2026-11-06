<script setup>
// 여행 중 지도 아래의 일정 카드 줄 (옆으로 넘겨 보기) + 카드 사이 이동 칩
// cards: [{ no, img, title, sub, subCls, state: 'done'|'now'|'', fixed, leg, legState }]
//   leg: 앞 장소에서 오는 이동 { mode, min, dist } / legState: 'past'(지나감) | 'now'(지금 이동할 구간) | ''
import { onMounted, onUnmounted, ref } from 'vue'
import { modes } from '@/data/places'

const props = defineProps({
  cards: { type: Array, required: true },
  selected: { type: Number, default: 0 },
})
const emit = defineEmits(['pick', 'seg', 'scrolled'])

const box = ref(null)
let movedByCode = false // 처음 가운데 맞출 때는 선택을 바꾸지 않아요

// 다른 카드를 보다가 손을 떼고 3초가 지나면 지금 있는 곳 카드로 돌아와요
const BACK_TO_NOW_MS = 3000
const RETURN_SCROLL_MS = 700 // 부드럽게 돌아가는 동안은 스크롤을 사용자 조작으로 보지 않음
let backTimer = null
let isReturning = false

function nowCardLeft() {
  const el = box.value && box.value.querySelector('.hcard.is-now')
  if (!el) return null
  return el.offsetLeft - (box.value.clientWidth - el.offsetWidth) / 2
}

function scheduleBackToNow() {
  clearTimeout(backTimer)
  backTimer = setTimeout(scrollBackToNow, BACK_TO_NOW_MS)
}

function scrollBackToNow() {
  if (drag) return scheduleBackToNow() // 아직 끄는 중이면 다시 기다림
  const left = nowCardLeft()
  if (left === null || Math.abs(box.value.scrollLeft - left) < 4) return
  isReturning = true
  box.value.scrollTo({ left: left, behavior: 'smooth' })
  setTimeout(() => {
    isReturning = false
    const nowCard = props.cards.find((card) => card.state === 'now')
    if (nowCard) emit('scrolled', nowCard.no) // 지도 핀 강조도 지금 카드로
  }, RETURN_SCROLL_MS)
}

// 옆으로 넘기면 가운데에 온 카드 번호를 알려 줘요 (지도 핀 강조용)
function onScroll() {
  if (movedByCode) {
    movedByCode = false
    return
  }
  if (isReturning) return
  scheduleBackToNow()
  const mid = box.value.scrollLeft + box.value.clientWidth / 2
  const cardEls = box.value.querySelectorAll('.hcard')
  let best = 0
  let bestGap = Infinity
  cardEls.forEach((el, i) => {
    const gap = Math.abs(el.offsetLeft + el.offsetWidth / 2 - mid)
    if (gap < bestGap) {
      bestGap = gap
      best = i
    }
  })
  emit('scrolled', props.cards[best].no)
}

// 마우스로 끌어서 옆으로 넘기기 (휴대폰 손가락은 브라우저가 알아서 넘겨 줌)
const DRAG_START_PX = 5
let drag = null // { startX, startScroll, isMoved }

function onPointerDown(e) {
  if (e.pointerType !== 'mouse' || e.button !== 0) return
  e.preventDefault() // 끄는 동안 글자가 파랗게 선택되지 않게 (클릭은 그대로 됨)
  drag = { startX: e.clientX, startScroll: box.value.scrollLeft, isMoved: false }
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp)
}
function onPointerMove(e) {
  if (!drag) return
  const moved = e.clientX - drag.startX
  if (!drag.isMoved && Math.abs(moved) < DRAG_START_PX) return
  if (!drag.isMoved) {
    drag.isMoved = true
    box.value.style.scrollSnapType = 'none' // 끄는 동안은 카드에 딱 붙지 않게
    box.value.style.cursor = 'grabbing'
  }
  box.value.scrollLeft = drag.startScroll - moved
}
function onPointerUp() {
  removeListeners()
  if (!drag) return
  const wasMoved = drag.isMoved
  drag = null
  box.value.style.cursor = ''
  if (!wasMoved) return
  box.value.style.scrollSnapType = '' // 놓으면 가까운 카드에 붙음
  // 끈 뒤 손을 뗀 자리의 카드가 눌리지 않게 잠깐 막음
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
onUnmounted(() => {
  removeListeners()
  clearTimeout(backTimer)
})

// 마우스 휠(위아래)도 옆으로 넘기기
function onWheel(e) {
  if (Math.abs(e.deltaY) <= Math.abs(e.deltaX)) return // 트랙패드 옆 밀기는 그대로
  e.preventDefault()
  box.value.scrollLeft += e.deltaY
}

// 처음 열 때 지금 있는 곳 카드를 가운데로
onMounted(() => {
  const el = box.value.querySelector('.hcard.is-now') || box.value.querySelector('.hcard.is-sel')
  if (el) {
    movedByCode = true
    box.value.scrollLeft = el.offsetLeft - (box.value.clientWidth - el.offsetWidth) / 2
  }
})
</script>

<template>
  <div ref="box" class="hscroll live-cards" aria-label="일정, 옆으로 넘겨 보기 (지난 일정은 왼쪽)" @scroll.passive="onScroll" @pointerdown="onPointerDown" @wheel="onWheel" @dragstart.prevent>
    <template v-for="card in cards" :key="card.no">
      <span v-if="card.leg" class="segw" :class="card.legState ? 'segw--' + card.legState : ''">
        <button
          type="button"
          class="segc"
          :class="card.legState ? 'segc--' + card.legState : ''"
          :aria-label="`${modes[card.leg.mode].label} ${card.leg.min}분, ${card.leg.dist}, 길찾기`"
          @click="emit('seg', card)"
        >
          <b class="tn"><i class="ic ic--s" :class="modes[card.leg.mode].icon" aria-hidden="true"></i>{{ card.leg.min }}분</b>
          <span class="tn segc__d">{{ card.leg.dist }}</span>
        </button>
      </span>
      <button
        type="button"
        class="hcard"
        :class="{ 'is-done': card.state === 'done', 'is-now': card.state === 'now', 'is-sel': card.no === selected }"
        @click="emit('pick', card)"
      >
        <span v-if="card.state === 'done'" class="hcard__tag"><i class="ic ic--s i-check" aria-hidden="true"></i>다녀옴</span>
        <img :src="card.img" alt="" />
        <span class="t-strong hcard__name">
          <i v-if="card.fixed" class="ic ic--s i-lock c-navy" aria-hidden="true"></i>{{ card.title }}
        </span>
        <span class="t-cap tn" :class="card.subCls">{{ card.sub }}</span>
      </button>
    </template>
    <span style="flex: none; width: 8px"></span>
  </div>
</template>

<style>
.live-cards { cursor: grab; user-select: none; }
.live-cards img { -webkit-user-drag: none; user-select: none; }
.hcard__name { font-size: 14px; display: flex; align-items: center; gap: 4px; }
</style>
