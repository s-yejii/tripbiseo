<script setup>
// S06-b 장소 검색 · 시간 고정 (③): 지역 / 출발지 / 꼭 지킬 시간(예약) 고르기를 한 화면이 같이 해요
// 주소: /plan/search?mode=area | origin | fixed
//   area   : 어디로 가요? 서울 구·동 이름으로 찾기 → GET /api/places/areas (카카오)
//   origin : 어디서 출발해요? 역·출구·건물·주소로 찾기 → GET /api/places/locations, 현재 위치 → /api/places/locations/here
//   고른 값은 store.planArea · store.planOrigin 에 { 이름, 좌표 }로 담아요
//   fixed  : 꼭 지킬 시간 (아직 가짜 목록)
import { computed, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import BottomSheet from '@/components/common/BottomSheet.vue'
import { getLocationHere, searchAreas, searchLocations } from '@/api/trips'
import { useTripStore } from '@/stores/trip'
import { goBack } from '@/utils/back'
import { getPositionOnce } from '@/utils/position'
import { showToast } from '@/utils/toast'

const SEARCH_WAIT_MS = 300 // 글자를 다 칠 때까지 기다렸다가 찾기
const TYPE_TEXT = { gu: '구', dong: '동', spot: '동네' }
const HERE_CATEGORY = '현재 위치' // 서버(/locations/here)가 붙여 주는 분류

const route = useRoute()
const store = useTripStore()
const mode = ['area', 'origin', 'fixed'].includes(route.query.mode) ? route.query.mode : 'fixed'
const isKakao = mode !== 'fixed' // 지역·출발지는 카카오로 진짜 검색

// 가짜 데이터: 꼭 지킬 시간 검색 결과 (백엔드가 붙으면 장소 검색 API)
const fixedResults = [
  { name: '잔나비 콘서트', desc: '공연 · 잠실실내체육관 · 19:00 공연 · 18:00 입장', img: '/img/concert.svg', time: '18:30' },
  { name: '잔나비 콘서트 (일요일 회차)', desc: '공연 · 잠실실내체육관 · 18:00 공연', img: '/img/concert.svg', time: '17:30' },
  { name: '천호 아트홀 전시', desc: '전시 · 천호동 · 10:00–19:00', img: '/img/gallery.jpg', time: '15:00' },
  { name: '천호 40년 순댓국', desc: '노포·한식 · 천호역 · 07:00–21:00', img: '/img/euljiro.jpg', time: '12:00' },
]
const texts = {
  area: { title: '어디로 가요?', placeholder: '동·구 이름 (예: 성수동, 마포구, 홍대)', button: '이 지역으로', hint: '서울 안에서 동이나 구 이름으로 찾아요' },
  origin: { title: '어디서 출발해요?', placeholder: '역·건물·주소 (예: 천호역 1번 출구)', button: '여기서 출발', hint: '역 출구, 건물 이름, 도로명 주소로 정확히 찾아요' },
  fixed: { title: '꼭 지킬 시간 추가', placeholder: '예약한 곳 이름', button: '고정 일정으로 넣기', hint: '' },
}
const text = texts[mode]
const current = mode === 'area' ? store.planArea : mode === 'origin' ? store.planOrigin : null // 지금 골라 둔 값

const keyword = ref(mode === 'fixed' ? '천호' : '')
const found = ref([]) // 카카오 결과
const isLoading = ref(false)
const isLocating = ref(false)
const error = ref('')
const pickedIndex = ref(-1) // 카카오 결과에서 고른 칸
const fixedPickedName = ref(null)
const time = ref('')
let position = null // 출발지: 내 위치 (가까운 곳 거리 표시)
let timer = null
let requestNo = 0 // 늦게 온 예전 검색 결과는 버리려고

if (mode === 'origin') {
  getPositionOnce().then((pos) => (position = pos))
}
// 이미 골라 둔 값이 있으면 그 이름으로 다시 찾아서 골라 둔 상태로 보여 줘요
if (current) {
  keyword.value = current.name
  isLoading.value = true
  runSearch(current.name).then(() => {
    let index = found.value.findIndex((item) => item.lat === current.lat && item.lng === current.lng)
    if (index < 0) {
      found.value.unshift(current) // 검색으로 다시 안 나오면(현재 위치 등) 골라 둔 값을 맨 위에
      index = 0
    }
    pickedIndex.value = index
  })
}
onUnmounted(() => clearTimeout(timer))

// 꼭 지킬 시간 (가짜)
const fixedList = computed(() => {
  const word = keyword.value.trim()
  return fixedResults.filter((item) => !word || item.name.includes(word) || item.desc.includes(word))
})
const fixedPicked = computed(() => fixedList.value.find((item) => item.name === (fixedPickedName.value || fixedList.value[0]?.name)) || null)

const picked = computed(() => (isKakao ? found.value[pickedIndex.value] || null : fixedPicked.value))

function search(value) {
  keyword.value = value
  if (!isKakao) {
    fixedPickedName.value = null
    return
  }
  clearTimeout(timer)
  pickedIndex.value = -1
  error.value = ''
  if (!value.trim()) {
    found.value = []
    isLoading.value = false
    return
  }
  isLoading.value = true
  timer = setTimeout(() => runSearch(value.trim()), SEARCH_WAIT_MS)
}

async function runSearch(word) {
  const no = ++requestNo
  try {
    const list = mode === 'area' ? await searchAreas(word) : await searchLocations(word, position?.lat, position?.lng)
    if (no !== requestNo) return
    found.value = list
  } catch (e) {
    if (no !== requestNo) return
    found.value = []
    error.value = e.message
  }
  isLoading.value = false
}

// 현재 위치로 출발: GPS → 주소 → 맨 위에 넣고 골라 둠
async function useHere() {
  isLocating.value = true
  const pos = await getPositionOnce()
  if (!pos) {
    isLocating.value = false
    showToast('위치를 받지 못했어요. 브라우저 위치 권한을 확인해 주세요')
    return
  }
  position = pos
  try {
    const here = await getLocationHere(pos.lat, pos.lng)
    // 전에 넣은 '현재 위치' 줄은 빼고 새 위치 하나만 맨 위에 (여러 번 눌러도 한 줄)
    found.value = [here, ...found.value.filter((item) => item.category !== HERE_CATEGORY)]
    pick(0)
  } catch (e) {
    showToast(e.message)
  }
  isLocating.value = false
}

// 결과를 고르면 그 이름을 검색창에 (다시 찾지는 않고 목록은 그대로)
function pick(i) {
  pickedIndex.value = i
  keyword.value = found.value[i].name
}

function iconOf(item) {
  if (mode === 'area') return 'i-pin'
  if (item.category === HERE_CATEGORY) return 'i-locate'
  if (/지하철|전철/.test(item.category)) return 'i-subway'
  if (/버스/.test(item.category)) return 'i-bus'
  return 'i-pin'
}
function subOf(item) {
  if (mode === 'area') return item.fullName
  const distance = item.distance == null ? '' : ' · ' + (item.distance >= 1000 ? (item.distance / 1000).toFixed(1) + 'km' : item.distance + 'm')
  return (item.category ? item.category + ' · ' : '') + item.address + distance
}

function pickFixed(item) {
  fixedPickedName.value = item.name
  time.value = item.time || ''
}

function done() {
  const item = picked.value
  if (mode === 'area') {
    store.planArea = { name: item.name, fullName: item.fullName, type: item.type, lat: item.lat, lng: item.lng }
    showToast('지역을 ' + item.name + '(으)로 정했어요')
  } else if (mode === 'origin') {
    store.planOrigin = { name: item.name, address: item.address, category: item.category, lat: item.lat, lng: item.lng }
    showToast(item.name + '에서 출발해요')
  } else {
    showToast(item.name + ' ' + (time.value || item.time) + ' 고정했어요')
  }
  goBack('/plan/info')
}
</script>

<template>
  <div class="scr scr-PlaceSearch">
    <div class="app">
      <div class="dim" aria-hidden="true"></div>
      <BottomSheet role="dialog" aria-modal="true" style="top: 60px">
        <div class="sheet__head">
          <h1 class="h-title">{{ text.title }}</h1>
          <button type="button" class="iconbtn iconbtn--soft" aria-label="닫기" @click="goBack('/plan/info')"><i class="ic i-close" aria-hidden="true"></i></button>
        </div>
        <div class="field is-focus">
          <label class="sr" for="ps-q">{{ text.title }}</label>
          <div class="field__box">
            <i class="ic i-search subtle" aria-hidden="true"></i>
            <input id="ps-q" :value="keyword" type="search" :placeholder="text.placeholder" autocomplete="off" @input="search($event.target.value)" />
            <button v-if="keyword" type="button" class="iconbtn" aria-label="검색어 지우기" @click="search('')"><i class="ic i-close subtle" aria-hidden="true"></i></button>
          </div>
        </div>

        <!-- 지역·출발지: 카카오 검색 -->
        <template v-if="isKakao">
          <button v-if="mode === 'origin'" type="button" class="btn btn--secondary btn--md" :disabled="isLocating" @click="useHere">
            <i class="ic i-locate" aria-hidden="true"></i>{{ isLocating ? '위치 찾는 중…' : '현재 위치에서 출발' }}
          </button>

          <div role="radiogroup" :aria-label="text.title + ' 검색 결과'" class="ps-list">
            <div v-for="(item, i) in found" :key="item.name + item.lat + item.lng" class="opt ps-item" :class="{ 'is-on': pickedIndex === i }">
              <button type="button" role="radio" :aria-checked="pickedIndex === i" class="ps-hit" @click="pick(i)">
                <span class="ico-tile"><i class="ic" :class="iconOf(item)" aria-hidden="true"></i></span>
                <span class="opt__body">
                  <b class="t-sub">{{ item.name }} <span v-if="mode === 'area'" class="badge badge--neutral" style="vertical-align: 2px">{{ TYPE_TEXT[item.type] }}</span></b>
                  <span class="t-cap">{{ subOf(item) }}</span>
                </span>
                <span class="radio"></span>
              </button>
            </div>
            <p v-if="isLoading" class="t-cap ps-msg">'{{ keyword }}' 찾는 중…</p>
            <p v-else-if="error" class="t-cap ps-msg"><i class="ic ic--s i-warning c-warning" aria-hidden="true"></i> {{ error }}</p>
            <p v-else-if="keyword && !found.length" class="t-cap ps-msg">'{{ keyword }}' 결과가 없어요. {{ mode === 'area' ? '동이나 구 이름으로 찾아보세요 (서울만)' : '역 이름이나 도로명 주소로 찾아보세요' }}</p>
            <p v-else-if="!keyword" class="t-cap ps-msg">
              {{ text.hint }}<template v-if="current"><br />지금: <b>{{ current.name }}</b></template>
            </p>
          </div>
        </template>

        <!-- 꼭 지킬 시간 (가짜 목록) -->
        <div v-else role="radiogroup" aria-label="검색 결과" class="ps-list">
          <div v-for="item in fixedList" :key="item.name" class="opt ps-item" :class="{ 'is-on': picked === item }">
            <button type="button" role="radio" :aria-checked="picked === item" class="ps-hit" @click="pickFixed(item)">
              <img class="thumb" :src="item.img" alt="" />
              <span class="opt__body"><b class="t-sub">{{ item.name }}</b><span class="t-cap">{{ item.desc }}</span></span>
              <span class="radio"></span>
            </button>
            <template v-if="picked === item">
              <div style="display: flex; align-items: center; gap: 8px; padding: 0 12px">
                <label class="field__box" style="flex: 1; height: 44px" for="ps-time">
                  <i class="ic i-clock subtle" aria-hidden="true"></i>
                  <input id="ps-time" :value="time || item.time" type="time" style="font-variant-numeric: tabular-nums" @input="time = $event.target.value" />
                </label>
                <span class="badge badge--navy" style="height: 32px"><i class="ic ic--s i-lock" aria-hidden="true"></i>시간 고정</span>
              </div>
              <p class="t-cap" style="display: flex; gap: 4px; align-items: center; padding: 0 12px 12px"><i class="ic ic--s i-info c-navy" aria-hidden="true"></i>다른 일정이 이 시간에 맞춰 움직여요</p>
            </template>
          </div>
          <p v-if="!fixedList.length" class="t-cap ps-msg">'{{ keyword }}' 결과가 없어요. 다른 이름으로 찾아보세요.</p>
        </div>

        <button type="button" class="btn btn--primary btn--lg btn--block" :disabled="!picked" @click="done">{{ text.button }}</button>
      </BottomSheet>
    </div>
  </div>
</template>

<style>
.scr-PlaceSearch .ps-list{display:flex;flex-direction:column;gap:8px;overflow:auto;min-height:0;flex:1;margin:0 -4px;padding:0 4px 4px}
.scr-PlaceSearch .ps-item{flex-direction:column;align-items:stretch;gap:12px;padding:0}
.scr-PlaceSearch .ps-hit{display:flex;align-items:center;gap:12px;padding:12px;border:0;background:none;text-align:left;font:inherit;color:inherit;cursor:pointer;width:100%;border-radius:var(--radius-lg)}
.scr-PlaceSearch .sheet{bottom:0}
.scr-PlaceSearch .ps-msg{text-align:center;padding:24px 0;line-height:20px}
.scr-PlaceSearch input[type='search']::-webkit-search-cancel-button{-webkit-appearance:none;appearance:none} /* 브라우저 기본 X 숨김 (우리 지우기 버튼만) */
</style>
