<script setup>
// 동 고르기 (건너뛰어도 돼요): 고른 구를 크게 보여 주고 행정동을 여러 개 골라요. 안 고르면 그 구 전체
// 고른 동의 경계는 일정 만들 때 서버로 보내서, 그 안의 장소를 먼저 넣어요 (좌표가 경계 안에 있는지 PostGIS로 확인)
// 경계: public/geo/seoul-dong.json (출처는 public/geo/README.md)
import { computed, onMounted, ref, watch } from 'vue'
import { getDongTypes } from '@/api/trips'

const props = defineProps({
  areas: { type: Array, required: true }, // 고른 구 [{ name, dongs: [{ name, code, geometry }] }]. 이 안의 dongs를 바꿔요
})

const MAP_WIDTH = 350
const MAP_HEIGHT = 240
const MAP_PADDING = 6
const LNG_RATIO = 0.792 // 서울에서 경도 1도는 위도 1도의 약 0.79배 길이

const allDongs = ref([])
const activeName = ref(props.areas.length ? props.areas[0].name : null)
const hover = ref(null)
const isFailed = ref(false)
const typeByCode = ref({}) // 행정동 코드 → food · cafe · tour (실제 장소 수로 계산, 서버)
const TYPE_LEGEND = [{ type: 'food', label: '맛집' }, { type: 'cafe', label: '카페' }, { type: 'tour', label: '볼거리' }]

onMounted(async () => {
  try {
    const res = await fetch('/geo/seoul-dong.json')
    allDongs.value = (await res.json()).features
  } catch {
    isFailed.value = true
  }
})

const activeArea = computed(() => props.areas.find((area) => area.name === activeName.value) || null)
const dongFeatures = computed(() => allDongs.value.filter((feature) => feature.properties.gu === activeName.value))

// 지금 구가 칸에 꽉 차게 맞춰서 그릴 모양 [{ code, name, d, x, y }]
const shapes = computed(() => {
  let minLng = Infinity
  let maxLng = -Infinity
  let minLat = Infinity
  let maxLat = -Infinity
  for (const feature of dongFeatures.value) {
    for (const polygon of feature.geometry.coordinates) {
      for (const [lng, lat] of polygon[0]) {
        minLng = Math.min(minLng, lng)
        maxLng = Math.max(maxLng, lng)
        minLat = Math.min(minLat, lat)
        maxLat = Math.max(maxLat, lat)
      }
    }
  }
  const usable = MAP_WIDTH - MAP_PADDING * 2
  const scale = Math.min(usable / ((maxLng - minLng) * LNG_RATIO), (MAP_HEIGHT - MAP_PADDING * 2) / (maxLat - minLat))
  const left = (MAP_WIDTH - (maxLng - minLng) * LNG_RATIO * scale) / 2
  const top = (MAP_HEIGHT - (maxLat - minLat) * scale) / 2
  const toX = (lng) => left + (lng - minLng) * LNG_RATIO * scale
  const toY = (lat) => top + (maxLat - lat) * scale
  return dongFeatures.value.map((feature) => {
    let d = ''
    for (const polygon of feature.geometry.coordinates) {
      for (const ring of polygon) d += 'M' + ring.map(([lng, lat]) => toX(lng).toFixed(1) + ',' + toY(lat).toFixed(1)).join(' ') + 'Z'
    }
    return { code: feature.properties.code, name: feature.properties.name, d, feature }
  })
})

// 구를 바꾸면 그 구의 특색을 받아요. 실패하면 색 없이 그대로
watch(dongFeatures, async (features) => {
  if (!features.length) return
  try {
    const dongs = features.map((feature) => ({ code: feature.properties.code, geometry: JSON.stringify(feature.geometry) }))
    const types = await getDongTypes(dongs)
    const next = { ...typeByCode.value }
    for (const item of types) next[item.code] = item.type
    typeByCode.value = next
  } catch {
    // 색칠은 없어도 동은 고를 수 있어요
  }
})

