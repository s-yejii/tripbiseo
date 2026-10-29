<script setup>
// S04-d 테마 코스 (③): 팀이 만든 테마 코스를 보고, 이 코스로 여행을 만들어요
// 주소: /theme?id=alley (alley · forest · nopo)
import { useRoute } from 'vue-router'
import TripPage from '@/components/trip/TripPage.vue'
import { themes } from '@/data/themes'
import { showToast } from '@/utils/toast'

const route = useRoute()
const theme = themes[route.query.id] || themes.alley
// 일정 틀(TripPage)이 쓰는 모양으로 맞춰요
const trip = { ...theme, checks: [] }
</script>

<template>
  <div class="scr scr-Theme">
    <TripPage :trip="trip" :sub="`테마 코스 · ${theme.stops.length}곳 · 이동 ${theme.move}분 · ${theme.who}`" back="/">
      <template #right>
        <button type="button" class="iconbtn iconbtn--float" aria-label="공유" @click="showToast('코스 링크를 복사했어요')"><i class="ic i-share" aria-hidden="true"></i></button>
      </template>
      <template #cta>
        <RouterLink to="/plan/info" class="btn btn--primary btn--lg btn--block">이 코스로 여행 만들기</RouterLink>
        <p class="cta__hint">날짜와 인원만 고르면 시간에 맞춰 다시 짜요</p>
      </template>
    </TripPage>
  </div>
</template>
