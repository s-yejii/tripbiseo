<script setup>
// 일정 목록 (시간 · 장소 카드 · 사이 이동). 일정 점검·여행 상세·테마 코스가 같이 써요
// 이동 줄을 누르면 도보·버스·지하철·택시 중에 고를 수 있어요
import { ref } from 'vue'
import { importance, modes } from '@/data/places'
import { minutesBy } from '@/data/trips'
import { showToast } from '@/utils/toast'

defineProps({
  stops: { type: Array, required: true },
  selected: { type: Number, default: null },
})
const emit = defineEmits(['select'])

const openLeg = ref(null) // 이동 수단 고르기를 연 줄 번호
const pickedMode = ref({}) // 줄 번호 → 고른 이동 수단

const weather = {
  sun: { icon: 'i-sun', label: '맑음' },
  cloud: { icon: 'i-cloud', label: '흐림' },
  rain: { icon: 'i-rain', label: '비 60%' },
}

function modeOf(i, leg) {
  return pickedMode.value[i] || leg.mode
}
function minutesOf(i, leg) {
  const mode = modeOf(i, leg)
  return mode === leg.mode ? leg.min : minutesBy(mode, leg.walk)
}
function toggleLeg(i) {
  openLeg.value = openLeg.value === i ? null : i
}
function pickMode(i, mode) {
  pickedMode.value[i] = mode
  openLeg.value = null
  showToast(modes[mode].label + '(으)로 바꿨어요 · 시간을 다시 계산했어요')
}
</script>

<template>
  <ol class="tl" aria-label="일정" style="list-style: none; margin: 0; padding: 0">
    <template v-for="(stop, i) in stops" :key="stop.id + i">
      <!-- 앞 장소에서 오는 이동 -->
      <li v-if="stop.leg" class="leg">
        <span></span>
        <span>
          <button type="button" class="legbtn" :class="{ 'is-open': openLeg === i }" :aria-expanded="openLeg === i" @click="toggleLeg(i)">
            <i class="ic ic--s" :class="modes[modeOf(i, stop.leg)].icon" aria-hidden="true"></i>{{ modes[modeOf(i, stop.leg)].label }} {{ minutesOf(i, stop.leg) }}분<i class="ic ic--s i-down" aria-hidden="true"></i>
          </button>
          <b v-if="stop.leg.rush" class="legwarn"><i class="ic ic--s i-users" aria-hidden="true"></i>퇴근길 혼잡</b>
        </span>
      </li>
      <li v-if="stop.leg && openLeg === i" class="legpick" role="radiogroup" aria-label="이동 수단">
        <button
          v-for="(m, key) in modes"
          :key="key"
          type="button"
          role="radio"
          :aria-checked="key === modeOf(i, stop.leg)"
          class="chip chip--sm"
          :class="{ 'is-on': key === modeOf(i, stop.leg) }"
          @click="pickMode(i, key)"
        >
          <i class="ic ic--s" :class="m.icon" aria-hidden="true"></i>{{ m.label }} {{ key === stop.leg.mode ? stop.leg.min : minutesBy(key, stop.leg.walk) }}분
        </button>
      </li>

      <!-- 장소 카드 -->
      <li>
        <button type="button" class="stop" :class="{ 'is-fixed': stop.fixed, 'is-sel': selected === i }" :data-stop="i" @click="emit('select', i)">
          <span class="stop__time">{{ stop.time }}</span>
          <span class="stop__card">
            <img class="thumb" :src="stop.img" alt="" />
            <span class="stop__body">
              <span class="stop__name">{{ stop.name }}</span>
              <span class="stop__meta" :class="stop.metaCls"><i v-if="stop.metaIcon" class="ic ic--s" :class="stop.metaIcon" aria-hidden="true"></i>{{ stop.meta }}</span>
            </span>
            <span class="stop__side">
              <span v-if="!stop.fixed" class="imp" :class="'imp--' + stop.imp">{{ importance[stop.imp] }}</span>
              <span class="stop__wx" :class="{ 'is-rain': stop.wx === 'rain' }"><i class="ic ic--s" :class="weather[stop.wx].icon" aria-hidden="true"></i>{{ weather[stop.wx].label }}</span>
            </span>
          </span>
        </button>
      </li>
    </template>
  </ol>
</template>
