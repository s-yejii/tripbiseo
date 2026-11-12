<script setup>
// 대안 고르기 화면 위 카드 (③, 11/06): 서버가 만든 "갈아타기"(택시로 바로·지하철 → 택시)나 "비 피하기"(야외 먼저)가 있으면 보여 줘요.
// 아래 세 탭(시간 줄이기·한 곳 빼기·장소 바꾸기)은 사용자가 직접 고르는 것, 이 카드는 서버가 계산을 끝낸 대안을 한 번에 적용하는 것
import { computed, onMounted, ref, watch } from 'vue'
import { getPlaceDetail } from '@/api/trips'
import { devMode } from '@/utils/devMode'
import { useRouter } from 'vue-router'
import { useReplanStore } from '@/stores/replan'
import { showToast } from '@/utils/toast'

const QUICK_SHORTS = ['지하철', '택시', '갈아타기', '비 피하기'] // 지하철·택시: 남은 곳이 예약(KTX)뿐일 때 바로 가는 방법 (11/08)
const router = useRouter()
const replan = useReplanStore()
const isApplying = ref(false)
const emit = defineEmits(['empty'])

// 보여 줄 카드들: 빠른 도착 순 (맨 앞 = 추천)
const quickList = computed(() => {
  const list = []
  for (const short of QUICK_SHORTS) {
    const option = replan.findOption(short)
    if (option) list.push(option)
  }
  return list.sort((a, b) => b.slackMin - a.slackMin)
})
// 11/12 ④: 빨리 가는 방법이 하나도 없으면(예약 앞에 다른 일정이 있고 택시도 안 빠를 때) 빈 화면이 돼서 알려 줌 → 화면이 원래 대안 화면으로 넘김
watch(() => quickList.value.length, (count) => {
  if (count === 0) emit('empty')
}, { immediate: true })
function changeTextOf(option) {
  const changed = option.stops.find((stop) => stop.change)
  return changed ? changed.name + ' · ' + changed.change : ''
}

// 다 늦을 때: 빈 화면 대신 가장 빨리 가는 방법을 알려 줌 (11/08 주형)
const isAllLate = computed(() => quickList.value.length > 0 && quickList.value.every((option) => option.slackMin < 0))
// 예약 장소: 식당이면 "가게에 늦는다고 알리기"(전화), 기차역이면 다음 열차 안내 한 줄
const fixedStop = computed(() => (replan.basisStop && replan.basisStop.fixed ? replan.basisStop : null))
const isStation = computed(() => !!fixedStop.value && /역|KTX|기차/.test((fixedStop.value.category || '') + ' ' + fixedStop.value.name))
const tel = ref('')
onMounted(async () => {
  const stop = fixedStop.value
  if (!stop || isStation.value || !stop.placeId) return
  const place = await getPlaceDetail(stop.placeId).catch(() => null)
  tel.value = place && place.tel ? place.tel : ''
})
// 시연 계정은 실제로 걸지 않고 번호만 보여 줌 (발표장에서 진짜 가게에 전화가 가지 않게)
function callDemo() {
  showToast(fixedStop.value.name + ' · ' + tel.value + ' 전화 연결')
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
  <p v-if="isAllLate" class="qalt-note"><i class="ic ic--s i-warning" aria-hidden="true"></i>늦지 않는 방법이 없어요 · 가장 빨리 가는 방법은 {{ quickList[0].short }} ({{ -quickList[0].slackMin }}분 늦음)</p>
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
  <section v-if="fixedStop && !isStation && tel" class="card card--pad qalt" aria-label="가게에 알리기">
    <b class="t-strong">{{ fixedStop.name }}에 늦는다고 알리기</b>
    <span class="t-cap">예약 시간보다 조금 늦어도 괜찮은지 가게에 물어봐요 · {{ tel }}</span>
    <button v-if="devMode.isOn" type="button" class="btn btn--secondary btn--block" @click="callDemo"><i class="ic ic--s i-phone" aria-hidden="true"></i>가게에 전화하기</button>
    <a v-else :href="'tel:' + tel" class="btn btn--secondary btn--block"><i class="ic ic--s i-phone" aria-hidden="true"></i>가게에 전화하기</a>
  </section>
  <p v-if="isStation && isAllLate" class="qalt-note">다음 열차로 바꾸기는 코레일톡 앱에서 할 수 있어요</p>
</template>

<style>
.qalt-note { display: flex; align-items: center; gap: 6px; margin: 0; font-size: 13px; line-height: 18px; color: var(--warning); }
.qalt { display: flex; flex-direction: column; gap: 6px; }
.qalt__head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
</style>
