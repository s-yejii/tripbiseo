<script setup>
// 개발자 모드 점 (③, 11/06): 여행 중 화면 왼쪽의 반투명 점. 개발자 모드(시연 계정)일 때만 보여요.
// 누르면: 가짜 GPS 켜고 끄기 · 시연 시계 맞추기 · 빨리 감기 · 진짜 시각으로
// 시계를 바꾸면 'clock', 가짜 GPS를 바꾸면 'gps'를 알려서 부모 화면이 상황을 다시 받게 해요.
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { clearDevRain, forwardDevClock, getDevStatus, resetDevClock, setDevClock, setDevRain, setDevSpeed } from '@/api/dev'
import { useRouter } from 'vue-router'
import { devMode, prepareDemo, sendCommand, setFakeGps, setShowPractice, syncClock, turnOffDevMode } from '@/utils/devMode'
import { showToast } from '@/utils/toast'
import { liveBanner } from '@/utils/liveBanner'

const props = defineProps({
  inline: { type: Boolean, default: false }, // 마이 화면 안에 펼친 채로 (11/08: 발표자가 바로 찾게)
})
const emit = defineEmits(['clock', 'gps'])
const FORWARD_STEPS = [5, 10, 30, 60] // 11/08: +5분은 '여유 5분 이하' 돌발을 맞추기 쉽게
const SPEEDS = [1, 10, 30, 60] // 시계 배속 (11/08 주형: 마이에서 고르게)

const isOpen = ref(false)

// 회색 점: 짧게 누르면 "다음 장소로"(직접 조작일 때), 길게 누르면 설정 열기 (11/08 주형: 관객에게 버튼이 안 보이게)
const LONG_PRESS_MS = 600
let pressTimer = null
let isLongPress = false
function onDotDown() {
  isLongPress = false
  clearTimeout(pressTimer)
  pressTimer = setTimeout(() => { isLongPress = true; isOpen.value = !isOpen.value }, LONG_PRESS_MS)
}
function onDotUp() {
  clearTimeout(pressTimer)
  if (isLongPress) return
  if (isOpen.value) { isOpen.value = false; return }
  isOpen.value = true // 11/08 주형: "다음 장소로"는 지도 위 작은 버튼(DemoDrive)으로 옮김 → 점은 설정만
}
function onDotCancel() {
  clearTimeout(pressTimer)
}

// 시연 준비: 연습 여행 정리 + 시계 오늘 15:00 + 다 켜기 → 홈으로
const router = useRouter()
const prepared = ref('')
async function onPrepare() {
  if (isBusy.value) return
  isBusy.value = true
  try {
    const realLeft = await prepareDemo()
    await loadStatus()
    prepared.value = realLeft ? '시연 준비됐어요 · 진행 중인 진짜 여행이 ' + realLeft + '개 있어요' : '시연 준비됐어요'
    showToast(prepared.value)
    isOpen.value = false
    router.push('/')
  } catch (e) {
    showToast(e.message)
  } finally {
    isBusy.value = false
  }
}
// 숨은 방법: 상태바(맨 위 시계 줄)를 두 번 누르면 설정이 열림 (관객 눈엔 안 보이게, 11/08)
function onDoubleClick(event) {
  if (props.inline || !devMode.isOn) return
  const bar = document.querySelector('.statusbar')
  if (!bar) return
  const box = bar.getBoundingClientRect()
  if (event.clientY >= box.top && event.clientY <= box.bottom && event.clientX >= box.left && event.clientX <= box.right) isOpen.value = !isOpen.value
}
onMounted(() => document.addEventListener('dblclick', onDoubleClick))
onUnmounted(() => document.removeEventListener('dblclick', onDoubleClick))
const serverNow = ref('') // "2026-11-07T18:05"
const offsetMinutes = ref(0)
const rainFrom = ref(null) // 시연 비 시작 시각 (돌발 ②)
const rainInput = ref('13:00')
const dateInput = ref('')
const timeInput = ref('')
const isBusy = ref(false)
const speed = computed(() => devMode.speed) // 서버 시계 배속 (시연 엔진이 돌발에서 멈추면 0)
const pausedSpeed = ref(60) // 멈춤 전 배속 (재생하면 이 배속으로)

// 개발자 모드일 때만 서버에 물어봄 (끈 사람·로그아웃한 사람은 요청 안 함, 11/06)
watch(() => devMode.isOn, (isOn) => { if (isOn) loadStatus() }, { immediate: true })

