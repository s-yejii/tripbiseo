<script setup>
// S04-a 홈 (②): 내 여행 중 하나(여행 중 → 오늘 → 가장 가까운 다가오는 여행)를 크게 보여 주고, 요즘 인기 장소
//   여행 중      : 지금 내 위치(GPS) 1.5km 안 인기 장소 (위치를 못 받으면 다음 일정 장소 기준)
//   여행 전(등록) : 그 여행 일정 장소들의 가운데 2km 안 인기 장소 ("이번 여행지 근처에서 많이 가요")
//   여행 없음    : 서울 시범 지역 전체 인기 장소 (신규 회원 포함)
//   어느 경우든 내 일정에 이미 있는 곳은 빼요. 홈을 열 때마다 새로 계산해요.
// 서버: GET /api/trips, GET /api/trips/{id}, GET /api/places/popular
import { computed, onMounted, ref } from 'vue'
import HomeHeader from '@/components/common/HomeHeader.vue'
import TabBar from '@/components/common/TabBar.vue'
import EmptyTripCard from '@/components/common/EmptyTripCard.vue'
import PageMessage from '@/components/common/PageMessage.vue'
import PopularPlaces from '@/components/trip/PopularPlaces.vue'
import NotiBell from '@/components/member/NotiBell.vue'
import ThemeCircles from '@/components/plan/ThemeCircles.vue'
import { getPopularPlaces, getTrip, getTripList } from '@/api/trips'
import { getPositionOnce } from '@/utils/position'
import { imageFor } from '@/utils/mapView'
import { daysLeft, isTodayTrip, pickHomeTrip, toShortDate, toTransportText, tripBadge, tripDetailLink } from '@/utils/tripView'
import { toDuration, toMinutes } from '@/utils/time'

const MAX_STOP_THUMBS = 6 // 카드에 보여 줄 일정 사진 수 (한 줄)
const LIVE_RADIUS_M = 1500 // 여행 중: 지금 위치에서 걸어서·한두 정거장 (20분 안)
const TRIP_AREA_RADIUS_M = 2000 // 여행 전: 여행지(일정 장소 가운데) 둘레
const TRIP_POPULAR_COUNT = 4 // 여행 카드가 있을 때는 조금만
const POPULAR_COUNT = 6
// 상황마다 다른 안내 문장 (제목 아래 작은 글씨)
const CAPTION_LIVE = '여행 중에 잠깐 들르기 좋은 곳이에요'
const CAPTION_LIVE_NO_GPS = '위치를 못 받아서 다음 일정 장소 근처로 골랐어요'
const CAPTION_TRIP = '이번 여행 일정 근처에서 같이 들르기 좋은 곳이에요'
const CAPTION_SEOUL = '요즘 여행자들이 많이 찾는 곳이에요'
const CAPTION_SEOUL_FALLBACK = '근처엔 아직 기록이 적어서 서울 전체에서 골랐어요'

const isLoading = ref(true)
const error = ref('')
const trip = ref(null) // 홈에 보여 줄 여행 (일정 칸 포함). 없으면 null
const popular = ref(null) // { title, caption, places } 없으면 null

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

// 카드 큰 글자: 오늘이면 다음 장소까지 남은 시간, 여행 전이면 남은 날
const countdown = computed(() => {
  if (!trip.value) return null
  if (!isNow.value) return { cap: '여행까지', big: daysLeft(trip.value.startDate) + '일' }
  const next = dayStops.value.find((stop) => stop.state === 'todo')
  if (!next) return { cap: '오늘 일정', big: '모두 다녀왔어요' }
  const now = new Date()
  const left = toMinutes(next.time) - (now.getHours() * 60 + now.getMinutes())
  return { cap: next.name + ' (' + next.time + ')까지', big: left > 0 ? toDuration(left) : '지금 출발' }
})

// 기준점 근처 인기 장소 → 내 일정에 있는 곳은 빼고 count개 (결과가 없으면 서울 전체로)
async function showPopular(base, radius, count, title, caption) {
  try {
    const inTrip = new Set(trip.value.stops.map((stop) => stop.placeId))
    const res = await getPopularPlaces(base.lat, base.lng, count + inTrip.size, radius)
    const places = res.places.filter((item) => !inTrip.has(item.place.id)).slice(0, count)
    if (!places.length) return loadSeoulPopular(CAPTION_SEOUL_FALLBACK)
    popular.value = { title, caption, places }
  } catch {
    popular.value = null // 추천은 없어도 홈은 보여 줌
  }
}

