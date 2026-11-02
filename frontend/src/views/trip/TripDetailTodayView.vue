<script setup>
// S11-b 여행 상세 · 여행 날 (②): 오늘 여행의 일정, 미리 확인할 것, 챙길 것
// 주소에 ?id=가 있거나 오늘 여행이 있으면 서버 여행(GET /api/trips/{id}) + 일정 점검 결과를 보여 줘요.
// 서버가 꺼져 있거나 여행이 없으면 시연용 가짜 일정(concertTrip)을 보여 줘요.
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import TripPage from '@/components/trip/TripPage.vue'
import PackList from '@/components/trip/PackList.vue'
import { concertTrip } from '@/data/trips'
import { checkPlan, getTrip } from '@/api/trips'
import { useTripStore } from '@/stores/trip'
import { toViewTrip } from '@/utils/tripView'

const route = useRoute()
const store = useTripStore()
const view = ref(null)
const trip = computed(() => view.value || concertTrip)

onMounted(async () => {
  const tripId = route.query.id || store.tripId || (await store.findOngoingTripId())
  if (!tripId) return
  try {
    const saved = await getTrip(tripId)
    store.tripId = saved.id
    const check = saved.stops.length ? await checkPlan(saved, 1) : null
    view.value = toViewTrip(saved, check)
  } catch {
    view.value = null // 시연용 가짜 일정 그대로
  }
})
</script>

<template>
  <div class="scr scr-TripDetailToday">
    <TripPage :trip="trip" :badge="{ text: '오늘', cls: 'badge--coral' }" back="/">
      <template #below>
        <PackList :items="trip.pack" />
      </template>
      <template #cta>
        <RouterLink to="/plan/edit" class="btn btn--primary btn--lg btn--block"><i class="ic i-edit" aria-hidden="true"></i>일정 편집하기</RouterLink>
      </template>
    </TripPage>
  </div>
</template>