async function loadStatus() {
  try {
    showStatus(await getDevStatus())
  } catch (e) {
    showToast(e.message)
  }
}

function showStatus(status) {
  serverNow.value = status.now
  offsetMinutes.value = status.offsetMinutes
  syncClock(status) // 홈의 D-day·상태바 시계도 시연 시계로 (배속까지)
  rainFrom.value = status.rainFrom
  dateInput.value = status.now.slice(0, 10)
  timeInput.value = status.now.slice(11, 16)
}

// 서버 시계를 바꾸는 요청을 보내고 화면에 반영
async function changeClock(request, message) {
  if (isBusy.value) return
  isBusy.value = true
  try {
    showStatus(await request())
    showToast(message + ' · 지금 ' + serverNow.value.slice(5, 10).replace('-', '/') + ' ' + serverNow.value.slice(11, 16))
    emit('clock')
  } catch (e) {
    showToast(e.message)
  } finally {
    isBusy.value = false
  }
}

function applyClock() {
  changeClock(() => setDevClock(dateInput.value, timeInput.value), '시연 시계를 맞췄어요')
}
function forward(minutes) {
  changeClock(() => forwardDevClock(minutes), minutes + '분 빨리 감았어요')
}
function changeSpeed(next) {
  if (next === 0) pausedSpeed.value = speed.value || 60
  changeClock(() => setDevSpeed(next), next === 0 ? '시계를 멈췄어요' : '시계 ' + next + '배속')
}
function togglePause() {
  changeSpeed(speed.value === 0 ? pausedSpeed.value : 0)
}
// 돌발 만들기 (11/08 주형: 늦는 건 확실히): 여행 중 화면이 시계를 "원래 수단으로 가면 예약에 10분 넘게 늦는 때"로 옮김 (DemoDrive)
function makeLate() {
  sendCommand({ type: 'late' })
  isOpen.value = false
}

function backToRealTime() {
  changeClock(resetDevClock, '진짜 시각으로 돌아왔어요')
}

// 시연 비 예보 넣고 빼기: 기상청 예보 대신 "오늘 13:00부터 비" (돌발 ②)
function toggleRain() {
  if (rainFrom.value) changeClock(clearDevRain, '시연 비 예보를 뺐어요')
  else changeClock(() => setDevRain(rainInput.value), rainInput.value + '부터 비 예보를 넣었어요')
}

function toggleFakeGps() {
  setFakeGps(!devMode.isFakeGps)
  showToast(devMode.isFakeGps ? '가짜 GPS로 일정 장소를 차례로 지나가요' : '진짜 GPS로 돌아왔어요')
  emit('gps')
}

// 실수 방지: 한 번 누르면 "정말 끌까요?"로 바뀌고 3초 안에 한 번 더 눌러야 꺼짐 (11/08)
const OFF_CONFIRM_MS = 3000
const isOffAsked = ref(false)
let offTimer = null
function turnOff() {
  if (!isOffAsked.value) {
    isOffAsked.value = true
    clearTimeout(offTimer)
    offTimer = setTimeout(() => (isOffAsked.value = false), OFF_CONFIRM_MS)
    return
  }
  isOffAsked.value = false
  turnOffDevMode()
  isOpen.value = false
  emit('gps')
}
</script>

