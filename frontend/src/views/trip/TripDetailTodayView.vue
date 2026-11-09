<script setup>
// S11-b 여행 상세 · 여행 날 (②): 오늘 여행의 일정, 미리 확인할 것(일정 점검), 챙길 것
// 주소의 ?id= 여행, 없으면 진행 중·오늘 여행을 서버에서 읽어요. 둘 다 없으면 안내.
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { setDevClock } from '@/api/dev'
import { addDays, todayText } from '@/utils/dates'
import { devMode, syncClock } from '@/utils/devMode'
import { toMinutes, toTime } from '@/utils/time'
import TripPage from '@/components/trip/TripPage.vue'
import PackList from '@/components/trip/PackList.vue'
import PageMessage from '@/components/common/PageMessage.vue'
import TopBar from '@/components/common/TopBar.vue'
import { useTripStore } from '@/stores/trip'
import { useTripDetail } from '@/utils/useTripDetail'
import { tripBadge } from '@/utils/tripView'
import { useStartTrip } from '@/utils/useStartTrip'
import StartConflictSheet from '@/components/trip/StartConflictSheet.vue'

const route = useRoute()
const store = useTripStore()
const { saved, view, isLoading, error, load, loadDay } = useTripDetail()
const { conflict, start, retry } = useStartTrip()

onMounted(async () => {
  const tripId = route.query.id || store.tripId || (await store.findOngoingTripId())
  await load(tripId)
  const day = Number(route.query.day || 1)
  if (day > 1) await loadDay(day) // ?day=2: 그날 일정으로 (11/08: 여행 중 화면 "2일차 일정 보기")
})


// 여행 이어가기: 여행 중 화면으로. 시연 계정이 다음 날 일정을 보고 누르면 그날 아침으로 시계를 옮겨 그날을 시작 (11/08 주형)
const MORNING_BEFORE_MIN = 60 // 그날 첫 장소 몇 분 전으로 (숙소에서 일어난 때)
const router = useRouter()
async function continueTrip() {
  const day = Number(route.query.day || 1)
  if (devMode.isOn && day > 1 && saved.value) {
    const date = addDays(saved.value.startDate, day - 1)
    const first = saved.value.stops.filter((stop) => (stop.day || 1) === day && stop.state !== 'skipped').sort((a, b) => a.time.localeCompare(b.time))[0]
    if (first && todayText() < date) {
      const status = await setDevClock(date, toTime(toMinutes(first.time) - MORNING_BEFORE_MIN)).catch(() => null)
      if (status) syncClock(status)
    }
  }
  router.push('/live')
}
</script>

<template>
  <div class="scr scr-TripDetailToday">
    <TripPage v-if="view" :trip="view" :badge="tripBadge(saved)" back="/" :trip-id="saved.id">
      <template #below>
        <!-- 챙길 것: API가 아직 "확인 필요"라 이 화면에서만 적어 둬요 (새로고침하면 사라짐) -->
        <PackList :items="[]" />
      </template>
      <template #cta>
        <button v-if="saved.status === 'ongoing'" type="button" class="btn btn--primary btn--lg btn--block" @click="continueTrip">여행 이어가기</button>
        <div v-else class="cta__row">
          <RouterLink :to="'/plan/edit?id=' + view.id" class="btn btn--secondary btn--lg"><i class="ic i-edit" aria-hidden="true"></i>편집</RouterLink>
          <button type="button" class="btn btn--primary btn--lg" :disabled="!view.stops.length" @click="start(saved.id)">여행 시작</button>
        </div>
      </template>
    </TripPage>
    <Teleport v-if="conflict" to=".scr-TripDetailToday .app">
      <StartConflictSheet :trip="conflict" @done="retry" @cancel="conflict = null" />
    </Teleport>
    <div v-else-if="!view" class="app">
      <TopBar title="오늘 여행" back="/" />
      <div class="scroll">
        <PageMessage v-if="isLoading" is-loading title="여행을 불러오는 중이에요" />
        <PageMessage v-else-if="error" icon="i-warning" title="여행을 불러오지 못했어요" :text="error" button="다시 시도" @retry="load(route.query.id || store.tripId)" />
        <PageMessage v-else title="오늘 여행이 없어요" text="새 여행을 만들거나 내 여행에서 골라 주세요" to="/trips" button="내 여행 보기" />
      </div>
    </div>
  </div>
</template>
