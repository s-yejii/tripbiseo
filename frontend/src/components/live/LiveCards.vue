<script setup>
// 여행 중 지도 아래의 일정 카드 줄 (옆으로 넘겨 보기) + 카드 사이 이동 칩
// cards: [{ no, img, title, sub, subCls, state: 'done'|'now'|'', fixed, leg, legState }]
//   leg: 앞 장소에서 오는 이동 { mode, min, dist } / legState: 'past'(지나감) | 'now'(지금 이동할 구간) | ''
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { modes } from '@/data/places'

const props = defineProps({
  cards: { type: Array, required: true },
  selected: { type: Number, default: 0 },
  autoFocus: { type: Boolean, default: false }, // 시연 계정(11/09): 지금 장소·다음 갈 곳이 바뀌면 그 카드로 자동으로 넘김
  focus: { type: Object, default: null },       // 지도 핀을 눌렀을 때 { no, at }: 그 카드를 가운데로 (11/12). at은 같은 핀을 다시 눌러도 바뀌게 하는 값
  showEnd: { type: Boolean, default: false },   // 맨 끝에 "여행 끝내기" 카드 (11/12)
})
const emit = defineEmits(['pick', 'seg', 'scrolled', 'end'])

const box = ref(null)
let movedByCode = false // 처음 가운데 맞출 때는 선택을 바꾸지 않아요

// 다른 카드를 보다가 손을 떼고 3초가 지나면 지금 있는 곳 카드로 돌아와요
const BACK_TO_NOW_MS = 3000
const RETURN_SCROLL_MS = 700 // 부드럽게 돌아가는 동안은 스크롤을 사용자 조작으로 보지 않음
let backTimer = null
let isReturning = false
let isHeld = false // 카드를 누르거나 핀으로 가운데 맞춘 뒤에는, 다시 손으로 넘기기 전까지 지금 카드로 안 돌아감 (11/12)

function nowCardLeft() {
  const el = box.value && box.value.querySelector('.hcard.is-now')
  if (!el) return null
  return el.offsetLeft - (box.value.clientWidth - el.offsetWidth) / 2
}

function scheduleBackToNow() {
  if (isHeld) return
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
  const cardEls = box.value.querySelectorAll('.hcard[data-no]') // 끝내기 카드(번호 없음)는 빼고 일정 카드만
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
  isHeld = false // 손을 대면 다시 3초 뒤 돌아가기
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
  isHeld = false
  if (Math.abs(e.deltaY) <= Math.abs(e.deltaX)) return // 트랙패드 옆 밀기는 그대로
  e.preventDefault()
  box.value.scrollLeft += e.deltaY
}

// 도착해서 지금 장소가 바뀌거나, 떠나서 다음 갈 곳이 바뀌면 그 카드로 자동으로 넘겨요 (11/09 시연 피드백)
// 지금 있는 곳(now)이 있으면 그 카드, 이동 중이면 아직 안 간 첫 카드
const focusNo = computed(() => {
  const nowCard = props.cards.find((card) => card.state === 'now')
  if (nowCard) return nowCard.no
  const nextCard = props.cards.find((card) => card.state !== 'done')
  return nextCard ? nextCard.no : null
})
watch(focusNo, async (no, oldNo) => {
  if (!props.autoFocus || no == null || no === oldNo || drag) return
  await nextTick() // 카드 모양(지금·다녀옴)이 화면에 바뀐 뒤에 위치를 재요
  centerCard(no, () => {
    // 도착한 곳이면 지도 핀 강조도 그 카드로. 이동 중(다음 갈 곳)은 지도가 내 위치를 따라가니 알리지 않음
    if (props.cards.some((card) => card.no === no && card.state === 'now')) emit('scrolled', no)
  })
})

// 지도 핀을 누르면 그 카드를 가운데로 (11/12). 선택(selected)은 손으로 넘길 때도 바뀌어서, 그걸 보면 넘기는 중에 끌려가요 → 핀 신호만 봄
watch(() => props.focus, async (focus) => {
  if (!focus || drag) return
  isHeld = true
  clearTimeout(backTimer)
  await nextTick()
  centerCard(focus.no, null)
})

// 카드 하나를 부드럽게 가운데로. 움직이는 동안은 사용자가 넘긴 걸로 보지 않고(3초 뒤 돌아가기 안 함), 끝나면 done을 불러요
function centerCard(no, done) {
  const el = box.value && box.value.querySelector('.hcard[data-no="' + no + '"]')
  if (!el) return
  isReturning = true
  box.value.scrollTo({ left: el.offsetLeft - (box.value.clientWidth - el.offsetWidth) / 2, behavior: 'smooth' })
  setTimeout(() => {
    isReturning = false
    if (done) done()
  }, RETURN_SCROLL_MS)
}

