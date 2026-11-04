<script setup>
// S10-b 일정 한 칸 수정 (②): 머무는 시간, 중요도, 시간 고정을 바꾸고 저장하거나 빼요
// 주소: /plan/edit/stop?day=1&idx=0 (그날 몇 번째 칸인지) &back=돌아갈 곳
// 11/04: 초안(store.draft)이 있으면 그 칸을 진짜로 고쳐요. 머무는 시간·고정을 바꾸면 그날 시각을 다시 맞추고(utils/draftEdit),
//   다음 일정 시각은 일정 점검(POST /api/plans/check)이 다시 계산해요. 초안이 없으면 시연용 가짜 일정이에요
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BottomSheet from '@/components/common/BottomSheet.vue'
import DurationSlider from '@/components/common/DurationSlider.vue'
import KakaoMap from '@/components/common/KakaoMap.vue'
import { concertTrip } from '@/data/trips'
import { importance } from '@/data/places'
import { useTripStore } from '@/stores/trip'
import { goBack } from '@/utils/back'
import { dayStopsOf, retime } from '@/utils/draftEdit'
import { imageFor } from '@/utils/mapView'
import { toDuration, toMinutes, toTime } from '@/utils/time'
import { showToast } from '@/utils/toast'

const STAY_STEP = 30 // 슬라이더 최대값을 30분 단위로
const MAX_STAY = 240
const MAP_PADDING = { top: 120, right: 40, bottom: 420, left: 40 }

const route = useRoute()
const router = useRouter()
const store = useTripStore()
const day = Number(route.query.day || 1)
const index = Number(route.query.idx || 0)
const isDraft = store.draft.stops.length > 0 && !!dayStopsOf(store, day)[index]

// 고칠 칸: 초안이면 초안 칸 그대로(고치면 초안이 바뀜), 아니면 시연용 가짜 칸
const dayStops = isDraft ? dayStopsOf(store, day) : concertTrip.stops
const target = dayStops[index]
const stop = isDraft
  ? { name: target.name, time: target.time || '', dur: target.stay || 0, imp: target.importance || 'maybe', fixed: !!target.fixed, img: imageFor(target.category), cat: target.category || '' }
  : { ...target, cat: '' }
const next = dayStops[index + 1]
const maxStay = Math.min(MAX_STAY, Math.ceil((stop.dur + 60) / STAY_STEP) * STAY_STEP)

const stay = ref(stop.dur)
const imp = ref(stop.imp || 'maybe')
const fixed = ref(stop.fixed)
const fixedTime = ref(stop.time)
const isMapOk = ref(true)

const diff = computed(() => stay.value - stop.dur) // 늘린 분(+) / 줄인 분(-)
const slack = computed(() => concertTrip.slack - diff.value) // 시연용만
const timeRange = computed(() => {
  if (!stop.time) return '시각은 일정 점검이 다시 계산해요'
  const start = toMinutes(stop.time)
  return toTime(start) + '–' + toTime(start + stay.value)
})
const nextText = computed(() => {
  if (!next) return '마지막 일정'
  const moved = diff.value === 0 ? '' : ` (약 ${Math.abs(diff.value)}분 ${diff.value > 0 ? '늦어짐' : '빨라짐'})`
  if (isDraft) return `다음 ${next.name}${moved}`
  return `다음 ${next.name} ${toTime(toMinutes(next.time) + diff.value)} 도착${moved}`
})
const mapPins = computed(() => (isDraft && target.lat != null ? [{ key: 'stop', lat: target.lat, lng: target.lng, text: index + 1, cls: 'pin--sel', label: stop.name }] : []))

// 슬라이더를 0분까지 내리면 빼기 확인으로 가요
watch(stay, (minutes) => {
  if (minutes === 0) remove()
})

function remove() {
  const back = route.query.back ? '&back=' + encodeURIComponent(route.query.back) : ''
  router.replace(`/plan/edit/delete?day=${day}&idx=${index}${back}`)
}

function save() {
  if (isDraft) {
    const startTime = dayStops.length ? dayStops[0].time : null
    const isTimeChanged = stay.value !== stop.dur || fixed.value !== stop.fixed || (fixed.value && fixedTime.value !== stop.time)
    target.stay = stay.value
    target.importance = imp.value
    target.fixed = fixed.value
    if (fixed.value) target.time = fixedTime.value
    if (isTimeChanged) retime(dayStops, startTime) // 고정 칸은 그대로, 나머지 시각은 점검이 다시 채움
  }
  showToast(stop.name + ' 수정했어요')
  goBack('/plan/edit')
}
</script>

