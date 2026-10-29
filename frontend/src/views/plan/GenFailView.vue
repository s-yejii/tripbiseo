<script setup>
// S08-b 일정 생성 실패 (③): 장소가 하루에 다 안 들어갈 때, 한 곳 빼고 다시 만들지 / 넘치는 채로 고칠지 골라요
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'

const router = useRouter()

// 가짜 데이터: 몇 곳이고 몇 분 넘치는지 (백엔드 계산값)
const count = 7
const overMinutes = 37

const choice = ref('trim') // trim: 한 곳 빼고 다시 / edit: 직접 고치기

function next() {
  router.push(choice.value === 'trim' ? '/plan/trim' : '/plan/check')
}
</script>

<template>
  <div class="scr scr-GenFail">
    <div class="app">
      <TopBar title="일정 만들기" back="/plan/chat"><span class="topbar__side">4 / 4</span></TopBar>

      <div class="scroll" style="padding-top: 8px; gap: 16px; bottom: 102px">
        <span class="ico-tile ico-tile--warn" style="width: 48px; height: 48px"><i class="ic ic--l i-warning" aria-hidden="true"></i></span>
        <h2 class="h-display">{{ count }}곳이 하루에<br />다 안 들어가요</h2>

        <section class="card card--pad gf-compare" aria-label="시간 비교">
          <div class="gf-row">
            <span class="t-cap tn gf-row__top"><span>쓸 수 있는 시간</span><span style="display: inline-flex; align-items: center; gap: 4px">13:00 → <i class="ic ic--s i-lock" aria-hidden="true"></i>18:30</span></span>
            <span class="gf-bar" style="width: 88%; background: var(--navy)"></span>
          </div>
          <div class="gf-row">
            <span class="t-cap tn">{{ count }}곳에 필요한 시간</span>
            <span class="gf-bar gf-bar--split"><span style="width: 88%; background: var(--navy)"></span><span style="width: 12%; background: var(--danger)"></span></span>
            <span class="t-label tn c-danger gf-over"><i class="ic ic--s i-danger" aria-hidden="true"></i>{{ overMinutes }}분 넘쳐요</span>
          </div>
        </section>

        <div role="radiogroup" aria-label="어떻게 할까요" style="display: flex; flex-direction: column; gap: 10px">
          <button type="button" role="radio" :aria-checked="choice === 'trim'" class="opt" :class="{ 'is-on': choice === 'trim' }" style="text-align: left" @click="choice = 'trim'">
            <span class="ico-tile"><i class="ic i-minus" aria-hidden="true"></i></span>
            <span class="opt__body"><span style="display: flex; align-items: center; gap: 6px"><b class="t-sub">한 곳 빼고 다시 만들기</b><span class="badge badge--coral">추천</span></span><span class="t-cap">뺄 곳은 다음 화면에서 골라요</span></span>
            <span class="radio"></span>
          </button>
          <button type="button" role="radio" :aria-checked="choice === 'edit'" class="opt" :class="{ 'is-on': choice === 'edit' }" style="text-align: left" @click="choice = 'edit'">
            <span class="ico-tile ico-tile--neutral"><i class="ic i-edit" aria-hidden="true"></i></span>
            <span class="opt__body"><b class="t-sub">만든 데까지 직접 고치기</b><span class="t-cap tn">넘치는 채로 열고 시간을 줄여요</span></span>
            <span class="radio"></span>
          </button>
        </div>
      </div>

      <div class="cta">
        <button type="button" class="btn btn--primary btn--lg btn--block" @click="next">다음</button>
      </div>
    </div>
  </div>
</template>

<style>
.scr-GenFail .gf-compare { display: flex; flex-direction: column; gap: 12px; }
.scr-GenFail .gf-row { display: flex; flex-direction: column; gap: 6px; }
.scr-GenFail .gf-row__top { display: flex; justify-content: space-between; }
.scr-GenFail .gf-bar { display: block; height: 12px; border-radius: var(--radius-full); }
.scr-GenFail .gf-bar--split { display: flex; overflow: hidden; }
.scr-GenFail .gf-over { align-self: flex-end; display: inline-flex; align-items: center; gap: 4px; }
</style>
