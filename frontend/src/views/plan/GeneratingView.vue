<script setup>
// S08 일정 만드는 중 (③): 서버가 일정을 짜는 단계를 실제로 보내 주는 대로(POST /api/plans/generate/stream) 보여 줘요 (11/04)
//   단계: 장소 후보 찾기 → (요청이 있으면) 리뷰로 확인 → AI가 칸마다 고르기 → 영업시간·종류 검사
//        → (있으면) 끝 장소 도착 맞추기 → 걷는 시간 확인 → 고정 일정 시간 맞추기
//   %는 서버가 단계마다 보낸 값이에요. AI가 고르는 동안처럼 오래 걸리는 단계는 그 단계 범위 안에서만 천천히 올라가요
//   끝나면 초안(store.draft)에 넣고 지도에 실제 일정 핀을 그려요 → "완성본 보기"로 일정 점검
//   다른 화면으로 가도 만들기는 계속되고, 끝나면 토스트로 알려요
//   11/04: 서버가 못 만들면(장소를 못 찾음·위치 없음·서버 오류) 그 이유를 들고 실패 화면(/plan/fail?why=…&code=…)으로 가요
//     (예전엔 실패하면 "7곳이 하루에 다 안 들어가요" 가짜 화면으로 갔어요)
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import KakaoMap from '@/components/common/KakaoMap.vue'
import TopBar from '@/components/common/TopBar.vue'
import { generatePlanStream } from '@/api/trips'
import { useTripStore } from '@/stores/trip'
import { daysBetween } from '@/utils/dates'
import { isSameStay, isStayStop } from '@/utils/mapView'
import { getPositionOnce } from '@/utils/position'
import { showToast } from '@/utils/toast'

const CREEP_MS = 400 // 하는 중인 단계에서 %가 1씩 오르는 간격
const FIRST_CREEP_TO = 15 // 첫 알림(후보 찾기 끝)이 오기 전까지 올라갈 수 있는 %
// 단계가 끝날 때의 % (서버 PlanServiceImpl이 보내는 값과 같음) → 하는 중이면 이 값 바로 아래까지만 올라감
const DONE_PERCENT = { candidates: 20, reviews: 40, ai: 80, rules: 90, finish: 93, walk: 94, fixed: 95 }
const STEP_ICONS = { candidates: 'i-pin', reviews: 'i-search', ai: 'i-sparkle', rules: 'i-clock', finish: 'i-flag', walk: 'i-walk', fixed: 'i-lock' }
const MAP_PADDING = { top: 40, right: 30, bottom: 30, left: 30 }

const router = useRouter()
const store = useTripStore()
const conditions = store.planConditions // 여행 정보 단계에서 모은 조건 (없으면 여행 정보 입력으로)

const steps = ref([]) // 서버가 보낸 단계 [{ step, state(now·done), percent, text, detail }] 받은 순서대로
const percent = ref(0)
const draft = ref(null) // 다 만든 초안
const failed = ref(false) // 서버가 초안을 못 만듦 (422 등)
const failReason = ref('') // 못 만든 이유 (서버 오류 문구)
const failCode = ref(null) // 오류 번호 (422 장소·위치 없음, 5xx 서버 오류, null 연결 끊김)
const isMapOk = ref(true)
let creepTo = FIRST_CREEP_TO
let timer = null
let isLeft = false // 화면을 떠났는지 (떠난 뒤 끝나면 토스트로 알림)

const done = computed(() => draft.value !== null || failed.value)
const placeName = computed(() => store.planArea?.name || conditions?.region || '여행')
const dayCount = computed(() => {
  const start = conditions?.startDate || store.planStartDate
  return daysBetween(start, conditions?.endDate || start) + 1
})
const mapBadge = computed(() => {
  if (draft.value) return '일정 ' + draft.value.stops.length + '곳 확정'
  const found = steps.value.find((item) => item.step === 'candidates')
  return found ? found.text : '후보 찾는 중'
})

