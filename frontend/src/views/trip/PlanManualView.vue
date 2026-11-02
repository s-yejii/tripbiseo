<script setup>
// S09-a 지도에서 직접 고르기 (②): 검색·걸러 보기로 장소를 찾고, 골라서 일정 동선에 맞게 담아요
// 검색어를 쓰면 서버(GET /api/places/search)에서 우리 DB 장소를 찾고, 고른 곳은 여행 초안(store)에 담아요.
// 검색어가 없을 때는 시연용 가짜 장소를 그대로 보여 줘요.
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import BottomSheet from '@/components/common/BottomSheet.vue'
import { places } from '@/data/places'
import { concertTrip } from '@/data/trips'
import { searchPlaces } from '@/api/trips'
import { useTripStore } from '@/stores/trip'
import { imageFor } from '@/utils/mapView'
import { goBack } from '@/utils/back'
import { showToast } from '@/utils/toast'

const router = useRouter()
const store = useTripStore()
const keyword = ref('')

// ---------------------------------------------------------------- 서버 검색
const serverResults = ref([]) // 검색 결과 (장소 카드)
const pickedServer = ref([]) // 고른 서버 장소 [{ place, fixedTime }]
const searchError = ref('')
let searchTimer = null
const SEARCH_DELAY_MS = 300 // 글자를 칠 때마다 부르지 않고, 멈추면 한 번

watch(() => keyword.value, (word) => {
  clearTimeout(searchTimer)
  if (!word.trim()) {
    serverResults.value = []
    return
  }
  searchTimer = setTimeout(() => runSearch(word.trim()), SEARCH_DELAY_MS)
})

async function runSearch(word) {
  try {
    serverResults.value = await searchPlaces(word)
    searchError.value = ''
  } catch (e) {
    serverResults.value = []
    searchError.value = e.message
  }
}

function isServerPicked(place) {
  return pickedServer.value.some((item) => item.place.id === place.id && item.place.name === place.name)
}
function toggleServerPick(place) {
  const i = pickedServer.value.findIndex((item) => item.place.id === place.id && item.place.name === place.name)
  if (i > -1) pickedServer.value.splice(i, 1)
  else pickedServer.value.push({ place, fixedTime: null })
}
// 공연·예약처럼 시간이 정해진 곳: 시각을 받아 고정 일정으로
function pickAsFixed(place) {
  const time = window.prompt(place.name + '의 정해진 시각 (예: 18:30)', '18:30')
  if (!time || !/^([01]\d|2[0-3]):[0-5]\d$/.test(time)) return
  const found = pickedServer.value.find((item) => item.place.id === place.id && item.place.name === place.name)
  if (found) found.fixedTime = time
  else pickedServer.value.push({ place, fixedTime: time })
}

const filters = ref({ star: true }) // 켜진 거르기
const picked = ref([]) // 고른 장소 id들

const filterList = [
  { key: 'star', label: '★3.0 이상' },
  { key: '맛집', label: '맛집', words: ['점심', '저녁', '한식'] },
  { key: '카페', label: '카페', words: ['카페'] },
  { key: '쇼핑', label: '쇼핑', words: ['쇼핑'] },
  { key: '전시', label: '전시', words: ['전시', '갤러리'] },
  { key: '술집', label: '술집', words: ['술집'] },
]

// 일정에서 가장 가까운 장소까지 걸어서 몇 분 (지도 8px ≈ 걸어서 1분)
function walkFromPlan(id) {
  const p = places[id]
  let nearest = Infinity
  concertTrip.stops.forEach((stop) => {
    nearest = Math.min(nearest, Math.hypot(p.x - stop.x, p.y - stop.y))
  })
  return Math.max(2, Math.round(nearest / 8))
}

// 일정에 아직 없는 장소 → 검색어 → 종류 → 별점 순서로 걸러요
const inPlan = concertTrip.stops.map((stop) => stop.id)
const notInPlan = Object.keys(places).filter((id) => !inPlan.includes(id))

const beforeStar = computed(() => {
  let list = notInPlan
  const word = keyword.value.trim()
  if (word) list = list.filter((id) => (places[id].name + places[id].cat).includes(word))
  const types = filterList.filter((f) => f.words && filters.value[f.key])
  if (types.length) list = list.filter((id) => types.some((f) => f.words.some((w) => places[id].cat.includes(w))))
  return list
})
const candidates = computed(() => {
  const list = filters.value.star ? beforeStar.value.filter((id) => places[id].rate >= 3) : beforeStar.value
  return [...list].sort((a, b) => walkFromPlan(a) - walkFromPlan(b))
})
const hiddenCount = computed(() => (filters.value.star ? beforeStar.value.length - candidates.value.length : 0))

