<script setup>
// 권한 묻는 창의 공통 틀: 뒤에는 진짜 홈 화면을 흐리게 깔고, 가운데 창(.modal) 안은 slot으로 채워요
// 권한 허용(S03)과 위치 권한 거부(S03-b)가 같이 써요
import MainView from '@/views/trip/MainView.vue'

defineProps({
  tight: { type: Boolean, default: false }, // 항목이 3개라 간격을 좁게
})
</script>

<template>
  <div class="app">
    <div class="perm-bg" aria-hidden="true" inert><MainView /></div>
    <div class="dim" aria-hidden="true"></div>
    <section class="modal perm-modal" :class="{ 'perm-modal--tight': tight }" role="dialog" aria-modal="true">
      <slot />
    </section>
  </div>
</template>

<style>
.perm-bg { position: absolute; inset: 0; pointer-events: none; }
.perm-modal { min-height: 404px; padding: 32px 20px 20px; gap: 26px; justify-content: flex-start; }
.perm-modal > div:last-child { margin-top: auto; }
.perm-modal .textlink { align-self: center; }
.perm-modal--tight { padding: 24px 20px 20px; gap: 18px; }
.perm-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 22px; }
.perm-modal--tight .perm-list { gap: 14px; }
.perm-list li { display: flex; gap: 12px; align-items: flex-start; }
.perm-list .ico-tile { width: 44px; height: 44px; }
.perm-list__body { flex: 1; display: flex; flex-direction: column; gap: 2px; }
.perm-list__title { display: flex; align-items: center; gap: 6px; }
</style>
