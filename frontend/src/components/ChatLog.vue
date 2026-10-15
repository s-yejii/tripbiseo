<script setup>
// 이 여행 대화 기록: AI 계획 대화와 여행 중 대화를 한 줄로 시간순 (날짜가 바뀌면 구분선)
import { onMounted, ref } from 'vue'
import { trips } from '@/api'
import { dateLabel } from '@/utils/format'
import Modal from './Modal.vue'

const props = defineProps({ tripId: [Number, String] })
defineEmits(['close'])
const list = ref(null)
const error = ref('')
onMounted(async () => {
  try {
    list.value = await trips.chats(props.tripId)
  } catch (e) {
    error.value = e.message
  }
})
const newDay = (i) => i === 0 || list.value[i - 1].at.slice(0, 10) !== list.value[i].at.slice(0, 10)
</script>

<template>
  <Modal title="이 여행 대화 기록" @close="$emit('close')">
    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="list && !list.length" class="empty">아직 대화가 없어요</p>
    <div class="log">
      <template v-for="(m, i) in list" :key="i">
        <p v-if="newDay(i)" class="day">{{ dateLabel(m.at.slice(0, 10)) }}</p>
        <div class="bubble" :class="m.role">{{ m.text }}<small>{{ m.at.slice(11, 16) }}</small></div>
      </template>
    </div>
  </Modal>
</template>

<style scoped>
.log { display: flex; flex-direction: column; gap: 8px; margin-top: 12px; }
.day { align-self: center; padding: 3px 10px; border-radius: 999px; background: var(--bg); color: var(--sub); font-size: 12px; }
.bubble { max-width: 82%; padding: 10px 12px; border-radius: 16px; font-size: 14px; line-height: 1.5; white-space: pre-line; }
.bubble small { display: block; margin-top: 2px; font-size: 11px; opacity: .7; }
.bubble.ai { align-self: flex-start; background: var(--bg); border-top-left-radius: 4px; }
.bubble.me { align-self: flex-end; background: var(--blue); color: #fff; border-top-right-radius: 4px; }
</style>
