<script setup>
// 홈 화면 맨 위: 왼쪽 로고(여행어때) + 오른쪽 칸(slot: 알림 버튼이나 로그인 버튼)
</script>

<template>
  <header class="topbar" style="padding: 0 8px 0 16px">
    <span class="home-logo"><i class="ic ic--l i-pin" aria-hidden="true"></i>여행어때</span>
    <slot />
  </header>
</template>

<style>
.home-logo { flex: 1; display: flex; align-items: center; gap: 6px; font-size: 18px; font-weight: 700; color: var(--navy); }
</style>