// 카드를 누르면 그 카드를 가운데로 옮기고 부모에게 알려요 (11/12)
function onCardClick(card) {
  isHeld = true
  clearTimeout(backTimer)
  centerCard(card.no, null)
  emit('pick', card)
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
        :data-no="card.no"
        :class="{ 'is-done': card.state === 'done', 'is-now': card.state === 'now', 'is-sel': card.no === selected }"
        @click="onCardClick(card)"
      >
        <span v-if="card.state === 'done'" class="hcard__tag"><i class="ic ic--s i-check" aria-hidden="true"></i>다녀옴</span>
        <img :src="card.img" alt="" />
        <span class="t-strong hcard__name">
          <i v-if="card.fixed" class="ic ic--s i-lock c-navy" aria-hidden="true"></i>{{ card.title }}
        </span>
        <span class="t-cap tn" :class="card.subCls">{{ card.sub }}</span>
      </button>
    </template>
    <!-- 여행 끝내기 (11/12): 마지막 카드 뒤 점선 끝에서 트리피가 말풍선으로 말해요(말풍선 왼쪽, 깃발 든 트리피 오른쪽). 누르면 부모가 한 번 더 물어봐요 -->
    <template v-if="showEnd">
      <span class="end-trail" aria-hidden="true"></span>
      <button type="button" class="trippie-end" aria-label="여행 끝내기" @click="emit('end')">
        <span class="trippie-end__bubble"><span class="trippie-end__sub">여행을 마쳤으면</span><b class="trippie-end__main">나를 눌러줘!</b></span>
        <img src="/img/trippie-flag.webp" alt="" class="trippie-end__img" aria-hidden="true" />
      </button>
    </template>
    <span style="flex: none; width: 8px"></span>
  </div>
</template>

<style>
.live-cards { cursor: grab; user-select: none; }
.live-cards img { -webkit-user-drag: none; user-select: none; }
.hcard__name { font-size: 14px; display: flex; align-items: center; gap: 4px; }
/* 여행 끝내기: 테두리 없이 트리피 + 말풍선, 마지막 카드에서 점선으로 이어짐 (11/12) */
.end-trail { flex: none; align-self: center; width: 44px; height: 6px; margin: 0 2px 0 6px; background: radial-gradient(circle, var(--line-strong) 1.5px, transparent 2px) 0 50% / 9px 6px repeat-x; opacity: .7; }
.trippie-end { flex: none; align-self: center; display: flex; align-items: center; gap: 8px; padding: 12px 4px 16px 8px; border: 0; border-radius: var(--radius-lg); background: none; color: var(--ink); cursor: pointer; font: inherit; text-align: left; scroll-snap-align: center; transition: transform .12s, background-color .15s; }
.trippie-end:hover .trippie-end__bubble { transform: translateY(-2px); }
.trippie-end:active { transform: scale(.96); }
.trippie-end:focus-visible { outline: 2px solid var(--navy); outline-offset: 2px; }
/* 말풍선: 흰 바탕 + 부드러운 그림자(테두리 없음), 꼬리는 둥글게 휜 모양이 트리피 쪽으로 */
.trippie-end__bubble { position: relative; display: flex; flex-direction: column; gap: 1px; padding: 12px 16px 13px; border-radius: 18px; background: #fff; filter: drop-shadow(0 6px 14px rgba(24, 33, 58, .12)) drop-shadow(0 1px 2px rgba(24, 33, 58, .08)); white-space: nowrap; transition: transform .2s ease; }
.trippie-end__bubble::after { content: ''; position: absolute; right: -11px; top: 50%; width: 14px; height: 16px; margin-top: -6px; background: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 14 16'%3E%3Cpath d='M0 0Q4 8 14 10Q5 12 0 16Z' fill='%23fff'/%3E%3C/svg%3E") no-repeat center / contain; }
.trippie-end__sub { font-size: 14px; line-height: 20px; font-weight: 600; color: var(--ink); letter-spacing: -.01em; }
.trippie-end__main { font-size: 16px; line-height: 22px; font-weight: 700; color: var(--ink); letter-spacing: -.02em; }
/* 트리피 그림 (착착트립 깃발 든 전신, 확인 창에서도 같이 씀) */
.trippie-end__img { flex: none; width: 160px; height: 132px; object-fit: contain; -webkit-user-drag: none; transform-origin: 30% 100%; animation: trippieBob 2.6s ease-in-out infinite; }
@keyframes trippieBob { 0%, 100% { transform: translateY(0); } 50% { transform: translateY(-3px); } }
@media (prefers-reduced-motion: reduce) { .trippie-end__img { animation: none; } }
</style>
