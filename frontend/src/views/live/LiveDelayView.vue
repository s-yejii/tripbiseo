<script setup>
// S15 지연 감지 (④): 왜 늦는지 보여 주고, 그냥 갈지 대안을 볼지 고르게 해요
import BottomSheet from '@/components/common/BottomSheet.vue'
import EventTimeline from '@/components/live/EventTimeline.vue'

// 가짜 데이터: 지연 판단 결과 (백엔드가 붙으면 서버 계산값)
const lateMinutes = 14
const reasons = [
  { time: '14:52', color: 'var(--success)', text: '성내동 카페 도착', badgeCls: 'badge--success', badgeIcon: 'i-check', badge: '제시간' },
  { time: '16:21', color: 'var(--warning)', text: '아직 성내동 카페', badgeCls: 'badge--warning', badgeIcon: 'i-clock', badge: '+29분' },
  { time: '18:44', color: 'var(--danger)', icon: 'i-lock', text: '예약 도착', badgeCls: 'badge--danger', badgeIcon: 'i-danger', badge: '+14분' },
]
</script>

<template>
  <div class="scr scr-LiveDelay">
    <div class="app">
      <div class="map" aria-hidden="true"><img class="base live" src="/img/mapB.svg" alt="" style="top: -126px" /></div>
      <span class="me" style="left: 215px; top: 285px"></span>
      <div class="dim" aria-hidden="true"></div>

      <BottomSheet role="alertdialog" aria-modal="true" style="gap: 16px">
        <div style="display: flex; align-items: center; gap: 12px">
          <span class="ico-tile ico-tile--warn" style="width: 48px; height: 48px"><i class="ic ic--l i-warning" aria-hidden="true"></i></span>
          <h1 class="h-title" style="font-size: 20px; line-height: 28px">공연 입장에<br /><span class="c-warning">{{ lateMinutes }}분</span> 늦을 것 같아요</h1>
        </div>

        <EventTimeline :items="reasons" label="왜 늦는지" />

        <div style="display: flex; flex-direction: column; gap: 4px">
          <div class="cta__row">
            <RouterLink to="/live/late" class="btn btn--secondary btn--lg">그냥 갈게요</RouterLink>
            <RouterLink to="/live/alt" class="btn btn--primary btn--lg">대안 3개 보기</RouterLink>
          </div>
          <p class="t-cap" style="text-align: center; margin: 4px 0 0">그냥 가도 10분 더 늦어지면 다시 알려 드려요</p>
        </div>
      </BottomSheet>
    </div>
  </div>
</template>