<template>
  <template v-if="devMode.isOn">
    <button v-if="!inline" type="button" class="dev-dot" aria-label="개발자 모드" @pointerdown="onDotDown" @pointerup="onDotUp" @pointerleave="onDotCancel" @contextmenu.prevent></button>
    <div v-if="isOpen || inline" class="dev-panel" :class="{ 'dev-panel--inline': inline }" role="dialog" aria-label="개발자 모드">
      <div class="dev-panel__row">
        <b class="t-strong">개발자 모드</b>
        <span class="t-cap tn">서버 시각 {{ serverNow.replace('T', ' ') }}<template v-if="offsetMinutes"> (시연)</template></span>
      </div>

      <button type="button" class="chip" :class="{ 'is-on': devMode.isFakeGps }" @click="toggleFakeGps">가짜 GPS {{ devMode.isFakeGps ? '켜짐' : '꺼짐' }}</button>

      <div class="dev-panel__row">
        <input v-model="dateInput" type="date" class="dev-panel__input" aria-label="시연 날짜" />
        <input v-model="timeInput" type="time" class="dev-panel__input" aria-label="시연 시각" />
        <button type="button" class="btn btn--primary btn--sm" :disabled="isBusy" @click="applyClock">맞추기</button>
      </div>

      <div class="dev-panel__row">
        <input v-model="rainInput" type="time" class="dev-panel__input" aria-label="비 시작 시각" :disabled="!!rainFrom" />
        <button type="button" class="chip" :class="{ 'is-on': rainFrom }" :disabled="isBusy" @click="toggleRain">{{ rainFrom ? rainFrom + '부터 비 · 빼기' : '비 예보 넣기' }}</button>
      </div>

      <div class="dev-panel__row">
        <button type="button" class="btn btn--primary btn--sm" :disabled="isBusy" @click="onPrepare">시연 준비</button>
        <span v-if="prepared" class="t-cap">{{ prepared }}</span>
      </div>


      <div class="dev-panel__row">
        <span class="t-cap">배속</span>
        <button v-for="item in SPEEDS" :key="item" type="button" class="chip chip--sm tn" :class="{ 'is-on': speed === item }" :disabled="isBusy" @click="changeSpeed(item)">{{ item }}x</button>
        <button type="button" class="chip chip--sm" :class="{ 'is-on': speed === 0 }" :disabled="isBusy" @click="togglePause">{{ speed === 0 ? '재생' : '멈춤' }}</button>
      </div>

      <div class="dev-panel__row">
        <button v-for="minutes in FORWARD_STEPS" :key="minutes" type="button" class="chip chip--sm tn" :disabled="isBusy || liveBanner.isCardOpen" @click="forward(minutes)">+{{ minutes }}분</button>
        <button type="button" class="chip chip--sm" :disabled="isBusy || liveBanner.isCardOpen" @click="makeLate">늦게 만들기</button>
        <span v-if="liveBanner.isCardOpen" class="t-cap">늦음 카드에서 고른 뒤에 쓸 수 있어요</span>
      </div>


      <button type="button" class="chip" :class="{ 'is-on': devMode.showPractice }" @click="setShowPractice(!devMode.showPractice)">연습 기록 보기 {{ devMode.showPractice ? '켜짐' : '꺼짐' }}</button>

      <div class="dev-panel__row">
        <button type="button" class="btn btn--secondary btn--sm" :disabled="isBusy || !offsetMinutes" @click="backToRealTime">진짜 시각으로</button>
        <button v-if="!inline" type="button" class="btn btn--ghost btn--sm" @click="turnOff">{{ isOffAsked ? '정말 끌까요? 한 번 더 누르기' : '개발자 모드 끄기' }}</button><!-- 마이에 펼친 칸에서는 실수로 안 꺼지게 숨김 (11/08). 다시 켜기: 버전 1.0.0 5번 -->
      </div>
    </div>
  </template>
</template>

<style>
.dev-dot { position: absolute; left: 6px; top: 16px; z-index: 60; width: 12px; height: 12px; padding: 0; border-radius: 50%; border: 0; background: #8a93a3; opacity: 0.55; cursor: pointer; } /* 상태바 시계 왼쪽 (11/08: 발표자가 찾기 쉽게, 마이·여행 중 같은 자리) */
.dev-panel { position: absolute; left: 10px; top: 40px; z-index: 9; width: min(330px, calc(100% - 20px)); max-height: calc(100% - 210px); overflow-y: auto; display: flex; flex-direction: column; gap: 10px; padding: 14px; border-radius: var(--radius-lg, 16px); background: var(--surface, #fff); box-shadow: 0 8px 24px rgba(0, 0, 0, 0.18); }
.dev-panel__row { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; justify-content: space-between; }
.dev-panel__input { flex: 1 1 130px; min-width: 0; height: 34px; padding: 0 8px; border: 1px solid var(--line, #ddd); border-radius: 8px; font: inherit; }
.dev-panel--inline { position: static; width: auto; max-height: none; box-shadow: none; border: 1px solid var(--line, #ddd); }
.dev-panel__guide { display: flex; flex-direction: column; gap: 2px; padding: 8px 10px; border-radius: 10px; background: var(--surface-2, #f4f5f8); }
.dev-panel__guide p { margin: 0; }
@keyframes dev-dot-soon { 50% { opacity: 0.95; transform: scale(1.25); } }
.dev-dot.is-soon { animation: dev-dot-soon 0.8s ease-in-out infinite; }
</style>
