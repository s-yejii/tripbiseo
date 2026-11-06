<script setup>
// S09-a 지도에서 직접 고르기 (②): 지도가 먼저예요. 칩·검색·링크로 실제 장소를 찾아 핀을 누르고 담아요
// 담을 때마다 일정 점검(POST /api/plans/check)으로 구간 시간·도착 시각·영업시간 문제를 다시 계산해요
// [동선 정리]는 거리 기준 (AI 아님), [AI로 채우기]는 고른 곳을 꼭 갈 곳으로 두고 남은 시간을 채워요 → 일정 만들기 흐름
import ChipScroller from '@/components/common/ChipScroller.vue'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BottomSheet from '@/components/common/BottomSheet.vue'
import KakaoMap from '@/components/common/KakaoMap.vue'
import TopBar from '@/components/common/TopBar.vue'
import TripTimeline from '@/components/trip/TripTimeline.vue'
import { checkPlan, findLinkPlaces, generatePlan, getManualPlaces, getPlaceDetail, getPlacePhotos, getRouteOrder, searchPlaces } from '@/api/trips'
import { useTripStore } from '@/stores/trip'
import { addDays, daysBetween } from '@/utils/dates'
import { dayStopsOf, retime, setDayStops } from '@/utils/draftEdit'
import { toShortDate, toViewTrip } from '@/utils/tripView'
import { showToast } from '@/utils/toast'
import { useStopPhotos } from '@/utils/useStopPhotos'
import { toMinutes, toTime } from '@/utils/time'
import { modes } from '@/data/places'

const KINDS = [
  { code: 'food', label: '맛집' },
  { code: 'cafe', label: '카페' },
  { code: 'bar', label: '술집' },
  { code: 'culture', label: '볼거리' },
  { code: 'tour', label: '들를 곳' },
]
const RESULT_MAX = 30 // 지도에 보여 줄 장소 수
const LIST_MAX = 5 // 시트에 보여 줄 장소 수
const AREA_MAX = 3 // 칩으로 찾을 때 살펴볼 구 수
const SEARCH_DELAY_MS = 300
const DEFAULT_START = '10:00'
const MAP_PADDING = { top: 110, right: 40, bottom: 520, left: 40 }
const SEOUL_CENTER = { lat: 37.5665, lng: 126.978 }

const route = useRoute()
const router = useRouter()
const store = useTripStore()

const day = ref(Number(route.query.day || 1))
const check = ref(null) // 점검 결과 (그날)
const kind = ref('food')
const keyword = ref('')
const results = ref([]) // 찾은 장소 카드
const isLoading = ref(false)
const message = ref('')
const picked = ref(null) // 핀을 눌러 본 장소
const pickedDetail = ref(null) // { rating, reviewCount, hours, photo }
const pane = ref('find') // 시트 내용: find 장소 찾기 / plan 내 일정
const isLinkOpen = ref(false)
const linkUrl = ref('')
const isLinkLoading = ref(false)
const fitKey = ref('') // 바뀌면 지도를 다시 맞춤
let searchTimer = null

const startTime = computed(() => store.planStartTime || DEFAULT_START)
const dayCount = computed(() => {
  if (!store.draft.startDate) return 1
  return daysBetween(store.draft.startDate, store.draft.endDate || store.draft.startDate) + 1
})
const dayList = computed(() => {
  const list = []
  for (let d = 1; d <= dayCount.value; d++) list.push({ day: d, label: toShortDate(addDays(store.draft.startDate, d - 1)) })
  return list
})
const view = computed(() => {
  const trip = toViewTrip(store.draft, check.value, day.value)
  const names = store.draft.stops.filter((stop) => stop.aiFilled).map((stop) => stop.name)
  // AI가 넣은 칸 표시 + 칸 사진 (홈 카드와 같은 함수)
  return { ...trip, stops: trip.stops.map((stop) => ({ ...stop, isAi: names.includes(stop.name), photo: stopPhotos.value[stop.name] || null })) }
})
const dayStops = computed(() => dayStopsOf(store.draft, day.value))
const stopPhotos = useStopPhotos(() => dayStops.value)
const addedIds = computed(() => new Set(store.draft.stops.map((stop) => stop.placeId || stop.name)))
const areaNames = computed(() => store.planAreas.map((area) => area.name))

