<script setup>
// 마이 화면 맨 아래 버전 글자. 5번 연달아 누르면 시연 계정인지 서버에 물어보고 개발자 모드를 켜요 (③, 11/06)
// 시연 계정이 아니면 아무 일도 없는 것처럼 보여요 (심사위원·일반 사용자에게 드러나지 않게)
import { getDevStatus } from '@/api/dev'
import { devMode, turnOffDevMode, turnOnDevMode } from '@/utils/devMode'
import { showToast } from '@/utils/toast'

const TAPS_TO_OPEN = 5
const TAP_GAP_MS = 1500 // 이 시간 안에 다음을 눌러야 이어서 셈

let count = 0
let lastTapAt = 0

async function onTap() {
  const now = Date.now()
  count = now - lastTapAt <= TAP_GAP_MS ? count + 1 : 1
  lastTapAt = now
  if (count < TAPS_TO_OPEN) return
  count = 0
  if (devMode.isOn) {
    turnOffDevMode()
    showToast('개발자 모드를 껐어요')
    return
  }
  try {
    const status = await getDevStatus()
    if (!status.allowed) return
    turnOnDevMode()
    showToast('개발자 모드를 켰어요 · 여행 중 화면의 점을 눌러 보세요')
  } catch (e) {
    // 로그인이 풀렸거나 서버가 꺼져 있으면 조용히 넘어감
  }
}
</script>

<template>
  <button type="button" class="t-cap subtle tn ver-tap" @click="onTap">버전 1.0.0<span v-if="devMode.isOn"> · 개발자 모드</span></button>
</template>

<style>
.ver-tap { background: none; border: 0; padding: 4px 0; cursor: default; }
</style>
