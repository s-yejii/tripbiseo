<script setup>
// S00 친구 초대 링크 (③, 11/06): 친구가 보낸 링크(/invite?from=민지)를 열면 처음 보는 화면 → 시작하기 → 온보딩
// 친구가 다녀온 코스는 시연용 데이터(data/friendNews.js). friend 테이블이 생기면 초대 코드로 바꿔요
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { friendNews } from '@/data/friendNews'

const route = useRoute()
const fromName = computed(() => (route.query.from ? String(route.query.from).slice(0, 10) : '친구'))
const course = computed(() => friendNews[fromName.value] || null)
</script>

<template>
  <div class="scr scr-Invite">
    <div class="app app--white">
      <div class="scroll iv-body">
        <span class="iv-logo"><img src="/img/logo-chakchak.png" alt="착착트립" height="28" /></span>

        <h1 class="h-display iv-title"><span class="c-navy">{{ fromName }}</span>님이<br />착착트립에 초대했어요</h1>
        <p class="t-body iv-sub">계획이 틀어져도 남은 일정을 다시 맞춰 주는 여행 앱이에요</p>

        <section v-if="course" class="card card--pad iv-course" :aria-label="fromName + '님이 다녀온 코스'">
          <span class="t-cap tn">{{ fromName }}님이 다녀온 코스 · {{ course.date }} · {{ course.area.name }}</span>
          <ol class="iv-places">
            <li v-for="(place, i) in course.places" :key="place"><span class="iv-no tn">{{ i + 1 }}</span>{{ place }}</li>
          </ol>
          <span class="t-cap">가입하면 이 코스를 내 장소에 담을 수 있어요</span>
        </section>
      </div>

      <div class="iv-cta">
        <RouterLink to="/onboarding" class="btn btn--primary btn--lg btn--block">시작하기</RouterLink>
        <RouterLink to="/login" class="textlink" style="align-self: center">이미 계정이 있어요</RouterLink>
      </div>
    </div>
  </div>
</template>

<style>
.scr-Invite .iv-body { display: flex; flex-direction: column; gap: 16px; padding-top: 48px; bottom: 140px; }
.scr-Invite .iv-logo { display: inline-flex; align-items: center; gap: 6px; font-weight: 700; color: var(--navy); }
.scr-Invite .iv-title { margin: 12px 0 0; }
.scr-Invite .iv-sub { margin: 0; color: var(--ink-muted); }
.scr-Invite .iv-course { display: flex; flex-direction: column; gap: 10px; }
.scr-Invite .iv-places { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px; }
.scr-Invite .iv-places li { display: flex; align-items: center; gap: 10px; font-weight: 600; }
.scr-Invite .iv-no { display: inline-grid; place-items: center; width: 22px; height: 22px; border-radius: 50%; background: var(--navy); color: #fff; font-size: 12px; }
.scr-Invite .iv-cta { position: absolute; left: 16px; right: 16px; bottom: 24px; display: flex; flex-direction: column; gap: 12px; }
</style>
