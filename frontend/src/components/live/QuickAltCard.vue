<script setup>
// 대안 고르기 화면 위 카드 (③, 11/06): 서버가 만든 "갈아타기"(택시로 바로·지하철 → 택시)나 "비 피하기"(야외 먼저)가 있으면 보여 줘요.
// 아래 세 탭(시간 줄이기·한 곳 빼기·장소 바꾸기)은 사용자가 직접 고르는 것, 이 카드는 서버가 계산을 끝낸 대안을 한 번에 적용하는 것
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useReplanStore } from '@/stores/replan'
import { showToast } from '@/utils/toast'

const QUICK_SHORTS = ['갈아타기', '비 피하기']
const router = useRouter()
const replan = useReplanStore()
const isApplying = ref(false)

const quick = computed(() => {
  for (const short of QUICK_SHORTS) {
    const option = replan.findOption(short)
    if (option) return option
  }
  return null
})
const changeText = computed(() => {
  if (!quick.value) return ''
  const changed = quick.value.stops.find((stop) => stop.change)
  return changed ? changed.name + ' · ' + changed.change : ''
})

async function applyQuick() {
  if (isApplying.value) return
  isApplying.value = true
  try {
    await replan.apply(quick.value)
    router.replace('/live/applied')
  } catch (e) {
    showToast(e.message)
  }
  isApplying.value = false
}
</script>

<template>
  <section v-if="quick" class="card card--pad qalt" aria-label="더 빨리 가는 방법">
    <div class="qalt__head">
      <span class="badge badge--navy">{{ quick.short }}</span>
      <span class="t-cap tn" :class="quick.slackMin < 0 ? 'c-warning' : 'c-success'">{{ quick.finalArrive }} 도착 · {{ quick.slackMin < 0 ? -quick.slackMin + '분 늦음' : '여유 ' + quick.slackMin + '분' }}</span>
    </div>
    <b class="t-strong">{{ quick.title }}</b>
    <span v-if="changeText" class="t-cap">{{ changeText }}</span>
    <button type="button" class="btn btn--primary btn--block" :disabled="isApplying" @click="applyQuick">이 방법으로 바꾸기</button>
  </section>
</template>

<style>
.qalt { display: flex; flex-direction: column; gap: 6px; }
.qalt__head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
</style>
