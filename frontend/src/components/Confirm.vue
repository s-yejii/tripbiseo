<script setup>
// 확인 창 (브라우저 confirm 대신 앱 안에서)
import Modal from './Modal.vue'
defineProps({ title: String, text: String, ok: { type: String, default: '확인' }, danger: Boolean })
defineEmits(['ok', 'close'])
</script>

<template>
  <Modal :title="title" @close="$emit('close')">
    <p class="text">{{ text }}</p>
    <div class="row">
      <button class="btn ghost grow" @click="$emit('close')">취소</button>
      <button class="btn primary grow" :class="{ danger }" @click="$emit('ok')">{{ ok }}</button>
    </div>
  </Modal>
</template>

<style scoped>
.text { margin: 12px 0 20px; color: var(--sub); line-height: 1.5; }
.danger { background: var(--red); }
</style>
