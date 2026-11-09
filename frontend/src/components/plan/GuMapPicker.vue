<script setup>
// 서울 구 지도: 구를 여러 개 골라요. 마우스를 올리면(휴대폰은 첫 번째 탭) 구가 살짝 떠오르고, 누르면(두 번째 탭) 선택·해제
// 경계: public/geo/seoul-gu.json (출처는 public/geo/README.md). 구 이름 목록으로도 고를 수 있어요
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { devMode } from '@/utils/devMode'

const props = defineProps({
  modelValue: { type: Array, default: () => [] }, // 고른 구 [{ name, lat, lng, dongs }]
  tops: { type: Array, default: () => [] }, // 추천지역: 리뷰 많은 구 이름 (색으로 표시)
})
const emit = defineEmits(['update:modelValue'])

const MAP_WIDTH = 350
const MAP_HEIGHT = 292
const LEFT_LNG = 126.76 // 지도 왼쪽 경도
const TOP_LAT = 37.7 // 지도 위쪽 위도
const SCALE = 1040 // 위도 1도 = 몇 칸
const LNG_RATIO = 0.792 // 서울에서 경도 1도는 위도 1도의 약 0.79배 길이

const SPIN_MS = 2800 // 룰렛 전체 시간
const DEMO_GU_NAME = '영등포' // 개발자 모드 주사위가 멈출 시연 지역
const SPIN_FAST_MS = 50 // 처음 반짝임 간격
const SPIN_SLOW_MS = 380 // 마지막 반짝임 간격

// 서울시 5개 생활권별 칸 색
const ZONES = {
  center: ['종로구', '중구', '용산구'],
  northeast: ['성동구', '광진구', '동대문구', '중랑구', '성북구', '강북구', '도봉구', '노원구'],
  northwest: ['은평구', '서대문구', '마포구'],
  southwest: ['양천구', '강서구', '구로구', '금천구', '영등포구', '동작구', '관악구'],
  southeast: ['서초구', '강남구', '송파구', '강동구'],
}

const gus = ref([])
const hover = ref(null) // 떠오른 구 이름
const isFailed = ref(false)
let pointerType = 'mouse'
const spinning = ref(null) // 룰렛이 지금 지나는 구 이름
const isSpinning = ref(false)
const isLanding = ref(false) // 주사위가 멈추며 한 번 통 튀는 동안
const diceFace = ref(5) // 주사위 윗면 눈 수 (굴리는 동안 계속 바뀜, 11/06)
// 눈 수별 점 자리 (24×24 안에서)
const PIPS = {
  1: [[12, 12]],
  2: [[8, 8], [16, 16]],
  3: [[8, 8], [12, 12], [16, 16]],
  4: [[8, 8], [16, 8], [8, 16], [16, 16]],
  5: [[8, 8], [16, 8], [12, 12], [8, 16], [16, 16]],
  6: [[8, 7.5], [16, 7.5], [8, 12], [16, 12], [8, 16.5], [16, 16.5]],
}
const LAND_MS = 420 // 멈출 때 통 튀는 시간
const HINT_KEY = 'guRouletteHintSeen' // 말풍선을 닫았는지 (이 브라우저에 기억)
const isHintOpen = ref(readHintOpen())
function readHintOpen() {
  try {
    return !localStorage.getItem(HINT_KEY)
  } catch {
    return false
  }
}
function closeHint() {
  isHintOpen.value = false
  try {
    localStorage.setItem(HINT_KEY, '1')
  } catch {
    // 저장이 안 되면 다음에 또 보여요
  }
}
const randomName = ref(null) // 룰렛으로 고른 구 ("○○구 어때요?")
let spinTimer = null
onUnmounted(() => clearTimeout(spinTimer))

onMounted(async () => {
  try {
    const res = await fetch('/geo/seoul-gu.json')
    const data = await res.json()
    gus.value = data.features.map(toGu)
  } catch {
    isFailed.value = true
  }
})

// GeoJSON 한 구 → 그릴 모양 { name, d(SVG 경로), x·y(이름 자리), lat·lng(가운데) }
function toGu(feature) {
  let d = ''
  let lngSum = 0
  let latSum = 0
  let count = 0
  for (const polygon of feature.geometry.coordinates) {
    for (const ring of polygon) {
      d += 'M' + ring.map(([lng, lat]) => toX(lng).toFixed(1) + ',' + toY(lat).toFixed(1)).join(' ') + 'Z'
      for (const [lng, lat] of ring) {
        lngSum += lng
        latSum += lat
        count++
      }
    }
  }
  const lng = lngSum / count
  const lat = latSum / count
  const points = new Set() // 이웃 구를 찾으려고 꼭짓점을 모아 둠
  for (const polygon of feature.geometry.coordinates) {
    for (const ring of polygon) ring.forEach((point) => points.add(point.join()))
  }
  return { name: feature.properties.gu, d, x: toX(lng), y: toY(lat), lat, lng, points }
}
function toX(lng) {
  return (lng - LEFT_LNG) * SCALE * LNG_RATIO
}
function toY(lat) {
  return (TOP_LAT - lat) * SCALE
}

