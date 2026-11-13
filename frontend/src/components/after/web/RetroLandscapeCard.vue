<script setup>
// ⑤ 웹 회고 오른쪽 칸 '여행 분석' 카드 (11/11, 11/12 정리)
//  - 회고를 보는 사람이 궁금한 것만: 어떤 길을 걸었나(구성 막대) · 어디서 얼마나 머물렀나(계획한 곳/계획 밖)
//  - 구간 목록은 접어 두고 누르면 펼침. 일조·가시권·숲 자료 설명은 뺌 (가시권은 지도 위 버튼으로)
// 분석·지도 그리기는 부모(WebRetroView)가 하고, 여기는 보여 주기만 해요
import { computed, ref } from 'vue'
import { toDuration } from '@/utils/time'
import { compositionOf } from './landscape/analyze'

const props = defineProps({
  landscape: { type: Object, required: true }, // { status: idle|running|done|error, done, total, items, message }
  isOn: { type: Boolean, default: false },      // 지도에 구간 색이 켜져 있는지
  day: { type: Number, default: 0 },            // 고른 날 (0 = 전체)
  clusters: { type: Object, required: true },   // { isOn, stays: [{ id, name, isPlanned, minutes, from }] } DBSCAN으로 찾은 머문 곳
})
const emit = defineEmits(['toggle', 'focus', 'toggle-clusters', 'focus-cluster'])

const MAX_ROWS = 40 // 구간 목록은 이만큼까지만 (길면 화면이 너무 길어짐)
const isListOpen = ref(false) // 구간 목록 펼침 (처음엔 접힘)

const shownItems = computed(() => {
  const list = []
  for (const item of props.landscape.items || []) {
    if (props.day === 0 || item.segment.day === props.day) list.push(item)
  }
  return list
})

const composition = computed(() => compositionOf(props.landscape.items || [], props.day))

// 계획 밖에 머문 곳 수 (머문 곳 제목 옆에 한 줄로)
const unplannedCount = computed(() => {
  let count = 0
  for (const stay of props.clusters.stays) {
    if (!stay.isPlanned) count++
  }
  return count
})

// "1일차 13:38" (여행 시작부터 몇 분째 → 날짜·시각)
function timeText(minutes) {
  const day = Math.floor(minutes / 1440) + 1
  const inDay = minutes % 1440
  return day + '일차 ' + String(Math.floor(inDay / 60)).padStart(2, '0') + ':' + String(inDay % 60).padStart(2, '0')
}

function itemTitle(item) {
  if (item.segment.kind === 'stay') return (item.segment.name || '머문 곳') + ' · ' + toDuration(item.segment.minutes)
  return item.segment.lengthM ? Math.round(item.segment.lengthM) + 'm 이동 · ' + toDuration(Math.max(1, item.segment.minutes)) : '이동'
}
</script>

