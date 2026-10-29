<script setup>
// S04-c 내 여행 · 빈 상태 (②): 여행이 하나도 없을 때 만들기 버튼과 테마 코스를 보여 줘요
import EmptyTripCard from '@/components/common/EmptyTripCard.vue'
import PhotoCard from '@/components/common/PhotoCard.vue'
import TabBar from '@/components/common/TabBar.vue'
import { themes } from '@/data/themes'
</script>

<template>
  <div class="scr scr-TripsEmpty">
    <div class="app">
      <header class="topbar" style="padding: 0 8px 0 16px">
        <h1 class="topbar__title" style="font-size: 24px; line-height: 32px">내 여행</h1>
      </header>

      <div class="scroll" style="bottom: 84px; gap: 24px; padding-top: 4px">
        <EmptyTripCard to="/new" />

        <section aria-labelledby="te-theme" style="display: flex; flex-direction: column; gap: 12px">
          <h2 id="te-theme" class="h-title">이런 코스로 시작해 보세요</h2>
          <div class="theme-grid">
            <PhotoCard v-for="theme in themes" :key="theme.id" :to="'/theme?id=' + theme.id" :img="theme.img" :title="theme.title" :sub="theme.count" :badge="theme.who" :height="168" />
          </div>
        </section>
      </div>

      <TabBar active="trips" />
    </div>
  </div>
</template>
