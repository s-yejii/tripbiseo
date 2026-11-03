<script setup>
// S16-a2 시간 줄이기 슬라이더 (④): 한 장소(?id=)의 머무는 시간을 왼쪽으로 밀어서 줄여요. 0분이면 일정에서 빠져요
// 숫자는 서버 계산(POST /live/preview)이에요. 끄는 동안에는 "1분 줄이면 고정 일정 도착도 1분 빨라진다"로 바로 보여 주고,
// 손을 떼면 서버가 다시 계산한 값으로 바꿔요. (고정 일정 앞 칸만 도착 시각에 영향을 줘요)
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import BottomSheet from '@/components/common/BottomSheet.vue'
import PageMessage from '@/components/common/PageMessage.vue'
import { useReplanStore } from '@/stores/replan'
import { imageFor } from '@/utils/mapView'
import { showToast } from '@/utils/toast'

const STEP_MIN = 5 // 슬라이더 한 칸

const route = useRoute()
const router = useRouter()
const replan = useReplanStore()
const stopId = Number(route.query.id)

const stay = ref(0)
const atMax = ref(null) // 이 칸은 그대로, 다른 곳 줄인 것만 반영한 계산
const checked = ref(null) // 손을 뗀 값으로 서버가 계산한 결과
const removed = ref(null) // 이 칸을 뺐을 때 서버 계산 (빼기 확인창)
const asking = ref(false)

onMounted(async () => {
  await replan.load()
  if (!stop.value) return
  const planned = replan.trimPlan[stopId]
  stay.value = planned == null || planned === 0 ? stop.value.stay : planned
  atMax.value = await replan.preview(replan.trimList(otherPlan()))
  await check()
})

const stop = computed(() => replan.stops.find((item) => item.id === stopId) || null)
const max = computed(() => (stop.value ? stop.value.stay : 0))
// 이 칸이 기준 칸(고정 일정) 앞에 있어야 줄인 만큼 도착이 빨라짐
const isBeforeBasis = computed(() => {
  const list = replan.stops
  return list.indexOf(stop.value) < list.indexOf(replan.basisStop)
})

// 이 칸 말고 다른 곳에서 정한 것
function otherPlan() {
  const plan = { ...replan.trimPlan }
  delete plan[stopId]
  return plan
}

// 지금 슬라이더 값의 여유: 서버가 같은 값으로 계산했으면 그 값, 아니면 바로 계산
const slack = computed(() => {
  if (checked.value && checked.value.stay === stay.value) return checked.value.result.slackMin
  if (!atMax.value) return null
  const saved = isBeforeBasis.value ? max.value - stay.value : 0
  return atMax.value.slackMin + saved
})
const arrive = computed(() => {
  if (checked.value && checked.value.stay === stay.value) return checked.value.result.finalArrive
  if (!atMax.value) return '-'
  const saved = isBeforeBasis.value ? max.value - stay.value : 0
  return shiftTime(atMax.value.finalArrive, -saved)
})
const isOk = computed(() => slack.value !== null && slack.value >= 0)
const cut = computed(() => max.value - stay.value)
const otherCut = computed(() => {
  let total = 0
  const plan = otherPlan()
  for (const item of replan.stops) {
    if (plan[item.id] != null) total += item.stay - plan[item.id]
  }
  return total
})

// 이 칸만 줄여서 안 늦으려면 몇 분 이하 (5분 단위). 이 칸으로 못 맞추면 null
const needStay = computed(() => {
  if (!atMax.value || atMax.value.slackMin >= 0 || !isBeforeBasis.value) return null
  const need = Math.floor((max.value + atMax.value.slackMin) / STEP_MIN) * STEP_MIN
  return need > 0 ? need : null
})
const markerLeft = computed(() => ((needStay.value / max.value) * 100).toFixed(1) + '%')

const basisName = computed(() => (replan.basisStop ? replan.basisStop.name : '마지막 일정'))
const cutText = computed(() => (cut.value === 0 ? '그대로' : '−' + cut.value + '분'))
const cutClass = computed(() => (cut.value === 0 ? 'muted' : isOk.value ? 'c-success' : 'c-warning'))
const buttonText = computed(() => (cut.value === 0 ? '줄이지 않기' : stay.value + '분으로 정하기'))

// "18:44"에서 분 더하기·빼기
function shiftTime(time, minutes) {
  if (!time) return '-'
  const total = Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5)) + minutes
  return String(Math.floor(total / 60)).padStart(2, '0') + ':' + String(total % 60).padStart(2, '0')
}

// 손을 뗐을 때: 0이면 빼기 확인, 아니면 서버로 다시 계산
async function onRelease() {
  if (stay.value === 0) return askRemove()
  await check()
}

async function check() {
  const value = stay.value
  try {
    const result = await replan.preview(replan.trimList({ ...otherPlan(), [stopId]: value }))
    checked.value = { stay: value, result }
  } catch (e) {
    showToast(e.message)
  }
}

async function askRemove() {
  try {
    removed.value = await replan.preview(replan.trimList({ ...otherPlan(), [stopId]: 0 }))
    asking.value = true
  } catch (e) {
    showToast(e.message)
  }
}

function cancelRemove() {
  asking.value = false
  stay.value = STEP_MIN
  check()
}

// 정한 값을 시간 줄이기 화면에 넘기고 돌아감 (그대로면 지움)
function done(minutes) {
  const plan = otherPlan()
  if (minutes !== max.value) plan[stopId] = minutes
  replan.trimPlan = plan
  router.replace('/live/alt')
  const name = stop.value.name
  if (minutes === 0) showToast(name + '을(를) 빼기로 했어요')
  else if (minutes === max.value) showToast(name + '은(는) 그대로 둬요')
  else showToast(`${name} ${max.value - minutes}분 줄이기로 했어요`)
}

