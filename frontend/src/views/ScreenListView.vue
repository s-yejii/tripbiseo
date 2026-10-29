<script setup>
// 개발용: 화면 57개를 담당별로 모아 둔 목록 (/screens). 눌러서 바로 가 볼 수 있어요
import { useRouter } from 'vue-router'

const router = useRouter()
const owners = [
  { no: 1, name: '① 회원·알림·마이·잠금화면' },
  { no: 2, name: '② 홈·내 여행·일정 점검/편집·상세' },
  { no: 3, name: '③ 새 여행·AI 일정 만들기·테마' },
  { no: 4, name: '④ 여행 중 판별·대안' },
  { no: 5, name: '⑤ 길 안내·결제 메모·회고·앨범' },
]

function screensOf(no) {
  return router.options.routes.filter((r) => r.meta && r.meta.owner === no)
}
</script>

<template>
  <div class="scr">
    <div class="app">
      <header class="topbar" style="padding: 0 16px">
        <h1 class="topbar__title">전체 화면 목록</h1>
      </header>
      <div class="scroll" style="overflow-y: auto; padding-bottom: 24px">
        <section v-for="owner in owners" :key="owner.no" class="screen-group">
          <h2 class="t-sub">{{ owner.name }} <span class="muted tn">{{ screensOf(owner.no).length }}</span></h2>
          <div class="group">
            <RouterLink v-for="r in screensOf(owner.no)" :key="r.path" :to="r.path" class="row">
              <span class="row__body">
                <span class="row__title">{{ r.meta.title }}</span>
                <span class="row__meta">{{ r.path }}</span>
              </span>
              <i class="ic i-next subtle" aria-hidden="true"></i>
            </RouterLink>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<style>
.screen-group { display: flex; flex-direction: column; gap: 8px; }
</style>
