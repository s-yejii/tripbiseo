<script setup>
// S13-b 핀 눌러 미리 바꾸기 (④): 남은 장소 하나를 순서 바꾸기 / 머무는 시간 바꾸기 / 빼기
// 주소: /live/pin?stopId={칸 id}
// 고를 때마다 서버가 남은 일정을 다시 계산(POST /live/preview, 저장 안 함)해서 고정 일정 여유를 보여 주고,
// "적용하기"를 누르면 그 계산을 그대로 저장(POST /live/apply)한 뒤 바뀐 일정 화면으로 가요.
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BottomSheet from '@/components/common/BottomSheet.vue'
import DurationSlider from '@/components/common/DurationSlider.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import PageMessage from '@/components/common/PageMessage.vue'
import KakaoMap from '@/components/common/KakaoMap.vue'
import { useReplanStore } from '@/stores/replan'
import { imageFor } from '@/utils/mapView'
import { toDuration } from '@/utils/time'
import { showToast } from '@/utils/toast'
import { goBack } from '@/utils/back'
import { updateStop } from '@/api/trips'

const STAY_EXTRA_MAX = 60 // 슬라이더 최대: 기존 + 60분
const STAY_STEP = 10
const PREVIEW_WAIT_MS = 400 // 슬라이더를 멈추고 이만큼 지나면 계산
const PIN_MAP_PADDING = { top: 80, right: 60, bottom: 470, left: 60 }
const ORDER_TEXT = ['다음 장소', '그다음 장소']

const route = useRoute()
const router = useRouter()
const replan = useReplanStore()
const stopId = Number(route.query.stopId)

const choice = ref(null) // swap | stay | skip
const stay = ref(0)
const results = ref({ swap: null, stay: null, skip: null }) // 고른 것마다 서버 계산
const isAsking = ref(false)
const isApplying = ref(false)
let stayTimer = null

onMounted(async () => {
  await replan.load(true)
  if (!stop.value) return
  stay.value = stop.value.stay
  previewSwap()
  previewSkip()
})

const index = computed(() => replan.stops.findIndex((item) => item.id === stopId))
const stop = computed(() => (index.value >= 0 ? replan.stops[index.value] : null))
const isLocked = computed(() => stop.value && stop.value.fixed)
const maxStay = computed(() => (stop.value ? Math.ceil((stop.value.stay + STAY_EXTRA_MAX) / STAY_STEP) * STAY_STEP : 60))
const metaText = computed(() => {
  if (!stop.value) return ''
  const order = ORDER_TEXT[index.value] || index.value + 1 + '번째 장소'
  return order + ' · ' + stop.value.eta + ' 도착 · ' + toDuration(stop.value.stay)
})
const canSkip = computed(() => stop.value && stop.value.importance !== 'must')

// 순서를 바꿀 짝: 바로 뒤 칸(고정 아님), 없으면 바로 앞 칸
const partner = computed(() => {
  const list = replan.stops
  const after = list[index.value + 1]
  if (after && !after.fixed) return after
  const before = list[index.value - 1]
  if (before && !before.fixed) return before
  return null
})
const skipText = computed(() => {
  const next = replan.stops[index.value + 1]
  if (next) return next.name + '(으)로 바로 가요'
  return replan.stops.length === 1 ? '남은 곳이 이곳뿐이라 빼면 오늘 일정이 끝나요' : '이 장소를 들르지 않아요'
})

function idList() {
  return replan.stops.map((item) => ({ id: item.id }))
}

async function runPreview(key, list) {
  try {
    results.value[key] = await replan.preview(list)
  } catch (e) {
    results.value[key] = { error: e.message }
  }
}

function previewSwap() {
  if (!partner.value) return
  const list = idList()
  const a = list.findIndex((item) => item.id === stopId)
  const b = list.findIndex((item) => item.id === partner.value.id)
  const temp = list[a]
  list[a] = list[b]
  list[b] = temp
  runPreview('swap', list)
}

// 남은 곳이 이곳뿐이면 계산할 일정이 없어서, 빼면 오늘 일정이 끝난다고만 보여 줌
const isLastStop = computed(() => replan.stops.length === 1)
function previewSkip() {
  if (!canSkip.value) return
  if (isLastStop.value) {
    results.value.skip = { isLast: true }
    return
  }
  runPreview('skip', idList().filter((item) => item.id !== stopId))
}

