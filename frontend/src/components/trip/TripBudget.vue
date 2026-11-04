<script setup>
// 1인 예상 금액 한 줄: 누르면 식사·카페·입장료·교통 4줄이 펼쳐져요 (계산은 utils/budget.js)
import { computed, ref } from 'vue'
import { toWonText, tripBudget } from '@/utils/budget'

const props = defineProps({
  stops: { type: Array, required: true }, // 화면용 일정 칸
})

const isOpen = ref(false)
const budget = computed(() => tripBudget(props.stops))
const rows = computed(() => [
  { label: '식사', won: budget.value.meal },
  { label: '카페', won: budget.value.cafe },
  { label: '입장료', won: budget.value.entry },
  { label: '교통', won: budget.value.transit },
])
const totalText = computed(() => toWonText(budget.value.total))
</script>

<template>
  <div class="trip-budget">
    <button type="button" class="trip-budget__line" :aria-expanded="isOpen" @click="isOpen = !isOpen">
      1인 예상 <b class="tn">{{ totalText }}</b>
      <i class="ic ic--s" :class="isOpen ? 'i-up' : 'i-down'" aria-hidden="true"></i>
    </button>
    <div v-if="isOpen" class="trip-budget__box">
      <p v-for="row in rows" :key="row.label" class="trip-budget__row"><span>{{ row.label }}</span><span class="tn">{{ toWonText(row.won) }}</span></p>
      <small class="trip-budget__note">예상 금액이에요</small>
    </div>
  </div>
</template>

<style>
.trip-budget { padding: 0 24px 8px; }
.trip-budget__line { display: inline-flex; align-items: center; gap: 4px; padding: 0; border: 0; background: none; font: inherit; font-size: 13px; line-height: 20px; color: var(--ink-muted); cursor: pointer; }
.trip-budget__line b { color: var(--ink); font-weight: 700; }
.trip-budget__box { display: flex; flex-direction: column; gap: 2px; margin-top: 4px; padding: 8px 10px; border-radius: var(--radius-md); background: var(--surface-sunken); }
.trip-budget__row { display: flex; justify-content: space-between; margin: 0; font-size: 13px; line-height: 20px; }
.trip-budget__note { margin-top: 2px; font-size: 10px; line-height: 14px; color: var(--ink-muted); }
</style>