// 찾는 기준 구들: 고른 구, 없으면(일정 점검에서 장소 추가로 왔을 때) 담은 곳 근처, 그것도 없으면 서울 가운데
const baseAreas = computed(() => {
  if (store.planAreas.length) return store.planAreas.slice(0, AREA_MAX)
  const first = store.draft.stops.find((stop) => stop.lat != null)
  return [first ? { lat: first.lat, lng: first.lng } : SEOUL_CENTER]
})

function isAdded(place) {
  return addedIds.value.has(place.id || place.name)
}

const mapPins = computed(() => {
  const pins = view.value.stops.filter((stop) => stop.lat != null).map((stop, i) => ({
    key: 'stop-' + i,
    lat: stop.lat,
    lng: stop.lng,
    text: i + 1,
    icon: stop.fixed ? 'i-lock' : '',
    cls: (stop.fixed ? ' pin--fixed' : '') + (stop.warn ? ' pin--warn' : ''),
    tag: stop.isAi ? 'AI' : '',
    label: i + 1 + ' ' + stop.name,
  }))
  if (pane.value === 'plan') return pins // 내 일정을 볼 때는 찾은 장소 핀을 숨겨서 일정 번호가 잘 보이게
  for (const place of results.value) {
    if (place.lat == null || isAdded(place)) continue
    const isOn = picked.value && picked.value.name === place.name
    pins.push({ key: 'res-' + (place.id || place.name), place, lat: place.lat, lng: place.lng, route: false, icon: 'i-pin', cls: ' pin--res' + (isOn ? ' pin--sel' : ''), tag: isOn ? place.name : '', label: place.name })
  }
  return pins
})
const mapPaths = computed(() => (view.value.legs || []).filter((leg) => leg.coords && leg.coords.length > 1))

onMounted(() => {
  if (store.draft.stops.length) runCheck()
  loadKind()
})

// 점검: 시각이 빈 칸은 서버가 앞 일정에 이어서 채워 줘요
async function runCheck() {
  if (!dayStops.value.length) {
    check.value = null
    return
  }
  try {
    check.value = await checkPlan(store.draft, day.value)
    for (const checked of check.value.stops) {
      const stop = store.draft.stops.find((item) => item.name === checked.name && (item.day || 1) === checked.day)
      if (stop && !stop.time) stop.time = checked.time
    }
  } catch (e) {
    showToast(e.message)
  }
}

// 고른 구 안의 장소만 (카카오·DB 반경 결과에 옆 구가 섞여서)
function keepInAreas(list) {
  if (!areaNames.value.length) return list
  return list.filter((place) => !place.address || areaNames.value.some((name) => place.address.includes(name)))
}

async function loadKind() {
  isLoading.value = true
  message.value = ''
  picked.value = null
  try {
    const lists = await Promise.all(baseAreas.value.map((area) => getManualPlaces(area.lat, area.lng, kind.value)))
    const seen = new Set()
    const merged = []
    for (const place of keepInAreas(lists.flat())) {
      if (seen.has(place.id || place.name)) continue
      seen.add(place.id || place.name)
      merged.push(place)
    }
    results.value = merged.slice(0, RESULT_MAX)
    if (!results.value.length) message.value = '이 지역에 장소가 없어요'
  } catch (e) {
    results.value = []
    message.value = e.message
  }
  isLoading.value = false
  fitKey.value = 'kind-' + kind.value
}

function pickKind(code) {
  keyword.value = ''
  kind.value = code
  loadKind()
}