// 지도 핀: 다 만들기 전에는 고른 지역(또는 출발지), 다 만들면 실제 일정 순서대로 (고정 일정은 자물쇠)
const mapPins = computed(() => {
  if (draft.value) {
    const pins = []
    const stays = [] // 이미 핀을 꽂은 숙소
    for (const stop of draft.value.stops) {
      if (stop.lat == null || stop.lng == null) continue
      const isStay = isStayStop(stop) // 숙소는 침대 모양 핀, 같은 숙소는 하나만 (11/09)
      if (isStay && stays.some((before) => isSameStay(before, stop))) continue
      if (isStay) stays.push(stop)
      const icon = isStay ? 'i-bed' : stop.fixed ? 'i-lock' : ''
      const cls = isStay ? 'pin--stay' : stop.fixed ? 'pin--fixed' : ''
      pins.push({ key: pins.length, lat: stop.lat, lng: stop.lng, text: pins.length + 1, icon, cls, label: stop.name })
    }
    return pins
  }
  const center = store.planArea || store.planOrigin
  return center ? [{ key: 'center', lat: center.lat, lng: center.lng, text: '', icon: 'i-pin', cls: '', label: center.name, route: false }] : []
})

onMounted(() => {
  if (!conditions) {
    showToast('여행 조건이 없어요. 여행 정보부터 골라 주세요')
    router.replace('/plan/info')
    return
  }
  timer = setInterval(creep, CREEP_MS)
  makeDraft()
})
onUnmounted(() => {
  isLeft = true
  clearInterval(timer)
})

// 위치는 브라우저에 물어보고(거절해도 계속), 조건 + 사용자가 한 말로 초안 만들기 (단계마다 onProgress)
async function makeDraft() {
  const position = await getPositionOnce()
  try {
    const result = await generatePlanStream(conditions, store.planMessages, position?.lat, position?.lng, onProgress)
    store.setDraft(result)
    draft.value = result
    // 안내(notes)는 토스트로 연달아 띄우면 마지막 하나만 보여서, 아래 목록으로 다 보여 줌 (11/13)
    if (isLeft) showToast('일정이 완성됐어요', { actionText: '완성본 보기', action: openResult }) // 홈에서 기다려도 점검 화면으로 갈 수 있게 (11/12)
  } catch (e) {
    failed.value = true
    failReason.value = e.message
    failCode.value = e.status ?? null
    if (isLeft) showToast('일정을 만들지 못했어요', { actionText: '이유 보기', action: openResult })
  }
  clearInterval(timer)
  percent.value = 100
}

// 서버가 보낸 단계 하나: 같은 단계면 바꾸고(now → done), 처음이면 더함
function onProgress(name, item) {
  const old = steps.value.find((step) => step.step === item.step)
  if (old) Object.assign(old, item)
  else steps.value.push({ ...item })
  percent.value = Math.max(percent.value, item.percent)
  creepTo = item.state === 'now' ? (DONE_PERCENT[item.step] || item.percent + 1) - 1 : percent.value
}

// 하는 중인 단계 안에서만 1씩 (다음 단계 %는 넘지 않음)
function creep() {
  if (percent.value < creepTo) percent.value++
}

// 다 만들었으면 일정 점검, 못 만들었으면 이유를 들고 실패 화면으로
function openResult() {
  if (!failed.value) {
    router.replace('/plan/check')
    return
  }
  const query = { why: failReason.value }
  if (failCode.value != null) query.code = failCode.value
  router.replace({ path: '/plan/fail', query })
}
function waitAtHome() {
  showToast('계속 만들고 있어요. 끝나면 알려 드릴게요')
  router.replace('/')
}
</script>

