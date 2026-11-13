<script setup>
// 화면 맨 위에서 내려오는 앱 알림. 누르면 to 화면으로 가고, 위로 밀면 닫혀요(close)
// 11/12 ④: 안에 버튼을 넣을 수 있어요 (기본 slot, 예: 지연 알림의 [10분 뒤 다시] [확인]). 버튼을 누를 때는 알림 누르기·밀기로 안 봐요
import { nextTick, ref } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps({
  title: { type: String, required: true },
  body: { type: String, default: '' },
  icon: { type: String, default: 'i-bell' },
  to: { type: String, default: '' },
  warn: { type: Boolean, default: false },
})
const emit = defineEmits(['close', 'open'])

const router = useRouter()
const startY = ref(null)
const moveY = ref(0)

function onDown(e) {
  if (e.target.closest('button')) return // 안의 버튼은 버튼대로 (알림 누르기·밀기 아님)
  e.currentTarget.setPointerCapture(e.pointerId) // 손가락이 알림 밖으로 나가도 계속 따라가요
  startY.value = e.clientY
  moveY.value = 0
}
function onMove(e) {
  if (startY.value === null) return
  moveY.value = Math.min(0, e.clientY - startY.value)
}
function onUp() {
  if (startY.value === null) return
  const moved = moveY.value
  startY.value = null
  moveY.value = 0
  if (moved < -36) emit('close') // 위로 충분히 밀면 닫기
  else if (moved > -5 && props.to) {
    // 거의 안 움직였으면 누른 것. 11/12: 누른 알림은 읽은 것 → 먼저 닫고(open) 화면이 바뀐 뒤 이동 (시트 화면 뒤 복사본에 안 남게)
    emit('open')
    nextTick(() => router.push(props.to))
  }
}
</script>

<template>
  <div
    class="noti"
    :class="{ 'noti--warn': warn }"
    :role="warn ? 'alert' : 'status'"
    :style="moveY ? { transform: `translateY(${moveY}px)`, transition: 'none' } : null"
    :aria-label="title + ' · 위로 밀면 사라져요'"
    @pointerdown="onDown"
    @pointermove="onMove"
    @pointerup="onUp"
    @pointercancel="onUp"
  >
    <span class="noti__app" aria-hidden="true"><i class="ic ic--s i-pin"></i></span>
    <span class="noti__body">
      <span class="noti__meta"><b>착착트립</b><span>지금</span></span>
      <span class="noti__t tn"><i class="ic ic--s" :class="icon" aria-hidden="true"></i>{{ title }}</span>
      <span class="noti__d">{{ body }}</span>
      <span v-if="$slots.default" class="noti__actions"><slot /></span>
    </span>
    <span class="noti__grab" aria-hidden="true"></span>
  </div>
</template>

<style>
.noti__actions { display: flex; gap: 6px; margin-top: 8px; }
.noti__actions .btn { cursor: pointer; }
</style>