// 마지막 한 곳 빼기: 대안 적용 대신 ②의 일정 칸 수정으로 state만 skipped
async function skipLastStop() {
  const original = replan.trip.stops.find((item) => item.id === stopId)
  const form = { time: original.time, stay: original.stay, state: 'skipped', mode: original.mode, memo: original.memo, importance: original.importance }
  await updateStop(replan.tripId, stopId, form)
  showToast(original.name + '을(를) 일정에서 뺐어요 · 오늘 남은 일정이 없어요')
  router.replace('/live')
}

// 슬라이더: 0분이면 뺄지 묻고, 아니면 멈춘 뒤 계산
watch(stay, (minutes) => {
  if (!stop.value || minutes === stop.value.stay && choice.value !== 'stay') return
  choice.value = 'stay'
  clearTimeout(stayTimer)
  if (minutes === 0) {
    if (canSkip.value) isAsking.value = true
    return
  }
  results.value.stay = null
  stayTimer = setTimeout(() => {
    const list = idList().map((item) => (item.id === stopId ? { id: stopId, stay: minutes } : item))
    runPreview('stay', list)
  }, PREVIEW_WAIT_MS)
})

function badgeOf(key) {
  const result = results.value[key]
  if (!result) return { cls: 'badge--neutral', text: '계산 중' }
  if (result.isLast) return { cls: 'badge--neutral', text: '오늘 일정 끝' }
  if (result.error) return { cls: 'badge--neutral', text: '계산 실패' }
  if (result.slackMin < 0) return { cls: 'badge--danger', text: -result.slackMin + '분 늦음' }
  return { cls: 'badge--success', text: '여유 ' + result.slackMin + '분' }
}

function cancelSkip() {
  isAsking.value = false
  stay.value = STAY_STEP
}
async function confirmSkip() {
  isAsking.value = false
  choice.value = 'skip'
  await apply()
}

async function apply() {
  if (isApplying.value || !choice.value) return
  if (choice.value === 'stay' && stay.value === 0) {
    isAsking.value = true
    return
  }
  const result = results.value[choice.value]
  if (result && result.isLast) {
    isApplying.value = true
    try {
      await skipLastStop()
    } catch (e) {
      showToast(e.message)
      isApplying.value = false
    }
    return
  }
  if (!result || result.error) {
    showToast(result && result.error ? result.error : '아직 계산 중이에요')
    return
  }
  isApplying.value = true
  try {
    await replan.apply(result)
    router.replace('/live/applied')
  } catch (e) {
    showToast(e.message)
    isApplying.value = false
  }
}

const mapPins = computed(() => (stop.value ? [{ key: stop.value.id, lat: stop.value.lat, lng: stop.value.lng, icon: 'i-pin', cls: ' pin--sel', label: stop.value.name }] : []))
</script>