<template>
  <div class="scr scr-StopEdit">
    <div class="app">
      <KakaoMap v-if="isDraft && isMapOk && mapPins.length" :pins="mapPins" :show-route="false" :padding="MAP_PADDING" @fail="isMapOk = false" />
      <template v-else>
        <div class="map" aria-hidden="true"><img class="base live" src="/img/mapB.svg" alt="" style="top: -186px" /></div>
        <span v-if="!isDraft" class="pin pin--sel" :style="{ left: stop.x + 'px', top: stop.y + 'px' }">{{ index + 1 }}</span>
      </template>
      <div class="dim" aria-hidden="true"></div>

      <BottomSheet role="dialog" aria-modal="true" style="gap: 18px">
        <div class="sheet__head" style="gap: 12px">
          <img class="thumb" :src="stop.img" alt="" />
          <div style="flex: 1; min-width: 0"><h1 class="h-title">{{ stop.name }}</h1><p class="t-cap">{{ day > 1 ? day + '일차 · ' : '' }}{{ index + 1 }}번째{{ stop.cat ? ' · ' + stop.cat : '' }}</p></div>
          <button type="button" class="iconbtn iconbtn--soft" aria-label="닫기" @click="goBack('/plan/edit')"><i class="ic i-close" aria-hidden="true"></i></button>
        </div>

        <div class="se-time">
          <i class="ic i-clock c-navy" aria-hidden="true"></i>
          <span style="flex: 1"><b class="t-strong tn">{{ timeRange }}</b><span class="t-cap tn" style="display: block">{{ nextText }}</span></span>
          <template v-if="!isDraft">
            <span v-if="slack >= 0" class="badge badge--success tn">예약 여유 {{ slack }}분</span>
            <span v-else class="badge badge--danger tn">예약 {{ -slack }}분 늦음</span>
          </template>
        </div>

        <div style="display: flex; flex-direction: column; gap: 8px">
          <span class="field__label" style="display: flex; justify-content: space-between"><span>머무는 시간</span><span class="t-cap tn" style="font-weight: 400">기존 {{ toDuration(stop.dur) }}</span></span>
          <DurationSlider v-model="stay" :orig="stop.dur" :max="maxStay" />
        </div>

        <div style="display: flex; flex-direction: column; gap: 8px">
          <span class="field__label">중요도 <span class="t-cap" style="font-weight: 400">늦어지면 낮은 곳부터 줄여요</span></span>
          <div class="seg" role="radiogroup" aria-label="중요도">
            <button v-for="(label, key) in importance" :key="key" type="button" :class="{ 'is-on': imp === key }" :aria-pressed="imp === key" @click="imp = key">{{ label }}</button>
          </div>
        </div>

        <div style="display: flex; align-items: center; gap: 12px">
          <i class="ic i-clock c-navy" aria-hidden="true"></i>
          <span style="flex: 1"><span class="t-strong" style="display: block">시간 고정</span><span class="t-cap">예약·공연처럼 늦으면 안 될 때</span></span>
          <input v-if="fixed" v-model="fixedTime" type="time" class="se-fixin" aria-label="고정할 시각" />
          <button type="button" class="toggle" :class="{ 'is-on': fixed }" role="switch" :aria-checked="fixed" aria-label="시간 고정" @click="fixed = !fixed"></button>
        </div>

        <div class="cta__row">
          <button type="button" class="btn btn--danger btn--lg" @click="remove"><i class="ic i-trash" aria-hidden="true"></i>빼기</button>
          <button type="button" class="btn btn--primary btn--lg" style="flex: 1" :disabled="fixed && !fixedTime" @click="save">저장</button>
        </div>
      </BottomSheet>
    </div>
  </div>
</template>

<style>
.scr-StopEdit .se-time{display:flex;align-items:center;gap:10px;padding:12px;border-radius:var(--radius-md);background:var(--surface-sunken)}
.scr-StopEdit .se-fixin{height:36px;border:1px solid var(--line-strong);border-radius:var(--radius-md);padding:0 8px;font:inherit;font-variant-numeric:tabular-nums;color:var(--navy);font-weight:600}
.scr-StopEdit .se-fixin:hover,.scr-StopEdit .se-fixin:focus{border-color:var(--navy);outline:0}
</style>
