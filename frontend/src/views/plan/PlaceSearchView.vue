<script setup>
// S06-b 장소 검색 · 시간 고정 (③): 지역 / 출발지 / 꼭 지킬 시간(예약) 고르기를 한 화면이 같이 해요
// 주소: /plan/search?mode=area | origin | fixed
//   area   : 어디로 가요? 서울 구·동 이름으로 찾기 → GET /api/places/areas (카카오)
//   origin : 어디서 출발해요? 역·출구·건물·주소로 찾기 → GET /api/places/locations, 현재 위치 → /api/places/locations/here
//   고른 값은 store.planArea · store.planOrigin 에 { 이름, 좌표 }로 담아요
//   fixed  : 꼭 지킬 시간: 예약한 곳을 같은 GET /api/places/locations (카카오)로 찾고 시각을 정해요
//            → store.planConditions.fixed 에 { name, time, day } + 직접 고르기 초안에 고정 칸
//            여러 날 여행이면 시각 위에서 며칠째(1일차·2일차…)인지 골라요 (11/04)
//            넣으면 화면을 닫지 않고 검색창을 비운 뒤 "넣은 꼭 지킬 시간" 목록을 보여 줘요 → 더 넣거나 "완료" (11/04)
import { computed, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import BottomSheet from '@/components/common/BottomSheet.vue'
import { getLocationHere, getLocationHubs, searchAreas, searchLocations, searchPlaces } from '@/api/trips'
import { useTripStore } from '@/stores/trip'
import { goBack } from '@/utils/back'
import { addDays, daysBetween, toMonthDay } from '@/utils/dates'
import { getPositionOnce } from '@/utils/position'
import { toShortDate } from '@/utils/tripView'
import { showToast } from '@/utils/toast'

const SEARCH_WAIT_MS = 300 // 글자를 다 칠 때까지 기다렸다가 찾기
const TYPE_TEXT = { gu: '구', dong: '동', spot: '동네' }
const HERE_CATEGORY = '현재 위치' // 서버(/locations/here)가 붙여 주는 분류
const WALK_M_PER_MIN = 67 // 걷는 속도 (4km/h)
const WALK_LIMIT_M = 2000
const LINE_COLOR = { 1: '#0052A4', 2: '#00A84D', 3: '#EF7C1C', 4: '#00A5DE', 5: '#996CAC', 6: '#CD7C2F', 7: '#747F00', 8: '#E6186C', 9: '#BDB092' } // 지하철 노선 색

const route = useRoute()
const store = useTripStore()
const mode = ['area', 'origin', 'fixed'].includes(route.query.mode) ? route.query.mode : 'fixed'
const TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/ // HH:mm (꼭 지킬 시각)
const texts = {
  area: { title: '어디로 가요?', placeholder: '동·구 이름 (예: 성수동, 마포구, 홍대)', button: '이 지역으로', hint: '서울 안에서 동이나 구 이름으로 찾아요', empty: '동이나 구 이름으로 찾아보세요 (서울만)' },
  origin: { title: '어디서 출발해요?', placeholder: '역·건물·주소 (예: 천호역 1번 출구)', button: '여기서 출발', hint: '역 출구, 건물 이름, 도로명 주소로 정확히 찾아요', empty: '역 이름이나 도로명 주소로 찾아보세요' },
  fixed: { title: '꼭 지킬 시간 추가', placeholder: '예약한 곳 이름 (예: 잠실실내체육관)', button: '고정 일정으로 넣기', hint: '공연장·식당·전시장 이름으로 찾고, 지켜야 할 시각을 정해요', empty: '가게·공연장 이름을 짧게 줄여서 찾아보세요' },
}
const text = texts[mode]
const current = mode === 'area' ? store.planArea : mode === 'origin' ? store.planOrigin : null // 지금 골라 둔 값

const keyword = ref('')
const found = ref([]) // 카카오 결과
const isLoading = ref(false)
const isLocating = ref(false)
const error = ref('')
const pickedIndex = ref(-1) // 카카오 결과에서 고른 칸
const hubs = ref([]) // 출발지 추천: 근처 지하철역·구청·시청
const pickedHub = ref(null)
const hubsTitle = ref('')
const isHubFromGps = ref(false) // 내 위치 기준이면 걷는 시간을 보여 줌
const time = ref('') // 꼭 지킬 시각 'HH:mm'
const fixedDay = ref(1) // 꼭 지킬 시간이 며칠째인지 (여러 날 여행에서만 고름)
// 여행 날짜 목록 [{ day: 1, label: '12/4(금)' }] (여행 정보 달력에서 고른 기간, 당일치기면 1개)
const dayList = computed(() => {
  const count = store.planEndDate ? daysBetween(store.planStartDate, store.planEndDate) + 1 : 1
  const list = []
  for (let i = 0; i < count; i++) {
    list.push({ day: i + 1, label: toShortDate(addDays(store.planStartDate, i)) })
  }
  return list
})
let position = null // 거리 기준: 출발지는 내 위치, 꼭 지킬 시간은 고른 여행 지역
let timer = null
let requestNo = 0 // 늦게 온 예전 검색 결과는 버리려고

if (mode === 'origin') {
  getPositionOnce().then((pos) => {
    position = pos
    loadHubs(pos)
  })
} else if (mode === 'fixed' && store.planArea) {
  position = { lat: store.planArea.lat, lng: store.planArea.lng } // 같은 이름 지점이 여럿일 때 여행 지역에서 가까운 곳을 고르게
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

// 출발지 추천 받기: 내 위치, 없으면 고른 지역 가운데 기준 (둘 다 없으면 안 보여 줌)
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
// 검색 결과가 있으면 추천은 숨김 (현재 위치 한 줄만 있으면 같이 보여 줌)
const showHubs = computed(() => mode === 'origin' && hubs.value.length > 0 && found.value.every((item) => item.category === HERE_CATEGORY))
function pickHub(hub) {
  pickedHub.value = hub
  pickedIndex.value = -1
}
function hubLine(hub) {
  const match = /(\d)호선/.exec(hub.category || '')
  return match ? LINE_COLOR[match[1]] : ''
}
function hubText(hub) {
  if (!isHubFromGps.value) return hub.category
  const distance = hub.distance >= 1000 ? (hub.distance / 1000).toFixed(1) + 'km' : hub.distance + 'm'
  if (hub.distance > WALK_LIMIT_M) return distance // 멀면 걷는 시간은 안 보여 줌
  return '도보 ' + Math.max(1, Math.round(hub.distance / WALK_M_PER_MIN)) + '분 · ' + distance
}

const picked = computed(() => pickedHub.value || found.value[pickedIndex.value] || null)
const isTimeOk = computed(() => TIME_PATTERN.test(time.value))
const fixedAdded = computed(() => store.planConditions?.fixed || []) // 지금까지 넣은 꼭 지킬 시간
const isFixedDone = computed(() => mode === 'fixed' && picked.value === null) // 꼭 지킬 시간에서 고른 곳이 없으면 버튼은 "완료"
// 꼭 지킬 시간은 시각까지 정해야 넣을 수 있어요 (고른 곳이 없으면 "완료"라서 늘 누를 수 있음)
const canDone = computed(() => isFixedDone.value || (picked.value !== null && (mode !== 'fixed' || isTimeOk.value)))
const buttonText = computed(() => (isFixedDone.value ? '완료' : text.button))

function search(value) {
  keyword.value = value
  clearTimeout(timer)
  pickedIndex.value = -1
  pickedHub.value = null
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
    const list = mode === 'area' ? await searchAreas(word) : mode === 'fixed' ? await searchFixed(word) : await searchLocations(word, position?.lat, position?.lng)
    if (no !== requestNo) return
    found.value = list
  } catch (e) {
    if (no !== requestNo) return
    found.value = []
    error.value = e.message
  }
  isLoading.value = false
}

// 예약한 곳 검색 (③ 11/09 상재 제보): 카카오에 없는 가게(예: 문래 골목집)도 찾게 우리 DB 장소를 먼저, 그 뒤에 카카오 결과
//   같은 이름은 한 번만. 띄어쓰기는 무시하고 비교해요 ("문래골목집"도 "문래 골목집"과 같은 이름)
const MAX_FIXED_RESULTS = 15
const squash = (text) => (text || '').replace(/\s+/g, '')
//   띄어쓰기 없이 친 말("문래골목집")은 DB 검색이 띄어 쓴 이름("문래 골목집")을 못 찾아서, 앞·뒤 3글자로도 한 번씩 더 찾고 띄어쓰기 빼고 비교
const PIECE_LENGTH = 3
function piecesOf(word) {
  if (/\s/.test(word) || word.length <= PIECE_LENGTH) return []
  return [word.slice(0, PIECE_LENGTH), word.slice(-PIECE_LENGTH)]
}
async function searchFixed(word) {
  const lat = position?.lat
  const lng = position?.lng
  const [kakao, ...ourLists] = await Promise.all([
    searchLocations(word, lat, lng).catch(() => []),
    ...[word, ...piecesOf(word)].map((piece) => searchPlaces(piece, lat, lng).catch(() => [])),
  ])
  const ours = ourLists.flat()
  const key = squash(word)
  const list = []
  const seen = new Set()
  for (const item of [...ours.filter((place) => squash(place.name).includes(key)), ...kakao]) {
    const name = squash(item.name)
    if (seen.has(name) || item.lat == null || item.lng == null) continue
    seen.add(name)
    list.push({ name: item.name, category: item.category, address: item.address, lat: item.lat, lng: item.lng })
  }
  return sortByNameMatch(list, key).slice(0, MAX_FIXED_RESULTS) // 정렬한 뒤에 자르기
}

// 이름이 검색어와 똑같은 곳 → 검색어로 시작하는 곳 → 나머지 순서로 (11/12 ①, 같은 단계 안은 원래 순서)
//   예) "서울역": 서울역 → 서울역사박물관 → 만천재 서울역점. key는 띄어쓰기를 뺀 검색어
function sortByNameMatch(list, key) {
  const same = []
  const startsWith = []
  const others = []
  for (const item of list) {
    const name = squash(item.name)
    if (name === key) {
      same.push(item)
    } else if (name.startsWith(key)) {
      startsWith.push(item)
    } else {
      others.push(item)
    }
  }
  return [...same, ...startsWith, ...others]
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
  loadHubs(pos)
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
  pickedHub.value = null
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
  const distanceFrom = mode === 'fixed' ? ' · 여행 지역에서 ' : ' · '
  const distance = item.distance == null ? '' : distanceFrom + (item.distance >= 1000 ? (item.distance / 1000).toFixed(1) + 'km' : item.distance + 'm')
  return (item.category ? item.category + ' · ' : '') + item.address + distance
}

// 꼭 지킬 시간 넣기: 일정 만들기 조건(fixed)에 { 이름, 시각 } + 직접 고르기 초안에 고정 칸 (예약 캡처와 같은 방식)
// 같은 곳을 다시 넣으면 시각만 바꿔요 (두 줄 안 생기게)
function addFixed(item) {
  const conditions = store.planConditions || {}
  const fixedList = []
  for (const fixed of conditions.fixed || []) {
    if (fixed.name !== item.name) fixedList.push(fixed)
  }
  fixedList.push({ name: item.name, time: time.value, day: fixedDay.value })
  store.planConditions = { ...conditions, fixed: fixedList }
  store.draft.stops = store.draft.stops.filter((stop) => !(stop.fixed && stop.name === item.name))
  const place = { name: item.name, category: item.category, address: item.address, lat: item.lat, lng: item.lng }
  store.addDraftPlace(place, true, time.value, fixedDay.value)
}

function done() {
  if (isFixedDone.value) {
    goBack('/plan/info')
    return
  }
  const item = picked.value
  if (mode === 'area') {
    store.planArea = { name: item.name, fullName: item.fullName, type: item.type, lat: item.lat, lng: item.lng }
    showToast('지역을 ' + item.name + '(으)로 정했어요')
  } else if (mode === 'origin') {
    store.planOrigin = { name: item.name, address: item.address, category: item.category, lat: item.lat, lng: item.lng }
    showToast(item.name + '에서 출발해요')
  } else {
    addFixed(item)
    showToast(item.name + ' ' + dayTextOf(fixedDay.value) + time.value + ' 고정했어요')
    search('') // 화면은 그대로 두고 검색창을 비움 → 넣은 목록이 보임
    time.value = ''
    return
  }
  goBack('/plan/info')
}

// 여러 날 여행이면 "2일차 " (당일치기면 빈 글)
function dayTextOf(day) {
  return dayList.value.length > 1 ? (day || 1) + '일차 ' : ''
}
// 일차 칸 글: 위 "2일차"(당일치기면 "당일"), 아래 "12/5"
function dayTileOf(day) {
  const index = (day || 1) - 1
  return { title: dayList.value.length > 1 ? index + 1 + '일차' : '당일', date: toMonthDay(addDays(store.planStartDate, index)) }
}

// 넣은 꼭 지킬 시간 빼기 (일정 만들기 조건과 직접 고르기 초안에서 같이)
function removeFixed(name) {
  store.planConditions = { ...store.planConditions, fixed: fixedAdded.value.filter((item) => item.name !== name) }
  store.draft.stops = store.draft.stops.filter((stop) => !(stop.fixed && stop.name === name))
}
</script>

<template>
  <div class="scr scr-PlaceSearch">
    <div class="app">
      <div class="dim" aria-hidden="true"></div>
      <BottomSheet closable role="dialog" aria-modal="true" style="top: 60px">
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

        <button v-if="mode === 'origin'" type="button" class="btn btn--secondary btn--md" :disabled="isLocating" @click="useHere">
          <i class="ic i-locate" aria-hidden="true"></i>{{ isLocating ? '위치 찾는 중…' : '현재 위치에서 출발' }}
        </button>

        <!-- 출발지 추천: 근처 지하철역·구청·시청 -->
        <section v-if="showHubs" class="ps-hubs" :aria-label="hubsTitle">
          <h2 class="t-strong" style="margin: 0">{{ hubsTitle }}</h2>
          <div role="radiogroup" :aria-label="hubsTitle" class="ps-hubs__list">
            <button v-for="hub in hubs" :key="hub.name + hub.lat" type="button" role="radio" :aria-checked="pickedHub === hub" class="ps-hub" :class="{ 'is-on': pickedHub === hub }" @click="pickHub(hub)">
              <span class="ps-hub__ic" :style="hubLine(hub) ? { background: hubLine(hub), color: '#fff' } : {}"><i class="ic ic--s" :class="hubLine(hub) ? 'i-subway' : 'i-flag'" aria-hidden="true"></i></span>
              <span class="ps-hub__body"><b>{{ hub.name }}</b><span class="tn">{{ hubText(hub) }}</span></span>
              <span class="radio"></span>
            </button>
          </div>
        </section>

        <!-- 카카오 검색 결과 (지역·출발지·꼭 지킬 시간 모두) -->
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
            <!-- 꼭 지킬 시간: 고른 곳 바로 아래에서 시각 정하기 -->
            <template v-if="mode === 'fixed' && pickedIndex === i">
              <div v-if="dayList.length > 1" class="chips ps-days" role="radiogroup" aria-label="며칠째 일정인지">
                <button v-for="d in dayList" :key="d.day" type="button" role="radio" class="chip chip--sm" :class="{ 'is-on': fixedDay === d.day }" :aria-checked="fixedDay === d.day" @click="fixedDay = d.day">
                  {{ d.day }}일차 · {{ d.label }}
                </button>
              </div>
              <div style="display: flex; align-items: center; gap: 8px; padding: 0 12px">
                <label class="field__box" style="flex: 1; height: 44px" for="ps-time">
                  <i class="ic i-clock subtle" aria-hidden="true"></i>
                  <input id="ps-time" v-model="time" type="time" aria-label="지킬 시각" style="font-variant-numeric: tabular-nums" />
                </label>
                <span class="badge badge--navy" style="height: 32px">시간 고정</span>
              </div>
              <p class="t-cap" style="display: flex; gap: 4px; align-items: center; padding: 0 12px 12px"><i class="ic ic--s i-info c-navy" aria-hidden="true"></i>{{ isTimeOk ? '다른 일정이 이 시간에 맞춰 움직여요' : '도착해야 하는 시각을 정해 주세요' }}</p>
            </template>
          </div>
          <p v-if="isLoading" class="t-cap ps-msg">'{{ keyword }}' 찾는 중…</p>
          <p v-else-if="error" class="t-cap ps-msg"><i class="ic ic--s i-warning c-warning" aria-hidden="true"></i> {{ error }}</p>
          <p v-else-if="keyword && !found.length" class="t-cap ps-msg">'{{ keyword }}' 결과가 없어요. {{ text.empty }}</p>
          <!-- 꼭 지킬 시간: 검색창이 비어 있으면 넣은 목록 -->
          <template v-if="mode === 'fixed' && !keyword && !isLoading">
            <p v-if="fixedAdded.length" class="t-cap">넣은 꼭 지킬 시간 {{ fixedAdded.length }}개 · 더 넣으려면 위에서 찾아요</p>
            <p v-else class="t-cap ps-msg">{{ text.hint }}</p>
            <div v-for="item in fixedAdded" :key="'added' + item.name" class="opt ps-item">
              <div class="ps-hit">
                <span class="day-tile"><b>{{ dayTileOf(item.day).title }}</b><span>{{ dayTileOf(item.day).date }}</span></span>
                <span class="opt__body"><b class="t-sub">{{ item.name }}</b><span class="t-cap tn">{{ item.time }} · 시간 고정</span></span>
                <button type="button" class="iconbtn" :aria-label="item.name + ' 빼기'" @click="removeFixed(item.name)"><i class="ic i-close muted" aria-hidden="true"></i></button>
              </div>
            </div>
          </template>
          <p v-else-if="!keyword && mode !== 'fixed'" class="t-cap ps-msg">
            {{ text.hint }}<template v-if="current"><br />지금: <b>{{ current.name }}</b></template>
          </p>
        </div>

        <button type="button" class="btn btn--primary btn--lg btn--block" :disabled="!canDone" @click="done">{{ buttonText }}</button>
      </BottomSheet>
    </div>
  </div>
</template>

<style>
.scr-PlaceSearch .ps-list{display:flex;flex-direction:column;gap:8px;overflow:auto;min-height:0;flex:1;margin:0 -4px;padding:0 4px 4px}
.scr-PlaceSearch .ps-item{flex-direction:column;align-items:stretch;gap:12px;padding:0}
.scr-PlaceSearch .ps-hit{display:flex;align-items:center;gap:12px;padding:12px;border:0;background:none;text-align:left;font:inherit;color:inherit;cursor:pointer;width:100%;border-radius:var(--radius-lg)}
.scr-PlaceSearch .sheet{bottom:0}
.scr-PlaceSearch .ps-days{padding:0 12px;flex-wrap:wrap}
.scr-PlaceSearch .ps-msg{text-align:center;padding:24px 0;line-height:20px}
.scr-PlaceSearch input[type='search']::-webkit-search-cancel-button{-webkit-appearance:none;appearance:none} /* 브라우저 기본 X 숨김 (우리 지우기 버튼만) */
.scr-PlaceSearch .ps-hubs{flex:none;display:flex;flex-direction:column;gap:8px}
.scr-PlaceSearch .ps-hubs__list{display:flex;flex-direction:column;gap:6px}
.scr-PlaceSearch .ps-hub{display:flex;align-items:center;gap:10px;min-height:56px;padding:8px 12px;border:1.5px solid var(--line);border-radius:14px;background:var(--surface-raised);font:inherit;color:var(--ink);text-align:left;cursor:pointer;transition:border-color .15s,background .15s}
.scr-PlaceSearch .ps-hub:hover{border-color:var(--navy)}
.scr-PlaceSearch .ps-hub.is-on{border-color:var(--navy);background:var(--navy-soft)}
.scr-PlaceSearch .ps-hub__ic{flex:none;width:32px;height:32px;border-radius:50%;display:flex;align-items:center;justify-content:center;color:var(--navy);background:var(--navy-soft)}
.scr-PlaceSearch .ps-hub__body{flex:1;min-width:0;display:flex;flex-direction:column}
.scr-PlaceSearch .ps-hub__body b{overflow:hidden;white-space:nowrap;text-overflow:ellipsis;font-size:15px;line-height:20px;font-weight:700}
.scr-PlaceSearch .ps-hub__body span{font-size:12px;line-height:16px;color:var(--ink-muted)}
</style>
