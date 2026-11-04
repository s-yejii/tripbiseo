<script setup>
// 몇 시부터 몇 시까지: 빠른 선택 칩 + 시작·끝을 30분씩 바꾸는 큰 버튼
// 시작 06:00~22:00, 끝 08:00~24:00, 끝 − 시작은 2시간 이상. 간격이 모자라면 반대쪽도 같이 밀어요 (다음날 새벽은 안 돼요)
import { ref } from 'vue'

const props = defineProps({
  start: { type: String, default: null }, // 시작 'HH:mm'
  end: { type: String, default: null }, // 끝 'HH:mm'
  note: { type: String, default: '' }, // 칩 아래 안내 ("오늘은 늦어서 내일로 바꿨어요")
})
const emit = defineEmits(['update:start', 'update:end'])

const STEP_MIN = 30
const MIN_SPAN_MIN = 120
const START_FIRST_MIN = 6 * 60
const START_LAST_MIN = 22 * 60
const END_FIRST_MIN = 8 * 60
const END_LAST_MIN = 24 * 60
const QUICK_LIST = [
  { label: '오전부터', start: '10:00', end: '20:00' },
  { label: '점심부터', start: '12:00', end: '21:00' },
  { label: '저녁만', start: '17:00', end: '22:00' },
]

const toMinutes = (time) => Number(time.slice(0, 2)) * 60 + Number(time.slice(3))
const toTime = (minutes) => String(Math.floor(minutes / 60)).padStart(2, '0') + ':' + String(minutes % 60).padStart(2, '0')

// 고른 칩: 값이 칩과 같으면 처음부터 켜 두고, −/+를 누르면 꺼요
const firstQuick = QUICK_LIST.find((quick) => quick.start === props.start && quick.end === props.end)
const pickedLabel = ref(firstQuick ? firstQuick.label : '')

function pickQuick(quick) {
  pickedLabel.value = quick.label
  emit('update:end', quick.end)
  emit('update:start', quick.start)
}
function changeStart(diff) {
  const start = toMinutes(props.start) + diff
  pickedLabel.value = ''
  if (toMinutes(props.end) - start < MIN_SPAN_MIN) emit('update:end', toTime(start + MIN_SPAN_MIN))
  emit('update:start', toTime(start))
}
function changeEnd(diff) {
  const end = toMinutes(props.end) + diff
  pickedLabel.value = ''
  emit('update:end', toTime(end))
  if (end - toMinutes(props.start) < MIN_SPAN_MIN) emit('update:start', toTime(end - MIN_SPAN_MIN))
}
</script>

<template>
  <section class="time-pick" aria-label="시작 시간과 끝 시간">
    <div class="time-pick__quick">
      <button v-for="quick in QUICK_LIST" :key="quick.label" type="button" class="chip" :class="{ 'is-on': pickedLabel === quick.label }" :aria-pressed="pickedLabel === quick.label" @click="pickQuick(quick)">{{ quick.label }}</button>
    </div>
    <p v-if="note" class="t-cap" style="margin: 0">{{ note }}</p>
    <div class="card card--pad time-pick__row">
      <span class="t-strong">시작</span>
      <button type="button" class="iconbtn iconbtn--soft" aria-label="시작 30분 앞으로" :disabled="toMinutes(start) <= START_FIRST_MIN" @click="changeStart(-STEP_MIN)"><i class="ic i-minus" aria-hidden="true"></i></button>
      <b class="time-pick__time tn" aria-live="polite">{{ start }}</b>
      <button type="button" class="iconbtn iconbtn--soft" aria-label="시작 30분 뒤로" :disabled="toMinutes(start) >= START_LAST_MIN" @click="changeStart(STEP_MIN)"><i class="ic i-plus" aria-hidden="true"></i></button>
    </div>
    <div class="card card--pad time-pick__row">
      <span class="t-strong">끝</span>
      <button type="button" class="iconbtn iconbtn--soft" aria-label="끝 30분 앞으로" :disabled="toMinutes(end) <= END_FIRST_MIN" @click="changeEnd(-STEP_MIN)"><i class="ic i-minus" aria-hidden="true"></i></button>
      <b class="time-pick__time tn" aria-live="polite">{{ end }}</b>
      <button type="button" class="iconbtn iconbtn--soft" aria-label="끝 30분 뒤로" :disabled="toMinutes(end) >= END_LAST_MIN" @click="changeEnd(STEP_MIN)"><i class="ic i-plus" aria-hidden="true"></i></button>
    </div>
    <p class="t-cap" style="margin: 0">최소 2시간</p>
  </section>
</template>

<style>
.time-pick { display: flex; flex-direction: column; gap: 10px; }
.time-pick__quick { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.time-pick__quick .chip { justify-content: center; }
.time-pick__row { display: grid; grid-template-columns: 1fr auto 96px auto; align-items: center; gap: 4px; }
.time-pick__row .iconbtn:disabled { opacity: 0.3; }
.time-pick__time { font-size: 24px; line-height: 32px; text-align: center; }
</style>
