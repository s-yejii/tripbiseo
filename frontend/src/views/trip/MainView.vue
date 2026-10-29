<script setup>
// S04-a 홈 · 여행 날 (②): 오늘 여행 카드(일정 보기가 메인), 테마 코스, 많이 가는 곳
import HomeHeader from '@/components/common/HomeHeader.vue'
import PhotoCard from '@/components/common/PhotoCard.vue'
import TabBar from '@/components/common/TabBar.vue'
import { concertTrip } from '@/data/trips'
import { themes } from '@/data/themes'
</script>

<template>
  <div class="scr scr-Main">
    <div class="app">
      <HomeHeader>
        <RouterLink to="/notifications" class="iconbtn" aria-label="알림 2개"><i class="ic ic--l i-bell" aria-hidden="true"></i><span class="dotbadge"></span></RouterLink>
      </HomeHeader>

      <div class="scroll" style="bottom: 84px; gap: 24px; padding-top: 4px">
        <!-- 오늘 여행 -->
        <section class="card" aria-label="오늘 여행" style="overflow: hidden">
          <div class="today-card">
            <div style="display: flex; align-items: center; gap: 8px">
              <span class="badge badge--coral">오늘</span>
              <span class="t-cap tn">10/24(토) · 연인 2명 · 지하철·도보</span>
            </div>
            <h1 class="h-display">{{ concertTrip.title }}</h1>
            <div style="display: flex; align-items: flex-end; justify-content: space-between; gap: 12px">
              <div>
                <p class="t-cap">첫 장소 출발까지</p>
                <p class="t-big" style="margin: 0; color: var(--navy)">2시간 20분</p>
              </div>
              <span class="badge badge--warning"><i class="ic ic--s i-rain" aria-hidden="true"></i>14–16시 비 60%</span>
            </div>
            <ol class="today-stops" :aria-label="`오늘 일정 ${concertTrip.stops.length}곳`">
              <li v-for="(stop, i) in concertTrip.stops" :key="stop.id">
                <img class="thumb" :class="{ 'is-first': i === 0, 'is-fixed': stop.fixed }" :src="stop.img" alt="" />
                <span class="t-label tn" style="display: inline-flex; align-items: center; gap: 2px">
                  <i v-if="stop.fixed" class="ic ic--s i-lock" aria-hidden="true" style="width: 12px; height: 12px"></i>{{ stop.time }}
                </span>
              </li>
            </ol>
          </div>
          <div style="padding: 0 16px 16px">
            <RouterLink to="/trips/today" class="btn btn--primary btn--lg btn--block">일정 보기</RouterLink>
          </div>
        </section>

        <!-- 테마로 떠나기 -->
        <section aria-labelledby="h-theme" style="display: flex; flex-direction: column; gap: 12px">
          <div class="sec-h"><h2 id="h-theme" class="h-title">테마로 떠나기</h2></div>
          <div class="theme-grid">
            <PhotoCard v-for="theme in themes" :key="theme.id" :to="'/theme?id=' + theme.id" :img="theme.img" :title="theme.short" :sub="theme.count" :badge="theme.badge" />
          </div>
        </section>

        <!-- 많이 가는 곳 -->
        <section aria-labelledby="h-hot" style="display: flex; flex-direction: column; gap: 12px">
          <h2 id="h-hot" class="h-title">천호에서 많이 가요</h2>
          <div class="group">
            <RouterLink to="/place" class="row">
              <img class="thumb" src="/img/daelim.jpg" alt="" />
              <span class="row__body"><span class="row__title">성내동 카페</span><span class="row__meta"><i class="ic ic--s i-star c-warning" aria-hidden="true"></i> 4.6 · 카페·갤러리</span></span>
              <i class="ic i-next subtle" aria-hidden="true"></i>
            </RouterLink>
            <RouterLink to="/place" class="row">
              <img class="thumb" src="/img/sopum.jpg" alt="" />
              <span class="row__body"><span class="row__title">천호 소품 편집숍</span><span class="row__meta"><span class="badge badge--neutral" style="height: 20px">광고</span> 일정 근처 · 도보 4분</span></span>
              <i class="ic i-next subtle" aria-hidden="true"></i>
            </RouterLink>
          </div>
        </section>
      </div>

      <TabBar active="home" />
    </div>
  </div>
</template>

<style>
.scr-Main .today-card { padding: 16px 16px 12px; display: flex; flex-direction: column; gap: 12px; }
.scr-Main .today-stops { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 6px; }
.scr-Main .today-stops li { display: flex; flex-direction: column; align-items: center; gap: 4px; }
.scr-Main .today-stops .thumb { width: 44px; height: 44px; }
.scr-Main .today-stops .thumb.is-first { border: 2px solid var(--navy); }
.scr-Main .today-stops .thumb.is-fixed { border: 2px solid var(--ink); }
</style>
