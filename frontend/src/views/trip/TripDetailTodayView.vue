<script setup>
// S11-b 여행 상세 · 여행 날 (②): 오늘 여행의 일정, 미리 확인할 것, 챙길 것
import TripPage from '@/components/trip/TripPage.vue'
import PackList from '@/components/trip/PackList.vue'
import { concertTrip } from '@/data/trips'
</script>

<template>
  <div class="scr scr-TripDetailToday">
    <TripPage :trip="concertTrip" :badge="{ text: '오늘', cls: 'badge--coral' }" back="/" rain-swap>
      <template #below>
        <PackList :items="concertTrip.pack" />
      </template>
      <template #cta>
        <RouterLink to="/plan/edit" class="btn btn--primary btn--lg btn--block"><i class="ic i-edit" aria-hidden="true"></i>일정 편집하기</RouterLink>
      </template>
    </TripPage>
  </div>
</template>
