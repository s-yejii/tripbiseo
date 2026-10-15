<script setup>
import { computed } from 'vue'
import { shortDate, today } from '@/utils/format'

const props = defineProps({ trip: Object })
const dday = computed(() => {
  const d = Math.round((new Date(props.trip.startDate) - new Date(today())) / 864e5)
  return d > 0 ? `D-${d}` : d === 0 ? 'D-DAY' : ''
})
</script>

<template>
  <article class="trip card">
    <div class="grow">
      <h3 class="ellipsis">{{ trip.title }}</h3>
      <p class="muted small">{{ shortDate(trip.startDate) }} ~ {{ shortDate(trip.endDate) }} · {{ trip.stopCount }}곳 · {{ trip.people }}명</p>
    </div>
    <span v-if="trip.status === 'ongoing'" class="chip green">진행 중</span>
    <span v-else-if="trip.status === 'done'" class="chip">다녀옴</span>
    <span v-else-if="dday" class="chip blue">{{ dday }}</span>
    <slot />
  </article>
</template>

<style scoped>
.trip { display: flex; align-items: center; gap: 10px; padding: 14px 16px; }
h3 { font-size: 16px; font-weight: 700; margin-bottom: 4px; }
</style>
