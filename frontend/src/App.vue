<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import TabBar from '@/components/TabBar.vue'

const route = useRoute()
const showTab = computed(() => Boolean(route.meta.tab))
const clock = ref('')
const tick = () => (clock.value = new Date().toTimeString().slice(0, 5))
let timer
onMounted(() => (tick(), (timer = setInterval(tick, 10000))))
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <div class="device">
    <div class="statusbar"><span>{{ clock }}</span><span class="notch" /><span>▮▮▮ ◔</span></div>
    <div class="screen">
      <RouterView />
      <TabBar v-if="showTab" />
    </div>
  </div>
</template>
