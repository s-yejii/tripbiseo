<script setup>
// 어디서 출발해요? (③): 여행 정보 단계 안에 바로 펼쳐요. 검색 · 현재 위치 · 내 주변 추천에서 골라 store.planOrigin에 담아요
// 서버: GET /api/places/locations, /locations/hubs, /locations/here
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { getLocationHere, getLocationHubs, searchLocations } from '@/api/trips'
import { useTripStore } from '@/stores/trip'
import { getPositionOnce } from '@/utils/position'
import { showToast } from '@/utils/toast'

const SEARCH_WAIT_MS = 300
const WALK_M_PER_MIN = 67 // 걷는 속도 (4km/h)
const WALK_LIMIT_M = 2000
const LINE_COLOR = { 1: '#0052A4', 2: '#00A84D', 3: '#EF7C1C', 4: '#00A5DE', 5: '#996CAC', 6: '#CD7C2F', 7: '#747F00', 8: '#E6186C', 9: '#BDB092' }

const store = useTripStore()
const keyword = ref('')
const found = ref([])
const hubs = ref([])
const hubsTitle = ref('')
const isHubFromGps = ref(false)
const isLoading = ref(false)
const isLocating = ref(false)
const error = ref('')
let position = null
let timer = null
let requestNo = 0 // 늦게 온 예전 검색 결과는 버리려고

onMounted(async () => {
  position = await getPositionOnce()
  loadHubs(position)
})
onUnmounted(() => clearTimeout(timer))

// 추천: 내 위치, 없으면 고른 지역 가운데 기준
async function loadHubs(pos) {
  const center = pos || store.planArea
  if (!center) return
  try {
    hubs.value = await getLocationHubs(center.lat, center.lng)
    isHubFromGps.value = !!pos
    hubsTitle.value = pos ? '내 주변' : store.planArea.name + ' 주변'
  } catch {
    hubs.value = [] // 추천은 없어도 검색으로 고를 수 있음
  }
}

const list = computed(() => (keyword.value.trim() ? found.value : hubs.value))
const listTitle = computed(() => (keyword.value.trim() ? '' : hubsTitle.value))

function isOn(item) {
  const origin = store.planOrigin
  return !!origin && origin.name === item.name && origin.lat === item.lat
}
function pick(item) {
  store.planOrigin = { name: item.name, address: item.address, lat: item.lat, lng: item.lng }
}

function search() {
  clearTimeout(timer)
  error.value = ''
  const word = keyword.value.trim()
  if (!word) {
    found.value = []
    isLoading.value = false
    return
  }
  isLoading.value = true
  timer = setTimeout(() => runSearch(word), SEARCH_WAIT_MS)
}
async function runSearch(word) {
  const no = ++requestNo
  try {
    const result = await searchLocations(word, position?.lat, position?.lng)
    if (no === requestNo) found.value = result
  } catch (e) {
    if (no !== requestNo) return
    found.value = []
    error.value = e.message
  }
  if (no === requestNo) isLoading.value = false
}

// 현재 위치에서 출발: GPS → 주소
async function useHere() {
  isLocating.value = true
  const pos = await getPositionOnce()
  if (!pos) {
    isLocating.value = false
    showToast('위치를 받지 못했어요. 위치 권한을 확인해 주세요')
    return
  }
  position = pos
  loadHubs(pos)
  try {
    pick(await getLocationHere(pos.lat, pos.lng))
  } catch (e) {
    showToast(e.message)
  }
  isLocating.value = false
}

function lineOf(item) {
  const match = /(\d)호선/.exec(item.category || '')
  return match ? LINE_COLOR[match[1]] : ''
}
function subText(item) {
  if (item.distance == null) return item.category || item.address || ''
  const distance = item.distance >= 1000 ? (item.distance / 1000).toFixed(1) + 'km' : item.distance + 'm'
  if (!isHubFromGps.value || item.distance > WALK_LIMIT_M) return (item.category ? item.category + ' · ' : '') + distance
  return '도보 ' + Math.max(1, Math.round(item.distance / WALK_M_PER_MIN)) + '분 · ' + distance
}
</script>

<template>
  <section class="card card--pad origin" aria-label="출발지">
    <div class="field">
      <label class="sr" for="origin-q">출발지 찾기</label>
      <div class="field__box"><i class="ic i-search subtle" aria-hidden="true"></i><input id="origin-q" v-model="keyword" type="search" placeholder="역·건물·주소 (예: 천호역 1번 출구)" autocomplete="off" @input="search" /></div>
    </div>
    <button type="button" class="btn btn--secondary btn--md btn--block" :disabled="isLocating" @click="useHere"><i class="ic i-locate" aria-hidden="true"></i>{{ isLocating ? '위치 찾는 중…' : '현재 위치에서 출발' }}</button>
    <p v-if="store.planOrigin" class="origin__picked t-cap"><i class="ic ic--s i-check" aria-hidden="true"></i>{{ store.planOrigin.name }}에서 출발</p>
    <h2 v-if="listTitle && list.length" class="t-strong" style="margin: 0">{{ listTitle }}</h2>
    <p v-if="isLoading" class="t-cap" style="margin: 0">찾는 중이에요</p>
    <p v-else-if="error" class="t-cap c-danger" style="margin: 0">{{ error }}</p>
    <p v-else-if="keyword.trim() && !found.length" class="t-cap" style="margin: 0">찾는 곳이 없어요</p>
    <div role="radiogroup" aria-label="출발지" class="origin__list">
      <button v-for="item in list" :key="item.name + item.lat" type="button" role="radio" :aria-checked="isOn(item)" class="origin__item" :class="{ 'is-on': isOn(item) }" @click="pick(item)">
        <span class="origin__ic" :style="lineOf(item) ? { background: lineOf(item), color: '#fff' } : {}"><i class="ic ic--s" :class="lineOf(item) ? 'i-subway' : 'i-pin'" aria-hidden="true"></i></span>
        <span class="origin__body"><b>{{ item.name }}</b><span class="tn">{{ subText(item) }}</span></span>
        <span class="radio"></span>
      </button>
    </div>
  </section>
</template>

<style>
.origin { display: flex; flex-direction: column; gap: 12px; }
.origin__picked { display: flex; align-items: center; gap: 4px; margin: 0; color: var(--navy); font-weight: 600; }
.origin__list { display: flex; flex-direction: column; gap: 6px; }
.origin__item { display: flex; align-items: center; gap: 10px; min-height: 56px; padding: 8px 12px; border: 1px solid var(--line); border-radius: var(--radius-lg); background: var(--surface-raised); font: inherit; color: var(--ink); text-align: left; cursor: pointer; }
.origin__item:hover { border-color: var(--navy); }
.origin__item.is-on { border-color: var(--navy); background: var(--navy-soft); }
.origin__item.is-on .radio { border: 7px solid var(--navy); }
.origin__ic { flex: none; width: 32px; height: 32px; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: var(--navy); background: var(--navy-soft); }
.origin__body { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.origin__body b { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; font-size: 15px; line-height: 20px; font-weight: 700; }
.origin__body span { font-size: 12px; line-height: 16px; color: var(--ink-muted); }
</style>
