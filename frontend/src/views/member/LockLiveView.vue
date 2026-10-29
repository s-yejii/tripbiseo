<script setup>
// S20-c 잠금화면 · 여행 중 배너 (①): 폰을 잠가도 진행 상황과 다음 출발 알림이 보여요
import LockNoti from '@/components/member/LockNoti.vue'
import LockScreen from '@/components/member/LockScreen.vue'

const trip = { progress: 62, next: '다음 · 성내동 소품샵 · 10분 뒤 출발', badge: 'success', badgeIcon: 'i-lock', badgeText: '여유 15분' }
</script>

<template>
  <div class="scr scr-LockLive">
    <LockScreen time="15:42" hint="눌러서 여행 중 지도 열기" :trip="trip">
      <LockNoti to="/live" icon="i-walk" title="10분 뒤 출발할 시간이에요" desc="15:52 성내동 카페 → 성내동 소품샵 · 도보 15분" is-new />
    </LockScreen>
  </div>
</template>