// 여행 중: 지금 내 위치 기준 (위치를 못 받으면 다음 일정 장소 기준)
async function loadLivePopular() {
  const position = await getPositionOnce()
  if (position) return showPopular(position, LIVE_RADIUS_M, TRIP_POPULAR_COUNT, '지금 내 주변에서 많이 가요', CAPTION_LIVE)
  const next = dayStops.value.find((stop) => stop.state === 'todo') || dayStops.value[0]
  if (!next || next.lat == null) return loadSeoulPopular()
  return showPopular(next, LIVE_RADIUS_M, TRIP_POPULAR_COUNT, next.name + ' 근처에서 많이 가요', CAPTION_LIVE_NO_GPS)
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
  return showPopular(center, TRIP_AREA_RADIUS_M, TRIP_POPULAR_COUNT, '이번 여행지 근처에서 많이 가요', CAPTION_TRIP)
}

// 여행 없음(신규 회원 포함): 서울 시범 지역 전체 (근처에 기록이 없을 때도 여기로)
async function loadSeoulPopular(caption = CAPTION_SEOUL) {
  try {
    const res = await getPopularPlaces(null, null, POPULAR_COUNT)
    popular.value = res.places.length ? { title: '서울에서 요즘 인기', caption, places: res.places } : null
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
        <EmptyTripCard v-else-if="!trip" to="/new" />

        <!-- 내 여행 하나 -->
        <section v-else class="card" aria-label="내 여행" style="overflow: hidden">
          <div class="today-card">
            <div style="display: flex; align-items: center; gap: 8px">
              <span class="badge" :class="tripBadge(trip).cls">{{ tripBadge(trip).text }}</span>
              <span class="t-cap tn">{{ toShortDate(trip.startDate) }} · {{ trip.people }}명 · {{ toTransportText(trip.transports) }}</span>
            </div>
            <h1 class="h-display">{{ trip.title }}</h1>
            <div>
              <p class="t-cap">{{ countdown.cap }}</p>
              <p class="t-big" style="margin: 0; color: var(--navy)">{{ countdown.big }}</p>
            </div>
            <ol v-if="dayStops.length" class="today-stops" :aria-label="`일정 ${dayStops.length}곳`">
              <li v-for="(stop, i) in dayStops.slice(0, MAX_STOP_THUMBS)" :key="stop.id">
                <img class="thumb" :class="{ 'is-first': i === 0, 'is-fixed': stop.fixed }" :src="imageFor(stop.category)" alt="" />
                <span class="t-label tn" style="display: inline-flex; align-items: center; gap: 2px">
                  <i v-if="stop.fixed" class="ic ic--s i-lock" aria-hidden="true" style="width: 12px; height: 12px"></i>{{ stop.time }}
                </span>
              </li>
            </ol>
            <p v-else class="t-cap">아직 넣은 일정이 없어요</p>
          </div>
          <div style="padding: 0 16px 16px">
            <RouterLink v-if="trip.status === 'ongoing'" to="/live" class="btn btn--primary btn--lg btn--block">여행 이어가기</RouterLink>
            <RouterLink v-else :to="tripDetailLink(trip)" class="btn btn--primary btn--lg btn--block">일정 보기</RouterLink>
          </div>
        </section>

        <!-- 테마로 떠나기 (③, 지금은 가짜 테마 → 누르면 준비 중) -->
        <ThemeCircles v-if="!isLoading && !error" />

        <!-- 요즘 인기 장소 (다른 사람들의 최근 한 달 여행 기록) -->
        <PopularPlaces v-if="popular" :title="popular.title" :caption="popular.caption" :places="popular.places" />
      </div>

      <TabBar active="home" />
    </div>
  </div>
</template>

<style>
.scr-Main .today-card { padding: 16px 16px 12px; display: flex; flex-direction: column; gap: 12px; }
.scr-Main .today-card .t-cap { margin: 0; }
.scr-Main .today-stops { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 6px; }
.scr-Main .today-stops li { display: flex; flex-direction: column; align-items: center; gap: 4px; }
.scr-Main .today-stops .thumb { width: 44px; height: 44px; }
.scr-Main .today-stops .thumb.is-first { border: 2px solid var(--navy); }
.scr-Main .today-stops .thumb.is-fixed { border: 2px solid var(--ink); }
</style>
