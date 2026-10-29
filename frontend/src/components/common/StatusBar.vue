<script setup>
// 휴대폰 맨 위 상태바 (PC에서 볼 때만). 잠금화면에서는 흰 글씨, 시간은 숨김
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const time = ref('')
const isLight = computed(() => route.meta.statusbar === 'light' || route.meta.statusbar === 'lock')
const hideTime = computed(() => route.meta.statusbar === 'lock')

function tick() {
  const now = new Date()
  time.value = now.getHours() + ':' + String(now.getMinutes()).padStart(2, '0')
}

let timer = null
onMounted(() => {
  tick()
  timer = setInterval(tick, 15000)
})
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <div class="statusbar" :class="{ 'statusbar--light': isLight }" aria-hidden="true">
    <span :style="{ visibility: hideTime ? 'hidden' : 'visible' }">{{ time }}</span>
    <span class="statusbar__icons">
      <svg width="18" height="12" viewBox="0 0 18 12" fill="currentColor"><rect x="0" y="8" width="3" height="4" rx="1" /><rect x="5" y="5.5" width="3" height="6.5" rx="1" /><rect x="10" y="3" width="3" height="9" rx="1" /><rect x="15" y="0" width="3" height="12" rx="1" /></svg>
      <svg width="16" height="12" viewBox="0 0 16 12" fill="currentColor"><path d="M8 2.4c2.3 0 4.4.9 6 2.4l1.3-1.3A10.3 10.3 0 0 0 8 .5 10.3 10.3 0 0 0 .7 3.5L2 4.8a8.4 8.4 0 0 1 6-2.4z" /><path d="M8 6c1.3 0 2.5.5 3.4 1.4l1.3-1.3A6.6 6.6 0 0 0 8 4.1a6.6 6.6 0 0 0-4.7 2l1.3 1.3A4.8 4.8 0 0 1 8 6z" /><circle cx="8" cy="10" r="1.6" /></svg>
      <svg width="27" height="13" viewBox="0 0 27 13" fill="none"><rect x=".5" y=".5" width="23" height="12" rx="3.5" stroke="currentColor" opacity=".4" /><rect x="2" y="2" width="17" height="9" rx="2" fill="currentColor" /><path d="M25 4.5v4c.8-.3 1.3-1.1 1.3-2s-.5-1.7-1.3-2z" fill="currentColor" opacity=".45" /></svg>
    </span>
  </div>
</template>
