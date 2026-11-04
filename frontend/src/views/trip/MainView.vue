<script setup>
// S04-a 홈 (②): 내 여행 중 하나(여행 중 → 오늘 → 가장 가까운 다가오는 여행)를 크게 보여 주고, 요즘 인기 장소
//   여행 중      : 지금 내 위치(GPS) 1.5km 안 인기 장소 (위치를 못 받으면 다음 일정 장소 기준)
//   여행 전(등록) : 그 여행 일정 장소들의 가운데 2km 안 인기 장소 ("이번 여행지 근처에서 많이 가요")
//   여행 없음    : 서울 시범 지역 전체 인기 장소 (신규 회원 포함)
//   어느 경우든 내 일정에 이미 있는 곳은 빼요. 홈을 열 때마다 새로 계산해요.
// 서버: GET /api/trips, GET /api/trips/{id}, GET /api/places/popular
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import HomeHeader from '@/components/common/HomeHeader.vue'
import TabBar from '@/components/common/TabBar.vue'
import EmptyTripCard from '@/components/common/EmptyTripCard.vue'
import PageMessage from '@/components/common/PageMessage.vue'
import PopularPlaces from '@/components/trip/PopularPlaces.vue'
import NotiBell from '@/components/member/NotiBell.vue'
import ThemeCircles from '@/components/plan/ThemeCircles.vue'
import { deleteTrip, getArchivedTrips, getLiveStatus, getPopularPlaces, getTrip, getTripList} from '@/api/trips'
import { getPositionOnce } from '@/utils/position'
import { useTripStore } from '@/stores/trip'
import { toDuration, toMinutes, toTime } from '@/utils/time'
import { areaFromPlaces } from '@/utils/planArea'
import { imageFor } from '@/utils/mapView'
import { useStopPhotos } from '@/utils/useStopPhotos'
import { showTrashToast } from '@/utils/trash'
import StopThumb from '@/components/trip/StopThumb.vue'
import { MOVE_CODES } from '@/utils/styleCodes'
import { showToast } from '@/utils/toast'
import { daysLeft, isTodayTrip, pickHomeTrip, toShortDate, toTransportText, tripBadge, tripDetailLink } from '@/utils/tripView'

const LIVE_RADIUS_M = 1500 // 여행 중: 지금 위치에서 걸어서·한두 정거장 (20분 안)
const TRIP_AREA_RADIUS_M = 2000 // 여행 전: 여행지(일정 장소 가운데) 둘레
const TRIP_POPULAR_COUNT = 4 // 여행 카드가 있을 때는 조금만
const POPULAR_COUNT = 6
const SLIDE_GAP_PX = 0 // 넘겨 보는 칸 사이 (한 칸이 카드 폭 전체)
const DAY_MIN = 24 * 60
const HALF_DAY_MIN = 12 * 60
const FIXED_NAME_MAX = 6 // 태그에 넣을 고정 일정 이름 길이
const MAX_STOP_THUMBS = 6 // 카드에 보여 줄 일정 사진 수 (한 줄)
const WHERE_STEP = 1 // 여행 정보 단계 번호: 어디로
const WHEN_STEP = 3 // 여행 정보 단계 번호: 언제
const MODE_ICON = { walk: 'i-walk', transit: 'i-subway', car: 'i-car' } // 서버 이동 수단 → 아이콘

const isLoading = ref(true)
const error = ref('')
const trip = ref(null) // 홈에 보여 줄 여행 (일정 칸 포함). 없으면 null
const popular = ref(null) // { title, places } 없으면 null
const liveStatus = ref(null) // 여행 중 지금 상황 (POST /live/status). 못 받으면 null
const router = useRouter()
const store = useTripStore()
const isMenuOpen = ref(false) // ··· 메뉴
const isConfirmOpen = ref(false) // 삭제 확인
const isDeleting = ref(false)
const archivedCount = ref(0) // 보관함 여행 수 (있을 때만 맨 아래 작은 글자)

onMounted(load)