const selectedNames = computed(() => props.modelValue.map((area) => area.name))
function isOn(name) {
  return selectedNames.value.includes(name)
}
// 떠 있는 구: 고른 구 + 마우스를 올린 구
const lifted = computed(() => gus.value.filter((gu) => isOn(gu.name) || gu.name === hover.value || gu.name === spinning.value))
function isHighlighted(name) {
  return isOn(name) || name === spinning.value
}
function zoneOf(name) {
  for (const zone in ZONES) {
    if (ZONES[zone].includes(name)) return 'zone-' + zone
  }
  return ''
}
function shortName(name) {
  return name.length > 2 ? name.replace(/구$/, '') : name
}

function toggle(gu) {
  if (isSpinning.value) return
  randomName.value = null // 직접 누르면 일반 선택으로
  if (isOn(gu.name)) {
    emit('update:modelValue', props.modelValue.filter((area) => area.name !== gu.name))
    return
  }
  emit('update:modelValue', [...props.modelValue, { name: gu.name, lat: gu.lat, lng: gu.lng, dongs: [] }])
}
function onEnter(e, gu) {
  if (e.pointerType === 'mouse') hover.value = gu.name
}
function onLeave(e) {
  if (e.pointerType === 'mouse') hover.value = null
}
// 휴대폰: 첫 탭은 띄우기만, 같은 구를 또 누르면 선택
function onClick(gu) {
  if (isSpinning.value) return
  if (pointerType !== 'mouse' && hover.value !== gu.name && !isOn(gu.name)) {
    hover.value = gu.name
    return
  }
  toggle(gu)
  hover.value = null
}

// 이웃 구: 경계 꼭짓점을 같이 쓰는 구
function neighborsOf(gu) {
  return gus.value.filter((other) => other !== gu && [...gu.points].some((point) => other.points.has(point)))
}
function randomOf(list) {
  return list[Math.floor(Math.random() * list.length)]
}
// 룰렛 길: 이웃 구를 따라 돌다가 결과 구에 도착 (from에서 target까지 이웃으로 가는 가장 짧은 길)
function pathTo(from, target) {
  const before = new Map([[from, null]])
  const queue = [from]
  while (queue.length) {
    const gu = queue.shift()
    if (gu === target) break
    for (const next of neighborsOf(gu)) {
      if (!before.has(next)) {
        before.set(next, gu)
        queue.push(next)
      }
    }
  }
  const path = []
  for (let gu = target; gu && gu !== from; gu = before.get(gu)) path.unshift(gu)
  return path
}
// 반짝일 순서와 간격: 처음엔 빠르게, 끝으로 갈수록 느리게. 전체 시간이 SPIN_MS쯤 되게 칸 수를 정함
function makeSteps(target) {
  const walk = []
  let current = randomOf(gus.value)
  const stepCount = Math.round((SPIN_MS * 2) / (SPIN_FAST_MS + SPIN_SLOW_MS))
  while (walk.length < stepCount) {
    walk.push(current)
    current = randomOf(neighborsOf(current))
  }
  return [...walk, ...pathTo(current, target), target]
}

// 랜덤으로 고르기: 결과는 25개 구 중 똑같은 확률로 먼저 정함 (추천 구 가중치 없음). 구 하나만
function spin() {
  if (isSpinning.value || !gus.value.length) return
  closeHint()
  const target = pickTarget()
  randomName.value = null
  emit('update:modelValue', [])
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return finishSpin(target)
  isSpinning.value = true
  const steps = makeSteps(target)
  let index = 0
  const next = () => {
    spinning.value = steps[index].name
    diceFace.value = 1 + Math.floor(Math.random() * 6) // 지도가 반짝일 때마다 주사위 윗면도 바뀜
    if (index === steps.length - 1) return finishSpin(target)
    const progress = index / (steps.length - 1)
    index++
    spinTimer = setTimeout(next, SPIN_FAST_MS + (SPIN_SLOW_MS - SPIN_FAST_MS) * progress * progress)
  }
  next()
}
// 굴려서 멈출 구: 개발자 모드(시연 계정)에서는 시연 지역에 멈춤 (11/08 주형: 발표 시나리오 고정), 그 밖에는 25개 구 중 하나
function pickTarget() {
  if (devMode.isOn) {
    const demoGu = gus.value.find((gu) => gu.name.includes(DEMO_GU_NAME))
    if (demoGu) return demoGu
  }
  return randomOf(gus.value)
}
function finishSpin(target) {
  spinning.value = null
  isSpinning.value = false
  isLanding.value = true
  setTimeout(() => (isLanding.value = false), LAND_MS)
  randomName.value = target.name
  emit('update:modelValue', [{ name: target.name, lat: target.lat, lng: target.lng, dongs: [] }])
}
</script>

