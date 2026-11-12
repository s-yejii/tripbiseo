<script setup>
// S16-e 비슷한 근처 가게 (④ 11/12): 놓친 식당·카페와 업종이 겹치는 곳을 고른 반경 안에서 3곳 (같은 브랜드 다른 지점 먼저).
//   고르면 놓친 칸 자리에 넣고 남은 일정을 다시 계산해요 (POST /live/insert + replaceStopId). LiveView의 놓침 카드에서 열어요
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'
import { askRecommend, insertPlace } from '@/api/trips'
import { radiusOptions } from '@/data/ask'
import { showToast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const tripId = Number(route.query.tripId)
const stopId = Number(route.query.stopId)
const placeId = route.query.placeId
const name = route.query.name || ''
const lat = Number(route.query.lat)
const lng = Number(route.query.lng)

const radius = ref(null)  // 고른 반경 (기존 추천과 같은 300·500·1000m 중에서 사용자가 고름)
const places = ref([])
const closedSkipped = ref(0)
const isLoading = ref(false)
const isApplying = ref(false)

// 반경을 고르면 찾기
async function search(meters) {
  radius.value = meters
  isLoading.value = true
  try {
    const res = await askRecommend(tripId, lat, lng, meters, null, [], placeId)
    places.value = res.places
    closedSkipped.value = res.closedSkipped
  } catch (e) {
    places.value = []
    showToast(e.message)
  }
  isLoading.value = false
}

// 이 가게로 바꾸기: 놓친 칸 자리에 넣고 여행 중 화면으로
async function choose(place) {
  if (isApplying.value) return
  isApplying.value = true
  try {
    const res = await insertPlace(tripId, place.id, lat, lng, stopId)
    showToast(res.message)
    router.replace('/live')
  } catch (e) {
    showToast(e.message)
  }
  isApplying.value = false
}
</script>

<template>
  <div class="scr">
    <div class="app">
      <TopBar title="비슷한 근처 가게" back="/live" />
      <div class="scroll" style="gap: 12px">
        <h1 class="h-title" style="font-size: 20px; line-height: 28px">{{ name }}와(과)<br />비슷한 근처 가게</h1>
        <p class="t-cap">얼마나 가까운 곳에서 찾을까요?</p>
        <div style="display: flex; gap: 8px">
          <button v-for="r in radiusOptions" :key="r" type="button" class="btn btn--md" :class="radius === r ? 'btn--primary' : 'btn--secondary'" @click="search(r)">{{ r }}m</button>
        </div>
        <p v-if="isLoading" class="t-cap">찾는 중이에요</p>
        <p v-else-if="radius && places.length === 0" class="t-cap">이 반경에는 비슷한 가게가 없어요 · 더 넓게 찾아보세요</p>
        <template v-else>
          <section v-for="place in places" :key="place.id" class="card card--pad" style="display: flex; flex-direction: column; gap: 6px">
            <b>{{ place.name }}</b>
            <span class="t-cap tn">{{ place.category }} · {{ place.distance }}m<template v-if="place.rating"> · ★ {{ place.rating }}</template><template v-if="place.closeAt"> · {{ place.closeAt }}까지</template></span>
            <button type="button" class="btn btn--primary btn--block" :disabled="isApplying" @click="choose(place)">이 가게로 바꾸기</button>
          </section>
        </template>
        <p v-if="closedSkipped > 0" class="t-cap">지금 가면 문 닫은 {{ closedSkipped }}곳은 뺐어요</p>
      </div>
    </div>
  </div>
</template>
