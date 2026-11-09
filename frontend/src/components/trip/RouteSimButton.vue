<script setup>
// 11/08 주형: 시연 계정의 "시연 재생"(자동 진행)은 뺐어요. 모든 계정이 같은 이동 미리보기를 써요
// 이동 미리보기 버튼 (일정 결과·상세 공통): ▶ 누르면 지도에서 점이 일정대로 움직이고, 지금 시각과 가는 곳을 보여 줘요
// sim: utils/useRouteSim의 돌려준 값

defineProps({
  sim: { type: Object, required: true },
})
</script>

<template>
  <button v-if="sim.canPlay.value" type="button" class="route-sim" :class="{ 'is-on': sim.isOn.value }" :aria-label="sim.isPlaying.value ? '이동 미리보기 멈추기' : '이동 미리보기'" @click="sim.toggle">
    <i class="ic ic--s" :class="sim.isPlaying.value ? 'i-pause' : 'i-play'" aria-hidden="true"></i>
    <template v-if="sim.isOn.value"><b class="tn">{{ sim.clock.value }}</b><span class="route-sim__name">{{ sim.label.value }}</span></template>
  </button>
</template>

<style>
.route-sim{flex:none;display:inline-flex;align-items:center;justify-content:center;gap:6px;min-width:44px;height:44px;max-width:270px;margin-left:auto;padding:0 12px;border:0;border-radius:22px;background:#fff;color:var(--navy);box-shadow:0 2px 8px rgba(24,33,58,.16);cursor:pointer}
.route-sim.is-on{padding:0 14px 0 12px}
.route-sim b{font-size:15px;font-weight:700}
.route-sim__name{min-width:0;overflow:hidden;white-space:nowrap;text-overflow:ellipsis;font-size:13px;color:var(--ink-muted)}
</style>
