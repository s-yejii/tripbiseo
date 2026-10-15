<script setup>
// 달력에서 시작일 → 종료일 순서로 눌러 기간 선택
import { computed, ref } from 'vue'
import { today } from '@/utils/format'

const props = defineProps({ start: String, end: String, min: { type: String, default: () => today() } })
const emit = defineEmits(['done'])
const s = ref(props.start), e = ref(props.end)
const base = new Date((props.start || new Date().toISOString().slice(0, 10)) + 'T00:00:00')
const month = ref(new Date(base.getFullYear(), base.getMonth(), 1))
const iso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

const cells = computed(() => {
  const first = month.value, out = Array(first.getDay()).fill(null)
  const last = new Date(first.getFullYear(), first.getMonth() + 1, 0).getDate()
  for (let i = 1; i <= last; i++) out.push(iso(new Date(first.getFullYear(), first.getMonth(), i)))
  return out
})
const move = (n) => (month.value = new Date(month.value.getFullYear(), month.value.getMonth() + n, 1))
function pick(d) {
  if (!s.value || e.value) return ((s.value = d), (e.value = null))
  if (d < s.value) return (s.value = d)
  e.value = d
}
const inRange = (d) => d && s.value && d >= s.value && d <= (e.value || s.value)
</script>

<template>
  <div>
    <div class="head row"><button class="nav" @click="move(-1)">‹</button><b class="grow">{{ month.getFullYear() }}년 {{ month.getMonth() + 1 }}월</b><button class="nav" @click="move(1)">›</button></div>
    <div class="grid week"><span v-for="w in '일월화수목금토'" :key="w">{{ w }}</span></div>
    <div class="grid">
      <button v-for="(d, i) in cells" :key="i" :disabled="!d || (min && d < min)" :class="{ on: inRange(d), edge: d && (d === s || d === e) }" @click="pick(d)">{{ d ? Number(d.slice(8)) : '' }}</button>
    </div>
    <button class="btn primary block" :disabled="!s" @click="emit('done', { start: s, end: e || s })">
      {{ s ? `${s.slice(5).replace('-', '.')} ~ ${(e || s).slice(5).replace('-', '.')} 선택` : '날짜를 고르세요' }}
    </button>
  </div>
</template>

<style scoped>
.head { margin: 14px 0 8px; text-align: center; }
.nav { width: 36px; height: 36px; font-size: 20px; }
.grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 2px; margin-bottom: 4px; }
.week span { text-align: center; font-size: 12px; color: var(--sub); padding: 6px 0; }
.grid button { height: 40px; border-radius: 10px; font-size: 14px; }
.grid button:disabled { color: #c9ced6; }
.grid button.on { background: var(--blue-soft); color: var(--blue); }
.grid button.edge { background: var(--blue); color: #fff; font-weight: 700; }
.btn { margin-top: 12px; }
</style>
