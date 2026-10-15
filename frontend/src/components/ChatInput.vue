<script setup>
import { ref } from 'vue'
defineProps({ placeholder: { type: String, default: '추가로 원하는 조건이 있나요?' }, busy: Boolean })
const emit = defineEmits(['send'])
const text = ref('')
function send() {
  const v = text.value.trim()
  if (!v) return
  emit('send', v)
  text.value = ''
}
</script>

<template>
  <form class="chat-input" @submit.prevent="send">
    <input v-model="text" :placeholder="placeholder" :disabled="busy" maxlength="300" />
    <button class="send" :disabled="busy || !text.trim()" aria-label="보내기">{{ busy ? '…' : '➤' }}</button>
  </form>
</template>

<style scoped>
.chat-input { display: flex; gap: 8px; padding: 10px 14px; background: #fff; }
input { flex: 1; height: 46px; padding: 0 16px; border: 1px solid var(--line); border-radius: 23px; background: #fff; }
input:focus { outline: 2px solid var(--blue); border-color: transparent; }
.send { width: 46px; height: 46px; border-radius: 50%; background: var(--blue); color: #fff; font-size: 18px; }
.send:disabled { background: #b8c4f5; }
</style>
