<script setup>
// 아래 탭 4개. active: home | map | trips | my
// guest: 로그인 전이면 홈 말고는 로그인 화면으로 보내요
import { computed } from 'vue'

const props = defineProps({
  active: { type: String, default: 'home' },
  guest: { type: Boolean, default: false },
})

const tabs = computed(() => [
  { key: 'home', to: props.guest ? '/guest' : '/', icon: 'i-home', label: '홈' },
  { key: 'map', to: props.guest ? '/login' : '/live', icon: 'i-pin', label: '지도' },
  { key: 'trips', to: props.guest ? '/login' : '/trips', icon: 'i-trip', label: '내 여행' },
  { key: 'my', to: props.guest ? '/login' : '/my', icon: 'i-user', label: '마이' },
])
</script>

<template>
  <nav class="tabbar" aria-label="주요 메뉴">
    <RouterLink
      v-for="tab in tabs"
      :key="tab.key"
      :to="tab.to"
      :class="{ 'is-on': tab.key === active }"
      :aria-current="tab.key === active ? 'page' : null"
    >
      <i class="ic ic--l" :class="tab.icon" aria-hidden="true"></i>{{ tab.label }}
    </RouterLink>
  </nav>
</template>
