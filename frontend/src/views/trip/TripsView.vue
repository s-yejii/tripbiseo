<script setup>
// S04-b 내 여행 (②): 지금 여행 중 · 다가오는 여행 · 지난 여행
// 서버(GET /api/trips)에서 내 여행을 읽어 status로 나눠요. 못 읽으면 오류 안내, 여행이 없으면 "여행 만들기" 카드.
import { computed, onMounted, ref } from 'vue'
import PhotoCard from '@/components/common/PhotoCard.vue'
import TabBar from '@/components/common/TabBar.vue'
import EmptyTripCard from '@/components/common/EmptyTripCard.vue'
import PageMessage from '@/components/common/PageMessage.vue'
import { getArchivedTrips, getTripList } from '@/api/trips'
import { daysLeft, toShortDate, tripDetailLink } from '@/utils/tripView'

const serverTrips = ref(null) // null: 아직 못 읽음
const error = ref('')
const archivedCount = ref(0) // 보관함 여행 수 (있을 때만 맨 아래 한 줄)
onMounted(load)

async function load() {
  error.value = ''
  try {
    serverTrips.value = await getTripList()
    archivedCount.value = (await getArchivedTrips()).length
  } catch (e) {
    error.value = e.message
  }
}
const ongoing = computed(() => (serverTrips.value || []).find((trip) => trip.status === 'ongoing') || null)
// 다가오는 여행: 끝 날짜가 안 지난 것만. 안 떠나고 날짜가 지난 여행은 지난 여행으로
const planned = computed(() => (serverTrips.value || []).filter((trip) => trip.status === 'planned' && daysLeft(trip.endDate) >= 0))
const done = computed(() => (serverTrips.value || []).filter((trip) => trip.status === 'done' || (trip.status === 'planned' && daysLeft(trip.endDate) < 0)))
</script>

<template>
  <div class="scr scr-Trips">
    <div class="app">
      <header class="topbar" style="padding: 0 8px 0 16px">
        <h1 class="topbar__title" style="font-size: 24px; line-height: 32px">내 여행</h1>
        <!-- ⑤ 앨범으로 (11/06, ② 이찬기 동의) -->
        <RouterLink to="/album" class="btn btn--secondary btn--sm"><i class="ic ic--s i-album" aria-hidden="true"></i>앨범</RouterLink>
      </header>

      <div class="scroll" style="bottom: 84px; gap: 20px; padding-top: 4px">
        <PageMessage v-if="error" icon="i-warning" title="여행을 불러오지 못했어요" :text="error" button="다시 시도" @retry="load" />
        <PageMessage v-else-if="!serverTrips" is-loading title="내 여행을 불러오는 중이에요" />
        <EmptyTripCard v-else-if="!serverTrips.length" to="/new" />
        <template v-else>
        <section aria-labelledby="t-now" class="trips-sec">
          <h2 id="t-now" class="t-strong muted">지금 여행 중</h2>
          <PhotoCard v-if="ongoing" to="/live" img="/img/cafe.jpg" :height="132">
            <span class="photo__txt now-trip">
              <span style="display: flex; flex-direction: column"><b style="font-size: 18px; line-height: 26px">{{ ongoing.title }}</b><small class="tn">{{ toShortDate(ongoing.startDate) }} · {{ ongoing.stopCount }}곳</small></span>
              <span class="btn btn--sm" style="background: #fff; color: var(--navy)">여행 이어가기</span>
            </span>
          </PhotoCard>
          <p v-else class="t-cap">진행 중인 여행이 없어요</p>
        </section>

        <section aria-labelledby="t-next" class="trips-sec">
          <h2 id="t-next" class="t-strong muted">다가오는 여행</h2>
          <div class="group">
            <RouterLink v-for="trip in planned" :key="trip.id" :to="tripDetailLink(trip)" class="row">
              <img class="thumb" src="/img/dessert.jpg" alt="" />
              <span class="row__body"><span class="row__title">{{ trip.title }}</span><span class="row__meta">{{ toShortDate(trip.startDate) }} · {{ trip.stopCount }}곳</span></span>
              <span class="badge badge--navy tn">{{ daysLeft(trip.startDate) > 0 ? 'D-' + daysLeft(trip.startDate) : '오늘' }}</span>
            </RouterLink>
            <RouterLink to="/new" class="row tr-new">
              <span class="thumb tr-new__ic"><i class="ic i-plus" aria-hidden="true"></i></span>
              <span class="row__body"><span class="row__title">새 여행 만들기</span><span class="row__meta">AI로 짜거나 직접 골라요</span></span>
              <i class="ic i-next" aria-hidden="true"></i>
            </RouterLink>
          </div>
        </section>

        <section aria-labelledby="t-past" class="trips-sec">
          <h2 id="t-past" class="t-strong muted">지난 여행 {{ done.length }}</h2>
          <div class="group">
            <RouterLink v-for="trip in done" :key="trip.id" :to="trip.status === 'done' ? '/retro?id=' + trip.id : tripDetailLink(trip)" class="row">
              <img class="thumb" src="/img/euljiro.jpg" alt="" />
              <span class="row__body"><span class="row__title">{{ trip.title }}</span><span class="row__meta">{{ toShortDate(trip.startDate) }} · {{ trip.stopCount }}곳</span></span>
              <i class="ic i-next subtle" aria-hidden="true"></i>
            </RouterLink>
          </div>
          <p v-if="!done.length" class="t-cap">아직 다녀온 여행이 없어요</p>
        </section>
        <RouterLink v-if="archivedCount" to="/trips/archive" class="textlink tn" style="align-self: center">보관함 {{ archivedCount }}</RouterLink>
        </template>
      </div>

      <TabBar active="trips" />
    </div>
  </div>
</template>

<style>
.scr-Trips .trips-sec { display: flex; flex-direction: column; gap: 10px; }
.scr-Trips .now-trip { flex-direction: row; align-items: flex-end; justify-content: space-between; gap: 8px; }
.scr-Trips .tr-new{background:var(--navy-soft);color:var(--navy)}
.scr-Trips .tr-new:hover{background:#DCE4F2}
.scr-Trips .tr-new .row__title{color:var(--navy);font-weight:700}
.scr-Trips .tr-new .row__meta{color:var(--navy);opacity:.75}
.scr-Trips .tr-new .ic{color:var(--navy)}
.scr-Trips .tr-new__ic{display:flex;align-items:center;justify-content:center;background:#fff;border:1px solid #C9D5EA}
.scr-Trips .tr-new__ic .ic{color:var(--navy);width:22px;height:22px}
</style>