async function load() {
  isLoading.value = true
  error.value = ''
  try {
    const picked = pickHomeTrip(await getTripList())
    trip.value = picked ? await getTrip(picked.id) : null
  } catch (e) {
    error.value = e.message
  }
  isLoading.value = false
  if (error.value) return
  getArchivedTrips().then((list) => { archivedCount.value = list.length }).catch(() => {}) // 못 읽어도 홈은 그대로
  if (trip.value && trip.value.status === 'ongoing') loadLivePopular()
  else if (trip.value) loadTripPopular()
  else loadSeoulPopular()
}

// 오늘(여행 중이면 오늘 날짜의 날) 일정 칸. 여행 전이면 첫째 날
const dayStops = computed(() => {
  if (!trip.value) return []
  const day = isTodayTrip(trip.value) ? 1 - daysLeft(trip.value.startDate) : 1
  const stops = []
  for (const stop of trip.value.stops) {
    if (stop.day === day && stop.state !== 'skipped') stops.push(stop)
  }
  return stops
})

const isNow = computed(() => trip.value && (trip.value.status === 'ongoing' || isTodayTrip(trip.value)))

const isOngoing = computed(() => !!trip.value && trip.value.status === 'ongoing')

// 카드에 쓰는 칸: 여행 중이면 서버 계산(도착 예정·이동), 아니면 계획 그대로
//   { name, category, placeId, time, reach, moveMin, moveMode }
const cardStops = computed(() => {
  if (liveStatus.value) {
    return liveStatus.value.stops.map((stop) => ({ ...stop, planTime: stop.time, time: stop.eta, isDone: stop.reach === 'done', isHere: stop.reach === 'here' }))
  }
  return dayStops.value.map((stop) => ({ ...stop, isDone: stop.state === 'done', isHere: false }))
})
// 카드 사진: 장소마다 진짜 사진 (같은 일정 안에서 안 겹침). 없으면 종류별 기본 사진
const stopPhotos = useStopPhotos(() => cardStops.value.slice(0, MAX_STOP_THUMBS))
const nextIndex = computed(() => cardStops.value.findIndex((stop) => !stop.isDone && !stop.isHere))
const nextStop = computed(() => (nextIndex.value >= 0 ? cardStops.value[nextIndex.value] : null))
const doneCount = computed(() => cardStops.value.filter((stop) => stop.isDone).length)

// 눈금 점: 다녀옴(진한 하늘) / 지금 있는 곳·다음 곳(코랄) / 남음(빈 점). 보고 있는 점은 테두리
//   slide: 그 칸의 넘겨 보기 번호 (다녀온 곳은 넘겨 볼 수 없어서 -1)
const segments = computed(() => {
  const hereIndex = cardStops.value.findIndex((stop) => stop.isHere)
  const nowIndex = hereIndex >= 0 ? hereIndex : nextIndex.value
  const viewed = slides.value[slideIndex.value]
  return cardStops.value.map((stop, i) => {
    const color = stop.isDone ? 'is-done' : i === nowIndex ? 'is-now' : ''
    return { name: stop.name, slide: slides.value.indexOf(stop), cls: color + (stop === viewed ? ' is-view' : '') }
  })
})

// 상태 태그: 늦음·문 닫을 수도만 코랄, 나머지(여유·여행 중·D-3)는 남색
const pill = computed(() => {
  if (!trip.value) return null
  if (!isOngoing.value) {
    const left = daysLeft(trip.value.startDate)
    return { text: left > 0 ? 'D-' + left : '오늘', isAlert: false }
  }
  const status = liveStatus.value
  if (status && status.fixed && status.fixed.slackMin < 0) return { text: fixedLateText(status.fixed), isAlert: true }
  if (nextStop.value && (nextStop.value.reach === 'closed' || nextStop.value.reach === 'tight')) return { text: '문 닫을 수도', isAlert: true }
  if (status && status.fixed) return { text: '여유 ' + status.fixed.slackMin + '분', isAlert: false }
  // 고정 일정이 없으면 서버가 여유를 안 줘서, 다음 곳의 계획 시각과 도착 예정으로 계산
  const diff = planDiff.value
  if (diff === null) return { text: '여행 중', isAlert: false }
  if (diff < 0) return { text: toLateText(-diff), isAlert: true }
  return { text: '여유 ' + toLateText(diff).replace(' 늦어요', ''), isAlert: false }
})