<template>
  <section class="card card--pad gu-map" aria-label="서울 구 지도">
    <p v-if="isFailed" class="t-cap">지도를 불러오지 못했어요. 위에서 검색해 주세요</p>
    <button v-if="!isFailed" type="button" class="gu-map__spin" aria-label="랜덤으로 고르기" title="랜덤으로 고르기" :disabled="isSpinning" @click="spin"><svg class="gu-map__dice" :class="{ 'is-rolling': isSpinning, 'is-landing': isLanding }" viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="4" width="16" height="16" rx="3" fill="#fff" stroke="currentColor" stroke-width="1.75"/><circle v-for="(pip, i) in PIPS[diceFace]" :key="i" :cx="pip[0]" :cy="pip[1]" r="1.5" fill="currentColor"/></svg></button>
    <p v-if="isHintOpen && !isFailed" class="gu-map__hint"><span>어디 갈지 모르겠다면</span><button type="button" aria-label="닫기" @click="closeHint"><i class="ic ic--s i-close" aria-hidden="true"></i></button></p>
    <svg v-if="!isFailed" class="gu-map__svg" :viewBox="`0 0 ${MAP_WIDTH} ${MAP_HEIGHT}`" role="img" aria-label="서울 25개 구" @pointerdown="pointerType = $event.pointerType">
      <path
        v-for="gu in gus"
        :key="gu.name"
        class="gu-map__gu"
        :class="[zoneOf(gu.name), { 'is-top': tops.includes(gu.name) }]"
        :d="gu.d"
        fill-rule="evenodd"
        @pointerenter="onEnter($event, gu)"
        @pointerleave="onLeave"
        @click="onClick(gu)"
      />
      <path v-for="gu in lifted" :key="'up-' + gu.name" class="gu-map__lift" :class="{ 'is-on': isHighlighted(gu.name) }" :d="gu.d" fill-rule="evenodd" />
      <text v-for="gu in gus" :key="'t-' + gu.name" class="gu-map__name" :class="{ 'is-on': isHighlighted(gu.name), 'is-up': isHighlighted(gu.name) || gu.name === hover, 'is-top': tops.includes(gu.name) }" :x="gu.x" :y="gu.y" text-anchor="middle" dominant-baseline="middle">{{ shortName(gu.name) }}</text>
      <!-- 착착트립 추천 지역: 구 이름 위에 깃발 -->
      <template v-for="gu in gus" :key="'f-' + gu.name">
        <g v-if="tops.includes(gu.name)" class="gu-map__flag" :class="{ 'is-on': isHighlighted(gu.name) }" :transform="`translate(${gu.x} ${gu.y - 6})`">
          <path class="gu-map__pole" d="M0 0V-12" />
          <path class="gu-map__cloth" d="M0 -12L8 -9L0 -6Z" />
        </g>
      </template>
    </svg>
    <p v-if="randomName" class="gu-map__random t-cap" role="status">{{ randomName }} 어때요?<button type="button" class="gu-map__again" @click="spin">다시</button></p>
    <p v-if="tops.length" class="gu-map__legend t-cap"><svg class="gu-map__legend-flag" viewBox="0 0 10 14" aria-hidden="true"><path d="M1 1V13" stroke="#1F3A68" stroke-width="1.5" /><path d="M1 1L9 4L1 7Z" fill="#E8603F" /></svg>착착트립 추천 지역<i class="is-on" aria-hidden="true"></i>선택</p>
    <button v-if="hover && !isOn(hover) && pointerType !== 'mouse'" type="button" class="btn btn--secondary btn--md btn--block" @click="toggle(gus.find((gu) => gu.name === hover)); hover = null">{{ hover }} 선택</button>
    <details class="gu-map__list">
      <summary class="t-cap">목록으로 고르기</summary>
      <div class="chips">
        <button v-for="gu in gus" :key="'l-' + gu.name" type="button" class="chip chip--sm" :class="{ 'is-on': isOn(gu.name) }" :aria-pressed="isOn(gu.name)" @click="toggle(gu)">{{ gu.name }}</button>
      </div>
    </details>
  </section>
</template>

