<script setup>
// S04-a0 홈 · 로그인 전 (①): 둘러보기만 하고, 무엇을 누르든 로그인으로 보내요
import EmptyTripCard from '@/components/common/EmptyTripCard.vue'
import HomeHeader from '@/components/common/HomeHeader.vue'
import ThemeCircles from '@/components/plan/ThemeCircles.vue'
import TabBar from '@/components/common/TabBar.vue'

// 가짜 데이터: 로그인 전 보여 줄 인기 장소 (테마는 data/themes.js)
const hotPlaces = [
  { name: '성내동 카페', img: '/img/daelim.jpg', meta: '4.6 · 천호 · 카페' },
  { name: '천호 로데오거리', img: '/img/understand.jpg', meta: '4.4 · 천호 · 쇼핑' },
]
</script>

<template>
  <div class="scr scr-MainGuest">
    <div class="app">
      <HomeHeader>
        <RouterLink to="/login" class="btn btn--secondary btn--sm">로그인</RouterLink>
      </HomeHeader>

      <div class="scroll" style="bottom: 84px; gap: 24px; padding-top: 4px">
        <EmptyTripCard to="/login" />

        <ThemeCircles to="/login" />

        <section aria-labelledby="g-hot" style="display: flex; flex-direction: column; gap: 12px">
          <h2 id="g-hot" class="h-title">요즘 많이 가는 곳</h2>
          <div class="group">
            <RouterLink v-for="place in hotPlaces" :key="place.name" to="/place" class="row">
              <img class="thumb" :src="place.img" alt="" />
              <span class="row__body"><span class="row__title">{{ place.name }}</span><span class="row__meta"><i class="ic ic--s i-star c-warning" aria-hidden="true"></i> {{ place.meta }}</span></span>
              <i class="ic i-next subtle" aria-hidden="true"></i>
            </RouterLink>
          </div>
        </section>
      </div>

      <TabBar active="home" guest />
    </div>
  </div>
</template>