// 다음 곳: 계획 시각 − 도착 예정 (분, 음수면 늦음). 모르면 null
const planDiff = computed(() => {
  const next = nextStop.value
  if (!liveStatus.value || !next || !next.planTime || !next.eta) return null
  const diff = toMinutes(next.planTime) - toMinutes(next.eta)
  return diff < -HALF_DAY_MIN ? diff + DAY_MIN : diff // 계획 시각이 자정을 넘긴 칸(00:29)이면 다음 날로 봄
})

function toLateText(minutes) {
  if (minutes < 60) return minutes + '분 늦어요'
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return hours + '시간' + (rest ? ' ' + rest + '분' : '') + ' 늦어요'
}

// 카드 윗줄: "문래동 · 오늘" (긴 여행 이름 대신). 동 → 구 → 여행 이름 순으로 찾음
const cardLabel = computed(() => {
  if (!trip.value) return ''
  const dong = /(\S+?동)\d*가?(?:\s|$)/.exec(trip.value.title || '')
  const area = areaFromPlaces(trip.value.stops)
  const place = dong ? dong[1] : area ? area.name : trip.value.title
  return place + ' · ' + (isTodayTrip(trip.value) ? '오늘' : toShortDate(trip.value.startDate))
})

// 주 버튼: 늦거나 문 닫을 수 있으면 대안, 아니면 여행 화면
const mainAction = computed(() => (pill.value && pill.value.isAlert ? { text: '대안 보기', to: '/live/delay' } : { text: '여행 이어가기', to: '/live' }))

// 큰 시각을 코랄로: 이 칸 자체가 계획보다 늦을 때만 (고정 일정이 늦는 건 태그에)
const isTimeLate = computed(() => planDiff.value !== null && planDiff.value < 0)

// 고정 일정이 늦을 때 태그: "공연장 40분 늦어요" (이름이 길면 "고정 일정")
function fixedLateText(fixed) {
  const name = fixed.name && fixed.name.length <= FIXED_NAME_MAX ? fixed.name : '고정 일정'
  return name + ' ' + toLateText(-fixed.slackMin)
}

// 넘겨 보는 칸: 지금 있는 곳·다음 곳부터 순서대로
const slides = computed(() => cardStops.value.filter((stop) => !stop.isDone))
const slideIndex = ref(0) // 보고 있는 칸 번호
const slider = ref(null)

// 여행 전·일정이 없을 때 카드 숫자: 여행까지 며칠 / 다음 장소까지 남은 시간
const countdown = computed(() => {
  if (!trip.value) return null
  if (!isNow.value) return { cap: '여행까지', big: daysLeft(trip.value.startDate) + '일' }
  const next = dayStops.value.find((stop) => stop.state === 'todo')
  if (!next) return { cap: '오늘 일정', big: '모두 다녀왔어요' }
  const now = new Date()
  const left = toMinutes(next.time) - (now.getHours() * 60 + now.getMinutes())
  return { cap: next.name + ' (' + next.time + ')까지', big: left > 0 ? toDuration(left) : '지금 출발' }
})

// 칸 글자: "다음 · 로라멘" / 큰 글자 "19:44 도착" / "20:30 출발 · 일본식라면"
function slideCap(stop) {
  return (stop.isHere ? '지금 여기' : '다음') + ' · ' + stop.name
}
function leaveOf(stop) {
  return stop.time && stop.stay != null ? toTime((toMinutes(stop.time) + stop.stay) % DAY_MIN) : ''
}
function slideClock(stop) {
  return stop.isHere ? leaveOf(stop) + ' 출발' : stop.time + ' 도착'
}
function slideMeta(stop) {
  const leave = !stop.isHere && leaveOf(stop) ? leaveOf(stop) + ' 출발 · ' : ''
  return leave + categoryOf(stop)
}
function slideMove(stop) {
  return stop.moveMin != null ? stop.moveMin + '분' : ''
}
function slideIcon(stop) {
  return MODE_ICON[stop.moveMode] || 'i-walk'
}
// 분류 첫 낱말만 ("육류,고기요리" → "육류")
function categoryOf(stop) {
  return (stop.category || '').split(',')[0]
}

