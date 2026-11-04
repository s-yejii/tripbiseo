<script setup>
// 파란 인증 뱃지: 물결 원 + 흰 체크. 외부 이미지 없이 원 여러 개로 그려요
const LOBES = [[20.2, 12], [17.8, 17.8], [12, 20.2], [6.2, 17.8], [3.8, 12], [6.2, 6.2], [12, 3.8], [17.8, 6.2]]
</script>

<template>
  <svg class="verified-badge" viewBox="0 0 24 24" width="14" height="14" role="img" aria-label="인증">
    <circle cx="12" cy="12" r="8.5" fill="#0095F6" />
    <circle v-for="(lobe, i) in LOBES" :key="i" :cx="lobe[0]" :cy="lobe[1]" r="3.4" fill="#0095F6" />
    <path d="M7.6 12.2l3 3 5.8-6.2" fill="none" stroke="#fff" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" />
  </svg>
</template>

<style>
.verified-badge { flex: none; }
</style>
