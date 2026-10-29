<script setup>
// S20-b 잠금화면 알림 (①): 앱 알림을 3분 동안 안 열면 문자로도 한 번 더 보내요
import LockNoti from '@/components/member/LockNoti.vue'
import LockScreen from '@/components/member/LockScreen.vue'

const trip = { progress: 62, next: '다음 · 성내동 소품샵', badge: 'warning', badgeIcon: 'i-warning', badgeText: '14분 늦음' }
</script>

<template>
  <div class="scr scr-Push">
    <LockScreen time="16:21" hint="" :trip="trip">
      <LockNoti to="/live/delay" icon="i-warning" title="공연 입장에 14분 늦을 것 같아요" desc="추천: 성내동 소품샵 30분 줄이면 18:14 도착 · 여유 16분" is-new>
        <RouterLink to="/live/late">그냥 갈게요</RouterLink>
        <RouterLink to="/live/alt" class="is-main">대안 보기</RouterLink>
      </LockNoti>
      <LockNoti to="/live/delay" app="문자" when="16:24" title="[여행어때] 18:30 잔나비 콘서트 입장 14분 지연 예상" desc="앱 알림을 3분 동안 안 열어서 문자로 보내요 · 대안 보기 yeohaeng.kr/d/24" />
      <LockNoti to="/live" when="21분 전" icon="i-clock" title="출발이 8분 늦었어요 · 여유 7분" desc="16:00 · 지금 출발하면 괜찮아요" old />
    </LockScreen>
  </div>
</template>
