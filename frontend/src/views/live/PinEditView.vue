<script setup>
// S13-b 핀 눌러 미리 바꾸기 (④): 다음 장소를 순서 바꾸기 / 머무는 시간 바꾸기 / 빼기
// 주소: /live/pin?no=4 (4번 성내동 소품샵, 5번 천호 로데오거리)
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BottomSheet from '@/components/common/BottomSheet.vue'
import DurationSlider from '@/components/common/DurationSlider.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import { toDuration } from '@/utils/time'
import { showToast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()

// 가짜 데이터: 바꿀 수 있는 장소 두 곳
const pinData = {
  4: { no: 4, name: '성내동 소품샵', img: '/img/sopum.jpg', meta: '다음 장소 · 16:07 도착 · 60분', other: '천호 로데오거리', skipTo: '천호 로데오거리로 바로 가요', x: 290, y: 200, orig: 60 },
  5: { no: 5, name: '천호 로데오거리', img: '/img/understand.jpg', meta: '그다음 장소 · 17:15 도착 · 40분', other: '성내동 소품샵', skipTo: '잔나비 콘서트 가기 전 여유가 생겨요', x: 300, y: 330, orig: 40 },
}
const pin = pinData[route.query.no] || pinData[4]
const maxStay = Math.ceil((pin.orig + 30) / 10) * 10

const choice = ref('swap') // swap | stay | skip
const stay = ref(pin.orig)
const asking = ref(false)

// 머무는 시간을 바꾸면 예약 여유도 바뀌어요 (지금 여유 15분)
const staySlack = computed(() => 15 + (pin.orig - stay.value))

// 슬라이더를 0분까지 내리면 정말 뺄지 물어요
watch(stay, (minutes) => {
  choice.value = 'stay'
  if (minutes === 0) asking.value = true
})

function cancelSkip() {
  asking.value = false
  stay.value = 5
}
function confirmSkip() {
  router.push('/live/applied')
  showToast(pin.name + '을(를) 일정에서 뺐어요')
}
function apply() {
  if (choice.value === 'stay' && stay.value === 0) {
    asking.value = true
    return
  }
  router.push('/live/applied')
}
</script>

<template>
  <div class="scr scr-PinEdit">
    <div class="app">
      <div class="map" aria-hidden="true"><img class="base live" src="/img/mapB.svg" alt="" style="top: -126px" /></div>
      <span class="pin pin--sel" :style="{ left: pin.x + 'px', top: pin.y + 'px' }">{{ pin.no }}</span>
      <span class="me" style="left: 215px; top: 285px"></span>
      <div class="dim" aria-hidden="true" style="background: rgba(16, 22, 38, 0.24)"></div>

      <BottomSheet role="dialog" aria-modal="true" style="gap: 14px">
        <div class="sheet__head" style="gap: 12px">
          <img class="thumb" :src="pin.img" alt="" />
          <RouterLink to="/place" class="pe-title">
            <h1 class="h-title" style="display: flex; align-items: center; gap: 4px">{{ pin.name }}<i class="ic ic--s i-next subtle" aria-hidden="true"></i></h1>
            <p class="t-cap tn">{{ pin.meta }}</p>
          </RouterLink>
          <RouterLink to="/live" class="iconbtn iconbtn--soft" aria-label="닫기"><i class="ic i-close" aria-hidden="true"></i></RouterLink>
        </div>

        <div role="radiogroup" aria-label="미리 바꾸기" style="display: flex; flex-direction: column; gap: 8px">
          <button type="button" role="radio" :aria-checked="choice === 'swap'" class="opt" :class="{ 'is-on': choice === 'swap' }" style="text-align: left" @click="choice = 'swap'">
            <span class="ico-tile"><i class="ic i-swap" aria-hidden="true"></i></span>
            <span class="opt__body"><b class="t-strong">{{ pin.other }}와 순서 바꾸기</b><span class="t-cap">가까운 곳부터 들러요</span></span>
            <span class="badge badge--success tn">여유 22분</span>
          </button>

          <div class="opt" :class="{ 'is-on': choice === 'stay' }" style="flex-direction: column; align-items: stretch; gap: 12px">
            <button type="button" role="radio" :aria-checked="choice === 'stay'" class="pe-stay" @click="choice = 'stay'">
              <span class="ico-tile"><i class="ic i-timer" aria-hidden="true"></i></span>
              <span class="opt__body"><b class="t-strong">머무는 시간 바꾸기</b><span class="t-cap tn">기존 {{ toDuration(pin.orig) }}</span></span>
              <span v-if="staySlack >= 0" class="badge badge--success tn">여유 {{ staySlack }}분</span>
              <span v-else class="badge badge--danger tn">{{ -staySlack }}분 늦음</span>
            </button>
            <DurationSlider v-if="choice === 'stay'" v-model="stay" :orig="pin.orig" :max="maxStay" />
          </div>

          <button type="button" role="radio" :aria-checked="choice === 'skip'" class="opt" :class="{ 'is-on': choice === 'skip' }" style="text-align: left" @click="choice = 'skip'">
            <span class="ico-tile ico-tile--neutral"><i class="ic i-minus" aria-hidden="true"></i></span>
            <span class="opt__body"><b class="t-strong">이 장소 빼기</b><span class="t-cap">{{ pin.skipTo }}</span></span>
            <span class="badge badge--success tn">여유 65분</span>
          </button>
        </div>

        <button type="button" class="btn btn--primary btn--lg btn--block" @click="apply">적용하기</button>
      </BottomSheet>

      <ConfirmModal
        v-if="asking"
        :img="pin.img"
        :title="pin.name + '을(를) 일정에서 뺄까요?'"
        :text="'머무는 시간을 0분으로 줄이면 이 장소는 일정에서 빠져요. ' + pin.skipTo"
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
