<script setup>
// S13-e 길 안내 · 버스 정류장 (⑤): 도보·버스·지하철·택시 중 골라 가는 길을 보고, 도착하면 자동으로 기록돼요
// 지금은 시연용으로 내 위치가 저절로 움직여요. 실제로는 GPS 위치로 움직여요
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import BottomSheet from '@/components/common/BottomSheet.vue'
import { routeModes, walkPath } from '@/data/route'
import { goBack } from '@/utils/back'
import { showToast } from '@/utils/toast'

const router = useRouter()
const modeKey = ref('walk')
const progress = ref(0) // 0~100 (%)
let timer = null

const mode = computed(() => routeModes[modeKey.value])
const minutesLeft = computed(() => Math.max(1, Math.round(mode.value.min * (1 - progress.value / 100))))

// 진행률에 맞춰 길 위의 점 사이에서 지금 위치를 계산해요
const me = computed(() => {
  const part = Math.min(2.999, (progress.value / 100) * 3)
  const i = Math.floor(part)
  const rate = part - i
  const [x1, y1] = walkPath[i]
  const [x2, y2] = walkPath[i + 1]
  return { x: Math.round(x1 + (x2 - x1) * rate), y: Math.round(y1 + (y2 - y1) * rate) }
})

onMounted(() => {
  timer = setInterval(() => {
    progress.value = Math.min(100, progress.value + 2)
    if (progress.value >= 100) arrive()
  }, 220)
})
onUnmounted(() => clearInterval(timer))

function arrive() {
  clearInterval(timer)
  setTimeout(() => {
    router.push('/live')
    showToast('성내동 소품샵에 도착했어요 · 지도 핀 색이 바뀌었어요')
  }, 500)
}
</script>

<template>
  <div class="scr scr-RouteGuide">
    <div class="app">
      <div class="map" aria-hidden="true" style="bottom: 380px">
        <img class="base live" src="/img/mapB.svg" alt="" style="top: -126px" />
        <svg class="route" viewBox="0 0 390 464">
          <polyline
            v-for="line in mode.lines"
            :key="line.points"
            :points="line.points"
            fill="none"
            :stroke="line.color"
            :stroke-width="line.dash ? 4 : 6"
            :stroke-dasharray="line.dash ? '1 7' : null"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </div>
      <span class="me rg-me" :style="{ left: me.x + 'px', top: me.y + 'px' }" aria-label="지금 위치"></span>
      <span class="pin pin--sel" style="left: 290px; top: 200px" aria-hidden="true">4</span>
      <template v-for="stop in mode.stops" :key="stop.x">
        <span class="busstop" :class="{ 'busstop--off': stop.off }" :style="{ left: stop.x + 'px', top: stop.y + 'px', background: stop.color }"><i class="ic" :class="stop.icon" aria-hidden="true"></i></span>
        <span v-if="stop.label" class="maplabel" :style="{ left: stop.labelX + 'px', top: stop.labelY + 'px' }">{{ stop.label }}</span>
      </template>

      <header class="topbar topbar--float">
        <button type="button" class="iconbtn iconbtn--float" aria-label="뒤로가기" @click="goBack('/live')"><i class="ic ic--l i-back" aria-hidden="true"></i></button>
        <span class="card livebar"><b class="t-strong" style="flex: 1">성내동 카페 → 성내동 소품샵</b></span>
      </header>

      <BottomSheet class="sheet--peek" style="top: 440px; padding: 8px 16px 0; gap: 12px">
        <div class="seg" role="tablist" aria-label="이동 방법">
          <button v-for="(m, key) in routeModes" :key="key" type="button" role="tab" :class="{ 'is-on': key === modeKey }" :aria-selected="key === modeKey" @click="modeKey = key">
            <i class="ic ic--s" :class="m.icon" aria-hidden="true"></i>{{ m.label }} {{ m.min }}분
          </button>
        </div>
        <h1 class="sr">{{ mode.label }}로 가는 길</h1>
        <ol class="rg-steps">
          <li v-for="step in mode.steps" :key="step.title" :class="{ 'is-key': step.key }">
            <span class="rg-dot"><i class="ic ic--s" :class="step.icon" aria-hidden="true"></i></span>
            <span><b><span v-if="step.busNo" class="busno">{{ step.busNo }}</span>{{ step.title }}</b><small v-if="step.desc">{{ step.desc }}</small></span>
          </li>
        </ol>
      </BottomSheet>

      <div class="cta cta--white">
        <div class="rg-status">
          <span class="rg-eta tn"><b>{{ mode.eta }}</b><small>도착 예상</small></span>
          <span style="flex: 1; display: flex; flex-direction: column; gap: 6px">
            <span class="t-cap rg-moving"><i class="ic ic--s i-locate" aria-hidden="true"></i>{{ progress >= 100 ? '도착 확인 중…' : `이동 중 · 도착하면 자동으로 기록돼요 (${minutesLeft}분 남음)` }}</span>
            <span class="bar"><i :style="{ width: progress + '%' }"></i></span>
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<style>
.scr-RouteGuide .rg-me{transition:left .25s linear,top .25s linear}
.scr-RouteGuide .rg-moving{display:flex;align-items:center;gap:4px;color:var(--success)}
.scr-RouteGuide .rg-steps{list-style:none;margin:0;padding:0 0 110px;display:flex;flex-direction:column;gap:2px;overflow-y:auto;min-height:0;flex:1}
.scr-RouteGuide .rg-steps li{display:flex;gap:12px;align-items:flex-start;padding:8px 0;position:relative}
.scr-RouteGuide .rg-steps li+li::before{content:"";position:absolute;left:15px;top:-10px;height:16px;border-left:2px dotted var(--line-strong)}
.scr-RouteGuide .rg-steps li.is-key{background:var(--success-soft);border-radius:var(--radius-md);padding:8px;margin:0 -8px}
.scr-RouteGuide .rg-steps b{display:block;font-size:15px;line-height:22px}
.scr-RouteGuide .rg-steps small{display:block;font-size:13px;line-height:18px;color:var(--ink-muted)}
.scr-RouteGuide .rg-dot{width:32px;height:32px;border-radius:50%;background:var(--navy-soft);color:var(--navy);display:flex;align-items:center;justify-content:center;flex:none}
.scr-RouteGuide .is-key .rg-dot{background:var(--success);color:#fff}
.scr-RouteGuide .busno{display:inline-flex;align-items:center;height:22px;padding:0 8px;border-radius:6px;background:var(--success);color:#fff;font-size:13px;font-weight:700;margin-right:4px;font-variant-numeric:tabular-nums}
.scr-RouteGuide .busstop{position:absolute;z-index:2;width:32px;height:32px;margin:-16px 0 0 -16px;border-radius:9px;background:var(--success);color:#fff;display:flex;align-items:center;justify-content:center;border:2px solid #fff;box-shadow:0 2px 6px rgba(24,33,58,.3)}
.scr-RouteGuide .busstop--off{background:#fff;color:var(--success);border-color:var(--success)}
.scr-RouteGuide .rg-status{display:flex;align-items:center;gap:12px;min-height:52px}
.scr-RouteGuide .rg-eta{display:flex;flex-direction:column;justify-content:center;min-width:64px}
.scr-RouteGuide .rg-eta b{font-size:20px;line-height:24px}
.scr-RouteGuide .rg-eta small{font-size:12px;color:var(--ink-muted)}
.scr-RouteGuide .seg button{font-size:12px;padding:0 2px;white-space:nowrap;gap:2px}
.scr-RouteGuide .seg button .ic{width:14px;height:14px}
</style>
