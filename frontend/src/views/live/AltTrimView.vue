<script setup>
// S16-a2 시간 줄이기 슬라이더 (④): 한 장소의 머무는 시간을 왼쪽으로 밀어서 줄여요. 0분이면 일정에서 빠져요
import { computed, ref, watch } from 'vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import BottomSheet from '@/components/common/BottomSheet.vue'
import { goBack } from '@/utils/back'
import { toTime } from '@/utils/time'
import { showToast } from '@/utils/toast'

// 가짜 데이터: 줄일 장소 (지금은 성내동 소품샵 하나)
const place = { name: '성내동 소품샵', img: '/img/sopum.jpg', stay: 60 }
const lateMinutes = 14 // 이대로면 늦는 시간
const arriveNow = 1124 // 공연장 도착 예상 18:44

const stay = ref(place.stay)
const asking = ref(false)

const cut = computed(() => place.stay - stay.value) // 줄인 분
const slack = computed(() => cut.value - lateMinutes) // 0 이상이면 안 늦음
const arrive = computed(() => toTime(arriveNow - cut.value))
// 이 장소만 줄여서 안 늦으려면 몇 분 이하로 머물러야 하는지 (5분 단위)
const needStay = Math.floor((place.stay - lateMinutes) / 5) * 5
const markerLeft = ((needStay / place.stay) * 100).toFixed(1) + '%'

const cutText = computed(() => (cut.value === 0 ? '그대로' : '−' + cut.value + '분'))
const cutClass = computed(() => (cut.value === 0 ? 'muted' : slack.value >= 0 ? 'c-success' : 'c-warning'))
const buttonText = computed(() => (cut.value === 0 ? '줄이지 않기' : stay.value + '분으로 정하기'))

watch(stay, (minutes) => {
  if (minutes === 0) asking.value = true
})

function done(minutes) {
  goBack('/live/alt')
  if (minutes === 0) showToast(place.name + '을(를) 빼기로 했어요')
  else if (minutes === place.stay) showToast(place.name + '은(는) 그대로 둬요')
  else showToast(`${place.name} ${place.stay - minutes}분 줄이기로 했어요`)
}
function cancelRemove() {
  asking.value = false
  stay.value = 5
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
        <div class="sheet__head" style="gap: 12px">
          <img class="thumb" :src="place.img" alt="" />
          <div style="flex: 1"><h2 class="h-title">{{ place.name }}</h2><p class="t-cap">왼쪽으로 밀어 머무는 시간을 줄여요</p></div>
          <button type="button" class="iconbtn iconbtn--soft" aria-label="닫기" @click="goBack('/live/alt')"><i class="ic i-close" aria-hidden="true"></i></button>
        </div>

        <div style="display: flex; align-items: baseline; justify-content: space-between">
          <span class="t-big">{{ stay }}분</span>
          <span class="t-strong tn" :class="cutClass">{{ cutText }}</span>
        </div>

        <div style="position: relative; padding-top: 18px">
          <span class="t-label tn c-danger trim-mark-text" :style="{ left: `clamp(0px, calc(${markerLeft} - 60px), calc(100% - 130px))` }">{{ needStay }}분 이하면 안 늦어요</span>
          <span class="trim-mark" :style="{ left: markerLeft }" aria-hidden="true"></span>
          <input v-model.number="stay" class="range" type="range" min="0" :max="place.stay" step="5" aria-label="머무는 시간(분)" />
          <div style="display: flex; justify-content: space-between"><span class="t-cap tn">0분</span><span class="t-cap tn">기존 {{ place.stay }}분</span></div>
        </div>

        <div v-if="stay === 0" class="banner banner--danger">
          <i class="ic i-trash" aria-hidden="true"></i><span class="banner__body"><span class="t-strong">0분이면 일정에서 빠져요</span></span>
        </div>
        <div v-else class="banner" :class="slack >= 0 ? 'banner--success' : 'banner--warning'">
          <i class="ic" :class="slack >= 0 ? 'i-success' : 'i-warning'" aria-hidden="true"></i>
          <span class="banner__body">
            <span class="t-strong tn">공연장 {{ arrive }} 도착 · {{ slack >= 0 ? `예약 여유 ${slack}분` : `아직 ${-slack}분 늦어요` }}</span>
            <span class="t-cap tn">총 {{ cut }}분 줄임</span>
          </span>
        </div>

        <button v-if="stay === 0" type="button" class="btn btn--danger btn--lg btn--block" @click="asking = true"><i class="ic i-trash" aria-hidden="true"></i>{{ buttonText }}</button>
        <button v-else type="button" class="btn btn--primary btn--lg btn--block" @click="done(stay)">{{ buttonText }}</button>
      </BottomSheet>

      <ConfirmModal
        v-if="asking"
        :img="place.img"
        :title="place.name + '을(를) 일정에서 뺄까요?'"
        text="머무는 시간을 0분으로 줄이면 이 장소는 일정에서 빠져요."
        ok-text="빼기"
        ok-icon="i-trash"
        danger
        @cancel="cancelRemove"
        @ok="done(0)"
      >
        <div class="trim-slack">
          <span class="t-cap">예약 여유</span>
          <b class="tn c-danger" style="margin-left: auto">{{ lateMinutes }}분 늦음</b><i class="ic ic--s i-next subtle" aria-hidden="true"></i><b class="tn c-success">여유 {{ place.stay - lateMinutes }}분</b>
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
