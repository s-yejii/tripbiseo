<script setup>
// 휴대폰 맨 위 상태바 (PC에서 볼 때만). 잠금화면에서는 흰 글씨, 시간은 숨김
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { appNow, devMode } from '@/utils/devMode'

const route = useRoute()
const time = ref('')
const isLight = computed(() => route.meta.statusbar === 'light' || route.meta.statusbar === 'lock')
const hideTime = computed(() => route.meta.statusbar === 'lock')

// 시연 계정(개발자 모드)은 진짜 휴대폰처럼 (11/08 주형): 빠른 시계가 계속 바뀌고, 와이파이 대신 5G, 배터리가 조금씩 닳고 다음 날 아침엔 완충
const BATTERY_FULL = 100
const BATTERY_START_HOUR = 11 + 20 / 60 // 그날 11:20에 완충이었다고 보고 → 1일차 15:00 78%, 2일차 아침(숙소에서 충전) 100%
const BATTERY_PER_HOUR = 6 // 한 시간에 이만큼 닳음 (15:00 78% → 21:00 42%, 2일차 18:45 56%)
const BATTERY_LOW = 15
const battery = ref(BATTERY_FULL)
const isDemo = computed(() => devMode.isOn)

function tick() {
  const now = appNow() // 개발자 모드면 시연 시계 (11/06)
  time.value = now.getHours() + ':' + String(now.getMinutes()).padStart(2, '0')
  const hours = Math.max(0, now.getHours() + now.getMinutes() / 60 - BATTERY_START_HOUR)
  battery.value = isDemo.value ? Math.max(BATTERY_LOW, Math.round(BATTERY_FULL - hours * BATTERY_PER_HOUR)) : BATTERY_FULL
}

let timer = null
onMounted(() => {
  tick()
  timer = setInterval(tick, 1000) // 빠른 시계(배속)도 바로바로 보이게 1초마다
})
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <div class="statusbar" :class="{ 'statusbar--light': isLight }" aria-hidden="true">
    <span :style="{ visibility: hideTime ? 'hidden' : 'visible' }">{{ time }}</span>
    <span class="statusbar__icons">
      <svg width="18" height="12" viewBox="0 0 18 12" fill="currentColor"><rect x="0" y="8" width="3" height="4" rx="1" /><rect x="5" y="5.5" width="3" height="6.5" rx="1" /><rect x="10" y="3" width="3" height="9" rx="1" /><rect x="15" y="0" width="3" height="12" rx="1" /></svg>
      <b v-if="isDemo" class="statusbar__net">5G</b>
      <svg v-else width="16" height="12" viewBox="0 0 16 12" fill="currentColor"><path d="M8 2.4c2.3 0 4.4.9 6 2.4l1.3-1.3A10.3 10.3 0 0 0 8 .5 10.3 10.3 0 0 0 .7 3.5L2 4.8a8.4 8.4 0 0 1 6-2.4z" /><path d="M8 6c1.3 0 2.5.5 3.4 1.4l1.3-1.3A6.6 6.6 0 0 0 8 4.1a6.6 6.6 0 0 0-4.7 2l1.3 1.3A4.8 4.8 0 0 1 8 6z" /><circle cx="8" cy="10" r="1.6" /></svg>
      <!-- 시연 계정: 아이폰처럼 알약 모양 안에 남은 % 숫자 (11/08 주형) -->
      <svg v-if="isDemo" width="29" height="14" viewBox="0 0 29 14" class="statusbar__battery">
        <defs><clipPath id="sb-fill"><rect x="0" y="0" :width="25 * battery / 100" height="14" /></clipPath></defs>
        <rect x="0" y="0" width="25" height="14" rx="4.5" fill="currentColor" opacity=".35" />
        <rect x="0" y="0" width="25" height="14" rx="4.5" :fill="battery <= 20 ? '#e5484d' : 'currentColor'" clip-path="url(#sb-fill)" />
        <path d="M26.5 4.8v4.4c.9-.3 1.5-1.2 1.5-2.2s-.6-1.9-1.5-2.2z" fill="currentColor" opacity=".45" />
        <text x="12.5" y="10.6" text-anchor="middle" class="statusbar__pct tn">{{ battery }}</text>
      </svg>
      <svg v-else width="27" height="13" viewBox="0 0 27 13" fill="none"><rect x=".5" y=".5" width="23" height="12" rx="3.5" stroke="currentColor" opacity=".4" /><rect x="2" y="2" width="20" height="9" rx="2" fill="currentColor" /><path d="M25 4.5v4c.8-.3 1.3-1.1 1.3-2s-.5-1.7-1.3-2z" fill="currentColor" opacity=".45" /></svg>
    </span>
  </div>
</template>

<style>
.statusbar__net { font-size: 12px; font-weight: 700; letter-spacing: -0.2px; }
.statusbar__pct { font-size: 10px; font-weight: 800; fill: var(--surface, #fff); letter-spacing: -0.3px; }
.statusbar__battery { margin-left: 1px; }
</style>
