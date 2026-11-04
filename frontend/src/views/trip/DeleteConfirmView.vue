<script setup>
// S10-c 일정 빼기 확인 (②): 빼면 뒤 일정이 얼마나 앞당겨지는지 보여 줘요
// 주소: /plan/edit/delete?day=1&idx=1 &back=돌아갈 곳
// 11/04: 초안(store.draft)이 있으면 그 칸을 진짜로 빼고 그날 시각을 다시 맞춰요 (일정 점검이 다시 계산).
//   초안이 없으면 시연용 가짜 일정이에요
import { useRoute } from 'vue-router'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import { concertTrip } from '@/data/trips'
import { useTripStore } from '@/stores/trip'
import { goBack } from '@/utils/back'
import { dayStopsOf, retime, setDayStops } from '@/utils/draftEdit'
import { imageFor } from '@/utils/mapView'
import { toDuration } from '@/utils/time'
import { showToast } from '@/utils/toast'

const route = useRoute()
const store = useTripStore()
const day = Number(route.query.day || 1)
const index = Number(route.query.idx ?? 1)
const isDraft = store.draft.stops.length > 0 && !!dayStopsOf(store, day)[index]

// 시연용 가짜 값: 칸을 뺐을 때 앞당겨지는 분과 예약 여유
const pullMinutes = [60, 40, 72, 75, 48]
const slackAfter = [85, 50, 83, 86, 65]

const target = isDraft ? dayStopsOf(store, day)[index] : concertTrip.stops[index]
const stop = isDraft ? { name: target.name, fixed: !!target.fixed, img: imageFor(target.category), stay: target.stay || 0 } : target
const hasNext = isDraft ? index < dayStopsOf(store, day).length - 1 : true
let desc = stop.fixed ? '고정 일정이에요. 빼면 예약 시간 알림도 사라져요' : `뒤 일정이 ${pullMinutes[index]}분쯤 앞당겨져요`
if (isDraft && !stop.fixed) {
  desc = hasNext ? `머무는 ${toDuration(stop.stay)}만큼 뒤 일정이 앞당겨져요 (이동 시간은 다시 계산)` : '그날 마지막 일정이에요'
}

function remove() {
  if (isDraft) {
    const list = dayStopsOf(store, day)
    const startTime = list.length ? list[0].time : null
    list.splice(index, 1)
    retime(list, startTime) // 고정 칸은 그대로, 나머지 시각은 일정 점검이 다시 채움
    setDayStops(store, day, list)
  }
  goBack('/plan/edit')
  showToast(stop.name + '을(를) 뺐어요')
}
</script>

<template>
  <div class="scr scr-DeleteConfirm">
    <div class="app app--white">
      <header class="topbar" style="padding-right: 16px">
        <span class="iconbtn" aria-hidden="true"><i class="ic ic--l i-close" aria-hidden="true"></i></span>
        <h1 class="topbar__title">일정 편집</h1>
      </header>

      <ConfirmModal :img="stop.img" :title="stop.name + '을(를) 뺄까요?'" :text="desc" ok-text="빼기" ok-icon="i-trash" danger @cancel="goBack('/plan/edit')" @ok="remove">
        <div v-if="!isDraft && !stop.fixed" class="del-slack">
          <span class="t-cap">예약 여유</span>
          <b class="tn" style="margin-left: auto">{{ concertTrip.slack }}분</b><i class="ic ic--s i-next subtle" aria-hidden="true"></i><b class="tn c-success">{{ slackAfter[index] }}분</b>
        </div>
      </ConfirmModal>
    </div>
  </div>
</template>

<style>
.scr-DeleteConfirm .del-slack { display: flex; align-items: center; gap: 8px; padding: 12px; border-radius: var(--radius-md); background: var(--surface-sunken); }
</style>
