<script setup>
// 일정 화면 공용 틀: 위 지도(핀·동선) + 아래 시트(제목·미리 확인할 것·일정 목록) + 맨 아래 버튼
// 일정 점검(S10-a), 여행 상세(S11, S11-b), 테마 코스(S04-d)가 같이 써요
//   slot right : 지도 위 오른쪽 버튼 (선택한 칸 번호를 받아요)
//   slot below : 일정 목록 아래 (예: 챙길 것)
//   slot cta   : 맨 아래 버튼
import { computed, nextTick, ref } from 'vue'
import BottomSheet from '@/components/common/BottomSheet.vue'
import TopBar from '@/components/common/TopBar.vue'
import TripTimeline from '@/components/trip/TripTimeline.vue'
import CheckList from '@/components/trip/CheckList.vue'

const props = defineProps({
  trip: { type: Object, required: true },
  badge: { type: Object, default: null }, // { text: '오늘', cls: 'badge--coral' }
  sub: { type: String, default: '' }, // 제목 아래 줄을 바꾸고 싶을 때
  back: { type: String, default: '/trips' },
  rainSwap: { type: Boolean, default: false },
})

const selected = ref(null)
const scrollBox = ref(null)

const subText = computed(() => props.sub || `${props.trip.date} · ${props.trip.stops.length}곳 · 이동 ${props.trip.move}분 · ${props.trip.who}`)
const routePoints = computed(() => props.trip.stops.map((s) => s.x + ',' + s.y).join(' '))

// 고른 칸의 사진·이름 말풍선 위치
const tooltip = computed(() => {
  if (selected.value === null) return null
  const stop = props.trip.stops[selected.value]
  return {
    stop,
    x: Math.min(Math.max(stop.x, 110), 280),
    y: stop.y - 82 < 104 ? stop.y + 26 : stop.y - 82,
  }
})

// 핀이나 목록을 누르면 같이 강조하고, 목록은 그 칸이 보이게 스크롤
async function select(i) {
  selected.value = selected.value === i ? null : i
  if (selected.value === null) return
  await nextTick()
  const el = scrollBox.value.querySelector(`[data-stop="${i}"]`)
  if (el) el.scrollIntoView({ block: 'center', behavior: 'smooth' })
}
</script>

<template>
  <div class="app">
    <div class="map" aria-hidden="true" style="bottom: 404px"><img class="base live" src="/img/mapB.svg" alt="" style="top: -186px" /></div>
    <svg class="route trip-route" viewBox="0 0 390 344">
      <polyline :points="routePoints" fill="none" stroke="#1F3A68" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" stroke-dasharray="1 8" />
    </svg>
    <button
      v-for="(stop, i) in trip.stops"
      :key="stop.id + i"
      type="button"
      class="pin"
      :class="{ 'pin--fixed': stop.fixed, 'pin--warn': stop.warn, 'pin--sel': selected === i }"
      :style="{ left: stop.x + 'px', top: stop.y + 'px' }"
      :aria-label="i + 1 + ' ' + stop.name"
      @click="select(i)"
    >
      <i v-if="stop.fixed" class="ic ic--s i-lock" aria-hidden="true" style="width: 13px; height: 13px"></i>
      <template v-else>{{ i + 1 }}</template>
    </button>
    <RouterLink v-if="tooltip" to="/place" class="tooltip" :style="{ left: tooltip.x + 'px', top: tooltip.y + 'px', transform: 'translateX(-50%)' }">
      <img :src="tooltip.stop.img" alt="" /><b class="t-strong" style="font-size: 14px">{{ tooltip.stop.name }}</b><i class="ic ic--s i-next subtle" aria-hidden="true"></i>
    </RouterLink>

    <TopBar float :back="back">
      <slot name="right" :selected="selected"></slot>
    </TopBar>

    <BottomSheet class="sheet--peek trip-sheet" aria-labelledby="trip-title">
      <div class="trip-head">
        <div style="flex: 1; min-width: 0">
          <h1 id="trip-title" class="h-title">{{ trip.title }}</h1>
          <p class="t-cap tn">
            <span v-if="badge" class="badge" :class="badge.cls" style="height: 20px; margin-right: 6px">{{ badge.text }}</span>{{ subText }}
          </p>
        </div>
        <span v-if="trip.slack !== undefined" class="badge badge--success tn"><i class="ic ic--s i-check" aria-hidden="true"></i>예약 여유 {{ trip.slack }}분</span>
      </div>
      <div ref="scrollBox" class="trip-scroll">
        <CheckList :items="trip.checks" :rain-swap="rainSwap" />
        <TripTimeline :stops="trip.stops" :selected="selected" @select="select" />
        <slot name="below"></slot>
      </div>
    </BottomSheet>

    <div v-if="$slots.cta" class="cta cta--white">
      <slot name="cta"></slot>
    </div>
  </div>
</template>

<style>
.trip-route { position: absolute; left: 0; top: 0; width: 390px; height: 344px; pointer-events: none; }
</style>
