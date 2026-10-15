<script setup>
// 일정 목록 (화면 7): Day별로 접고 펼치기, 장소를 누르면 select
import { computed, ref } from 'vue'
import { dateLabel, dayDate, duration, metres, placeIcon } from '@/utils/format'

const props = defineProps({
  stops: { type: Array, default: () => [] }, // stop + (선택) arrive, depart, issue, moveMin, moveM
  startDate: String,
  editable: Boolean,
  currentId: [Number, String],
})
const emit = defineEmits(['select', 'edit'])
const closed = ref(new Set())
const days = computed(() => {
  const map = new Map()
  for (const s of props.stops) (map.get(s.day) ?? map.set(s.day, []).get(s.day)).push(s)
  return [...map.entries()].sort((a, b) => a[0] - b[0])
})
const toggle = (d) => (closed.value.has(d) ? closed.value.delete(d) : closed.value.add(d), (closed.value = new Set(closed.value)))
</script>

<template>
  <div class="itin">
    <section v-for="[day, list] in days" :key="day">
      <button class="day" @click="toggle(day)">
        <span class="caret" :class="{ shut: closed.has(day) }">⌄</span>
        <b>Day {{ day }}</b><span class="muted small">{{ startDate ? dateLabel(dayDate(startDate, day)) : '' }}</span>
      </button>
      <ol v-if="!closed.has(day)">
        <template v-for="(s, i) in list" :key="s.id ?? i">
          <li v-if="i > 0 && (s.moveMin != null || s.moveM != null)" class="gap muted small">
            ↳ {{ s.moveM != null ? metres(s.moveM) : '' }}{{ s.moveMin != null ? ` · 이동 ${duration(s.moveMin)}` : '' }}
          </li>
          <li class="stop" :class="{ now: s.id === currentId, done: s.state === 'done', skip: s.state === 'skipped' }">
            <span class="idx">{{ i + 1 }}</span>
            <button class="body" @click="emit('select', s)">
              <span class="thumb">{{ placeIcon(s.category) }}</span>
              <span class="grow">
                <b class="ellipsis">{{ s.name }}</b>
                <small class="muted ellipsis">{{ s.category || '장소' }}</small>
                <small class="time">
                  🕑 {{ s.arrive || s.time }}<template v-if="s.stay"> · {{ duration(s.stay) }}</template>
                  <span v-if="s.fixed" class="chip red">고정</span>
                  <span v-if="s.state === 'done'" class="chip green">완료</span>
                  <span v-if="s.state === 'skipped'" class="chip">일정에서 뺌</span>
                </small>
                <small v-if="s.issue" class="issue">⚠ {{ s.issue }}</small>
              </span>
            </button>
            <button v-if="editable" class="edit" aria-label="수정" @click="emit('edit', s)">✎</button>
          </li>
        </template>
      </ol>
    </section>
  </div>
</template>

<style scoped>
.day { display: flex; align-items: baseline; gap: 8px; padding: 12px 0 8px; }
.day b { font-size: 18px; }
.caret { display: inline-block; transition: transform .2s; }
.caret.shut { transform: rotate(-90deg); }
ol { list-style: none; margin: 0; padding: 0; }
.stop { display: flex; align-items: center; gap: 10px; margin: 6px 0; }
.idx { width: 22px; height: 22px; flex: none; border-radius: 50%; background: var(--bg); color: var(--sub); font-size: 12px; display: grid; place-items: center; }
.now .idx { background: var(--blue); color: #fff; }
.done { opacity: .6; }
.skip .body b { text-decoration: line-through; }
.body { flex: 1; min-width: 0; display: flex; gap: 12px; align-items: center; padding: 12px; border: 1px solid var(--line); border-radius: 14px; text-align: left; }
.now .body { border-color: var(--blue); box-shadow: 0 0 0 1px var(--blue); }
.thumb { width: 52px; height: 52px; flex: none; border-radius: 10px; background: var(--bg); display: grid; place-items: center; font-size: 24px; }
.body b, .body small { display: block; }
.body b { font-size: 16px; }
.body small { font-size: 12px; margin-top: 2px; }
.time { color: var(--ink); display: flex !important; align-items: center; gap: 6px; }
.time .chip { padding: 1px 6px; font-size: 11px; }
.issue { color: #b45309; }
.gap { padding: 0 0 0 34px; }
.edit { width: 32px; height: 32px; border-radius: 50%; background: var(--bg); flex: none; }
</style>
