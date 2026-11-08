<script setup>
// 일정 짤 때 날씨 안내 (③, 11/08 주형: 날씨는 여행 중 돌발이 아니라 일정을 짤 때 알려 주고 반영)
//   시연 계정(개발자 모드)만: 시나리오 비 예보(서버 plan/service/DemoScenario.java, 2일차 13:00부터)를 일정 점검 화면에서 알려 주고
//   [반영하기]를 누르면 그날 일정을 "바깥 일정을 오전으로" 바꾼 순서로 바꿔요. [그대로]면 닫기만
import { computed, onMounted, ref } from 'vue'
import { getDevScenario } from '@/api/dev'
import { devMode } from '@/utils/devMode'
import { dayStopsOf, setDayStops } from '@/utils/draftEdit'
import { showToast } from '@/utils/toast'

const props = defineProps({
  draft: { type: Object, required: true }, // 일정 초안 (store.draft)
})
const emit = defineEmits(['applied'])

const setup = ref(null)
const isClosed = ref(false)
const OUTDOOR = /한강|공원|숲|산책/

onMounted(async () => {
  if (!devMode.isOn) return
  try {
    setup.value = await getDevScenario()
  } catch (e) {
    setup.value = null // 시연 계정이 아니면 안 보임
  }
})

// 비 오는 날에 비 시작 뒤 바깥 일정이 있을 때만 보여 줌
const isShown = computed(() => {
  if (isClosed.value || !setup.value || !setup.value.rainDay) return false
  const list = dayStopsOf(props.draft, setup.value.rainDay)
  return list.some((stop) => stop.time && stop.time >= setup.value.rainFrom && OUTDOOR.test(stop.name + ' ' + (stop.category || '')))
})

function apply() {
  const stops = setup.value.rainDayStops.map((stop) => ({ ...stop }))
  setDayStops(props.draft, setup.value.rainDay, stops)
  isClosed.value = true
  showToast(setup.value.rainDay + '일차 바깥 일정을 오전으로 옮겼어요')
  emit('applied')
}
</script>

<template>
  <div v-if="isShown" class="rain-notice" role="status">
    <i class="ic ic--s i-warning" aria-hidden="true"></i>
    <span class="rain-notice__text"><b>{{ setup.rainDay }}일차 {{ setup.rainFrom }}부터 비 예보</b><br />바깥 일정을 오전으로 옮길까요?</span>
    <div class="rain-notice__actions">
      <button type="button" class="btn btn--secondary btn--sm" @click="isClosed = true">그대로</button>
      <button type="button" class="btn btn--primary btn--sm" @click="apply">반영하기</button>
    </div>
  </div>
</template>

<style>
.rain-notice { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 10px; padding: 10px 12px; border-radius: 12px; background: var(--warning-soft, #fff4e0); color: var(--ink); font-size: 13px; line-height: 18px; }
.rain-notice__text { flex: 1; min-width: 0; }
.rain-notice__actions { display: flex; gap: 6px; }
</style>