<style>
.gu-map { position: relative; display: flex; flex-direction: column; gap: 8px; background: #EEF7FC; }
.gu-map__spin { position: absolute; top: 12px; right: 12px; z-index: 2; width: 40px; height: 40px; display: flex; align-items: center; justify-content: center; padding: 0; border: 1px solid var(--line); border-radius: 50%; background: #fff; color: var(--navy); cursor: pointer; }
.gu-map__spin::before { content: ""; position: absolute; inset: -2px; }
.gu-map__spin:disabled { color: var(--navy); cursor: default; }
.gu-map__dice { width: 24px; height: 24px; transform-origin: 50% 50%; }
/* 굴리는 동안: 제자리에서 데굴데굴 (돌면서 살짝 뜨고 눌림), 멈출 때: 통 튀고 바로 섬 */
.gu-map__dice.is-rolling { animation: dice-roll .36s linear infinite; }
.gu-map__dice.is-landing { animation: dice-land .42s ease-out; }
@keyframes dice-roll {
  0% { transform: translateY(0) rotate(0deg) scale(1, 1); }
  25% { transform: translateY(-5px) rotate(90deg) scale(.92, 1.06); }
  50% { transform: translateY(0) rotate(180deg) scale(1.06, .92); }
  75% { transform: translateY(-5px) rotate(270deg) scale(.92, 1.06); }
  100% { transform: translateY(0) rotate(360deg) scale(1, 1); }
}
@keyframes dice-land {
  0% { transform: translateY(-6px) rotate(-12deg); }
  55% { transform: translateY(0) rotate(4deg) scale(1.08, .9); }
  80% { transform: translateY(-2px) rotate(0deg) scale(.98, 1.02); }
  100% { transform: translateY(0) rotate(0deg) scale(1, 1); }
}
@media (prefers-reduced-motion: reduce) { .gu-map__dice.is-rolling, .gu-map__dice.is-landing { animation: none; } }
.gu-map__hint { position: absolute; top: 60px; right: 12px; z-index: 2; display: flex; align-items: center; gap: 2px; margin: 0; padding: 2px 2px 2px 10px; border-radius: var(--radius-md); background: var(--navy); color: #fff; font-size: 12px; line-height: 16px; }
.gu-map__hint button { width: 28px; height: 28px; display: flex; align-items: center; justify-content: center; padding: 0; border: 0; background: none; color: #fff; cursor: pointer; }
.gu-map__flag { pointer-events: none; }
.gu-map__pole { stroke: var(--navy); stroke-width: 1.4; }
.gu-map__cloth { fill: var(--coral); }
.gu-map__flag.is-on .gu-map__pole { stroke: #fff; }
.gu-map__flag.is-on .gu-map__cloth { fill: #fff; }
.gu-map__legend-flag { width: 10px; height: 14px; flex: none; }
.gu-map__again { min-height: 28px; margin-left: 6px; padding: 0 6px; border: 0; background: none; font: inherit; font-weight: 600; color: var(--navy); text-decoration: underline; cursor: pointer; }
.gu-map__svg { display: block; width: 100%; height: auto; touch-action: manipulation; overflow: visible; }
.gu-map__gu { stroke: #fff; stroke-width: 1.5; cursor: pointer; }
.gu-map__gu.zone-center { fill: #CFE6F7; }
.gu-map__gu.zone-northeast { fill: #CFEDE3; }
.gu-map__gu.zone-northwest { fill: #F6EBC2; }
.gu-map__gu.zone-southwest { fill: #F8D9C8; }
.gu-map__gu.zone-southeast { fill: #E0D9F2; }
.gu-map__lift { fill: var(--navy-soft); stroke: var(--navy); stroke-width: 1; pointer-events: none; transform: translateY(-3px); filter: drop-shadow(0 3px 3px rgba(24, 33, 58, .25)); animation: gu-lift .12s ease-out; }
.gu-map__lift.is-on { fill: var(--navy); stroke: #fff; }
.gu-map__name.is-top:not(.is-on) { fill: var(--navy); font-weight: 700; }
@keyframes gu-lift { from { transform: translateY(0); } to { transform: translateY(-3px); } }
@media (prefers-reduced-motion: reduce) { .gu-map__lift { animation: none; } }
.gu-map__name { font-size: 12px; font-weight: 600; fill: var(--ink); pointer-events: none; }
.gu-map__name.is-up { transform: translateY(-3px); fill: var(--navy); font-weight: 700; }
.gu-map__name.is-on { fill: #fff; }
.gu-map__random { margin: 0; text-align: center; color: var(--ink); font-size: 18px; line-height: 26px; font-weight: 700; }
.gu-map__random .gu-map__again { font-size: 15px; }
.gu-map__legend { display: flex; align-items: center; gap: 6px; margin: 0; font-size: 14px; line-height: 20px; }
.gu-map__legend { color: var(--ink); }
.gu-map__legend i { width: 12px; height: 12px; border-radius: 3px; }
.gu-map__legend i.is-on { margin-left: 8px; background: var(--navy); }
.gu-map__list summary { min-height: 32px; cursor: pointer; }
.gu-map__list .chips { margin-top: 8px; }
</style>