// 스크롤 위치 → 보고 있는 칸 번호
function onSlideScroll() {
  const box = slider.value
  const first = box && box.firstElementChild
  if (!first) return
  const step = first.offsetWidth + SLIDE_GAP_PX
  slideIndex.value = Math.min(slides.value.length - 1, Math.round(box.scrollLeft / step))
}
function goSlide(index) {
  const box = slider.value
  if (box && box.firstElementChild) box.scrollTo({ left: index * (box.firstElementChild.offsetWidth + SLIDE_GAP_PX), behavior: 'smooth' })
}

// 마우스로 끌어 넘기기 (손가락은 브라우저 스크롤이 처리). 끄는 동안은 칸에 붙는 동작을 꺼 둠
let drag = null
function onSlidePointerDown(e) {
  if (e.pointerType !== 'mouse') return
  drag = { x: e.clientX, left: slider.value.scrollLeft }
  slider.value.classList.add('is-dragging')
  window.addEventListener('pointermove', onSlidePointerMove)
  window.addEventListener('pointerup', onSlidePointerUp)
}
function onSlidePointerMove(e) {
  if (drag) slider.value.scrollLeft = drag.left - (e.clientX - drag.x)
}
function onSlidePointerUp() {
  window.removeEventListener('pointermove', onSlidePointerMove)
  window.removeEventListener('pointerup', onSlidePointerUp)
  drag = null
  if (!slider.value) return
  slider.value.classList.remove('is-dragging')
  onSlideScroll()
  goSlide(slideIndex.value)
}
onUnmounted(onSlidePointerUp)

// 여행 중이면 서버 계산으로 도착 예정·늦는 분 (실패하면 계획 시각으로 보여 줌)
async function loadLiveNext(position) {
  try {
    liveStatus.value = await getLiveStatus(trip.value.id, position.lat, position.lng)
  } catch {
    liveStatus.value = null
  }
}

// 다시 짜기: 지금 여행 내용(지역·날짜·인원·이동 수단·꼭 갈 곳·고정 일정)을 채워 여행 정보로.
//   지금 여행은 그대로 두고, 새 일정을 저장한 뒤에 바꿀지 물어봐요 (일정 점검 화면)
function replan() {
  isMenuOpen.value = false
  const old = trip.value
  store.resetPlan()
  store.replanFrom = { id: old.id, title: old.title }
  store.planStartDate = old.startDate
  store.planEndDate = old.endDate !== old.startDate ? old.endDate : null
  store.planPeople = old.people || store.planPeople
  store.planMoves = Object.keys(MOVE_CODES).filter((label) => (old.transports || []).includes(MOVE_CODES[label]))
  store.planArea = areaFromPlaces(old.stops)
  const kept = old.stops.filter((stop) => stop.state !== 'skipped' && stop.state !== 'done')
  store.planConditions = {
    mustVisit: kept.filter((stop) => stop.importance === 'must' && !stop.fixed).map((stop) => stop.name),
    fixed: kept.filter((stop) => stop.fixed).map((stop) => ({ name: stop.name, time: stop.time, day: stop.day })),
  }
  store.planStep = store.planArea ? WHEN_STEP : WHERE_STEP
  router.push('/plan/info')
}

function askDelete() {
  isMenuOpen.value = false
  isConfirmOpen.value = true
}

// 여행 삭제 (확인 창에서 눌렀을 때만). 지우면 홈을 다시 읽음
async function removeTrip() {
  isDeleting.value = true
  try {
    const removedId = trip.value.id
    await deleteTrip(removedId)
    showTrashToast(removedId, load)
    isConfirmOpen.value = false
    liveStatus.value = null
    popular.value = null
    await load()
  } catch (e) {
    showToast(e.message)
  }
  isDeleting.value = false
}