watch(keyword, (word) => {
  clearTimeout(searchTimer)
  if (!word.trim()) return loadKind()
  searchTimer = setTimeout(() => runSearch(word.trim()), SEARCH_DELAY_MS)
})
async function runSearch(word) {
  message.value = ''
  picked.value = null
  try {
    const base = baseAreas.value[0]
    results.value = (await searchPlaces(word, base.lat, base.lng)).slice(0, RESULT_MAX)
    if (!results.value.length) message.value = '찾는 곳이 없어요'
  } catch (e) {
    results.value = []
    message.value = e.message
  }
  fitKey.value = 'search-' + word
}

// 링크로 담기: 찾은 장소를 지도에 보여 줘요
async function findByLink() {
  const url = linkUrl.value.trim()
  if (!url) return
  isLinkLoading.value = true
  message.value = ''
  try {
    const found = await findLinkPlaces(url, null)
    results.value = found.places
    if (!found.places.length) message.value = '장소를 못 찾았어요'
    isLinkOpen.value = false
    linkUrl.value = ''
    fitKey.value = 'link-' + url
  } catch (e) {
    message.value = e.message
  }
  isLinkLoading.value = false
}

// 핀·목록을 누르면 작은 카드: 평점·리뷰·오늘 영업시간·진짜 사진 (없으면 안 보여 줌)
async function pickPlace(place) {
  picked.value = place
  pickedDetail.value = null
  if (!place.id) return
  try {
    const [detail, photos] = await Promise.all([getPlaceDetail(place.id), getPlacePhotos([place.id])])
    if (picked.value !== place) return
    const today = (detail.hours || []).find((hour) => hour.isToday || hour.today)
    pickedDetail.value = { useTime: today ? today.time || today.text : detail.useTime, photo: photos[0] ? photos[0].imageUrl : detail.imageUrl }
  } catch {
    pickedDetail.value = null // 카카오에서 온 곳은 상세가 없어요
  }
}
function onPickPin(pin) {
  if (pin.place) pickPlace(pin.place)
}

async function addPlace(place) {
  const isFirst = !dayStops.value.length
  store.addDraftPlace(place, false, isFirst ? startTime.value : null, day.value)
  picked.value = null
  showToast(place.name + ' 담음')
  await runCheck()
}

function pickDay(d) {
  if (d === day.value) return
  day.value = d
  check.value = null
  router.replace('/plan/manual?day=' + d)
  runCheck()
}

// 목록에서 끌어 순서 바꾸기 → 시각을 비우고 다시 점검
function reorder(order) {
  const list = dayStops.value
  const first = list.length ? list[0].time : startTime.value
  const moved = order.map((i) => list[i])
  retime(moved, first)
  setDayStops(store.draft, day.value, moved)
  runCheck()
}

async function changeMode(i, mode) {
  const list = dayStops.value
  list[i].mode = mode
  retime(list, list[0].time)
  await runCheck()
  showToast(modes[mode].label + '(으)로 바꿨어요')
}

function editStop(i) {
  const back = encodeURIComponent('/plan/manual?day=' + day.value)
  router.push(`/plan/edit/stop?day=${day.value}&idx=${i}&back=${back}`)
}

// 동선 정리: 서버가 실제 좌표로 순서를 정해요 (AI 일정과 같은 함수). 고정 칸은 그 자리, 식사는 식사 때 안에서
async function sortRoute() {
  const list = dayStops.value
  if (list.length < 3) return showToast('세 곳부터 정리할 수 있어요')
  const origin = day.value === 1 ? store.planOrigin : null
  try {
    const { order } = await getRouteOrder(list, addDays(store.draft.startDate, day.value - 1), origin?.lat ?? null, origin?.lng ?? null)
    const first = list[0].time || startTime.value
    const moved = order.map((index) => list[index])
    retime(moved, first)
    setDayStops(store.draft, day.value, moved)
    showToast('가까운 순서로 정리했어요')
    runCheck()
  } catch {
    showToast('동선을 정리하지 못했어요. 잠시 뒤 다시 해 주세요')
  }
}

