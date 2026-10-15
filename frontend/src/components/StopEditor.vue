<script setup>
// 일정 한 칸 수정·삭제 창
import { ref } from 'vue'
import Modal from './Modal.vue'

const props = defineProps({ stop: Object, days: { type: Number, default: 1 } })
const emit = defineEmits(['save', 'remove', 'close'])
const f = ref({ day: props.stop.day, time: props.stop.time, stay: props.stop.stay, fixed: props.stop.fixed, memo: props.stop.memo || '' })
</script>

<template>
  <Modal :title="stop.name" @close="emit('close')">
    <div class="form">
      <label v-if="days > 1">날짜 <select v-model.number="f.day" class="field"><option v-for="n in days" :key="n" :value="n">Day {{ n }}</option></select></label>
      <label>시작 시각 <input v-model="f.time" type="time" class="field" /></label>
      <label>머무는 시간(분) <input v-model.number="f.stay" type="number" min="10" max="600" step="10" class="field" /></label>
      <label class="check"><input v-model="f.fixed" type="checkbox" /> 시간 고정 (공연·예약처럼 늦으면 안 되는 일정)</label>
      <label>메모 <input v-model="f.memo" class="field" maxlength="200" placeholder="예약 번호, 할 일 등" /></label>
      <div class="row">
        <button class="btn ghost" @click="emit('remove')">삭제</button>
        <button class="btn primary grow" @click="emit('save', f)">저장</button>
      </div>
    </div>
  </Modal>
</template>

<style scoped>
.form { display: flex; flex-direction: column; gap: 12px; margin-top: 14px; font-size: 14px; color: var(--sub); }
.form .field { margin-top: 6px; color: var(--ink); }
.check { display: flex; gap: 8px; align-items: center; color: var(--ink); }
.btn.ghost { color: var(--red); }
</style>
