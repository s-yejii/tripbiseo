<script setup>
// 중요한 알림 카드 (11/08 주형): 예약(회식·KTX)에 늦을 때처럼 대안이 필요한 알림은 위쪽 배너 대신 화면 가운데 카드로 (뒤는 어둡게)
// [대안 보기] → 지연 화면(대안), [나중에] → 카드를 닫고 위쪽 알약으로만 남김
defineProps({
  title: { type: String, required: true },
  text: { type: String, default: '' },
  to: { type: String, default: '/live/delay' },
})
const emit = defineEmits(['later'])
</script>

<template>
  <div class="crit" role="alertdialog" aria-modal="true" :aria-label="title">
    <div class="crit__card">
      <span class="crit__icon"><i class="ic ic--l i-warning" aria-hidden="true"></i></span>
      <b class="crit__title">{{ title }}</b>
      <span v-if="text" class="crit__text">{{ text }}</span>
      <div class="crit__buttons">
        <button type="button" class="btn btn--secondary btn--lg" @click="emit('later')">나중에</button>
        <RouterLink :to="to" class="btn btn--primary btn--lg">대안 보기</RouterLink>
      </div>
    </div>
  </div>
</template>

<style>
.crit { position: absolute; inset: 0; z-index: 58; display: grid; place-items: center; padding: 24px; background: rgba(15, 20, 35, 0.45); }
.crit__card { width: 100%; max-width: 320px; display: flex; flex-direction: column; align-items: center; gap: 10px; padding: 22px 18px 18px; border-radius: 20px; background: var(--surface-raised, #fff); box-shadow: 0 16px 40px -8px rgba(24, 33, 58, 0.45); text-align: center; }
.crit__icon { display: grid; place-items: center; width: 52px; height: 52px; border-radius: 16px; background: var(--warning-soft, #fff3e0); color: var(--warning, #c46a00); }
.crit__title { font-size: 18px; line-height: 26px; color: var(--ink); }
.crit__text { font-size: 14px; line-height: 20px; color: var(--ink-muted); }
.crit__buttons { display: grid; grid-template-columns: 1fr 1.4fr; gap: 8px; width: 100%; margin-top: 6px; }
</style>
