<script setup>
// 여행 중 지도 위 칩: 화장실·쓰레기통을 누르면 근처 위치 핀과 가는 길을 보여 줘요 (⑤)
// 맨 앞 "AI에게 묻기"를 누르면 ask 이벤트를 보내요 → 여행 중 AI 채팅(④ AskPanel)이 열려요
import { computed, ref } from 'vue'
import { amenities } from '@/data/near'

const props = defineProps({
  me: { type: Object, required: true }, // 지금 위치 { x, y }
})
const emit = defineEmits(['ask'])

const kind = ref(null) // 'toilet' | 'trash' | null
const pointIndex = ref(0)

const selected = computed(() => (kind.value ? amenities[kind.value] : null))
const target = computed(() => (selected.value ? selected.value.points[pointIndex.value] : null))
// 내 위치 → 옆으로 → 위아래로 가는 꺾인 선
const pathPoints = computed(() => {
  if (!target.value) return ''
  const [x, y] = target.value
  return `${props.me.x},${props.me.y} ${x},${props.me.y} ${x},${y}`
})

function pick(key) {
  kind.value = kind.value === key ? null : key
  pointIndex.value = 0
}
</script>

<template>
  <svg v-if="selected" class="route amen-path" viewBox="0 0 390 844" aria-hidden="true">
    <polyline :points="pathPoints" fill="none" stroke="#1F3A68" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" stroke-dasharray="2 8" />
  </svg>
  <template v-if="selected">
    <button
      v-for="(p, i) in selected.points"
      :key="p[2]"
      type="button"
      class="amen"
      :class="{ 'is-on': i === pointIndex }"
      :style="{ left: p[0] + 'px', top: p[1] + 'px', animationDelay: i * 60 + 'ms' }"
      :aria-label="`${selected.label} ${p[2]}, ${p[3]}m`"
      @click="pointIndex = i"
    >
      <i class="ic" :class="selected.icon"></i>
    </button>
  </template>

  <div class="quick amen-chips" role="group" aria-label="AI 채팅과 근처 편의시설">
    <button type="button" class="chip chip--sm chip--add" @click="emit('ask')">
      <i class="ic ic--s i-sparkle" aria-hidden="true"></i>AI에게 묻기
    </button>
    <button
      v-for="(a, key) in amenities"
      :key="key"
      type="button"
      class="chip chip--sm"
      :class="{ 'is-on': kind === key }"
      :aria-pressed="kind === key"
      @click="pick(key)"
    >
      <i class="ic ic--s" :class="a.icon" aria-hidden="true"></i>{{ a.label }}
    </button>
  </div>
</template>

<style>
.amen-path { position: absolute; left: 0; top: 0; width: 390px; height: 844px; pointer-events: none; z-index: 1; }
.amen-chips { position: absolute; top: 392px; left: 0; right: 70px; z-index: 2; }
</style>