// 나머지 AI가 채우기: 담은 곳은 순서·시각 그대로, 빈 시간만 일정 만들기와 같은 규칙으로 채워요. 화면을 안 떠나고 바로 지도·목록에 보여 줘요
// 다시 누르면(다시 추천) AI가 넣은 곳만 빼고, 그곳들을 피해서 다시 골라요
const aiNames = computed(() => store.draft.stops.filter((stop) => stop.aiFilled).map((stop) => stop.name))
const isFilling = ref(false)
const isSlowFill = ref(false) // 3초 넘게 걸리면 안내를 보여 줘요
const SLOW_FILL_MS = 3000
const DEFAULT_END_MIN = 20 * 60
const MIN_SPAN_MIN = 120
const LAST_END_MIN = 24 * 60

function endTimeOf(start) {
  const startMin = toMinutes(start)
  return toTime(Math.min(LAST_END_MIN, Math.max(DEFAULT_END_MIN, startMin + MIN_SPAN_MIN)))
}
function makeFillConditions(kept, avoidNames) {
  const first = store.planAreas[0] || {}
  const near = kept.find((stop) => stop.lat != null)
  const firstTime = kept.length && kept[0].time && kept[0].time < startTime.value ? kept[0].time : startTime.value
  return {
    title: store.draft.title,
    region: first.name ? '서울 ' + first.name : null,
    areaLat: first.lat ?? near?.lat ?? null,
    areaLng: first.lng ?? near?.lng ?? null,
    areaType: 'gu',
    areas: store.planAreas.map((area) => ({ name: area.name, lat: area.lat, lng: area.lng, dongs: [] })),
    startDate: store.draft.startDate,
    endDate: store.draft.endDate,
    startTime: firstTime,
    endTime: store.planToTime || endTimeOf(firstTime),
    people: store.draft.people,
    transports: store.draft.transports,
    kinds: [],
    tags: [],
    keptStops: kept,
    avoidVisit: avoidNames,
    originLat: store.planOrigin?.lat ?? null,
    originLng: store.planOrigin?.lng ?? null,
  }
}
async function fillWithAi() {
  if (isFilling.value) return
  isFilling.value = true
  const slowTimer = setTimeout(() => {
    isSlowFill.value = true
  }, SLOW_FILL_MS)
  try {
    await runCheck() // 담은 곳의 빈 시각을 먼저 채워요
    const kept = store.draft.stops.filter((stop) => !stop.aiFilled)
    const conditions = makeFillConditions(kept.map((stop) => ({ ...stop })), aiNames.value)
    const result = await generatePlan(conditions, [], null, null)
    const keptNames = kept.map((stop) => stop.name)
    store.draft.stops = result.stops.map((stop) => (keptNames.includes(stop.name) ? kept.find((item) => item.name === stop.name) : { ...stop, aiFilled: true }))
    pane.value = 'plan'
    fitKey.value = 'ai-' + Date.now()
    for (const note of result.notes || []) showToast(note.text)
    await runCheck()
  } catch (e) {
    showToast(e.message || 'AI가 고르지 못했어요', { actionText: '다시 시도', action: fillWithAi })
  } finally {
    clearTimeout(slowTimer)
    isFilling.value = false
    isSlowFill.value = false
  }
}
// AI가 넣은 곳 하나 빼기
function removeAiStop(i) {
  const target = dayStops.value[i]
  store.draft.stops = store.draft.stops.filter((stop) => stop !== target)
  runCheck()
}

function goCheck() {
  router.push('/plan/check?day=' + day.value)
}
</script>