function toggleFilter(key) {
  filters.value[key] = !filters.value[key]
}
function togglePick(id) {
  const i = picked.value.indexOf(id)
  if (i > -1) picked.value.splice(i, 1)
  else picked.value.push(id)
}
function addToPlan() {
  for (const item of pickedServer.value) store.addDraftPlace(item.place, !!item.fixedTime, item.fixedTime)
  const count = picked.value.length + pickedServer.value.length
  pickedServer.value = []
  showToast(count + '곳을 동선에 맞춰 넣었어요')
  router.push('/plan/check')
}
const pickCount = computed(() => picked.value.length + pickedServer.value.length)
</script>

<template>
  <div class="scr scr-PlanManual">
    <div class="app">
      <div class="map" aria-hidden="true" style="bottom: 380px"><img class="base live" src="/img/mapB.svg" alt="" style="top: -186px" /></div>

      <!-- 이미 일정에 있는 곳: 작은 회색 핀 -->
      <span v-for="(stop, i) in concertTrip.stops" :key="stop.id" class="pin pin--done pm-small" :style="{ left: stop.x + 'px', top: stop.y + 'px' }" aria-hidden="true">
        <i v-if="stop.fixed" class="ic ic--s i-lock" style="width: 11px; height: 11px"></i>
        <template v-else>{{ i + 1 }}</template>
      </span>
      <!-- 담을 수 있는 곳 -->
      <button
        v-for="id in candidates"
        :key="id"
        type="button"
        class="pin"
        :class="picked.includes(id) ? 'pin--sel' : 'pin--ghost'"
        :style="{ left: places[id].x + 'px', top: places[id].y + 'px' }"
        :aria-label="places[id].name + (picked.includes(id) ? ' 선택됨' : ' 선택')"
        @click="togglePick(id)"
      >
        <i v-if="picked.includes(id)" class="ic ic--s i-check" aria-hidden="true" style="width: 14px; height: 14px"></i>
        <i v-else class="ic ic--s i-plus" aria-hidden="true" style="width: 13px; height: 13px"></i>
      </button>

      <header class="topbar topbar--float">
        <button type="button" class="iconbtn iconbtn--float" aria-label="뒤로가기" @click="goBack('/plan/check')"><i class="ic ic--l i-back" aria-hidden="true"></i></button>
        <label class="search" for="pm-q">
          <i class="ic i-search" aria-hidden="true"></i>
          <input id="pm-q" v-model="keyword" type="search" placeholder="장소 이름·종류 검색" autocomplete="off" class="pm-input" />
        </label>
      </header>
      <button type="button" class="iconbtn iconbtn--float" aria-label="내 위치" style="position: absolute; right: 16px; top: 404px" @click="showToast('지금 위치 근처로 옮겼어요')"><i class="ic i-locate" aria-hidden="true"></i></button>

      <BottomSheet class="sheet--peek" aria-label="검색 결과" style="top: 464px; padding: 8px 0 0; gap: 4px">
        <div class="quick" role="group" aria-label="걸러 보기" style="padding-top: 4px; padding-bottom: 4px">
          <button v-for="f in filterList" :key="f.key" type="button" class="chip chip--sm" :class="{ 'is-on': filters[f.key] }" :aria-pressed="!!filters[f.key]" @click="toggleFilter(f.key)">{{ f.label }}</button>
        </div>
        <p class="t-cap" style="padding: 0 16px; display: flex; justify-content: space-between">
          <span>내 일정 동선에서 가까운 순</span><span v-if="hiddenCount" class="tn">평점 3점 미만 {{ hiddenCount }}곳 숨김</span>
        </p>
        <div class="pm-list">
          <!-- 서버 검색 결과 (검색어가 있을 때) -->
          <template v-if="keyword.trim()">
            <div v-for="place in serverResults" :key="(place.id || '') + place.name" class="row">
              <span class="pm-hit">
                <img class="thumb thumb--lg" :src="imageFor(place.category)" alt="" />
                <span class="row__body">
                  <span class="row__title">{{ place.name }}</span>
                  <span class="row__meta tn"><template v-if="place.rating"><i class="ic ic--s i-star c-warning" aria-hidden="true"></i> {{ place.rating }} · </template>{{ place.category }}</span>
                </span>
              </span>
              <button type="button" class="btn btn--sm btn--secondary" @click="pickAsFixed(place)"><i class="ic ic--s i-lock" aria-hidden="true"></i>시간 고정</button>
              <button type="button" class="btn btn--sm" :class="isServerPicked(place) ? 'btn--primary' : 'btn--secondary'" :aria-pressed="isServerPicked(place)" @click="toggleServerPick(place)">
                {{ isServerPicked(place) ? '선택됨' : '선택' }}
              </button>
            </div>
            <p v-if="searchError" class="t-cap c-danger" style="padding: 24px 16px; text-align: center">{{ searchError }}</p>
            <p v-else-if="!serverResults.length" class="t-cap" style="padding: 24px 16px; text-align: center">검색 결과가 없어요. 다른 이름으로 찾아보세요.</p>
          </template>
          <template v-else>
          <div v-for="id in candidates" :key="id" class="row">
            <RouterLink to="/place" class="pm-hit">
              <img class="thumb thumb--lg" :src="places[id].img" alt="" />
              <span class="row__body">
                <span class="row__title">{{ places[id].name }}</span>
                <span class="row__meta tn"><i class="ic ic--s i-star c-warning" aria-hidden="true"></i> {{ places[id].rate }} · {{ places[id].cat }} · 일정에서 도보 {{ walkFromPlan(id) }}분</span>
              </span>
            </RouterLink>
            <button type="button" class="btn btn--sm" :class="picked.includes(id) ? 'btn--primary' : 'btn--secondary'" :aria-pressed="picked.includes(id)" @click="togglePick(id)">
              <template v-if="picked.includes(id)"><i class="ic ic--s i-check" aria-hidden="true"></i>선택됨</template>
              <template v-else>선택</template>
            </button>
          </div>
          <p v-if="!candidates.length" class="t-cap" style="padding: 24px 16px; text-align: center">조건에 맞는 곳이 없어요. 거르기를 풀어 보세요.</p>
          </template>
        </div>
      </BottomSheet>

      <div class="cta cta--white" style="gap: 12px">
        <div v-if="pickCount" class="pm-picks" aria-label="담은 장소">
          <span v-for="item in pickedServer" :key="'s' + item.place.name" class="pickchip">
            <img :src="imageFor(item.place.category)" alt="" /><b>{{ item.place.name }}{{ item.fixedTime ? ' ' + item.fixedTime : '' }}</b>
            <button type="button" class="iconbtn" :aria-label="item.place.name + ' 빼기'" style="width: 32px; height: 32px" @click="toggleServerPick(item.place)"><i class="ic ic--s i-close subtle" aria-hidden="true"></i></button>
          </span>
          <span v-for="id in picked" :key="id" class="pickchip">
            <img :src="places[id].img" alt="" /><b>{{ places[id].name }}</b>
            <button type="button" class="iconbtn" :aria-label="places[id].name + ' 빼기'" style="width: 32px; height: 32px" @click="togglePick(id)"><i class="ic ic--s i-close subtle" aria-hidden="true"></i></button>
          </span>
        </div>
        <button type="button" class="btn btn--primary btn--lg btn--block" :disabled="!pickCount" @click="addToPlan">
          {{ pickCount ? pickCount + '곳 일정에 담기' : '담을 곳을 골라 주세요' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style>
.scr-PlanManual .pm-small{width:22px;height:22px;margin:-11px 0 0 -11px;font-size:11px}
.scr-PlanManual .pm-input{border:0;outline:0;background:transparent;flex:1;min-width:0;font-size:16px}
.scr-PlanManual .pm-picks{display:flex;gap:8px;overflow-x:auto}
.scr-PlanManual .quick .chip{box-shadow:none;border-color:var(--line-strong);padding:0 10px}
.scr-PlanManual .quick{gap:5px}
.scr-PlanManual .quick .chip.is-on{border-color:var(--navy)}
.scr-PlanManual .pickchip{display:inline-flex;align-items:center;gap:6px;height:44px;padding:0 4px;border-radius:var(--radius-full);background:var(--surface-raised);border:1px solid var(--line);flex:none}
.scr-PlanManual .pickchip img{width:32px;height:32px;border-radius:50%;object-fit:cover}
.scr-PlanManual .pickchip b{font-size:14px;font-weight:600;max-width:90px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.scr-PlanManual .pm-list{flex:1;min-height:0;overflow-y:auto;padding-bottom:170px}
.scr-PlanManual .pm-list .row{padding:8px 16px}
.scr-PlanManual .pm-hit{display:flex;align-items:center;gap:12px;flex:1;min-width:0;color:inherit;text-decoration:none}
</style>
