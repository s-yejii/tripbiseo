<script setup>
// showToast()로 띄운 문구를 보여 주는 곳 (App.vue에 하나만 둠)
import { toast } from '@/utils/toast'
</script>

<template>
  <div v-if="toast.text" class="toast" role="status">{{ toast.text }}</div>
</template>
