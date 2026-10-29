<script setup>
// S06-c 숙소 위치 찍기 (③): 1박 이상이면 지도를 눌러 숙소에 핀을 찍어요. 다음 날은 여기서 출발해요
import { computed, ref } from 'vue'
import BottomSheet from '@/components/common/BottomSheet.vue'
import { goBack } from '@/utils/back'
import { showToast } from '@/utils/toast'

const pinX = ref(255) // 숙소 핀 위치 (지도 그림 위 px)
const pinY = ref(360)
const stayName = ref('')
const last = { x: 300, y: 330 } // 첫날 마지막 장소

// 마지막 장소에서 걸어서 몇 분 (지도 9px ≈ 1분)
const walk = computed(() => Math.max(2, Math.round(Math.hypot(pinX.value - last.x, pinY.value - last.y) / 9)))
const area = computed(() => (pinY.value < 260 ? '천호동' : pinX.value > 230 ? '성내동' : '강동역'))

// 지도를 누른 자리로 핀을 옮겨요 (화면이 줄어 있어도 맞게 계산)
function tapMap(e) {
  const box = e.currentTarget.getBoundingClientRect()
  const scale = box.width / 390
  pinX.value = Math.round((e.clientX - box.left) / scale)
  pinY.value = Math.round((e.clientY - box.top) / scale)
}
function save() {
  showToast('숙소 위치를 저장했어요')
  goBack('/plan/info')
}
</script>

<template>
  <div class="scr scr-StayPin">
    <div class="app">
      <div class="map" aria-hidden="true" style="bottom: 0"><img class="base live" src="/img/mapB.svg" alt="" style="top: -126px" /></div>
      <button type="button" class="sp-hit" aria-label="지도를 눌러 숙소 위치 정하기" @click="tapMap"></button>
      <span class="pin pin--done" :style="{ left: last.x + 'px', top: last.y + 'px' }" aria-hidden="true">5</span>
      <span class="maplabel" style="left: 300px; top: 300px; transform: translateX(-50%)">마지막 장소</span>
      <span class="sp-ring" :style="{ left: last.x + 'px', top: last.y + 'px' }" aria-hidden="true"></span>
      <span class="sp-pin" :style="{ left: pinX + 'px', top: pinY + 'px' }" aria-hidden="true"><i class="ic i-home" aria-hidden="true"></i></span>

      <header class="topbar topbar--float">
        <button type="button" class="iconbtn iconbtn--float" aria-label="뒤로가기" @click="goBack('/plan/info')"><i class="ic ic--l i-back" aria-hidden="true"></i></button>
        <span class="card sp-help"><i class="ic ic--s i-info c-navy" aria-hidden="true"></i><span class="t-cap" style="color: var(--ink)">지도를 눌러 숙소에 핀을 찍어요</span></span>
      </header>

      <BottomSheet class="sheet--peek" style="gap: 12px">
        <div style="display: flex; align-items: center; gap: 12px">
          <span class="ico-tile"><i class="ic ic--l i-home" aria-hidden="true"></i></span>
          <div style="flex: 1; min-width: 0"><h1 class="h-title" style="font-size: 18px">{{ area }} 근처</h1><p class="t-cap tn">마지막 장소에서 도보 {{ walk }}분</p></div>
          <span class="badge" :class="walk <= 8 ? 'badge--success' : 'badge--warning'">{{ walk <= 8 ? '동선 짧음' : '조금 멀어요' }}</span>
        </div>
        <div class="field">
          <label class="field__label" for="sp-name">숙소 이름 (선택)</label>
          <div class="field__box"><input id="sp-name" v-model="stayName" type="text" placeholder="예: 천호 게스트하우스" /></div>
        </div>
        <button type="button" class="btn btn--primary btn--lg btn--block" @click="save">이 위치로 저장</button>
      </BottomSheet>
    </div>
  </div>
</template>

<style>
.scr-StayPin .sp-help{flex:1;min-width:0;height:44px;display:flex;align-items:center;gap:8px;padding:0 14px;border-radius:var(--radius-full)}
.scr-StayPin .sp-hit{position:absolute;left:0;top:0;right:0;bottom:250px;z-index:1;background:transparent;border:0;cursor:crosshair}
.scr-StayPin .sp-pin{position:absolute;z-index:2;width:44px;height:44px;margin:-44px 0 0 -22px;border-radius:50% 50% 50% 0;transform:rotate(-45deg);background:var(--coral);display:flex;align-items:center;justify-content:center;box-shadow:0 4px 10px rgba(24,33,58,.35);transition:left .2s,top .2s;pointer-events:none}
.scr-StayPin .sp-pin .ic{transform:rotate(45deg);color:#fff}
.scr-StayPin .sp-ring{position:absolute;z-index:1;width:150px;height:150px;margin:-75px 0 0 -75px;border-radius:50%;background:rgba(29,122,76,.12);border:1.5px dashed var(--success);pointer-events:none}
</style>
