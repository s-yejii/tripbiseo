<script setup>
// S04-a 홈 (②): 내 여행 중 하나(여행 중 → 오늘 → 가장 가까운 다가오는 여행)를 크게 보여 주고, 요즘 인기 장소
//   여행이 있으면: 다음(첫) 일정 장소 800m 안에서 다른 사람들이 많이 가는 곳 ("○○ 근처에서 많이 가요")
//   여행이 없으면(신규 회원 포함): "여행 만들기" 카드 + 내 주변 요즘 인기 (위치를 거부하면 서울 전체)
// 서버: GET /api/trips, GET /api/trips/{id}, GET /api/places/popular
import { computed, onMounted, ref } from 'vue'
import HomeHeader from '@/components/common/HomeHeader.vue'
import TabBar from '@/components/common/TabBar.vue'
import EmptyTripCard from '@/components/common/EmptyTripCard.vue'
import PageMessage from '@/components/common/PageMessage.vue'
import PopularPlaces from '@/components/trip/PopularPlaces.vue'
import { getPopularPlaces, getTrip, getTripList } from '@/api/trips'
import { getPositionOnce } from '@/utils/position'
import { imageFor } from '@/utils/mapView'
import { daysLeft, isTodayTrip, pickHomeTrip, toShortDate, toTransportText, tripBadge, tripDetailLink } from '@/utils/tripView'
import { toDuration, toMinutes } from '@/utils/time'

const MAX_STOP_THUMBS = 6 // 카드에 보여 줄 일정 사진 수 (한 줄)
const NEAR_STOP_RADIUS_M = 800 // 일정 장소 근처 (걸어서 10분 정도)
const NEAR_STOP_COUNT = 3
const POPULAR_COUNT = 6
const SCOPE_TITLE = { near: '내 주변 요즘 인기', wide: '내 주변 5km 요즘 인기', seoul: '서울에서 요즘 인기' }

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
  if (trip.value) loadNearStop()
  else loadPopular()
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

// 다음 일정 장소(없으면 첫 장소) 근처에서 다른 사람들이 많이 가는 곳 (이미 내 일정에 있는 곳은 빼요)
async function loadNearStop() {
  const base = dayStops.value.find((stop) => stop.state === 'todo') || dayStops.value[0]
  if (!base || base.lat == null) return loadPopular()
  try {
    const res = await getPopularPlaces(base.lat, base.lng, NEAR_STOP_COUNT + trip.value.stops.length, NEAR_STOP_RADIUS_M)
    const inTrip = new Set(trip.value.stops.map((stop) => stop.placeId))
    const places = res.places.filter((item) => !inTrip.has(item.place.id)).slice(0, NEAR_STOP_COUNT)
    popular.value = places.length ? { title: base.name + ' 근처에서 많이 가요', caption: '걸어서 10분 안 · 최근 한 달 다른 여행 기록', places } : null
  } catch {
    popular.value = null // 추천은 없어도 홈은 보여 줌
  }
}

// 내 주변 요즘 인기 (위치를 거부하면 서울 시범 지역 전체)
async function loadPopular() {
  const position = await getPositionOnce()
  try {
    const res = await getPopularPlaces(position?.lat, position?.lng, POPULAR_COUNT)
    const caption = res.scope === 'seoul' && position ? '근처에 기록이 적어서 서울 전체에서 골랐어요' : '최근 한 달 여행 기록으로 골랐어요'
    popular.value = res.places.length ? { title: SCOPE_TITLE[res.scope] || '요즘 인기', caption, places: res.places } : null
  } catch {
    popular.value = null
  }
}
</script>

<template>
  <div class="scr scr-Main">
    <div class="app">
      <HomeHeader>
        <RouterLink to="/notifications" class="iconbtn" aria-label="알림"><i class="ic ic--l i-bell" aria-hidden="true"></i></RouterLink>
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
