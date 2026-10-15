<script setup>
// 여행 조건 카드: 날짜 · 동행 인원 · 이동 수단 (누르면 선택 창)
import { ref } from 'vue'
import { TRANSPORTS, dateLabel, transportText } from '@/utils/format'
import Modal from './Modal.vue'
import DateRange from './DateRange.vue'

const model = defineModel({ type: Object, required: true }) // { startDate, endDate, people, transports }
const open = ref('')
const people = ref(1)
const picked = ref([])

function openPeople() {
  people.value = model.value.people || 1
  open.value = 'people'
}
function openTransports() {
  picked.value = [...(model.value.transports || [])]
  open.value = 'transports'
}
const toggle = (k) => (picked.value = picked.value.includes(k) ? picked.value.filter((x) => x !== k) : [...picked.value, k])
function setDates({ start, end }) {
  model.value = { ...model.value, startDate: start, endDate: end }
  open.value = ''
}
</script>

<template>
  <section class="cond card">
    <h3>여행 조건</h3>
    <button class="item" @click="open = 'date'">
      <span class="ic">📅</span><span><small>여행 날짜</small><b>{{ dateLabel(model.startDate) }}<template v-if="model.endDate !== model.startDate"> ~ {{ dateLabel(model.endDate) }}</template></b></span>
    </button>
    <button class="item" @click="openPeople">
      <span class="ic">👥</span><span><small>동행 인원</small><b>{{ model.people }}명</b></span>
    </button>
    <button class="item" @click="openTransports">
      <span class="ic">🚇</span><span><small>이동 수단</small><b>{{ transportText(model.transports) }}</b></span>
    </button>
  </section>

  <Modal v-if="open === 'date'" title="여행 날짜" @close="open = ''">
    <DateRange :start="model.startDate" :end="model.endDate" @done="setDates" />
  </Modal>
  <Modal v-if="open === 'people'" title="동행 인원" @close="open = ''">
    <div class="stepper"><button @click="people = Math.max(1, people - 1)">−</button><b>{{ people }}명</b><button @click="people = Math.min(20, people + 1)">+</button></div>
    <button class="btn primary block" @click="(model = { ...model, people }), (open = '')">확인</button>
  </Modal>
  <Modal v-if="open === 'transports'" title="이동 수단 (여러 개 선택)" @close="open = ''">
    <div class="opts">
      <button v-for="t in TRANSPORTS" :key="t.key" :class="{ on: picked.includes(t.key) }" @click="toggle(t.key)">{{ t.icon }} {{ t.label }}</button>
    </div>
    <button class="btn primary block" :disabled="!picked.length" @click="(model = { ...model, transports: picked }), (open = '')">확인</button>
  </Modal>
</template>

<style scoped>
.cond h3 { font-size: 16px; margin-bottom: 8px; }
.item { display: flex; align-items: center; gap: 12px; width: 100%; padding: 10px 12px; margin-top: 8px; border: 1px solid var(--line); border-radius: 12px; text-align: left; }
.item small { display: block; font-size: 12px; color: var(--sub); }
.item b { font-size: 15px; }
.ic { width: 34px; height: 34px; border-radius: 10px; background: var(--blue-soft); display: grid; place-items: center; }
.stepper { display: flex; align-items: center; justify-content: center; gap: 28px; margin: 24px 0; }
.stepper button { width: 48px; height: 48px; border-radius: 50%; background: var(--bg); font-size: 24px; }
.stepper b { font-size: 24px; min-width: 60px; text-align: center; }
.opts { display: flex; flex-wrap: wrap; gap: 8px; margin: 16px 0; }
.opts button { padding: 12px 16px; border-radius: 12px; border: 1.5px solid var(--line); font-weight: 600; }
.opts button.on { border-color: var(--blue); background: var(--blue-soft); color: var(--blue); }
</style>
