<script setup>
// 개발자 모드 점 (③, 11/06): 여행 중 화면 왼쪽의 반투명 점. 개발자 모드(시연 계정)일 때만 보여요.
// 누르면: 가짜 GPS 켜고 끄기 · 시연 시계 맞추기 · 빨리 감기 · 진짜 시각으로
// 시계를 바꾸면 'clock', 가짜 GPS를 바꾸면 'gps'를 알려서 부모 화면이 상황을 다시 받게 해요.
import { ref, watch } from 'vue'
import { clearDevRain, forwardDevClock, getDevStatus, resetDevClock, setDevClock, setDevRain } from '@/api/dev'
import { devMode, setClockOffset, setFakeGps, turnOffDevMode } from '@/utils/devMode'
import { showToast } from '@/utils/toast'

const emit = defineEmits(['clock', 'gps'])
const FORWARD_STEPS = [5, 15, 30, 60]

const isOpen = ref(false)
const serverNow = ref('') // "2026-11-07T18:05"
const offsetMinutes = ref(0)
const rainFrom = ref(null) // 시연 비 시작 시각 (돌발 ②)
const rainInput = ref('13:00')
const dateInput = ref('')
const timeInput = ref('')
const isBusy = ref(false)

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
  setClockOffset(status.offsetMinutes) // 홈의 D-day·상태바 시계도 시연 시계로
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
    <button type="button" class="dev-dot" aria-label="개발자 모드" @click="isOpen = !isOpen"></button>
    <div v-if="isOpen" class="dev-panel" role="dialog" aria-label="개발자 모드">
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
        <button v-for="minutes in FORWARD_STEPS" :key="minutes" type="button" class="chip tn" :disabled="isBusy" @click="forward(minutes)">+{{ minutes }}분</button>
      </div>

      <div class="dev-panel__row">
        <button type="button" class="btn btn--secondary btn--sm" :disabled="isBusy || !offsetMinutes" @click="backToRealTime">진짜 시각으로</button>
        <button type="button" class="btn btn--ghost btn--sm" @click="turnOff">개발자 모드 끄기</button>
      </div>
    </div>
  </template>
</template>

<style>
.dev-dot { position: absolute; left: 10px; top: 168px; z-index: 6; width: 18px; height: 18px; border-radius: 50%; border: 0; background: var(--navy, #1f3a68); opacity: 0.28; cursor: pointer; }
.dev-panel { position: absolute; left: 10px; top: 192px; z-index: 9; width: min(330px, calc(100% - 20px)); display: flex; flex-direction: column; gap: 10px; padding: 14px; border-radius: var(--radius-lg, 16px); background: var(--surface, #fff); box-shadow: 0 8px 24px rgba(0, 0, 0, 0.18); }
.dev-panel__row { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; justify-content: space-between; }
.dev-panel__input { flex: 1 1 130px; min-width: 0; height: 34px; padding: 0 8px; border: 1px solid var(--line, #ddd); border-radius: 8px; font: inherit; }
</style>
