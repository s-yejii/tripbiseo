<script setup>
// S04-a0 홈 · 로그인 전 (①): 둘러보기만 하고, 무엇을 누르든 로그인으로 보내요
import EmptyTripCard from '@/components/common/EmptyTripCard.vue'
import HomeHeader from '@/components/common/HomeHeader.vue'
import PhotoCard from '@/components/common/PhotoCard.vue'
import TabBar from '@/components/common/TabBar.vue'

// 가짜 데이터: 로그인 전 보여 줄 테마와 인기 장소
const themes = [
  { title: '천호 골목', sub: '5곳 · 4시간', img: '/img/alley.jpg', badge: '비 와도 OK' },
  { title: '전주 한옥', sub: '1박 2일', img: '/img/jeonju.jpg' },
  { title: '부산 야경', sub: '1박 2일', img: '/img/busan.jpg' },
]
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

        <section aria-labelledby="g-theme" style="display: flex; flex-direction: column; gap: 12px">
          <h2 id="g-theme" class="h-title">테마로 떠나기</h2>
          <div class="theme-grid">
            <PhotoCard v-for="theme in themes" :key="theme.title" to="/login" :img="theme.img" :title="theme.title" :sub="theme.sub" :badge="theme.badge" />
          </div>
        </section>

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
