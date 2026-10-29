<script setup>
// S10-a 일정 점검 (②): 만든 일정을 지도와 목록으로 보고, 미리 확인할 것을 살핀 뒤 저장해요
import { useRouter } from 'vue-router'
import TripPage from '@/components/trip/TripPage.vue'
import { concertTrip } from '@/data/trips'
import { showToast } from '@/utils/toast'

const router = useRouter()

// 편집 버튼: 고른 칸이 있으면 "성내동 카페 편집"처럼 보여요
function editLabel(selected) {
  if (selected === null) return '편집'
  return concertTrip.stops[selected].name.replace('천호 ', '') + ' 편집'
}
function save() {
  router.push('/trips/today')
  showToast('일정을 저장했어요')
}
</script>

<template>
  <div class="scr scr-PlanCheck">
    <TripPage :trip="concertTrip" back="/trips" rain-swap>
      <template #right="{ selected }">
        <RouterLink to="/plan/edit" class="btn btn--sm btn--float"><i class="ic ic--s i-edit" aria-hidden="true"></i>{{ editLabel(selected) }}</RouterLink>
      </template>
      <template #cta>
        <div class="cta__row">
          <RouterLink to="/plan/manual" class="btn btn--secondary btn--lg"><i class="ic i-plus" aria-hidden="true"></i>장소 추가</RouterLink>
          <button type="button" class="btn btn--primary btn--lg" @click="save">이 일정으로 저장</button>
        </div>
      </template>
    </TripPage>
  </div>
</template>
