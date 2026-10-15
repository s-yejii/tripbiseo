<script setup>
const tabs = [
  { to: '/', label: '홈', icon: '⌂' },
  { to: '/trips', label: '내 여행', icon: '🗺' },
  { to: '/retro', label: '회고', icon: '◷' },
  { to: '/my', label: '마이', icon: '☺' },
]
</script>

<template>
  <nav class="tabbar">
    <RouterLink v-for="t in tabs" :key="t.to" :to="t.to" class="tab" :class="{ on: $route.meta.tab === t.to }">
      <span class="ico">{{ t.icon }}</span>{{ t.label }}
    </RouterLink>
  </nav>
</template>

<style scoped>
.tabbar { position: absolute; left: 0; right: 0; bottom: 0; height: var(--tab-h); display: flex; background: #fff; border-top: 1px solid var(--line); z-index: 30; }
.tab { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px; font-size: 11px; color: var(--sub); text-decoration: none; }
.tab .ico { font-size: 20px; line-height: 1; filter: grayscale(1); opacity: .6; }
.tab.on { color: var(--blue); font-weight: 700; }
.tab.on .ico { filter: none; opacity: 1; }
</style>