<template>
  <div class="scr scr-PinEdit">
    <div class="app">
      <KakaoMap v-if="stop" :pins="mapPins" :me="replan.position" :show-route="false" :padding="PIN_MAP_PADDING" />
      <div class="dim" aria-hidden="true" style="background: rgba(16, 22, 38, 0.24)"></div>

      <BottomSheet closable role="dialog" aria-modal="true" style="gap: 14px">
        <PageMessage v-if="replan.error" icon="i-warning" title="남은 일정을 불러오지 못했어요" :text="replan.error" to="/live" button="여행 중 화면으로" />
        <PageMessage v-else-if="replan.isLoading && !stop" is-loading title="남은 일정을 계산하는 중이에요" />
        <PageMessage v-else-if="!stop" icon="i-info" title="이 장소는 이미 지나갔어요" text="남은 일정에 있는 장소만 미리 바꿀 수 있어요" to="/live" button="여행 중 화면으로" />
        <PageMessage v-else-if="isLocked" icon="i-lock" :title="stop.name + '은(는) 고정 일정이에요'" text="예약·공연처럼 시간이 정해진 곳은 바꿀 수 없어요. 늦을 것 같으면 대안 보기에서 다른 곳을 줄이거나 빼 보세요" to="/live" button="여행 중 화면으로" />

        <template v-else>
          <div class="sheet__head" style="gap: 12px">
            <img class="thumb" :src="imageFor(stop.category)" alt="" />
            <RouterLink :to="stop.placeId ? '/place?id=' + encodeURIComponent(stop.placeId) : route.fullPath" class="pe-title">
              <h1 class="h-title" style="display: flex; align-items: center; gap: 4px">{{ stop.name }}<i v-if="stop.placeId" class="ic ic--s i-next subtle" aria-hidden="true"></i></h1>
              <p class="t-cap tn">{{ metaText }}</p>
            </RouterLink>
            <button type="button" class="iconbtn iconbtn--soft" aria-label="닫기" @click="goBack('/live')"><i class="ic i-close" aria-hidden="true"></i></button>
          </div>

          <div role="radiogroup" aria-label="미리 바꾸기" style="display: flex; flex-direction: column; gap: 8px">
            <button v-if="partner" type="button" role="radio" :aria-checked="choice === 'swap'" class="opt" :class="{ 'is-on': choice === 'swap' }" style="text-align: left" @click="choice = 'swap'">
              <span class="ico-tile"><i class="ic i-swap" aria-hidden="true"></i></span>
              <span class="opt__body"><b class="t-strong">{{ partner.name }}와(과) 순서 바꾸기</b><span class="t-cap">{{ partner.name }}에 먼저 들러요</span></span>
              <span class="badge tn" :class="badgeOf('swap').cls">{{ badgeOf('swap').text }}</span>
            </button>

            <div class="opt" :class="{ 'is-on': choice === 'stay' }" style="flex-direction: column; align-items: stretch; gap: 12px">
              <button type="button" role="radio" :aria-checked="choice === 'stay'" class="pe-stay" @click="choice = 'stay'">
                <span class="ico-tile"><i class="ic i-timer" aria-hidden="true"></i></span>
                <span class="opt__body"><b class="t-strong">머무는 시간 바꾸기</b><span class="t-cap tn">기존 {{ toDuration(stop.stay) }}</span></span>
                <span v-if="choice === 'stay'" class="badge tn" :class="badgeOf('stay').cls">{{ badgeOf('stay').text }}</span>
              </button>
              <DurationSlider v-if="choice === 'stay'" v-model="stay" :orig="stop.stay" :max="maxStay" />
            </div>

            <button v-if="canSkip" type="button" role="radio" :aria-checked="choice === 'skip'" class="opt" :class="{ 'is-on': choice === 'skip' }" style="text-align: left" @click="choice = 'skip'">
              <span class="ico-tile ico-tile--neutral"><i class="ic i-minus" aria-hidden="true"></i></span>
              <span class="opt__body"><b class="t-strong">이 장소 빼기</b><span class="t-cap">{{ skipText }}</span></span>
              <span class="badge tn" :class="badgeOf('skip').cls">{{ badgeOf('skip').text }}</span>
            </button>
            <p v-else class="t-cap" style="margin: 0"><i class="ic ic--s i-info" aria-hidden="true"></i> '꼭 갈 곳'으로 정한 장소라 뺄 수 없어요</p>
          </div>

          <button type="button" class="btn btn--primary btn--lg btn--block" :disabled="!choice || isApplying" @click="apply">{{ isApplying ? '적용하는 중…' : '적용하기' }}</button>
        </template>
      </BottomSheet>

      <ConfirmModal
        v-if="isAsking && stop"
        :img="imageFor(stop.category)"
        :title="stop.name + '을(를) 일정에서 뺄까요?'"
        :text="'머무는 시간을 0분으로 줄이면 이 장소는 일정에서 빠져요. ' + skipText"
        ok-text="빼기"
        ok-icon="i-trash"
        danger
        @cancel="cancelSkip"
        @ok="confirmSkip"
      />
    </div>
  </div>
</template>

<style>
.scr-PinEdit .pe-title { flex: 1; min-width: 0; text-decoration: none; color: var(--ink); }
.scr-PinEdit .pe-stay { display: flex; align-items: center; gap: 12px; border: 0; background: none; padding: 0; text-align: left; cursor: pointer; color: inherit; }
</style>
