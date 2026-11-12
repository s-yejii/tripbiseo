<script setup>
// 앱 전체 틀: PC에서는 휴대폰 모양 안에, 폰에서는 화면 가득 보여 줘요
import { onMounted, onUnmounted, ref } from 'vue'
import { ensureDemoMode } from '@/utils/devMode'
import { useRouter } from 'vue-router'
import StatusBar from '@/components/common/StatusBar.vue'
import ToastBox from '@/components/common/ToastBox.vue'

const scale = ref(1)
const router = useRouter()
const backdrop = ref(null) // 위에 뜨는 시트 뒤에 보여 줄 앞 화면 그림

// 시트 화면(meta.overlay)으로 갈 때 지금 화면을 복사해 뒤에 깔아요. 복사본은 누를 수 없고, 앞 화면을 다시 불러오지 않아요
function keepBehind() {
  const current = document.querySelector('.phone__screen > .scr')
  if (!current || !backdrop.value) return
  const copy = current.cloneNode(true) // cloneNode: 화면 모양(DOM)을 그대로 복사
  const scrolled = current.querySelectorAll('.scroll, .trip-scroll')
  copy.querySelectorAll('.scroll, .trip-scroll').forEach((box, i) => {
    queueMicrotask(() => { box.scrollTop = scrolled[i] ? scrolled[i].scrollTop : 0 })
  })
  copy.setAttribute('inert', '') // inert: 누르기·키보드 이동이 안 되게
  copy.setAttribute('aria-hidden', 'true')
  backdrop.value.replaceChildren(copy)
}
const removeBefore = router.beforeEach((to, from) => {
  if (to.meta.overlay && !from.meta.overlay) keepBehind()
})
const removeAfter = router.afterEach((to) => {
  if (!to.meta.overlay && backdrop.value) backdrop.value.replaceChildren()
})
onUnmounted(() => {
  removeBefore()
  removeAfter()
})

// 화면 크기에 맞춰 휴대폰(390×844)을 줄여요
function fitPhone() {
  const isMobile = window.innerWidth <= 760
  const width = isMobile ? 390 : 410
  const height = isMobile ? 844 : 864
  const gap = isMobile ? 0 : 32
  scale.value = Math.min(1, (window.innerWidth - gap) / width, (window.innerHeight - gap) / height)
}

onMounted(() => {
  fitPhone()
  window.addEventListener('resize', fitPhone)
  ensureDemoMode() // 이미 로그인된 시연 계정도 앱을 열면 시연 모드로 (③ 11/09)
})
onUnmounted(() => window.removeEventListener('resize', fitPhone))
</script>

<template>
  <div class="stage">
    <div class="phone" :style="{ transform: `scale(${scale})` }">
      <div class="phone__screen">
        <div ref="backdrop" class="overlay-backdrop" aria-hidden="true"></div>
        <RouterView v-slot="{ Component, route }">
          <Transition :name="route.meta.overlay ? 'sheet-up' : 'fade'">
            <component :is="Component" :key="route.path" />
          </Transition>
        </RouterView>
        <StatusBar />
        <ToastBox />
      </div>
    </div>
  </div>
</template>
