<script setup>
// S04-d 테마 코스 (③): 주소 /theme?id=ikseon (data/themes.js의 id)
// 11/13: "준비 중" 대신, 누르면 그 테마의 지역·종류를 채워서 AI 일정 만들기(날짜 고르기 단계)로 바로 보내요
//   예전 테마 id(전참시 등)나 id 없이 오면 테마 목록만 보여 줘요
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'
import ThemeCircles from '@/components/plan/ThemeCircles.vue'
import { findTheme } from '@/data/themes'
import { useTripStore } from '@/stores/trip'

const WHEN_STEP = 3 // 여행 정보 11단계 중 "언제 가요?" (PlanAiView STEPS 순서)

const route = useRoute()
const router = useRouter()
const store = useTripStore()
const theme = computed(() => findTheme(route.query.id))

// 이 테마로 시작: 여행 정보를 비우고 지역·종류·분위기를 채운 뒤 날짜 고르기로
function startWithTheme() {
  const picked = theme.value
  store.resetPlan()
  store.planArea = { ...picked.area }
  store.planKinds = [...picked.kinds]
  store.planTags = [...picked.tags]
  store.planStep = WHEN_STEP
  router.push('/plan/info')
}
</script>

<template>
  <div class="scr scr-Theme">
    <div class="app">
      <TopBar title="테마로 떠나기" back="/" />
      <div class="scroll" style="padding-top: 8px; gap: 20px; bottom: 24px">
        <section v-if="theme" class="theme-start" aria-labelledby="theme-start-title">
          <img class="theme-start__photo" :src="theme.img" alt="" />
          <h1 id="theme-start-title" class="h-display" style="margin: 0">{{ theme.title }}</h1>
          <p class="t-body muted" style="margin: 0">{{ theme.tagline }}</p>
          <p class="theme-start__area t-cap"><i class="ic ic--s i-pin" aria-hidden="true"></i>{{ theme.area.fullName }}</p>
          <button type="button" class="btn btn--primary btn--lg" style="width: 100%" @click="startWithTheme">이 코스로 AI 일정 만들기</button>
          <p class="t-cap muted" style="margin: 0">날짜만 고르면 영업시간·이동 거리에 맞춰 짜 드려요</p>
        </section>
        <ThemeCircles :title="theme ? '다른 테마 둘러보기' : '테마 고르기'" :except-id="theme ? theme.id : ''" :key="theme ? theme.id : 'all'" />
      </div>
    </div>
  </div>
</template>

<style>
.scr-Theme .theme-start { display: flex; flex-direction: column; align-items: center; gap: 12px; text-align: center; }
.scr-Theme .theme-start__photo { width: 112px; height: 112px; border-radius: 50%; object-fit: cover; border: 1px solid var(--line); margin-bottom: 4px; }
.scr-Theme .theme-start__area { display: inline-flex; align-items: center; gap: 4px; margin: 0; }
</style>
