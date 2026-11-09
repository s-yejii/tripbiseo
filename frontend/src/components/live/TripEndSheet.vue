<script setup>
// 여행 끝 시트 (③, 11/06): 마지막 날 마지막 고정 일정(KTX 같은)에 도착하면 여행 중 화면이 띄워요.
// 총 지출 = 일정 칸마다 1인 예상 금액(utils/budget.js) × 인원 + 결제 메모 금액.
//   결제 메모는 아직 서버에 저장되지 않아서(⑤ /payments 확인 필요) 지금은 0건으로 보여요.
// "회고 보기"를 누르면 부모가 여행을 끝내고(finishTrip) 회고로 가요.
import { computed } from 'vue'
import BottomSheet from '@/components/common/BottomSheet.vue'
import { SHOW_PRICE_ESTIMATE, toWonText, tripBudget } from '@/utils/budget'
import { toMinutes } from '@/utils/time'

const props = defineProps({
  trip: { type: Object, required: true }, // { title, people, stops: [{ category, mode, state }] }
  placeName: { type: String, required: true }, // 마지막에 도착한 곳 "서울역"
  arrivedAt: { type: String, default: '' }, // "18:41"
  plannedAt: { type: String, default: '' }, // 도착 목표 "18:30" (예약 시각)
  isBusy: { type: Boolean, default: false },
})
const emit = defineEmits(['finish', 'close'])

// 다녀온 칸(뺀 칸 제외)만, 앞에서 오는 이동 수단은 칸의 mode로
const visitedStops = computed(() =>
  props.trip.stops.filter((stop) => stop.state !== 'skipped').map((stop) => ({ category: stop.category, leg: stop.mode ? { mode: stop.mode } : null })),
)
const people = computed(() => Math.max(1, props.trip.people || 1))
const perPerson = computed(() => tripBudget(visitedStops.value))
const rows = computed(() => [
  { label: '식사', won: perPerson.value.meal },
  { label: '카페', won: perPerson.value.cafe },
  { label: '입장료', won: perPerson.value.entry },
  { label: '교통', won: perPerson.value.transit },
])
// 목표 시각과 비교한 한 줄 (11/09 주형: 그대로 가서 늦었으면 늦었다고 사실대로)
const gapText = computed(() => {
  if (!props.arrivedAt || !props.plannedAt) return ''
  const gap = toMinutes(props.arrivedAt) - toMinutes(props.plannedAt)
  if (gap > 0) return '목표 ' + props.plannedAt + '보다 ' + gap + '분 늦게 도착'
  if (gap < 0) return '목표 ' + props.plannedAt + '보다 ' + -gap + '분 일찍 도착'
  return '목표 ' + props.plannedAt + ' 딱 맞게 도착'
})
const paymentCount = 0 // 결제 메모 (서버 저장이 생기면 여기서 더함)
</script>

<template>
  <div class="dim" style="z-index: 20"></div>
  <BottomSheet closable :on-close="() => emit('close')" class="trip-end" role="dialog" aria-modal="true" aria-label="여행을 마쳤어요" style="z-index: 21">
    <span class="ico-tile ico-tile--success" style="align-self: center"><i class="ic ic--l i-flag" aria-hidden="true"></i></span>
    <h2 class="h-title" style="text-align: center">{{ placeName }} 도착, 여행을 마쳤어요</h2>
    <p v-if="arrivedAt" class="t-cap tn" style="text-align: center; margin: 0">{{ arrivedAt }} 도착 · {{ trip.title }}</p>
    <p v-if="gapText" class="t-cap tn" :class="{ 'c-coral': gapText.includes('늦게') }" style="text-align: center; margin: 0">{{ gapText }}</p>

    <section v-if="SHOW_PRICE_ESTIMATE" class="trip-end__sum" aria-label="총 지출"><!-- 추정 금액이라 중간발표에서는 숨김 (11/08) -->
      <div class="trip-end__row">
        <b class="t-strong">총 지출 (예상)</b>
        <b class="t-big tn">{{ toWonText(perPerson.total * people) }}</b>
      </div>
      <span class="t-cap tn">{{ people }}명 · 1인 {{ toWonText(perPerson.total) }} · 결제 메모 {{ paymentCount }}건</span>
      <ul class="trip-end__list">
        <li v-for="row in rows" :key="row.label" class="trip-end__row t-cap tn"><span>{{ row.label }}</span><span>{{ toWonText(row.won * people) }}</span></li>
      </ul>
      <span class="t-cap subtle">실제 가격이 아니라 장소 종류별 평균 금액으로 계산했어요</span>
    </section>

    <button type="button" class="btn btn--primary btn--lg btn--block" :disabled="isBusy" @click="emit('finish')"><i class="ic i-flag" aria-hidden="true"></i>회고 보기</button>
  </BottomSheet>
</template>

<style>
.trip-end { display: flex; flex-direction: column; gap: 12px; }
.trip-end__sum { display: flex; flex-direction: column; gap: 6px; padding: 14px; border-radius: var(--radius-lg, 16px); background: var(--surface-2, #f4f5f8); }
.trip-end__row { display: flex; justify-content: space-between; align-items: baseline; gap: 8px; }
.trip-end__list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 4px; }
</style>
