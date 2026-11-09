<script setup>
// S11 여행 상세 · 여행 전 (②): 다가오는 여행(?id=)의 일정과 챙길 것
import { computed, onMounted, ref, watch } from 'vue'
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
import ChipScroller from '@/components/common/ChipScroller.vue'
import TripWeather from '@/components/plan/TripWeather.vue'
import { addDays } from '@/utils/dates'
import { toShortDate } from '@/utils/tripView'

const route = useRoute()
const { saved, view, isLoading, error, load, loadDay } = useTripDetail()
onMounted(() => load(route.query.id))
// 여러 날 여행이면 1일차·2일차 고르기 (11/06: 예전엔 1일차만 보였음)
const day = ref(1)
const dayList = computed(() => {
  if (!saved.value) return []
  let last = 1
  for (const stop of saved.value.stops) last = Math.max(last, stop.day || 1)
  const list = []
  for (let d = 1; d <= last; d++) list.push({ day: d, label: toShortDate(addDays(saved.value.startDate, d - 1)) })
  return list
})
const dayDate = computed(() => (saved.value ? addDays(saved.value.startDate, day.value - 1) : ''))
const dayStops = computed(() => (saved.value ? saved.value.stops.filter((stop) => (stop.day || 1) === day.value) : []))
async function pickDay(d) {
  if (d === day.value) return
  day.value = d
  optimize.reset()
  await loadDay(d)
}
// 칸 사진 (홈 카드와 같은 함수)
const stopPhotos = useStopPhotos(() => dayStops.value)
// 동선 최적화: 누를 때마다 다음 후보로 바꿔 서버에 저장 (PUT /api/trips/{id}/stops) → 다시 읽어 이동 시간 비교
const optimize = useRouteOptimize({
  stopsOf: () => dayStopsOf(saved.value, day.value),
  dateOf: () => dayDate.value,
  transportsOf: () => saved.value.transports,
  apply: applyOrder,
})
async function saveStops(stops) {
  await updateStops(saved.value.id, stops)
  await load(route.query.id)
  if (day.value !== 1) await loadDay(day.value) // 보던 날 그대로
}
async function applyOrder(list) {
  const before = view.value.move
  const backup = JSON.parse(JSON.stringify(saved.value.stops))
  setDayStops(saved.value, day.value, list)
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
      <template #head>
        <ChipScroller v-if="dayList.length > 1" class="td-days" role="tablist" aria-label="일차">
          <button v-for="d in dayList" :key="d.day" type="button" role="tab" class="chip chip--sm" :class="{ 'is-on': day === d.day }" :aria-selected="day === d.day" @click="pickDay(d.day)">
            {{ d.day }}일차<span class="t-cap tn" style="margin-left: 4px">{{ d.label }}</span>
          </button>
        </ChipScroller>
        <TripWeather :date="dayDate" :stops="dayStops" />
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
