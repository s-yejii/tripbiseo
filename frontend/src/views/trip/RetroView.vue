<script setup>
// 회고: 실제로 다닌 길(GPS)과 계획 비교
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { trips } from '@/api'
import { duration, shortDate } from '@/utils/format'
import AppBar from '@/components/AppBar.vue'
import ChatLog from '@/components/ChatLog.vue'
import MapView from '@/components/map/MapView.vue'

const route = useRoute()
const r = ref(null)
const day = ref(1)
const error = ref('')
const logOpen = ref(false)
onMounted(async () => {
  try {
    r.value = await trips.retro(route.params.id)
  } catch (e) {
    error.value = e.message
  }
})
const days = computed(() => [...new Set((r.value?.stops ?? []).map((s) => s.day))])
const stops = computed(() => (r.value?.stops ?? []).filter((s) => s.day === day.value))
const markers = computed(() => stops.value.map((s, i) => ({ id: s.id, lat: s.lat, lng: s.lng, num: i + 1, color: s.actualArrive ? 'blue' : 'gray', label: s.name, sub: s.actualArrive ? `${s.actualArrive} 도착` : s.state === 'skipped' ? '일정에서 뺌' : '안 감', subColor: s.actualArrive ? '' : 'gray' })))
const lines = computed(() => {
  const track = (r.value?.track ?? []).filter((t) => t.day === day.value).map((t) => [t.lat, t.lng])
  return track.length > 1 ? [{ coords: track, color: 'blue', width: 4 }] : stops.value.length > 1 ? [{ coords: stops.value.map((s) => [s.lat, s.lng]), color: 'gray', dashed: true, width: 3 }] : []
})
const diff = (s) => (s.actualStay == null ? '' : s.actualStay - s.stay)
</script>

<template>
  <AppBar :title="r?.title || '회고'" />
  <div class="page retro">
    <p v-if="error" class="error">{{ error }}</p>
    <template v-if="r">
      <p class="muted small">{{ shortDate(r.startDate) }} ~ {{ shortDate(r.endDate) }} · 다닌 곳 {{ r.visited }} / {{ r.stops.filter((s) => s.state !== 'skipped').length }}곳 · 이동 기록 {{ r.track.length }}개</p>
      <button class="btn line sm log" @click="logOpen = true">이 여행 대화 기록</button>
      <div v-if="days.length > 1" class="days"><button v-for="d in days" :key="d" :class="{ on: day === d }" @click="day = d">Day {{ d }}</button></div>
      <div class="map"><MapView :markers="markers" :lines="lines" :fit-key="day" /></div>
      <h3>계획 vs 실제</h3>
      <ol class="cmp">
        <li v-for="s in stops" :key="s.id">
          <b>{{ s.name }}</b>
          <span class="small muted">계획 {{ s.time }} · {{ duration(s.stay) }}</span>
          <span v-if="s.actualArrive" class="small">실제 {{ s.actualArrive }} 도착 · {{ s.actualStay == null ? '출발 기록 없음' : duration(s.actualStay) }}
            <em v-if="diff(s)" :class="diff(s) > 0 ? 'late' : 'ok'">({{ diff(s) > 0 ? '+' : '' }}{{ diff(s) }}분)</em></span>
          <span v-else class="small muted">{{ s.state === 'skipped' ? '여행 중 일정에서 뺌' : '기록 없음' }}</span>
        </li>
      </ol>
    </template>
  </div>
  <ChatLog v-if="logOpen" :trip-id="route.params.id" @close="logOpen = false" />
</template>

<style scoped>
.retro { display: flex; flex-direction: column; gap: 10px; }
.log { align-self: flex-start; }
.days { display: flex; gap: 6px; }
.days button { padding: 6px 12px; border-radius: 999px; background: var(--bg); font-weight: 700; }
.days button.on { background: var(--ink); color: #fff; }
.map { height: 300px; border-radius: 16px; overflow: hidden; flex: none; }
h3 { font-size: 17px; margin-top: 8px; }
.cmp { margin: 0; padding-left: 20px; }
.cmp li { padding: 10px 0; border-bottom: 1px solid var(--line); }
.cmp b, .cmp span { display: block; }
em { font-style: normal; font-weight: 700; } .late { color: var(--red); } .ok { color: var(--green); }
</style>
