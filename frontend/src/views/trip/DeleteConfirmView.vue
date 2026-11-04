<script setup>
// S10-c 일정 빼기 확인 (②): 빼면 뒤 일정이 얼마나 앞당겨지는지 보여 줘요
// 주소: /plan/edit/delete?day=1&idx=1 &back=돌아갈 곳 · 저장된 여행이면 &id=여행 번호
// 11/04: 지금 고치는 여행(초안 또는 저장된 여행 복사본)에서 그 칸을 진짜로 빼고 그날 시각을 다시 맞춰요 (일정 점검이 다시 계산).
//   서버 저장은 편집 화면 "완료"에서 한 번에 해요
import { useRoute, useRouter } from 'vue-router'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import { useTripStore } from '@/stores/trip'
import { goBackTo } from '@/utils/back'
import { dayStopsOf, editingTripOf, retime, setDayStops } from '@/utils/draftEdit'
import { imageFor } from '@/utils/mapView'
import { toDuration } from '@/utils/time'
import { showToast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const store = useTripStore()
const day = Number(route.query.day || 1)
const index = Number(route.query.idx ?? 1)
const backLink = route.query.back || '/plan/edit' // 끝나면 돌아갈 편집 화면
const trip = editingTripOf(store, route.query.id)

// 뺄 칸 (없으면 새로고침 등으로 편집 내용이 사라진 것 → 편집 화면으로)
const list = trip ? dayStopsOf(trip, day) : []
const target = list[index]
if (!target) router.replace(backLink)
const stop = { name: target ? target.name : '', fixed: !!(target && target.fixed), img: imageFor(target ? target.category : null), stay: target ? target.stay || 0 : 0 }
const hasNext = index < list.length - 1
let desc = hasNext ? `머무는 ${toDuration(stop.stay)}만큼 뒤 일정이 앞당겨져요 (이동 시간은 다시 계산)` : '그날 마지막 일정이에요'
if (stop.fixed) desc = '고정 일정이에요. 빼면 예약 시간 알림도 사라져요'

function remove() {
  const startTime = list[0].time
  const rest = [...list]
  rest.splice(index, 1)
  retime(rest, startTime) // 고정 칸은 그대로, 나머지 시각은 일정 점검이 다시 채움
  setDayStops(trip, day, rest)
  goBackTo(backLink)
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

      <ConfirmModal :img="stop.img" :title="stop.name + '을(를) 뺄까요?'" :text="desc" ok-text="빼기" ok-icon="i-trash" danger @cancel="goBackTo(backLink)" @ok="remove" />
    </div>
  </div>
</template>