// 기준점 근처 인기 장소 → 내 일정에 있는 곳은 빼고 count개 (결과가 없으면 서울 전체로)
async function showPopular(base, radius, count, title) {
  try {
    const inTrip = new Set(trip.value.stops.map((stop) => stop.placeId))
    const res = await getPopularPlaces(base.lat, base.lng, count + inTrip.size, radius)
    const places = res.places.filter((item) => !inTrip.has(item.place.id)).slice(0, count)
    if (!places.length) return loadSeoulPopular()
    popular.value = { title, places }
  } catch {
    popular.value = null // 추천은 없어도 홈은 보여 줌
  }
}

// 여행 중: 지금 내 위치 기준 (위치를 못 받으면 다음 일정 장소 기준)
async function loadLivePopular() {
  const position = await getPositionOnce()
  if (position) {
    loadLiveNext(position)
    return showPopular(position, LIVE_RADIUS_M, TRIP_POPULAR_COUNT, '내 주변 인기')
  }
  const next = dayStops.value.find((stop) => stop.state === 'todo') || dayStops.value[0]
  if (!next || next.lat == null) return loadSeoulPopular()
  return showPopular(next, LIVE_RADIUS_M, TRIP_POPULAR_COUNT, next.name + ' 근처 인기')
}

// 여행 전: 등록한 여행의 일정 장소들 가운데 기준
async function loadTripPopular() {
  const placed = trip.value.stops.filter((stop) => stop.lat != null && stop.state !== 'skipped')
  if (!placed.length) return loadSeoulPopular()
  let lat = 0
  let lng = 0
  for (const stop of placed) {
    lat += stop.lat
    lng += stop.lng
  }
  const center = { lat: lat / placed.length, lng: lng / placed.length }
  return showPopular(center, TRIP_AREA_RADIUS_M, TRIP_POPULAR_COUNT, '여행지 근처 인기')
}

// 여행 없음(신규 회원 포함): 서울 시범 지역 전체 (근처에 기록이 없을 때도 여기로)
async function loadSeoulPopular() {
  try {
    const res = await getPopularPlaces(null, null, POPULAR_COUNT)
    popular.value = res.places.length ? { title: '서울 인기', places: res.places } : null
  } catch {
    popular.value = null
  }
}
</script>