<template>
  <div class="scr scr-Generating">
    <div class="app">
      <TopBar title="일정 만들기" back="/plan/info"><span class="topbar__side">4 / 4</span></TopBar>

      <!-- 지도: 만드는 동안은 고른 지역, 다 만들면 실제 일정 핀 -->
      <div class="gen-map">
        <KakaoMap v-if="isMapOk" :pins="mapPins" :show-route="!!draft" :padding="MAP_PADDING" :fit-key="draft ? 'draft' : 'wait'" @fail="isMapOk = false" />
        <p v-else class="t-cap gen-map__msg">지도를 불러오지 못했어요</p>
        <span class="badge badge--navy gen-map__badge">{{ mapBadge }}</span>
      </div>

      <div class="gen-body">
        <div style="display: flex; align-items: flex-end; justify-content: space-between">
          <h2 v-if="done && failed" class="h-display">일정을<br />만들지 못했어요</h2>
          <h2 v-else-if="done" class="h-display">일정이<br />완성됐어요</h2>
          <h2 v-else class="h-display">{{ placeName }} {{ dayCount > 1 ? dayCount + '일 일정을' : '하루를' }}<br />짜고 있어요</h2>
          <span class="t-big c-navy">{{ percent }}%</span>
        </div>
        <div class="bar" role="progressbar" aria-label="일정 만드는 중" :aria-valuenow="percent" aria-valuemin="0" aria-valuemax="100"><i :style="{ width: percent + '%' }"></i></div>
        <ol class="gen-steps" aria-live="polite">
          <li v-for="item in steps" :key="item.step" class="step" :class="item.state">
            <span class="dotc"><i class="ic ic--s" :class="item.state === 'done' ? 'i-check' : STEP_ICONS[item.step]" aria-hidden="true"></i></span>
            <span class="gen-step__body">
              <span class="t-strong">{{ item.text }}{{ item.state === 'now' ? '…' : '' }}</span>
              <span v-if="item.detail" class="t-cap">{{ item.detail }}</span>
            </span>
          </li>
          <li v-if="!steps.length && !done" class="step now">
            <span class="dotc"><i class="ic ic--s i-pin" aria-hidden="true"></i></span><span class="t-strong">장소 후보 찾는 중…</span>
          </li>
        </ol>
        <ul v-if="draft && draft.notes && draft.notes.length" class="gen-notes" aria-label="일정 안내">
          <li v-for="(note, i) in draft.notes" :key="i" class="t-cap"><i class="ic ic--s i-info" aria-hidden="true"></i>{{ note.text }}</li>
        </ul>
      </div>

      <div class="cta">
        <div class="cta__row">
          <button type="button" class="btn btn--secondary btn--lg" @click="waitAtHome">홈에서 기다리기</button>
          <button type="button" class="btn btn--primary btn--lg" style="flex: 1" :disabled="!done" @click="openResult">{{ failed ? '이유 보기' : '완성본 보기' }}</button>
        </div>
        <p class="cta__hint">다른 화면으로 가도 계속 만들고, 끝나면 알려 드려요</p>
      </div>
    </div>
  </div>
</template>

<style>
.scr-Generating .gen-map{position:absolute;top:108px;left:16px;right:16px;height:200px;border-radius:var(--radius-lg);overflow:hidden;background:var(--surface)}
.scr-Generating .gen-map__msg{position:absolute;inset:0;display:flex;align-items:center;justify-content:center}
.scr-Generating .gen-map__badge{position:absolute;left:10px;top:10px;background:#fff;z-index:1}
.scr-Generating .gen-body{position:absolute;top:328px;left:16px;right:16px;bottom:128px;display:flex;flex-direction:column;gap:16px;overflow:auto}
.scr-Generating .gen-steps{list-style:none;margin:0;padding:0;display:flex;flex-direction:column}
.scr-Generating .gen-step__body{display:flex;flex-direction:column;gap:2px;min-width:0}
.scr-Generating .step{display:flex;align-items:center;gap:12px;min-height:40px;padding:4px 0}
.scr-Generating .step .dotc{width:24px;height:24px;flex:none;border-radius:50%;display:flex;align-items:center;justify-content:center}
.scr-Generating .step.done .dotc{background:var(--navy);color:#fff}
.scr-Generating .step.now .dotc{border:2px solid var(--navy);color:var(--navy)}
.scr-Generating .step.wait .dotc{border:2px solid var(--line-strong)}
.scr-Generating .step.wait .t-strong{color:var(--ink-subtle)}
.scr-Generating .gen-notes{list-style:none;margin:0;padding:12px;display:flex;flex-direction:column;gap:6px;background:var(--surface);border-radius:var(--radius-md, 12px)}
.scr-Generating .gen-notes li{display:flex;gap:6px;align-items:flex-start;color:var(--ink)}
</style>
