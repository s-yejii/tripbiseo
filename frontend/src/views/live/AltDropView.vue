<script setup>
// S16-b 대안 · 한 곳 빼기 (④): 남은 장소 중 뺄 곳을 고르고, 남는 시간을 다음 장소에 쓸지 정해요
// 뺄 수 있는 곳마다 "빼면 어떻게 되는지"를 서버가 계산(POST /live/preview)하고, 고른 대로 적용(POST /live/apply)해요.
// 고정 일정·꼭 갈 곳은 뺄 수 없어요.
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'
import PageMessage from '@/components/common/PageMessage.vue'
import AltTabs from '@/components/live/AltTabs.vue'
import LateBadge from '@/components/live/LateBadge.vue'
import { importance } from '@/data/places'
import { useReplanStore } from '@/stores/replan'
import { imageFor } from '@/utils/mapView'
import { showToast } from '@/utils/toast'

const BUFFER_MIN = 10 // 남는 시간을 다음 장소에 줄 때 남겨 둘 여유 (실제 길찾기가 추정보다 몇 분 길 수 있어서)
const EXTRA_DIVIDE = 2 // 다음 장소에는 뺀 곳 머묾의 절반까지만 더 줌
const STEP_MIN = 5

const router = useRouter()
const replan = useReplanStore()
const results = ref({}) // 칸 id → 그 칸을 뺐을 때 계산
const dropId = ref(null) // 고른 뺄 곳
const useExtra = ref(true) // 남는 시간을 다음 장소에 쓰기
const final = ref(null) // 고른 대로(남는 시간 포함) 계산한 결과
const isApplying = ref(false)

onMounted(async () => {
  await replan.load()
  if (!replan.base) return
  for (const stop of droppable.value) {
    try {
      results.value[stop.id] = await replan.preview(listWithout(stop.id, null))
    } catch (e) {
      showToast(e.message)
    }
  }
  dropId.value = recommendedDropId()
})

const droppable = computed(() => replan.stops.filter((stop) => !stop.fixed && stop.importance !== 'must'))
const basisName = computed(() => (replan.basisStop ? replan.basisStop.name : '마지막 일정'))
const dropStop = computed(() => replan.stops.find((stop) => stop.id === dropId.value) || null)

// 서버 추천(B 한 곳 빼기)이 뺀 곳, 없으면 빼면 여유가 가장 큰 곳
function recommendedDropId() {
  const option = replan.findOption('한 곳 빼기')
  if (option) {
    const keptIds = option.stops.map((stop) => stop.id)
    const dropped = droppable.value.find((stop) => !keptIds.includes(stop.id))
    if (dropped) return dropped.id
  }
  let best = null
  for (const stop of droppable.value) {
    const result = results.value[stop.id]
    if (result && (!best || result.slackMin > results.value[best].slackMin)) best = stop.id
  }
  return best
}

// 남는 시간을 받을 다음 장소: 뺀 곳 바로 다음 칸 (고정 일정·꼭 갈 곳이 아닐 때만)
const extra = computed(() => {
  if (!dropStop.value || !results.value[dropId.value]) return null
  const index = replan.stops.indexOf(dropStop.value)
  const next = replan.stops[index + 1]
  if (!next || next.fixed) return null
  const isBeforeBasis = index + 1 < replan.stops.indexOf(replan.basisStop)
  // 뺀 곳 머묾의 절반까지, 고정 일정 앞이면 여유를 10분은 남김
  const half = dropStop.value.stay / EXTRA_DIVIDE
  const room = isBeforeBasis ? results.value[dropId.value].slackMin - BUFFER_MIN : half
  const minutes = Math.floor(Math.min(half, room) / STEP_MIN) * STEP_MIN
  return minutes > 0 ? { stop: next, minutes } : null
})

const extraText = computed(() => {
  if (!extra.value) return '다음 장소에 더 쓸 시간이 없어요'
  if (!useExtra.value) return '남는 시간은 비워 둬요'
  return `${extra.value.stop.name} ${extra.value.stop.stay}분 → ${extra.value.stop.stay + extra.value.minutes}분`
})

// 뺄 곳을 빼고, (있으면) 다음 장소 머묾을 늘린 목록
function listWithout(id, extraInfo) {
  const list = []
  for (const stop of replan.stops) {
    if (stop.id === id) continue
    if (extraInfo && extraInfo.stop.id === stop.id) list.push({ id: stop.id, stay: stop.stay + extraInfo.minutes })
    else list.push({ id: stop.id })
  }
  return list
}

// 고른 것·남는 시간 쓰기가 바뀌면 적용할 일정을 다시 계산
watch([dropId, useExtra], async () => {
  final.value = null
  if (!dropId.value) return
  const extraInfo = useExtra.value ? extra.value : null
  if (!extraInfo) {
    final.value = results.value[dropId.value]
    return
  }
  try {
    final.value = await replan.preview(listWithout(dropId.value, extraInfo))
  } catch (e) {
    showToast(e.message)
  }
})

