<script setup>
// S20-d 잠금화면 · 지연 알림 (①): 늦을 것 같으면 잠금화면에서 바로 "그냥 갈게요 / 대안 보기"를 골라요
import LockNoti from '@/components/member/LockNoti.vue'
import LockScreen from '@/components/member/LockScreen.vue'

const trip = { progress: 62, next: '다음 · 성내동 소품샵', badge: 'warning', badgeIcon: 'i-warning', badgeText: '14분 늦음' }
</script>

<template>
  <div class="scr scr-LockDelay">
    <LockScreen time="16:21" :trip="trip">
      <LockNoti to="/live/delay" icon="i-warning" title="공연 입장에 14분 늦을 것 같아요" desc="잔나비 콘서트 18:30 입장 · 지금 출발해도 18:44 도착" is-new>
        <RouterLink to="/live/late">그냥 갈게요</RouterLink>
        <RouterLink to="/live/alt" class="is-main">대안 보기</RouterLink>
      </LockNoti>
    </LockScreen>
  </div>
</template>
