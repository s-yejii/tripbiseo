<script setup>
// S04-c 내 여행 · 빈 상태 (②): 여행이 하나도 없을 때 만들기 버튼
// (테마 코스는 GET /api/themes가 아직 "확인 필요"라서 API가 생기면 다시 붙여요)
import EmptyTripCard from '@/components/common/EmptyTripCard.vue'
import TabBar from '@/components/common/TabBar.vue'
</script>

<template>
  <div class="scr scr-TripsEmpty">
    <div class="app">
      <header class="topbar" style="padding: 0 8px 0 16px">
        <h1 class="topbar__title" style="font-size: 24px; line-height: 32px">내 여행</h1>
      </header>

      <div class="scroll" style="bottom: 84px; gap: 24px; padding-top: 4px">
        <EmptyTripCard to="/new" />
      </div>

      <TabBar active="trips" />
    </div>
  </div>
</template>
