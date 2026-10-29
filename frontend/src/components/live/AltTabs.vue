<script setup>
// 대안 3가지 탭: 시간 줄이기 / 한 곳 빼기 / 장소 바꾸기 (세 화면이 같이 써요)
// active: trim | drop | swap
defineProps({
  active: { type: String, required: true },
})

// 가짜 데이터: 대안마다 예약 여유(분)
const tabs = [
  { key: 'trim', to: '/live/alt', label: '시간 줄이기', slack: 16 },
  { key: 'drop', to: '/live/alt/drop', label: '한 곳 빼기', slack: 65 },
  { key: 'swap', to: '/live/alt/swap', label: '장소 바꾸기', slack: 28 },
]
</script>

<template>
  <nav class="alt-tabs" aria-label="대안 종류">
    <RouterLink
      v-for="tab in tabs"
      :key="tab.key"
      :to="tab.to"
      replace
      :class="{ 'is-on': tab.key === active }"
      :aria-current="tab.key === active ? 'page' : null"
    >
      <b>{{ tab.label }}</b><small>여유 {{ tab.slack }}분</small>
    </RouterLink>
  </nav>
</template>