<template>
  <div class="scr scr-Main">
    <div class="app">
      <HomeHeader>
        <NotiBell />
      </HomeHeader>

      <div class="scroll" style="bottom: 84px; gap: 24px; padding-top: 4px">
        <PageMessage v-if="isLoading" is-loading title="내 여행을 불러오는 중이에요" />
        <PageMessage v-else-if="error" icon="i-warning" title="여행을 불러오지 못했어요" :text="error" button="다시 시도" @retry="load" />
        <EmptyTripCard v-else-if="!trip" to="/new" link-to="/plan/info" />

        <!-- 내 여행 하나 -->
        <section v-else class="card trip-card" aria-label="내 여행">
          <div class="today-card">
            <div class="home-top">
              <span class="badge" :class="tripBadge(trip).cls">{{ tripBadge(trip).text }}</span>
              <span class="t-cap tn home-top__info">{{ toShortDate(trip.startDate) }} · {{ trip.people }}명 · {{ toTransportText(trip.transports) }}</span>
              <button type="button" class="home-more" aria-label="더 보기" :aria-expanded="isMenuOpen" @click="isMenuOpen = !isMenuOpen"><i></i><i></i><i></i></button>
              <div v-if="isMenuOpen" class="home-menu" role="menu">
                <button type="button" role="menuitem" @click="replan"><i class="ic ic--s i-refresh" aria-hidden="true"></i>다시 짜기</button>
                <button type="button" role="menuitem" class="c-danger" @click="askDelete"><i class="ic ic--s i-trash" aria-hidden="true"></i>여행 삭제</button>
              </div>
            </div>
            <h1 class="h-display">{{ trip.title }}</h1>
            <span v-if="isOngoing && pill.isAlert" class="badge badge--warning tn" style="align-self: flex-start">{{ pill.text }}</span>

            <!-- 여행 중: 남은 곳을 옆으로 넘겨 봐요 -->
            <div v-if="isOngoing && slides.length" ref="slider" class="home-slides" aria-label="남은 일정" @scroll.passive="onSlideScroll" @pointerdown="onSlidePointerDown">
              <article v-for="(stop, i) in slides" :key="stop.id || stop.name" class="home-slide">
                <p class="t-cap">{{ slideCap(stop) }}</p>
                <p class="t-big" :class="i === 0 && isTimeLate ? 'c-danger' : 'c-navy'" style="margin: 0">{{ slideClock(stop) }}</p>
                <p class="t-cap tn home-slide__meta">{{ slideMeta(stop) }}<template v-if="slideMove(stop)"> · <i class="ic ic--s" :class="slideIcon(stop)" aria-hidden="true"></i> {{ slideMove(stop) }}</template></p>
              </article>
            </div>
            <div v-else>
              <p class="t-cap">{{ countdown.cap }}</p>
              <p class="t-big" style="margin: 0; color: var(--navy)">{{ countdown.big }}</p>
            </div>

            <ol v-if="cardStops.length" class="today-stops" :aria-label="`일정 ${cardStops.length}곳`">
              <li v-for="(stop, stopIndex) in cardStops.slice(0, MAX_STOP_THUMBS)" :key="stop.id || stop.name">
                <button type="button" class="today-stops__btn" :disabled="!isOngoing || slides.indexOf(stop) < 0" :aria-label="stop.name" @click="goSlide(slides.indexOf(stop))">
                  <StopThumb :class="{ 'is-first': stop === slides[slideIndex], 'is-fixed': stop.fixed, 'is-done': stop.isDone }" :src="stopPhotos[stop.name]" :name="stop.name" :category="stop.category" />
                </button>
                <span class="t-label tn" :class="{ 'story-time--next': stopIndex === nextIndex }" style="display: inline-flex; align-items: center; gap: 2px">
                  <i v-if="stop.fixed" class="ic ic--s i-lock" aria-hidden="true" style="width: 12px; height: 12px"></i>{{ stop.time }}
                </span>
              </li>
            </ol>
            <p v-else class="t-cap">아직 넣은 일정이 없어요</p>
          </div>
          <div class="home-actions">
            <RouterLink v-if="isOngoing" :to="mainAction.to" class="btn btn--primary btn--lg" style="flex: 1">{{ mainAction.text }}</RouterLink>
            <RouterLink v-else :to="tripDetailLink(trip)" class="btn btn--primary btn--lg btn--block">일정 보기</RouterLink>
            <RouterLink v-if="isOngoing && nextStop" to="/live/route" class="btn btn--secondary btn--lg" style="padding: 0 16px"><i class="ic ic--s i-route" aria-hidden="true"></i>길 안내</RouterLink>
          </div>
        </section>

        <!-- 테마로 떠나기 (③, 지금은 가짜 테마 → 누르면 준비 중) -->
        <ThemeCircles v-if="!isLoading && !error" />

        <!-- 요즘 인기 장소 (다른 사람들의 최근 한 달 여행 기록) -->
        <PopularPlaces v-if="popular" :title="popular.title" :places="popular.places" />

        <RouterLink v-if="archivedCount" to="/trips/archive" class="textlink tn" style="align-self: center">보관함 {{ archivedCount }}</RouterLink>
      </div>

      <TabBar active="home" />

      <div v-if="isMenuOpen" class="home-menu-dim" @click="isMenuOpen = false"></div>
      <template v-if="isConfirmOpen">
        <div class="dim" style="z-index: 20" @click="isConfirmOpen = false"></div>
        <section class="home-confirm" role="alertdialog" aria-modal="true" aria-label="여행 삭제 확인">
          <h2 class="h-title" style="margin: 0">이 여행을 삭제할까요?</h2>
          <p class="t-cap home-confirm__name">{{ trip.title }} · 휴지통으로 옮겨요</p>
          <div class="cta__row">
            <button type="button" class="btn btn--secondary btn--lg" @click="isConfirmOpen = false">취소</button>
            <button type="button" class="btn btn--danger btn--lg" :disabled="isDeleting" @click="removeTrip">{{ isDeleting ? '삭제 중' : '삭제' }}</button>
          </div>
        </section>
      </template>
    </div>
  </div>
