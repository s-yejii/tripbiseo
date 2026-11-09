<script setup>
// S11 여행 상세 · 여행 전 (②): 다가오는 여행(?id=)의 일정과 챙길 것
import { computed, onMounted, watch } from 'vue'
import { useRouteSim } from '@/utils/useRouteSim'
import RouteSimButton from '@/components/trip/RouteSimButton.vue'
import { useRoute } from 'vue-router'
import TripPage from '@/components/trip/TripPage.vue'
import PackList from '@/components/trip/PackList.vue'
import PageMessage from '@/components/common/PageMessage.vue'
import TopBar from '@/components/common/TopBar.vue'
import { useTripDetail } from '@/utils/useTripDetail'
import { tripBadge } from '@/utils/tripView'
import { useStopPhotos } from '@/utils/useStopPhotos'
import { useRouteOptimize } from '@/utils/useRouteOptimize'
import { dayStopsOf, setDayStops } from '@/utils/draftEdit'
import { updateStops } from '@/api/trips'
import { showToast } from '@/utils/toast'

const route = useRoute()
const { saved, view, isLoading, error, load } = useTripDetail()
onMounted(() => load(route.query.id))
// 칸 사진 (홈 카드와 같은 함수)
const stopPhotos = useStopPhotos(() => (saved.value ? saved.value.stops.filter((stop) => (stop.day || 1) === 1) : []))
// 동선 최적화: 누를 때마다 다음 후보로 바꿔 서버에 저장 (PUT /api/trips/{id}/stops) → 다시 읽어 이동 시간 비교
const optimize = useRouteOptimize({
  stopsOf: () => dayStopsOf(saved.value, 1),
  dateOf: () => saved.value.startDate,
  transportsOf: () => saved.value.transports,
  apply: applyOrder,
})
async function saveStops(stops) {
  await updateStops(saved.value.id, stops)
  await load(route.query.id)
}
async function applyOrder(list) {
  const before = view.value.move
  const backup = JSON.parse(JSON.stringify(saved.value.stops))
  setDayStops(saved.value, 1, list)
  await saveStops(saved.value.stops)
  showToast(`이동 ${before}분 → ${view.value.move}분`, {
    isTop: true,
    actionText: '되돌리기',
    action: async () => {
      optimize.reset()
      await saveStops(backup)
    },
  })
}
const shownTrip = computed(() => (view.value ? {
  ...view.value,
  stops: view.value.stops.map((stop) => ({ ...stop, img: stopPhotos.value[stop.name] || stop.img, photo: stopPhotos.value[stop.name] || null })),
} : null))
// 이동 미리보기 (일정 결과 화면과 같은 함수·버튼)
const sim = useRouteSim(() => shownTrip.value)
watch(view, sim.reset)
</script>

<template>
  <div class="scr scr-TripDetail">
    <TripPage v-if="view" :trip="shownTrip" :badge="tripBadge(saved)" back="/trips" :trip-id="saved.id" :sim-me="sim.me.value" :active-stop="sim.activeStop.value">
      <template #right>
        <RouteSimButton :sim="sim" />
      </template>
      <template #below>
        <!-- 챙길 것: API가 아직 "확인 필요"라 이 화면에서만 적어 둬요 (새로고침하면 사라짐) -->
        <PackList :items="[]" />
      </template>
      <template #cta>
        <RouterLink :to="'/plan/edit?id=' + view.id" class="btn btn--primary btn--lg btn--block"><i class="ic i-edit" aria-hidden="true"></i>일정 편집하기</RouterLink>
      </template>
    </TripPage>
    <div v-else class="app">
      <TopBar title="여행 상세" back="/trips" />
      <div class="scroll">
        <PageMessage v-if="isLoading" is-loading title="여행을 불러오는 중이에요" />
        <PageMessage v-else-if="error" icon="i-warning" title="여행을 불러오지 못했어요" :text="error" button="다시 시도" @retry="load(route.query.id)" />
        <PageMessage v-else title="여행을 찾을 수 없어요" text="내 여행에서 볼 여행을 골라 주세요" to="/trips" button="내 여행 보기" />
      </div>
    </div>
  </div>
</template>
