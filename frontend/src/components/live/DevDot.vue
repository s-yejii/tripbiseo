<script setup>
// 개발자 모드 점 (③, 11/06): 여행 중 화면 왼쪽의 반투명 점. 개발자 모드(시연 계정)일 때만 보여요.
// 누르면: 가짜 GPS 켜고 끄기 · 시연 시계 맞추기 · 빨리 감기 · 진짜 시각으로
// 시계를 바꾸면 'clock', 가짜 GPS를 바꾸면 'gps'를 알려서 부모 화면이 상황을 다시 받게 해요.
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { clearDevRain, forwardDevClock, getDevStatus, resetDevClock, setDevClock, setDevRain, setDevSpeed } from '@/api/dev'
import { devMode, sendCommand, setAutoSpeed, setFakeGps, setFlow, setShowPractice, syncClock, turnOffDevMode } from '@/utils/devMode'
import { showToast } from '@/utils/toast'

const props = defineProps({
  inline: { type: Boolean, default: false }, // 마이 화면 안에 펼친 채로 (11/08: 발표자가 바로 찾게)
})
const emit = defineEmits(['clock', 'gps'])
const FORWARD_STEPS = [10, 30, 60]
const SPEEDS = [1, 10, 30, 60] // 시계 배속 (11/08 주형: 마이에서 고르게)
const SCENES = ['출발', '회식', '숙소', '2일차 아침', '서울역 늦음', '서울역'] // 장면 바로 가기 (DemoBar의 장면 순서와 같음)

const isOpen = ref(false)

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
  if (next !== 0) setAutoSpeed(false) // 직접 고르면 자동 배속은 끔
  if (next === 0) pausedSpeed.value = speed.value || 60
  changeClock(() => setDevSpeed(next), next === 0 ? '시계를 멈췄어요' : '시계 ' + next + '배속')
}
function togglePause() {
  changeSpeed(speed.value === 0 ? pausedSpeed.value : 0)
}
// 장면 바로 가기·다음/이전 장소·처음부터: 여행 중 화면이 열려 있으면 바로, 아니면 열 때 실행
function goScene(index) {
  sendCommand({ type: 'scene', index })
  showToast(SCENES[index] + ' 장면으로 · 여행 중 화면에서 움직여요')
  emit('clock')
}
function goStop(step) {
  sendCommand({ type: 'stop', step })
  emit('clock')
}
function restart() {
  sendCommand({ type: 'restart' })
  showToast('1일차 처음으로')
  emit('clock')
}
function toggleFlow() {
  setFlow(!devMode.isFlow)
  showToast(devMode.isFlow ? '시계에 맞춰 일정대로 움직여요' : '자동으로 안 움직여요 (장면·장소 버튼으로)')
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

function turnOff() {
  turnOffDevMode()
  isOpen.value = false
  emit('gps')
}
</script>

<template>
  <template v-if="devMode.isOn">
    <button v-if="!inline" type="button" class="dev-dot" aria-label="개발자 모드" @click="isOpen = !isOpen"></button>
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
        <span class="t-cap">배속</span>
        <button v-for="item in SPEEDS" :key="item" type="button" class="chip chip--sm tn" :class="{ 'is-on': speed === item }" :disabled="isBusy" @click="changeSpeed(item)">{{ item }}x</button>
        <button type="button" class="chip chip--sm" :class="{ 'is-on': speed === 0 }" :disabled="isBusy" @click="togglePause">{{ speed === 0 ? '재생' : '멈춤' }}</button>
        <button type="button" class="chip chip--sm" :class="{ 'is-on': devMode.isAutoSpeed }" @click="setAutoSpeed(!devMode.isAutoSpeed)">자동 배속 {{ devMode.isAutoSpeed ? '켜짐' : '꺼짐' }}</button>
      </div>

      <div class="dev-panel__row">
        <button v-for="minutes in FORWARD_STEPS" :key="minutes" type="button" class="chip chip--sm tn" :disabled="isBusy" @click="forward(minutes)">+{{ minutes }}분</button>
      </div>

      <div class="dev-panel__row">
        <span class="t-cap">장면</span>
        <button v-for="(name, i) in SCENES" :key="name" type="button" class="chip chip--sm" @click="goScene(i)">{{ name }}</button>
      </div>

      <div class="dev-panel__row">
        <button type="button" class="chip chip--sm" @click="goStop(-1)">이전 장소</button>
        <button type="button" class="chip chip--sm" @click="goStop(1)">다음 장소</button>
        <button type="button" class="chip chip--sm" :class="{ 'is-on': devMode.isFlow }" @click="toggleFlow">자동 진행 {{ devMode.isFlow ? '켜짐' : '꺼짐' }}</button>
        <button type="button" class="chip chip--sm" @click="restart">처음부터</button>
      </div>

      <button type="button" class="chip" :class="{ 'is-on': devMode.showPractice }" @click="setShowPractice(!devMode.showPractice)">연습 기록 보기 {{ devMode.showPractice ? '켜짐' : '꺼짐' }}</button>

      <div class="dev-panel__row">
        <button type="button" class="btn btn--secondary btn--sm" :disabled="isBusy || !offsetMinutes" @click="backToRealTime">진짜 시각으로</button>
        <button v-if="!inline" type="button" class="btn btn--ghost btn--sm" @click="turnOff">개발자 모드 끄기</button><!-- 마이에 펼친 칸에서는 실수로 안 꺼지게 숨김 (11/08). 다시 켜기: 버전 1.0.0 5번 -->
      </div>
    </div>
  </template>
</template>

<style>
.dev-dot { position: absolute; left: 6px; top: 16px; z-index: 30; width: 12px; height: 12px; padding: 0; border-radius: 50%; border: 0; background: #8a93a3; opacity: 0.55; cursor: pointer; } /* 상태바 시계 왼쪽 (11/08: 발표자가 찾기 쉽게, 마이·여행 중 같은 자리) */
.dev-panel { position: absolute; left: 10px; top: 40px; z-index: 9; width: min(330px, calc(100% - 20px)); max-height: calc(100% - 210px); overflow-y: auto; display: flex; flex-direction: column; gap: 10px; padding: 14px; border-radius: var(--radius-lg, 16px); background: var(--surface, #fff); box-shadow: 0 8px 24px rgba(0, 0, 0, 0.18); }
.dev-panel__row { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; justify-content: space-between; }
.dev-panel__input { flex: 1 1 130px; min-width: 0; height: 34px; padding: 0 8px; border: 1px solid var(--line, #ddd); border-radius: 8px; font: inherit; }
.dev-panel--inline { position: static; width: auto; max-height: none; box-shadow: none; border: 1px solid var(--line, #ddd); }
</style>