function slackLabel(value) {
  return value < 0 ? -value + '분 늦음' : '여유 ' + value + '분'
}
</script>

<template>
  <div class="scr scr-AltTrim">
    <div class="app">
      <header class="topbar">
        <span class="iconbtn" aria-hidden="true"><i class="ic ic--l i-back" aria-hidden="true"></i></span>
        <h1 class="topbar__title">대안 고르기</h1>
      </header>
      <div class="dim" aria-hidden="true"></div>

      <BottomSheet role="dialog" aria-modal="true" style="gap: 18px">
        <PageMessage v-if="replan.error" icon="i-warning" title="계산할 수 없어요" :text="replan.error" to="/live" button="여행 중 화면으로" />
        <PageMessage v-else-if="replan.base && !stop" title="줄일 장소를 찾을 수 없어요" to="/live/alt" button="대안 고르기로" />
        <PageMessage v-else-if="!stop || !atMax" is-loading title="계산하는 중이에요" />

        <template v-else>
        <div class="sheet__head" style="gap: 12px">
          <img class="thumb" :src="imageFor(stop.category)" alt="" />
          <div style="flex: 1"><h2 class="h-title">{{ stop.name }}</h2><p class="t-cap">왼쪽으로 밀어 머무는 시간을 줄여요</p></div>
          <button type="button" class="iconbtn iconbtn--soft" aria-label="닫기" @click="router.replace('/live/alt')"><i class="ic i-close" aria-hidden="true"></i></button>
        </div>

        <div style="display: flex; align-items: baseline; justify-content: space-between">
          <span class="t-big">{{ stay }}분</span>
          <span class="t-strong tn" :class="cutClass">{{ cutText }}</span>
        </div>

        <div style="position: relative; padding-top: 18px">
          <template v-if="needStay !== null">
            <span class="t-label tn c-danger trim-mark-text" :style="{ left: `clamp(0px, calc(${markerLeft} - 60px), calc(100% - 130px))` }">{{ needStay }}분 이하면 안 늦어요</span>
            <span class="trim-mark" :style="{ left: markerLeft }" aria-hidden="true"></span>
          </template>
          <input v-model.number="stay" class="range" type="range" min="0" :max="max" :step="STEP_MIN" aria-label="머무는 시간(분)" @change="onRelease" />
          <div style="display: flex; justify-content: space-between"><span class="t-cap tn">0분</span><span class="t-cap tn">기존 {{ max }}분</span></div>
        </div>

        <div v-if="stay === 0" class="banner banner--danger">
          <i class="ic i-trash" aria-hidden="true"></i><span class="banner__body"><span class="t-strong">0분이면 일정에서 빠져요</span></span>
        </div>
        <div v-else class="banner" :class="isOk ? 'banner--success' : 'banner--warning'">
          <i class="ic" :class="isOk ? 'i-success' : 'i-warning'" aria-hidden="true"></i>
          <span class="banner__body">
            <span class="t-strong tn">{{ basisName }} {{ arrive }} 도착 · {{ isOk ? `여유 ${slack}분` : `아직 ${-slack}분 늦어요` }}</span>
            <span class="t-cap tn">{{ otherCut ? '다른 곳까지 합쳐 ' : '' }}총 {{ cut + otherCut }}분 줄임</span>
          </span>
        </div>
        <p v-if="!isBeforeBasis" class="t-cap" style="margin: 0">{{ stop === replan.basisStop ? '기준이 되는 마지막 일정이라' : basisName + ' 뒤에 있는 곳이라' }} 줄여도 {{ basisName }} 도착 시각은 그대로예요</p>

        <button v-if="stay === 0" type="button" class="btn btn--danger btn--lg btn--block" @click="askRemove"><i class="ic i-trash" aria-hidden="true"></i>{{ buttonText }}</button>
        <button v-else type="button" class="btn btn--primary btn--lg btn--block" @click="done(stay)">{{ buttonText }}</button>
        </template>
      </BottomSheet>

      <ConfirmModal
        v-if="asking && stop"
        :img="imageFor(stop.category)"
        :title="stop.name + '을(를) 일정에서 뺄까요?'"
        text="머무는 시간을 0분으로 줄이면 이 장소는 일정에서 빠져요."
        ok-text="빼기"
        ok-icon="i-trash"
        danger
        @cancel="cancelRemove"
        @ok="done(0)"
      >
        <div class="trim-slack">
          <span class="t-cap">{{ basisName }}</span>
          <b class="tn" :class="atMax.slackMin < 0 ? 'c-danger' : ''" style="margin-left: auto">{{ slackLabel(atMax.slackMin) }}</b><i class="ic ic--s i-next subtle" aria-hidden="true"></i><b class="tn" :class="removed.slackMin < 0 ? 'c-danger' : 'c-success'">{{ slackLabel(removed.slackMin) }}</b>
        </div>
      </ConfirmModal>
    </div>
  </div>
</template>

<style>
.scr-AltTrim .trim-mark-text { position: absolute; top: 0; white-space: nowrap; }
.scr-AltTrim .trim-mark { position: absolute; top: 18px; bottom: 22px; width: 2px; background: var(--danger); }
.scr-AltTrim .trim-slack { display: flex; align-items: center; gap: 8px; padding: 12px; border-radius: var(--radius-md); background: var(--surface-sunken); }
</style>
