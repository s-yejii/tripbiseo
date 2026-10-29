<script setup>
// 머무는 시간 슬라이더 (v-model = 분)
// 기존 시간에 표시가 있고, ±10분 버튼, 0분이면 "일정에서 삭제"로 보여요. 0분 처리는 쓰는 화면이 해요
// 예) <DurationSlider v-model="stay" :orig="60" :max="90" />
import { computed } from 'vue'
import { toDuration } from '@/utils/time'

const props = defineProps({
  modelValue: { type: Number, required: true },
  orig: { type: Number, required: true }, // 기존 시간
  max: { type: Number, required: true },
})
const emit = defineEmits(['update:modelValue'])

const value = computed(() => props.modelValue)
const diff = computed(() => value.value - props.orig)

const nowText = computed(() => (value.value === 0 ? '0분' : toDuration(value.value)))
const diffText = computed(() => {
  if (value.value === 0) return '일정에서 삭제'
  if (diff.value === 0) return '변경 없음'
  return (diff.value > 0 ? '+' : '−') + toDuration(Math.abs(diff.value))
})
const diffClass = computed(() => {
  if (value.value === 0) return 'is-zero'
  if (diff.value > 0) return 'is-up'
  if (diff.value < 0) return 'is-down'
  return ''
})

// 0~max 중 몇 % 자리인지
function percent(v) {
  return ((v / props.max) * 100).toFixed(2)
}
// 기존 시간과 지금 시간 사이를 색칠 (늘리면 남색, 줄이면 주황)
const trackStyle = computed(() => {
  const low = Math.min(value.value, props.orig)
  const high = Math.max(value.value, props.orig)
  const color = diff.value >= 0 ? 'var(--navy)' : 'var(--warning)'
  return `--a:${percent(low)}%;--b:${percent(high)}%;--fill:${color}`
})
const origLeft = computed(() => `calc(${percent(props.orig)}% + (13px - ${percent(props.orig)} * 0.26px))`)

function setValue(v) {
  emit('update:modelValue', Math.max(0, Math.min(props.max, v)))
}
function step(amount) {
  setValue(Math.max(5, value.value + amount))
}
</script>

<template>
  <div class="dur-dur">
    <div class="dur-dur__top">
      <span class="dur-dur__now" aria-live="polite">{{ nowText }}</span>
      <button type="button" class="dur-delta" :class="diffClass" :disabled="diff === 0" :title="diff === 0 ? null : '눌러서 기존 시간으로'" @click="setValue(orig)">
        {{ diffText }}<i v-if="diff !== 0" class="ic ic--s i-undo" aria-hidden="true"></i>
      </button>
      <span class="dur-step">
        <button type="button" class="iconbtn iconbtn--soft" aria-label="10분 줄이기" @click="step(-10)"><i class="ic ic--s i-minus" aria-hidden="true"></i></button>
        <button type="button" class="iconbtn iconbtn--soft" aria-label="10분 늘리기" @click="step(10)"><i class="ic ic--s i-plus" aria-hidden="true"></i></button>
      </span>
    </div>
    <div class="dur-track">
      <span class="dur-orig" :style="{ left: origLeft }" aria-hidden="true"><span>기존</span></span>
      <input
        class="dur-range"
        type="range"
        min="0"
        :max="max"
        step="5"
        :value="value"
        :style="trackStyle"
        :aria-valuetext="nowText"
        aria-label="머무는 시간(분)"
        @input="setValue(Number($event.target.value))"
      />
      <div class="dur-ends"><span>0분</span><span>{{ toDuration(max) }}</span></div>
    </div>
  </div>
</template>