<template>
  <div class="scr scr-PlanManual">
    <div class="app">
      <KakaoMap :pins="mapPins" :paths="mapPaths" :show-route="!mapPaths.length" :padding="MAP_PADDING" :fit-key="fitKey" @pick="onPickPin" />
      <TopBar float back="/plan/manual/start" />

      <!-- 핀을 누르면 뜨는 작은 카드 -->
      <section v-if="picked" class="pm-card" aria-label="고른 장소">
        <img v-if="pickedDetail && pickedDetail.photo" class="thumb" :src="pickedDetail.photo" alt="" />
        <span class="pm-card__body">
          <b class="t-strong">{{ picked.name }}</b>
          <span v-if="picked.rating != null" class="t-cap tn"><i class="ic ic--s i-star" aria-hidden="true"></i> {{ picked.rating }}<template v-if="picked.reviewCount"> · 리뷰 {{ picked.reviewCount.toLocaleString() }}</template></span>
          <span v-if="pickedDetail && pickedDetail.useTime" class="t-cap pm-card__time">{{ pickedDetail.useTime }}</span>
        </span>
        <button type="button" class="btn btn--primary btn--sm" style="height: 44px" :disabled="isAdded(picked)" @click="addPlace(picked)">{{ isAdded(picked) ? '담김' : '담기' }}</button>
      </section>

      <BottomSheet class="sheet--peek pm-sheet" aria-label="장소 찾기와 내 일정">
        <div class="sheet__head pm-head">
          <h1 class="h-title">{{ store.draft.title }}</h1>
        </div>
        <div class="pm-tabs" role="tablist" aria-label="찾기와 내 일정">
          <button type="button" role="tab" :aria-selected="pane === 'find'" :class="{ 'is-on': pane === 'find' }" @click="pane = 'find'">장소 찾기</button>
          <button type="button" role="tab" :aria-selected="pane === 'plan'" :class="{ 'is-on': pane === 'plan' }" @click="pane = 'plan'">내 일정 <span class="tn">{{ store.draft.stops.length }}</span></button>
        </div>
        <div v-if="pane === 'find'" class="pm-scroll">
          <div class="pm-search">
            <div class="field" style="flex: 1; min-width: 0">
              <label for="pm-q" class="sr">장소 검색</label>
              <div class="field__box"><i class="ic i-search subtle" aria-hidden="true"></i><input id="pm-q" v-model="keyword" type="search" placeholder="장소 이름 검색" autocomplete="off" /></div>
            </div>
            <button type="button" class="iconbtn iconbtn--soft" aria-label="링크로 담기" :aria-expanded="isLinkOpen" @click="isLinkOpen = !isLinkOpen"><i class="ic i-link" aria-hidden="true"></i></button>
          </div>
          <form v-if="isLinkOpen" class="pm-search" @submit.prevent="findByLink">
            <div class="field" style="flex: 1; min-width: 0">
              <label for="pm-link" class="sr">링크</label>
              <div class="field__box"><input id="pm-link" v-model="linkUrl" type="text" inputmode="url" placeholder="인스타·유튜브·네이버 링크" autocomplete="off" /></div>
            </div>
            <button type="submit" class="btn btn--secondary btn--md" :disabled="isLinkLoading">{{ isLinkLoading ? '찾는 중' : '찾기' }}</button>
          </form>
          <ChipScroller role="group" aria-label="장소 종류">
            <button v-for="item in KINDS" :key="item.code" type="button" class="chip chip--sm" :class="{ 'is-on': !keyword && kind === item.code }" :aria-pressed="!keyword && kind === item.code" @click="pickKind(item.code)">{{ item.label }}</button>
          </ChipScroller>
          <p v-if="isLoading" class="t-cap">찾는 중이에요</p>
          <p v-else-if="message" class="t-cap">{{ message }}</p>
          <div v-if="results.length" class="group">
            <div v-for="place in results.slice(0, LIST_MAX)" :key="place.id || place.name" class="row pm-row">
              <button type="button" class="pm-row__info" @click="pickPlace(place)">
                <span class="row__title">{{ place.name }}</span>
                <span class="row__meta">{{ (place.category || '').split(',')[0] }}<template v-if="place.rating != null"> · ★ {{ place.rating }}</template></span>
              </button>
              <button type="button" class="btn btn--secondary btn--sm" style="height: 44px" :disabled="isAdded(place)" @click="addPlace(place)">{{ isAdded(place) ? '담김' : '담기' }}</button>
            </div>
          </div>
        </div>
        <div v-else class="pm-scroll">
          <ChipScroller v-if="dayList.length > 1" role="tablist" aria-label="일차">
            <button v-for="d in dayList" :key="d.day" type="button" role="tab" class="chip chip--sm" :class="{ 'is-on': day === d.day }" :aria-selected="day === d.day" @click="pickDay(d.day)">{{ d.day }}일차<span class="t-cap tn" style="margin-left: 4px; color: inherit">{{ d.label }}</span></button>
          </ChipScroller>
          <p v-if="!view.stops.length" class="t-cap">장소를 담아 보세요</p>
          <p v-if="view.stops.length" class="t-cap tn">{{ view.stops.length }}곳 · 이동 {{ view.move }}분</p>
          <TripTimeline v-if="view.stops.length" :stops="view.stops" reorderable mode-editable @select="editStop" @reorder="reorder" @change-mode="changeMode" @remove="removeAiStop" />
        </div>
      </BottomSheet>

      <p v-if="isSlowFill" class="pm-filling" role="status">AI가 장소를 고르고 있어요</p>
      <div class="cta cta--white">
        <div class="cta__row">
          <button type="button" class="btn btn--secondary btn--lg pm-side" @click="sortRoute">동선 정리</button>
          <button type="button" class="btn btn--secondary btn--lg pm-side" :disabled="isFilling" @click="fillWithAi"><i class="ic ic--s i-sparkle" aria-hidden="true"></i>{{ isFilling ? '고르는 중' : aiNames.length ? '다시 추천' : 'AI 채우기' }}</button>
          <button type="button" class="btn btn--primary btn--lg" :disabled="!store.draft.stops.length" @click="goCheck">다음</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style>
