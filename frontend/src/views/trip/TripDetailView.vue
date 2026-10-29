<script setup>
// S11 여행 상세 · 여행 전 (②): 다가오는 여행의 일정과 챙길 것
import TripPage from '@/components/trip/TripPage.vue'
import PackList from '@/components/trip/PackList.vue'
import { cafeTrip } from '@/data/trips'
</script>

<template>
  <div class="scr scr-TripDetail">
    <TripPage :trip="cafeTrip" :badge="{ text: 'D-8', cls: 'badge--navy' }" back="/trips">
      <template #below>
        <PackList :items="cafeTrip.pack" />
      </template>
      <template #cta>
        <RouterLink to="/plan/edit" class="btn btn--primary btn--lg btn--block"><i class="ic i-edit" aria-hidden="true"></i>일정 편집하기</RouterLink>
      </template>
    </TripPage>
  </div>
</template>
