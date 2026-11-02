<script setup>
// S08 일정 만드는 중 (③): 진행률이 차오르고, 100%가 되면 "완성본 보기"가 켜져요
// 대화로 모은 조건(store.planConditions)이 있으면 서버(POST /api/plans/generate)가 초안을 만들어 초안(store.draft)에 넣고
// 일정 점검 화면으로. 조건이 없으면 시간만 흐르는 시연용 화면이에요.
// ?extra=1 : 원하는 것에서 장소를 더 넣어 달라고 해서 하루에 안 들어가는 경우 → 생성 실패 화면으로
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'
import { generatePlan } from '@/api/trips'
import { useTripStore } from '@/stores/trip'
import { showToast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const store = useTripStore()
const failed = ref(false) // 서버가 초안을 못 만듦 (422 등)
const tooMany = computed(() => route.query.extra === '1' || failed.value) // 하루에 다 안 들어감 / 장소를 못 찾음
const percent = ref(68)
let timer = null

const done = computed(() => percent.value >= 100)
const step3 = computed(() => (percent.value >= 85 ? 'done' : 'now'))
const step4 = computed(() => (done.value ? 'done' : percent.value >= 85 ? 'now' : 'wait'))

onMounted(() => {
  timer = setInterval(() => {
    percent.value = Math.min(store.planConditions ? 95 : 100, percent.value + 4) // 서버 답을 기다리는 동안은 95%에서 멈춤
    if (percent.value >= 100) clearInterval(timer)
  }, 160)
  if (store.planConditions) makeDraft()
})

// 위치는 브라우저에 물어보고(거절해도 계속), 조건 + 사용자가 한 말로 초안 만들기
async function makeDraft() {
  const position = await getPosition()
  try {
    const draft = await generatePlan(store.planConditions, store.planMessages, position?.lat, position?.lng)
    store.setDraft(draft)
    for (const note of draft.notes || []) showToast(note.text)
  } catch (e) {
    failed.value = true
    showToast(e.message)
  }
  clearInterval(timer)
  percent.value = 100
}

function getPosition() {
  return new Promise((resolve) => {
    if (!navigator.geolocation) return resolve(null)
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve({ lat: pos.coords.latitude, lng: pos.coords.longitude }),
      () => resolve(null),
      { timeout: 5000 },
    )
  })
}
onUnmounted(() => clearInterval(timer))

function openResult() {
  router.push(tooMany.value ? '/plan/fail' : '/plan/check')
}
function waitAtHome() {
  showToast('끝나면 알림을 보낼게요')
  router.push('/')
}
</script>

<template>
  <div class="scr scr-Generating">
    <div class="app">
      <TopBar title="일정 만들기" back="/plan/chat"><span class="topbar__side">4 / 4</span></TopBar>

      <!-- 후보를 고르는 모습 (그림) -->
      <div class="gen-map" aria-hidden="true">
        <img src="/img/mapA.svg" alt="" class="gen-map__img" />
        <span class="pin pin--ghost gen-ghost" style="left: 60px; top: 60px"></span>
        <span class="pin pin--ghost gen-ghost" style="left: 120px; top: 140px"></span>
        <span class="pin pin--ghost gen-ghost" style="left: 290px; top: 50px"></span>
        <span class="pin pin--ghost gen-ghost" style="left: 310px; top: 150px"></span>
        <span class="pin" style="left: 140px; top: 90px">1</span>
        <span class="pin" style="left: 210px; top: 70px">2</span>
        <span class="pin" style="left: 240px; top: 120px">3</span>
        <span class="pin pin--fixed" style="left: 300px; top: 100px"><i class="ic ic--s i-lock" aria-hidden="true" style="width: 13px; height: 13px"></i></span>
        <span class="badge badge--navy" style="position: absolute; left: 10px; top: 10px; background: #fff">후보 42곳 · 확정 3곳</span>
      </div>

      <div class="gen-body">
        <div style="display: flex; align-items: flex-end; justify-content: space-between">
          <h2 v-if="done && tooMany" class="h-display">하루에 다 안<br />들어가요</h2>
          <h2 v-else-if="done" class="h-display">일정이<br />완성됐어요</h2>
          <h2 v-else class="h-display">천호 하루를<br />짜고 있어요</h2>
          <span class="t-big c-navy">{{ percent }}%</span>
        </div>
        <div class="bar" role="progressbar" aria-label="일정 만드는 중" :aria-valuenow="percent" aria-valuemin="0" aria-valuemax="100"><i :style="{ width: percent + '%' }"></i></div>
        <ol style="list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column">
          <li class="step done"><span class="dotc"><i class="ic ic--s i-check" aria-hidden="true"></i></span><span class="t-strong">장소 후보 42곳 찾기</span></li>
          <li class="step done"><span class="dotc"><i class="ic ic--s i-check" aria-hidden="true"></i></span><span class="t-strong">영업시간·휴무 확인</span><span class="badge badge--neutral" style="margin-left: auto">3곳 제외</span></li>
          <li class="step" :class="step3"><span class="dotc"><i class="ic ic--s i-walk" aria-hidden="true"></i></span><span class="t-strong">이동 시간 계산</span></li>
          <li class="step" :class="step4"><span class="dotc"><i class="ic ic--s i-lock" aria-hidden="true"></i></span><span class="t-strong">18:30 예약에 맞춰 순서 정하기</span></li>
        </ol>
      </div>

      <div class="cta">
        <div class="cta__row">
          <button type="button" class="btn btn--secondary btn--lg" @click="waitAtHome">홈에서 기다리기</button>
          <button type="button" class="btn btn--primary btn--lg" style="flex: 1" :disabled="!done" @click="openResult">완성본 보기</button>
        </div>
        <p class="cta__hint">다른 화면에 있어도 끝나면 알림이 와요</p>
      </div>
    </div>
  </div>
</template>

<style>
.scr-Generating .gen-map{position:absolute;top:108px;left:16px;right:16px;height:200px;border-radius:var(--radius-lg);overflow:hidden;background:var(--surface)}
.scr-Generating .gen-map__img{position:absolute;left:-20px;top:-320px;width:390px;height:850px;opacity:.8}
.scr-Generating .gen-ghost{width:14px;height:14px;margin:-7px;border-width:2px}
.scr-Generating .gen-body{position:absolute;top:328px;left:16px;right:16px;display:flex;flex-direction:column;gap:16px}
.scr-Generating .step{display:flex;align-items:center;gap:12px;min-height:40px}
.scr-Generating .step .dotc{width:24px;height:24px;flex:none;border-radius:50%;display:flex;align-items:center;justify-content:center}
.scr-Generating .step.done .dotc{background:var(--navy);color:#fff}
.scr-Generating .step.now .dotc{border:2px solid var(--navy);color:var(--navy)}
.scr-Generating .step.wait .dotc{border:2px solid var(--line-strong)}
.scr-Generating .step.wait .t-strong{color:var(--ink-subtle)}
</style>