.scr-PlanManual .pm-sheet { top: 360px !important; gap: 8px; }
.scr-PlanManual .pm-scroll { flex: 1; min-height: 0; overflow-y: auto; display: flex; flex-direction: column; gap: 12px; padding-bottom: 112px; }
.scr-PlanManual .pm-scroll > * { flex: none; }
.scr-PlanManual .pm-tabs { display: flex; flex: none; gap: 4px; padding: 4px; border-radius: var(--radius-md); background: var(--surface-sunken); }
.scr-PlanManual .pm-tabs button { flex: 1; min-height: 40px; border: 0; border-radius: var(--radius-sm); background: none; font: inherit; font-size: 14px; font-weight: 600; color: var(--ink-muted); cursor: pointer; }
.scr-PlanManual .pm-tabs button.is-on { background: var(--surface-raised); color: var(--navy); box-shadow: var(--shadow-card); }
.scr-PlanManual .pm-search { display: flex; align-items: center; gap: 8px; }
.scr-PlanManual .pm-row { padding: 6px 12px 6px 16px; }
.scr-PlanManual .pm-row__info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; padding: 0; border: 0; background: none; font: inherit; color: inherit; text-align: left; cursor: pointer; }
.scr-PlanManual .pm-side { flex: none; padding: 0 12px; font-size: 14px; }
.scr-PlanManual .pm-card { position: absolute; left: 16px; right: 16px; top: 256px; z-index: 3; display: flex; align-items: center; gap: 12px; padding: 12px; border-radius: var(--radius-lg); background: var(--surface-raised); box-shadow: var(--shadow-sheet); }
.scr-PlanManual .pm-card__body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.scr-PlanManual .pm-card__time { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.scr-PlanManual .pm-filling { position: absolute; left: 50%; bottom: 118px; z-index: 5; margin: 0; padding: 8px 14px; border-radius: var(--radius-full); background: var(--navy); color: #fff; font-size: 13px; white-space: nowrap; transform: translateX(-50%); }
.scr-PlanManual .pm-card .i-star { width: 12px; height: 12px; }
</style>
