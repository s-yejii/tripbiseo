<script setup>
// 아래에서 올라오는 시트. 손잡이를 누르면 손잡이만 남기고 내려가고, 다시 누르면 올라와요
// 예) <BottomSheet class="sheet--peek" style="top: 440px"> 내용 </BottomSheet>
import { ref } from 'vue'

const sheet = ref(null)
const down = ref(0) // 내려간 거리(px). 0이면 펼친 상태

function toggle() {
  if (down.value > 0) {
    down.value = 0
    return
  }
  // 아래 버튼 줄(.cta)이 있으면 그 위까지만 내려가요
  const app = sheet.value.parentElement
  const cta = app.querySelector(':scope > .cta')
  const bottom = cta ? cta.offsetTop : app.offsetHeight
  down.value = bottom - 34 - sheet.value.offsetTop
}
</script>

<template>
  <section
    ref="sheet"
    class="sheet"
    :class="{ 'is-collapsed': down > 0 }"
    :style="down > 0 ? { transform: `translateY(${down}px)` } : null"
  >
    <span
      class="handle"
      role="button"
      tabindex="0"
      :aria-label="down > 0 ? '시트 펼치기' : '시트 내리기'"
      :aria-expanded="down === 0"
      @click="toggle"
      @keydown.enter="toggle"
    ></span>
    <slot />
  </section>
</template>
