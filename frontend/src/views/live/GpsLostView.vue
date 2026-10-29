<script setup>
// S13-d GPS 끊김 (④): 위치가 안 잡히면 마지막 위치로 계산하고, 지금 어디인지 물어봐요
import BottomSheet from '@/components/common/BottomSheet.vue'
</script>

<template>
  <div class="scr scr-GpsLost">
    <div class="app">
      <div class="map" aria-hidden="true" style="filter: grayscale(0.7)">
        <img class="base live" src="/img/mapB.svg" alt="" style="top: -126px" />
        <span class="gps-ring"></span>
      </div>
      <span class="pin pin--done" style="left: 215px; top: 285px" aria-label="마지막 위치"><i class="ic ic--s i-pin" aria-hidden="true" style="width: 13px; height: 13px"></i></span>
      <span class="maplabel tn" style="left: 140px; top: 236px">마지막 위치 · 15:40</span>

      <header class="topbar topbar--float">
        <RouterLink to="/live" class="iconbtn iconbtn--float" aria-label="뒤로가기"><i class="ic ic--l i-back" aria-hidden="true"></i></RouterLink>
        <div class="banner banner--warning banner--float" role="status" style="flex: 1; padding: 8px 12px">
          <i class="ic i-nolocation c-warning" aria-hidden="true"></i>
          <span class="banner__body"><span class="t-strong" style="font-size: 14px">위치를 찾고 있어요</span><span class="t-cap">건물 안·지하에선 잠시 끊겨요</span></span>
        </div>
      </header>

      <BottomSheet style="gap: 16px">
        <h1 class="h-title">지금 성내동 카페에 계신가요?</h1>
        <div class="gps-fixed">
          <i class="ic i-lock c-navy" aria-hidden="true"></i>
          <span style="flex: 1" class="t-strong tn">공연 입장 18:30</span>
          <span class="badge badge--neutral tn">여유 약 15분</span>
        </div>
        <p class="t-cap">위치가 다시 잡힐 때까지 마지막 위치로 계산해요</p>
        <div class="cta__row">
          <RouterLink to="/live" class="btn btn--secondary btn--lg">이미 출발했어요</RouterLink>
          <RouterLink to="/live" class="btn btn--primary btn--lg">네, 여기 있어요</RouterLink>
        </div>
      </BottomSheet>
    </div>
  </div>
</template>

<style>
.scr-GpsLost .gps-ring { position: absolute; left: 155px; top: 225px; width: 120px; height: 120px; border-radius: 50%; background: rgba(100, 109, 129, 0.18); border: 2px dashed var(--ink-subtle); }
.scr-GpsLost .gps-fixed { display: flex; align-items: center; gap: 12px; padding: 12px; border-radius: var(--radius-md); background: var(--surface-sunken); }
</style>