function resultText(id) {
  const result = results.value[id]
  if (!result) return '계산하는 중…'
  const slack = result.slackMin < 0 ? -result.slackMin + '분 늦음' : '여유 ' + result.slackMin + '분'
  return `빼면 ${basisName.value} ${result.finalArrive} 도착 · ${slack}`
}
function resultClass(id) {
  const result = results.value[id]
  return result && result.slackMin >= 0 ? 'c-success' : 'c-warning'
}

async function apply() {
  if (!final.value || isApplying.value) return
  isApplying.value = true
  try {
    await replan.apply(final.value)
    router.replace('/live/applied')
  } catch (e) {
    showToast(e.message)
  }
  isApplying.value = false
}
</script>

<template>
  <div class="scr scr-AltDrop">
    <div class="app">
      <TopBar title="대안 고르기" back="/live/delay"><LateBadge /></TopBar>

      <div v-if="!replan.base" class="scroll">
        <PageMessage v-if="replan.error" icon="i-warning" title="대안을 만들 수 없어요" :text="replan.error" to="/live" button="여행 중 화면으로" />
        <PageMessage v-else is-loading title="지금 위치에서 다시 계산하는 중이에요" />
      </div>

      <template v-else>
      <div class="scroll" style="padding-top: 4px; gap: 12px; bottom: 102px">
        <AltTabs active="drop" />

        <div class="now-block">
          <span class="me" aria-hidden="true"></span>
          <img v-if="replan.currentStop" class="thumb" :src="imageFor(replan.currentStop.category)" alt="" style="width: 40px; height: 40px" />
          <span style="flex: 1"><span class="t-strong" style="display: block">지금 · {{ replan.currentStop ? replan.currentStop.name : '이동 중' }}</span><span class="t-cap tn">{{ replan.status.now }}</span></span>
        </div>

        <p class="divlabel">남은 일정 · 뺄 곳을 골라요</p>

        <p v-if="!droppable.length" class="t-cap">뺄 수 있는 곳이 없어요 · 남은 곳이 고정 일정이거나 꼭 갈 곳이에요</p>

        <div role="radiogroup" aria-label="뺄 장소" style="display: flex; flex-direction: column; gap: 8px">
          <template v-for="stop in replan.stops" :key="stop.id">
            <div v-if="stop.fixed" class="opt is-locked">
              <img class="thumb" :src="imageFor(stop.category)" alt="" />
              <span class="opt__body"><b class="t-sub">{{ stop.name }}</b><span class="t-cap tn">{{ stop.eta }} 고정 · 뺄 수 없어요</span></span>
              <i class="ic i-lock subtle" aria-hidden="true"></i>
            </div>
            <div v-else-if="stop.importance === 'must'" class="opt is-locked">
              <img class="thumb" :src="imageFor(stop.category)" alt="" />
              <span class="opt__body">
                <span class="drop-name"><b class="t-sub">{{ stop.name }}</b><span class="imp imp--must">꼭</span></span>
                <span class="t-cap tn">꼭 갈 곳이라 빼지 않아요</span>
              </span>
              <i class="ic i-lock subtle" aria-hidden="true"></i>
            </div>
            <button v-else type="button" role="radio" :aria-checked="dropId === stop.id" class="opt" :class="{ 'is-on': dropId === stop.id }" style="text-align: left" @click="dropId = stop.id">
              <img class="thumb" :src="imageFor(stop.category)" alt="" />
              <span class="opt__body">
                <span class="drop-name"><b class="t-sub">{{ stop.name }}</b><span class="imp" :class="'imp--' + (stop.importance || 'maybe')">{{ importance[stop.importance || 'maybe'] }}</span></span>
                <span class="t-cap tn" :class="resultClass(stop.id)">{{ resultText(stop.id) }}</span>
              </span>
              <span class="radio"></span>
            </button>
          </template>
        </div>

        <button v-if="dropStop" type="button" class="opt" role="checkbox" :aria-checked="useExtra && !!extra" :disabled="!extra" style="text-align: left; box-shadow: none" @click="useExtra = !useExtra">
          <span class="check" :class="{ 'is-on': useExtra && extra }"><i class="ic ic--s i-check" aria-hidden="true"></i></span>
          <span class="opt__body"><b class="t-strong">남는 시간은 다음 장소에 쓰기</b><span class="t-cap tn">{{ extraText }}</span></span>
        </button>
      </div>

      <div class="cta">
        <button type="button" class="btn btn--primary btn--lg btn--block" :disabled="!final || isApplying" @click="apply">{{ dropStop ? dropStop.name + ' 빼고 적용' : '뺄 곳을 골라 주세요' }}</button>
      </div>
      </template>
    </div>
  </div>
</template>

<style>
.scr-AltDrop .drop-name { display: flex; align-items: center; gap: 6px; }
</style>
