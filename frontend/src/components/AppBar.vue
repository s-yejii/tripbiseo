<script setup>
// 화면 위 제목줄. back을 주면 뒤로 가기 버튼
defineProps({ title: String, back: { type: Boolean, default: true } })
</script>

<template>
  <header class="topbar">
    <button v-if="back" class="icon-btn" aria-label="뒤로" @click="$router.back()">‹</button>
    <span v-else style="width: 36px" />
    <h1>{{ title }}</h1>
    <div style="min-width: 36px; display: flex; justify-content: flex-end"><slot /></div>
  </header>
</template>
