<script setup>
// 대안 고르기 화면 위 카드 (③, 11/06): 서버가 만든 "갈아타기"(택시로 바로·지하철 → 택시)나 "비 피하기"(야외 먼저)가 있으면 보여 줘요.
// 아래 세 탭(시간 줄이기·한 곳 빼기·장소 바꾸기)은 사용자가 직접 고르는 것, 이 카드는 서버가 계산을 끝낸 대안을 한 번에 적용하는 것
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useReplanStore } from '@/stores/replan'
import { showToast } from '@/utils/toast'

const QUICK_SHORTS = ['지하철', '택시', '갈아타기', '비 피하기'] // 지하철·택시: 남은 곳이 예약(KTX)뿐일 때 바로 가는 방법 (11/08)
const router = useRouter()
const replan = useReplanStore()
const isApplying = ref(false)

// 보여 줄 카드들: 빠른 도착 순 (맨 앞 = 추천)
const quickList = computed(() => {
  const list = []
  for (const short of QUICK_SHORTS) {
    const option = replan.findOption(short)
    if (option) list.push(option)
  }
  return list.sort((a, b) => b.slackMin - a.slackMin)
})
function changeTextOf(option) {
  const changed = option.stops.find((stop) => stop.change)
  return changed ? changed.name + ' · ' + changed.change : ''
}

async function applyQuick(option) {
  if (isApplying.value) return
  isApplying.value = true
  try {
    await replan.apply(option)
    router.replace('/live/applied')
  } catch (e) {
    showToast(e.message)
  }
  isApplying.value = false
}
</script>

<template>
  <section v-for="(quick, i) in quickList" :key="quick.short" class="card card--pad qalt" aria-label="더 빨리 가는 방법">
    <div class="qalt__head">
      <span class="badge badge--navy">{{ i === 0 ? '추천 · ' : '' }}{{ quick.short }}</span>
      <span class="t-cap tn" :class="quick.slackMin < 0 ? 'c-warning' : 'c-success'">{{ quick.finalArrive }} 도착 · {{ quick.slackMin < 0 ? -quick.slackMin + '분 늦음' : '여유 ' + quick.slackMin + '분' }}</span>
    </div>
    <b class="t-strong">{{ quick.title }}</b>
    <span v-if="quick.reason" class="t-cap">{{ quick.reason }}</span>
    <span v-else-if="changeTextOf(quick)" class="t-cap">{{ changeTextOf(quick) }}</span>
    <button type="button" class="btn btn--block" :class="i === 0 ? 'btn--primary' : 'btn--secondary'" :disabled="isApplying" @click="applyQuick(quick)">이 방법으로 바꾸기</button>
  </section>
</template>

<style>
.qalt { display: flex; flex-direction: column; gap: 6px; }
.qalt__head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
</style>
