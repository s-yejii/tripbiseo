<script setup>
import { computed, onMounted, ref } from 'vue'
import { trips as tripApi } from '@/api'
import { today } from '@/utils/format'
import TripCard from '@/components/TripCard.vue'

const list = ref([])
const error = ref('')
const done = computed(() => list.value.filter((t) => t.status === 'done' || t.endDate < today()))
onMounted(async () => {
  try {
    list.value = await tripApi.list()
  } catch (e) {
    error.value = e.message
  }
})
</script>

<template>
  <main class="page with-tab">
    <h1 class="title">회고</h1>
    <p class="muted small">다녀온 여행의 실제 동선과 계획을 비교해 봐요</p>
    <p v-if="error" class="error">{{ error }}</p>
    <div class="list">
      <RouterLink v-for="t in done" :key="t.id" :to="`/trips/${t.id}/retro`" class="plain"><TripCard :trip="t" /></RouterLink>
    </div>
    <p v-if="!done.length && !error" class="empty">다녀온 여행이 아직 없어요</p>
  </main>
</template>

<style scoped>
.title { font-size: 24px; font-weight: 800; margin: 8px 0 4px; }
.list { display: flex; flex-direction: column; gap: 10px; margin-top: 16px; }
.plain { color: inherit; text-decoration: none; }
</style>