function isOn(code) {
  return !!activeArea.value && activeArea.value.dongs.some((dong) => dong.code === code)
}
function toggle(shape) {
  const area = activeArea.value
  if (isOn(shape.code)) area.dongs = area.dongs.filter((dong) => dong.code !== shape.code)
  else area.dongs = [...area.dongs, { name: shape.name, code: shape.code, geometry: JSON.stringify(shape.feature.geometry) }]
}
function clearDongs() {
  activeArea.value.dongs = []
}
</script>

<template>
  <section class="card card--pad dong-pick" aria-label="동 고르기">
    <div v-if="areas.length > 1" class="chips" role="tablist" aria-label="구">
      <button v-for="area in areas" :key="area.name" type="button" role="tab" class="chip chip--sm" :class="{ 'is-on': area.name === activeName }" :aria-selected="area.name === activeName" @click="activeName = area.name">{{ area.name }}<template v-if="area.dongs.length"> {{ area.dongs.length }}</template></button>
    </div>
    <p v-if="isFailed" class="t-cap">지도를 불러오지 못했어요. 건너뛰어도 돼요</p>
    <svg v-else class="dong-pick__svg" :viewBox="`0 0 ${MAP_WIDTH} ${MAP_HEIGHT}`" role="img" :aria-label="activeName + ' 행정동'">
      <path v-for="shape in shapes" :key="shape.code" class="dong-pick__dong" :class="[typeByCode[shape.code] ? 'type-' + typeByCode[shape.code] : '', { 'is-on': isOn(shape.code), 'is-hover': hover === shape.code }]" :d="shape.d" fill-rule="evenodd" @pointerenter="hover = shape.code" @pointerleave="hover = null" @click="toggle(shape)" />
    </svg>
    <p v-if="!isFailed" class="dong-pick__legend" aria-label="색 설명">
      <span v-for="item in TYPE_LEGEND" :key="item.type"><i class="dong-pick__dot" :class="'type-' + item.type" aria-hidden="true"></i>{{ item.label }}</span>
    </p>
    <div class="chips dong-pick__list">
      <button type="button" class="chip chip--sm" :class="{ 'is-on': activeArea && !activeArea.dongs.length }" @click="clearDongs">전체</button>
      <button v-for="shape in shapes" :key="'l-' + shape.code" type="button" class="chip chip--sm" :class="{ 'is-on': isOn(shape.code) }" :aria-pressed="isOn(shape.code)" @click="toggle(shape)" @pointerenter="hover = shape.code" @pointerleave="hover = null"><i v-if="typeByCode[shape.code]" class="dong-pick__dot" :class="'type-' + typeByCode[shape.code]" aria-hidden="true"></i>{{ shape.name }}</button>
    </div>
  </section>
</template>

<style>
.dong-pick { display: flex; flex-direction: column; gap: 12px; }
.dong-pick__svg { display: block; width: 100%; height: auto; touch-action: manipulation; }
.dong-pick__dong { fill: var(--surface-sunken); stroke: #fff; stroke-width: 1; cursor: pointer; }
.dong-pick__dong.type-food { fill: #FFB4A2; }
.dong-pick__dong.type-cafe { fill: #E8C9A0; }
.dong-pick__dong.type-tour { fill: #A8DCC8; }
.dong-pick__dong.is-hover { fill: var(--navy-soft); stroke: var(--navy); }
.dong-pick__dong.is-on { fill: var(--navy); }
.dong-pick__legend { display: flex; justify-content: center; gap: 12px; margin: 0; font-size: 12px; line-height: 16px; color: var(--ink-muted); }
.dong-pick__legend span { display: inline-flex; align-items: center; gap: 4px; }
.dong-pick__dot { display: inline-block; flex: none; width: 6px; height: 6px; margin-right: 4px; border-radius: 50%; }
.dong-pick__legend .dong-pick__dot { margin-right: 0; }
.dong-pick__dot.type-food { background: #FFB4A2; }
.dong-pick__dot.type-cafe { background: #E8C9A0; }
.dong-pick__dot.type-tour { background: #A8DCC8; }
.dong-pick__list { max-height: 148px; overflow-y: auto; }
</style>
