<script setup>
// 화면 맨 위에서 내려오는 앱 알림. 누르면 to 화면으로 가고, 위로 밀면 닫혀요(close)
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps({
  title: { type: String, required: true },
  body: { type: String, default: '' },
  icon: { type: String, default: 'i-bell' },
  to: { type: String, default: '' },
  warn: { type: Boolean, default: false },
})
const emit = defineEmits(['close'])

const router = useRouter()
const startY = ref(null)
const moveY = ref(0)

function onDown(e) {
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
  else if (moved > -5 && props.to) router.push(props.to) // 거의 안 움직였으면 누른 것
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
      <span class="noti__meta"><b>여행어때</b><span>지금</span></span>
      <span class="noti__t tn"><i class="ic ic--s" :class="icon" aria-hidden="true"></i>{{ title }}</span>
      <span class="noti__d">{{ body }}</span>
    </span>
    <span class="noti__grab" aria-hidden="true"></span>
  </div>
</template>
