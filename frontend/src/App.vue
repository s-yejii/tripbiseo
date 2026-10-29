<script setup>
// 앱 전체 틀: PC에서는 휴대폰 모양 안에, 폰에서는 화면 가득 보여 줘요
import { onMounted, onUnmounted, ref } from 'vue'
import StatusBar from '@/components/common/StatusBar.vue'
import ToastBox from '@/components/common/ToastBox.vue'

const scale = ref(1)

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
})
onUnmounted(() => window.removeEventListener('resize', fitPhone))
</script>

<template>
  <div class="stage">
    <div class="phone" :style="{ transform: `scale(${scale})` }">
      <div class="phone__screen">
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
