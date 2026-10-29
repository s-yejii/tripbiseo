<script setup>
// S10-c 일정 빼기 확인 (②): 빼면 뒤 일정이 얼마나 앞당겨지고 예약 여유가 어떻게 되는지 보여 줘요
// 주소: /plan/edit/delete?idx=1
import { useRoute } from 'vue-router'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import { concertTrip } from '@/data/trips'
import { goBack } from '@/utils/back'
import { showToast } from '@/utils/toast'

const route = useRoute()
const index = Number(route.query.idx ?? 1)
const stop = concertTrip.stops[index]

// 가짜 데이터: 칸을 뺐을 때 앞당겨지는 분과 예약 여유 (백엔드가 다시 계산해 줄 값)
const pullMinutes = [60, 40, 72, 75, 48]
const slackAfter = [85, 50, 83, 86, 65]

const desc = stop.fixed ? '고정 일정이에요. 빼면 예약 시간 알림도 사라져요' : `뒤 일정이 ${pullMinutes[index]}분쯤 앞당겨져요`

function remove() {
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
        <div v-if="!stop.fixed" class="del-slack">
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
