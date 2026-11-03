<script setup>
// S04-d 테마 코스 (③): 지금은 테마 코스가 준비 중이라 "준비 중" 안내만 보여 줘요
// 주소: /theme?id=jeonchamsi (data/themes.js의 id). 코스를 만들면 GET /api/themes/{id}로 장소 목록을 보여 줄 자리예요
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'
import ThemeCircles from '@/components/plan/ThemeCircles.vue'
import { findTheme } from '@/data/themes'

const route = useRoute()
// 없는 id(예: 예전 주소)면 이름 없는 테마로 보여 줘요
const theme = computed(() => findTheme(route.query.id) || { id: '', title: '테마 코스', tagline: '', img: '/img/alley.jpg' })
</script>

<template>
  <div class="scr scr-Theme">
    <div class="app app--white">
      <TopBar title="테마로 떠나기" back="/" />

      <div class="scroll" style="padding-top: 16px; gap: 28px; bottom: 0; padding-bottom: 32px">
        <section class="theme-soon" aria-labelledby="theme-soon-title">
          <img class="theme-soon__photo" :src="theme.img" alt="" />
          <span class="badge badge--neutral">준비 중</span>
          <h1 id="theme-soon-title" class="h-display" style="margin: 0">{{ theme.title }}</h1>
          <p v-if="theme.tagline" class="t-body muted" style="margin: 0">{{ theme.tagline }}</p>
          <p class="theme-soon__note">이 테마는 지금 코스를 만들고 있어요.<br />곧 서울 실제 장소로 열어 드릴게요.</p>
          <RouterLink to="/new" class="btn btn--primary btn--lg btn--block">그동안 내 여행 직접 만들기</RouterLink>
        </section>

        <ThemeCircles title="다른 테마 둘러보기" :except-id="theme.id" :key="theme.id" />
      </div>
    </div>
  </div>
</template>

<style>
.scr-Theme .theme-soon { display: flex; flex-direction: column; align-items: center; gap: 12px; text-align: center; }
.scr-Theme .theme-soon__photo { width: 96px; height: 96px; border-radius: 50%; object-fit: cover; border: 1px solid var(--line); margin-bottom: 4px; }
.scr-Theme .theme-soon__note { margin: 4px 0 8px; padding: 14px 16px; width: 100%; border-radius: var(--radius-lg); background: var(--surface); color: var(--ink-muted); font-size: 14px; line-height: 22px; }
</style>
