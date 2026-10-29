<script setup>
// S04-a2 홈 · 여행 시간 확인 (②): 첫 장소 출발 시간이 지나면 "지금 여행 중이신가요?" 물어요
import { useRouter } from 'vue-router'
import HomeHeader from '@/components/common/HomeHeader.vue'
import TabBar from '@/components/common/TabBar.vue'
import { showToast } from '@/utils/toast'

const router = useRouter()

function notNow() {
  router.push('/')
  showToast('알겠어요. 일정은 그대로 둘게요')
}
</script>

<template>
  <div class="scr scr-MainStart">
    <div class="app">
      <HomeHeader>
        <RouterLink to="/notifications" class="iconbtn" aria-label="알림 2개"><i class="ic ic--l i-bell" aria-hidden="true"></i><span class="dotbadge"></span></RouterLink>
      </HomeHeader>

      <div class="scroll" style="bottom: 84px; gap: 24px; padding-top: 4px">
        <section class="start-card" aria-label="여행 시작 확인">
          <div style="display: flex; align-items: center; gap: 8px">
            <span class="badge start-card__badge"><i class="ic ic--s i-clock" aria-hidden="true"></i>12:40 출발 시간이 지났어요</span>
          </div>
          <div style="display: flex; align-items: center; gap: 12px">
            <img class="thumb thumb--lg" src="/img/gamjatang.jpg" alt="" />
            <div style="flex: 1; min-width: 0">
              <p class="start-card__cap">잔나비 공연 데이트 · 첫 장소</p>
              <p style="margin: 0; font-size: 18px; line-height: 26px; font-weight: 700">천호 쭈꾸미골목 13:00</p>
            </div>
          </div>
          <h1 style="margin: 0; font-size: 24px; line-height: 32px; font-weight: 700">지금 여행 중이신가요?</h1>
          <div class="modal__btns">
            <button type="button" class="btn btn--lg start-card__no" @click="notNow">아니요</button>
            <RouterLink to="/live" class="btn btn--lg start-card__yes">네, 시작할게요</RouterLink>
          </div>
          <p class="start-card__cap">'아니요'를 누르면 일정은 그대로 두고 다시 묻지 않아요</p>
        </section>
      </div>

      <TabBar active="home" />
    </div>
  </div>
</template>

<style>
.scr-MainStart .start-card { border-radius: var(--radius-lg); background: var(--ink); color: #fff; padding: 20px 16px 16px; display: flex; flex-direction: column; gap: 16px; box-shadow: var(--shadow-card); }
.scr-MainStart .start-card__badge { background: rgba(255, 255, 255, 0.14); color: #fff; }
.scr-MainStart .start-card__cap { margin: 0; font-size: 13px; line-height: 18px; color: var(--on-navy); opacity: 0.82; }
.scr-MainStart .start-card__no { background: rgba(255, 255, 255, 0.12); color: #fff; }
.scr-MainStart .start-card__yes { background: #fff; color: var(--ink); }
</style>
