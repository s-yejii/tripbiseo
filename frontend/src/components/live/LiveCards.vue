<script setup>
// 여행 중 지도 아래의 일정 카드 줄 (옆으로 넘겨 보기) + 카드 사이 이동 칩
// cards: [{ no, img, title, sub, subCls, state: 'done'|'now'|'', fixed, leg, legState }]
//   leg: 앞 장소에서 오는 이동 { mode, min, dist } / legState: 'past'(지나감) | 'now'(지금 이동할 구간) | ''
import { onMounted, ref } from 'vue'
import { modes } from '@/data/places'

const props = defineProps({
  cards: { type: Array, required: true },
  selected: { type: Number, default: 0 },
})
const emit = defineEmits(['pick', 'seg', 'scrolled'])

const box = ref(null)
let movedByCode = false // 처음 가운데 맞출 때는 선택을 바꾸지 않아요

// 옆으로 넘기면 가운데에 온 카드 번호를 알려 줘요 (지도 핀 강조용)
function onScroll() {
  if (movedByCode) {
    movedByCode = false
    return
  }
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
  <div ref="box" class="hscroll" aria-label="일정, 옆으로 넘겨 보기" @scroll.passive="onScroll">
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
.hcard__name { font-size: 14px; display: flex; align-items: center; gap: 4px; }
</style>
