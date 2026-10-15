<script setup>
// 바로 적용 (목업 5): 바뀐 일정 타임라인 + 다음 알림 안내
import { useRoute, useRouter } from 'vue-router'
import { useLive } from '@/stores/live'
import { duration, placeIcon } from '@/utils/format'
import AppBar from '@/components/AppBar.vue'

const route = useRoute()
const router = useRouter()
const store = useLive()
const id = route.params.id
const r = store.applied
if (!r) router.replace(`/trips/${id}/live`)
const TAG = { done: ['완료', 'green'], kept: ['유지', 'blue'], changed: ['변경', 'orange'], added: ['추가', 'orange'] }
</script>

<template>
  <AppBar title="변경된 일정" :back="false" />
  <div v-if="r" class="page applied">
    <p class="muted small">{{ r.key }}안이 지도와 일정에 반영됐어요</p>
    <ol class="timeline">
      <li v-for="s in r.stops" :key="s.id">
        <span class="time">{{ s.time }}</span>
        <span class="dot" :class="TAG[s.tag]?.[1]" />
        <div class="grow">
          <b>{{ s.name }}</b>
          <small class="muted">{{ s.note || duration(s.stay) }}</small>
          <span class="chip" :class="TAG[s.tag]?.[1]">{{ TAG[s.tag]?.[0] }}</span>
        </div>
        <span class="thumb">{{ placeIcon(s.category) }}</span>
      </li>
    </ol>
    <p class="ok">✓ {{ r.message }}</p>
    <p v-if="r.nextAlert" class="next small">🔔 {{ r.nextAlert }}</p>
    <button class="btn primary block" @click="router.replace(`/trips/${id}/live`)">여행 화면으로</button>
  </div>
</template>

<style scoped>
.applied { display: flex; flex-direction: column; gap: 12px; }
.timeline { list-style: none; margin: 0; padding: 0; }
.timeline li { display: flex; gap: 12px; align-items: flex-start; padding: 10px 0; position: relative; }
.time { width: 46px; font-weight: 800; font-size: 15px; padding-top: 2px; }
.dot { width: 12px; height: 12px; margin-top: 5px; border-radius: 50%; border: 2.5px solid var(--sub); flex: none; background: #fff; }
.dot.blue { border-color: var(--blue); } .dot.orange { border-color: var(--orange); } .dot.green { border-color: var(--green); }
.timeline b { display: block; font-size: 16px; }
.timeline small { display: block; margin: 2px 0 4px; }
.thumb { width: 52px; height: 52px; border-radius: 12px; background: var(--bg); display: grid; place-items: center; font-size: 24px; }
.ok { padding: 14px; border-radius: 14px; background: var(--green-soft); color: var(--green); font-weight: 700; }
.next { padding: 12px 14px; border-radius: 12px; background: var(--bg); }
</style>