<template>
  <section class="rl">
    <div class="rl-head">
      <h2>여행 분석</h2>
      <button type="button" class="rl-switch" :class="{ 'is-on': isOn }" role="switch" :aria-checked="isOn" :disabled="landscape.status !== 'done'" @click="emit('toggle')">
        {{ isOn ? '지도에 켜짐' : '지도에 보기' }}
      </button>
    </div>

    <p v-if="landscape.status === 'running'" class="rl-state">걸은 길을 살펴보는 중… {{ landscape.done }} / {{ landscape.total }}</p>
    <p v-else-if="landscape.status === 'error'" class="rl-state">분석하지 못했어요 ({{ landscape.message }})</p>
    <p v-else-if="landscape.status === 'done' && shownItems.length === 0" class="rl-state">GPS 기록이 없어 살펴볼 길이 없어요.</p>

    <template v-if="landscape.status === 'done' && shownItems.length > 0">
      <h3 class="rl-sub rl-sub--first">어떤 길이었나 <small>보낸 시간 기준</small></h3>
      <div class="rl-bar" role="img" :aria-label="'걸은 길 구성: ' + composition.map((part) => part.type.name + ' ' + part.percent + '%').join(', ')">
        <i v-for="part in composition" :key="part.type.id" :style="{ width: part.percent + '%', background: part.type.color }"></i>
      </div>
      <ul class="rl-legend">
        <li v-for="part in composition" :key="part.type.id"><i :style="{ background: part.type.color }"></i>{{ part.type.name }} <b>{{ part.percent }}%</b></li>
      </ul>
    </template>

    <!-- 머문 곳 (DBSCAN 군집): 계획한 곳인지, 계획 밖에 들른 곳인지 -->
    <div class="rl-head rl-head--sub">
      <h3 class="rl-sub">머문 곳 {{ clusters.stays.length }}곳<small v-if="unplannedCount > 0"> 계획 밖 {{ unplannedCount }}곳</small></h3>
      <button type="button" class="rl-switch" :class="{ 'is-on': clusters.isOn }" role="switch" :aria-checked="clusters.isOn" :disabled="clusters.stays.length === 0" @click="emit('toggle-clusters')">{{ clusters.isOn ? '지도에 켜짐' : '지도에 보기' }}</button>
    </div>
    <p v-if="clusters.stays.length === 0" class="rl-state">머문 곳을 아직 못 찾았어요.</p>
    <button v-for="stay in clusters.stays" :key="stay.id" type="button" class="rl-row" @click="emit('focus-cluster', stay)">
      <span class="rl-row__chip" :style="{ background: stay.isPlanned ? '#1F3A68' : '#E8603F' }">{{ stay.isPlanned ? '계획한 곳' : '계획 밖' }}</span>
      <span class="rl-row__main">
        <b>{{ stay.name || '이름 없는 곳' }}</b>
        <small>{{ timeText(stay.from) }}부터 {{ toDuration(stay.minutes) }}</small>
      </span>
    </button>

    <!-- 구간 목록: 처음엔 접어 둠 -->
    <template v-if="landscape.status === 'done' && shownItems.length > 0">
      <button type="button" class="rl-more" :aria-expanded="isListOpen" @click="isListOpen = !isListOpen">구간 {{ shownItems.length }}개 자세히 {{ isListOpen ? '▴' : '▾' }}</button>
      <template v-if="isListOpen">
        <button v-for="item in shownItems.slice(0, MAX_ROWS)" :key="item.segment.id" type="button" class="rl-row" @click="emit('focus', item)">
          <span class="rl-row__chip" :style="{ background: item.type.color }">{{ item.type.name }}</span>
          <span class="rl-row__main">
            <b>{{ itemTitle(item) }}</b>
            <small>{{ timeText(item.segment.from) }}</small>
          </span>
        </button>
        <p v-if="shownItems.length > MAX_ROWS" class="rl-hint">나머지 {{ shownItems.length - MAX_ROWS }}개는 지도에서 눌러 보세요.</p>
      </template>
    </template>

  </section>
</template>

<style scoped>
.rl h2 { margin: 0; font-size: 16px; }
.rl-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 10px; }
.rl-switch { padding: 4px 12px; border: 1px solid var(--navy); border-radius: var(--radius-full); background: #fff; color: var(--navy); font: inherit; font-size: 12px; font-weight: 700; cursor: pointer; white-space: nowrap; }
.rl-switch.is-on { background: var(--navy); color: var(--on-navy); }
.rl-switch:disabled { opacity: 0.4; cursor: default; }
.rl-state { margin: 0; color: var(--ink-muted); font-size: 13px; }
.rl-bar { display: flex; height: 14px; overflow: hidden; border-radius: var(--radius-full); background: var(--surface); }
.rl-bar i { display: block; height: 100%; }
.rl-legend { display: flex; flex-wrap: wrap; gap: 4px 12px; margin: 8px 0 0; padding: 0; list-style: none; font-size: 12px; }
.rl-legend i { display: inline-block; width: 10px; height: 10px; margin-right: 4px; border-radius: 2px; vertical-align: -1px; }
.rl-sub { margin: 14px 0 6px; font-size: 13px; }
.rl-sub small { margin-left: 4px; font-weight: 400; color: var(--ink-muted); }
.rl-row { display: flex; align-items: center; gap: 8px; width: 100%; padding: 6px 0; border: 0; border-top: 1px solid var(--line); background: none; color: var(--ink); font: inherit; text-align: left; cursor: pointer; }
.rl-row:hover { background: var(--surface); }
.rl-row__chip { flex: none; width: 92px; padding: 2px 0; border-radius: var(--radius-full); color: #fff; font-size: 11px; font-weight: 700; text-align: center; }
.rl-row__main { display: flex; flex-direction: column; flex: 1; min-width: 0; font-size: 13px; }
.rl-row__main small { overflow: hidden; color: var(--ink-muted); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.rl-hint { margin: 8px 0 0; color: var(--ink-muted); font-size: 11px; line-height: 16px; }
.rl-head--sub { margin: 16px 0 6px; }
.rl-head--sub .rl-sub { margin: 0; }
.rl-sub--first { margin-top: 0; }
.rl-more { width: 100%; margin-top: 12px; padding: 6px 0; border: 0; border-top: 1px solid var(--line); background: none; color: var(--ink-muted); font: inherit; font-size: 12px; text-align: left; cursor: pointer; }
</style>
