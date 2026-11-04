<script setup>
// S08-b 일정 생성 실패 (③): 서버가 일정을 못 만든 진짜 이유를 보여 주고, 어떻게 할지 골라요 (11/04)
// 주소: /plan/fail?why=서버 오류 문구&code=오류 번호 (일정 만드는 중 화면이 붙여 줘요)
//   422 위치 없음 → 여행 정보에서 지역 고르기 추천 / 422 장소 못 찾음 → 원하는 것 다시 말하기 추천
//   그 밖(서버 오류·연결 끊김) → 그대로 다시 만들기 추천
// (예전엔 "7곳이 하루에 다 안 들어가요 · 37분 넘쳐요" 가짜 값이었어요. 서버는 넘치는 곳을 알아서 빼고 짜서 그런 실패는 없어요)
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'

const DEFAULT_REASON = '잠시 뒤 다시 시도해 주세요.'
const route = useRoute()
const router = useRouter()

const reason = computed(() => route.query.why || DEFAULT_REASON)
const code = computed(() => (route.query.code ? Number(route.query.code) : null))
const isLocation = computed(() => code.value === 422 && reason.value.includes('위치'))
const isNoPlace = computed(() => code.value === 422 && !isLocation.value)

// 고를 수 있는 것 (추천은 이유에 맞게 하나)
const choices = computed(() => [
  { key: 'retry', icon: 'i-undo', title: '그대로 다시 만들기', desc: '같은 조건으로 한 번 더 짜 봐요', to: '/plan/generating', isBest: !isLocation.value && !isNoPlace.value },
  { key: 'chat', icon: 'i-sparkle', title: '원하는 것 다시 말하기', desc: '가고 싶은 곳·종류를 더 자세히 말해요', to: '/plan/chat', isBest: isNoPlace.value },
  { key: 'info', icon: 'i-edit', title: '여행 정보 고치기', desc: '지역·출발지·어디 위주로를 바꿔요', to: '/plan/info', isBest: isLocation.value },
])
const choice = ref(choices.value.find((item) => item.isBest).key)

function next() {
  router.replace(choices.value.find((item) => item.key === choice.value).to)
}
</script>

<template>
  <div class="scr scr-GenFail">
    <div class="app">
      <TopBar title="일정 만들기" back="/plan/chat"><span class="topbar__side">4 / 4</span></TopBar>

      <div class="scroll" style="padding-top: 8px; gap: 16px; bottom: 102px">
        <span class="ico-tile ico-tile--warn" style="width: 48px; height: 48px"><i class="ic ic--l i-warning" aria-hidden="true"></i></span>
        <h2 class="h-display">일정을<br />만들지 못했어요</h2>

        <section class="card card--pad gf-why" aria-label="못 만든 이유">
          <span class="t-cap">이유</span>
          <p class="t-strong" style="margin: 0">{{ reason }}</p>
        </section>

        <div role="radiogroup" aria-label="어떻게 할까요" style="display: flex; flex-direction: column; gap: 10px">
          <button
            v-for="item in choices"
            :key="item.key"
            type="button"
            role="radio"
            :aria-checked="choice === item.key"
            class="opt"
            :class="{ 'is-on': choice === item.key }"
            style="text-align: left"
            @click="choice = item.key"
          >
            <span class="ico-tile" :class="{ 'ico-tile--neutral': !item.isBest }"><i class="ic" :class="item.icon" aria-hidden="true"></i></span>
            <span class="opt__body">
              <span style="display: flex; align-items: center; gap: 6px"><b class="t-sub">{{ item.title }}</b><span v-if="item.isBest" class="badge badge--coral">추천</span></span>
              <span class="t-cap">{{ item.desc }}</span>
            </span>
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
.scr-GenFail .gf-why { display: flex; flex-direction: column; gap: 4px; }
</style>