</template>

<style>
/* 내 여행 카드 테두리: 칸 안쪽(padding-box)은 흰색, 테두리까지(border-box)는 남색 → 파랑 */
.scr-Main .trip-card { border: 1.5px solid transparent; background: linear-gradient(var(--surface-raised), var(--surface-raised)) padding-box, linear-gradient(135deg, var(--navy), #5B8DEF) border-box; box-shadow: 0 8px 24px rgba(31, 58, 104, .10); }
.scr-Main .today-card { padding: 16px 16px 12px; display: flex; flex-direction: column; gap: 12px; }
.scr-Main .today-card .t-cap { margin: 0; }
.scr-Main .today-stops { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 6px; }
.scr-Main .today-stops li { display: flex; flex-direction: column; align-items: center; gap: 4px; }
.scr-Main .today-stops__btn { padding: 0; border: 0; background: none; cursor: pointer; border-radius: var(--radius-md); }
.scr-Main .today-stops__btn:disabled { cursor: default; }
.scr-Main .today-stops .thumb { width: 44px; height: 44px; }
.scr-Main .today-stops .thumb.is-first { border: 2px solid var(--navy); }
.scr-Main .today-stops .thumb.is-fixed { border: 2px solid var(--ink); }
.scr-Main .today-stops .thumb.is-done { opacity: .45; }
.scr-Main .story-time--next { color: var(--navy); font-weight: 700; }
.scr-Main .home-top { position: relative; display: flex; align-items: center; gap: 8px; }
.scr-Main .home-top__info { flex: 1; min-width: 0; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.scr-Main .home-more { flex: none; width: 44px; height: 44px; margin: -10px -12px -10px 0; display: flex; align-items: center; justify-content: center; gap: 3px; border: 0; border-radius: 50%; background: none; cursor: pointer; }
.scr-Main .home-more i { width: 4px; height: 4px; border-radius: 50%; background: var(--ink-muted); }
.scr-Main .home-menu { position: absolute; top: 30px; right: -4px; z-index: 12; display: flex; flex-direction: column; min-width: 140px; padding: 6px; border-radius: var(--radius-lg); background: #fff; box-shadow: var(--shadow-sheet); }
.scr-Main .home-menu button { display: flex; align-items: center; gap: 8px; min-height: 44px; padding: 0 10px; border: 0; border-radius: var(--radius-sm); background: none; font: inherit; font-size: 14px; font-weight: 600; color: var(--ink); cursor: pointer; }
.scr-Main .home-menu button.c-danger { color: var(--danger); }
.scr-Main .home-menu button:hover { background: var(--surface-sunken); }
.scr-Main .home-menu-dim { position: absolute; inset: 0; z-index: 4; }
.scr-Main section.card { position: relative; z-index: 5; }
.scr-Main .home-confirm { position: absolute; left: 0; right: 0; bottom: 0; z-index: 21; display: flex; flex-direction: column; gap: 8px; padding: 24px 16px; border-radius: var(--radius-xl) var(--radius-xl) 0 0; background: #fff; }
.scr-Main .home-confirm__name { margin: 0 0 12px; }
.scr-Main .home-confirm .btn { flex: 1; }
.scr-Main .home-slides { display: flex; margin: 0 -16px; overflow-x: auto; scroll-snap-type: x mandatory; scrollbar-width: none; cursor: grab; }
.scr-Main .home-slides::-webkit-scrollbar { display: none; }
.scr-Main .home-slides.is-dragging { cursor: grabbing; scroll-snap-type: none; user-select: none; }
.scr-Main .home-slide { flex: 0 0 100%; min-width: 0; box-sizing: border-box; padding: 0 16px; scroll-snap-align: start; }
.scr-Main .home-slide__meta .ic { width: 13px; height: 13px; vertical-align: -2px; }
.scr-Main .home-actions { display: flex; gap: 8px; padding: 0 16px 16px; }
</style>
