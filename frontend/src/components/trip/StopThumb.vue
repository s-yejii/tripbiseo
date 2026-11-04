<script setup>
// 일정 칸 사진: 믿을 만한 사진이 있으면 사진, 없으면 이름 첫 글자 + 종류 색 칸 (엉뚱한 사진보다 나아요)
import { computed, ref, watch } from 'vue'
import { kindOf } from '@/utils/budget'

const props = defineProps({
  src: { type: String, default: null },
  name: { type: String, default: '' },
  category: { type: String, default: '' },
})
const COLOR_OF_KIND = { food: '#FF7A59', bar: '#FF7A59', cafe: '#C8915B', tour: '#2FA67A', culture: '#2FA67A', shop: '#2FA67A', stay: '#1F3A68' }

const isBroken = ref(false) // 사진 주소가 깨졌으면 글자 칸으로
watch(() => props.src, () => {
  isBroken.value = false
})
const color = computed(() => COLOR_OF_KIND[kindOf(props.category)])
</script>

<template>
  <img v-if="src && !isBroken" class="thumb" :src="src" alt="" @error="isBroken = true" />
  <span v-else class="thumb stop-thumb--letter" :style="{ background: color }" aria-hidden="true">{{ name.charAt(0) }}</span>
</template>

<style>
.stop-thumb--letter { display: flex; align-items: center; justify-content: center; color: #fff; font-size: 18px; font-weight: 700; }
</style>
