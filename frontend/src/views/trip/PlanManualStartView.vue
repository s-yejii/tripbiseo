<script setup>
// 직접 고르기 시작 (②): 질문 없이 한 화면에서 어디(구 지도)·언제(날짜·시작 시각)만 고르고 바로 지도 화면으로
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'
import GuMapPicker from '@/components/plan/GuMapPicker.vue'
import RangeCalendar from '@/components/plan/RangeCalendar.vue'
import { useTripStore } from '@/stores/trip'
import { showToast } from '@/utils/toast'
import { daysBetween } from '@/utils/dates'

const DEFAULT_START = '10:00'
const router = useRouter()
const store = useTripStore()
const startTime = ref(DEFAULT_START)

onMounted(() => {
  store.resetDraft()
  store.resetPlan()
})

const canNext = computed(() => store.planAreas.length > 0 && !!store.planStartDate)

// 고른 구 저장 (planArea는 일정 만들기 흐름이 같이 씀)
function setAreas(areas) {
  store.planAreas = areas
  const first = areas[0]
  store.planArea = first ? { name: first.name, fullName: '서울 ' + first.name, type: 'gu', lat: first.lat, lng: first.lng } : null
}

function titleOf(endDate) {
  const nights = daysBetween(store.planStartDate, endDate)
  const names = store.planAreas.map((area) => area.name).join('·')
  return names + (nights > 0 ? ` ${nights}박 ${nights + 1}일` : ' 하루')
}

function goNext() {
  if (!store.planAreas.length) return showToast('구를 골라 주세요')
  if (!store.planStartDate) return showToast('날짜를 골라 주세요')
  const endDate = store.planEndDate || store.planStartDate
  store.setDraft({ title: titleOf(endDate), startDate: store.planStartDate, endDate, people: store.planPeople || 2, transports: ['subway', 'bus', 'walk'], stops: [] })
  store.planStartTime = startTime.value
  router.push('/plan/manual')
}
</script>

<template>
  <div class="scr scr-ManualStart">
    <div class="app">
      <TopBar title="어디로, 언제 가요?" back="/new" />
      <div class="scroll" style="bottom: 104px; gap: 12px; padding-top: 4px">
        <GuMapPicker :model-value="store.planAreas" @update:model-value="setAreas" />
        <section class="card card--pad" aria-label="날짜">
          <RangeCalendar v-model:start="store.planStartDate" v-model:end="store.planEndDate" />
        </section>
        <section class="card card--pad ms-time">
          <label for="ms-start" class="t-strong">시작 시각</label>
          <input id="ms-start" v-model="startTime" type="time" class="ms-time__input tn" />
        </section>
      </div>
      <div class="cta">
        <button type="button" class="btn btn--primary btn--lg btn--block" :class="{ 'is-disabled': !canNext }" @click="goNext">지도에서 고르기</button>
      </div>
    </div>
  </div>
</template>

<style>
.scr-ManualStart .ms-time { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.scr-ManualStart .ms-time__input { height: 44px; padding: 0 12px; border: 1px solid var(--line-strong); border-radius: var(--radius-md); background: var(--surface-raised); font: inherit; font-size: 16px; color: var(--ink); }
</style>
