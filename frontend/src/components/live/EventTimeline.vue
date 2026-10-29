<script setup>
// 시간 · 점 · 내용 · 배지 한 줄씩 보여 주는 작은 타임라인 (지연 감지, 예약에 늦어요 화면)
// items: [{ time, timeCls, color, icon, text, badgeCls, badgeIcon, badge }]
defineProps({
  items: { type: Array, required: true },
  label: { type: String, default: '' },
  compact: { type: Boolean, default: false }, // 조금 좁게 (예약에 늦어요 화면)
})
</script>

<template>
  <ol class="ev-list" :class="{ 'ev-list--compact': compact }" :aria-label="label">
    <li v-for="item in items" :key="item.time + item.text" class="ev">
      <span class="t-strong tn" :class="item.timeCls" style="text-align: right">{{ item.time }}</span>
      <span class="dot" :style="{ background: item.color }"></span>
      <span class="t-strong ev__text"><i v-if="item.icon" class="ic ic--s" :class="item.icon" aria-hidden="true"></i>{{ item.text }}</span>
      <span class="badge tn" :class="item.badgeCls"><i v-if="item.badgeIcon" class="ic ic--s" :class="item.badgeIcon" aria-hidden="true"></i>{{ item.badge }}</span>
    </li>
  </ol>
</template>

<style>
.ev-list { list-style: none; margin: 0; padding: 12px; border-radius: var(--radius-lg); background: var(--surface-sunken); display: flex; flex-direction: column; }
.ev-list .ev { display: grid; grid-template-columns: 48px 16px 1fr auto; column-gap: 10px; align-items: center; min-height: 52px; }
.ev-list .ev .dot { width: 12px; height: 12px; border-radius: 50%; justify-self: center; }
.ev-list .ev__text { display: flex; align-items: center; gap: 4px; }
.ev-list--compact { padding: 8px 12px; }
.ev-list--compact .ev { grid-template-columns: 44px 16px 1fr auto; min-height: 48px; }
</style>
