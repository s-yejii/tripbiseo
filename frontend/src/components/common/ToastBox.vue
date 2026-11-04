<script setup>
// showToast()로 띄운 문구를 보여 주는 곳 (App.vue에 하나만 둠)
import { runToastAction, toast } from '@/utils/toast'
</script>

<template>
  <div v-if="toast.text" class="toast" :class="{ 'toast--top': toast.isTop }" role="status">{{ toast.text }}<button v-if="toast.actionText" type="button" class="toast__action" @click="runToastAction">{{ toast.actionText }}</button></div>
</template>

<style>
.toast.toast--top { top: 108px; bottom: auto; }
.toast__action { margin-left: 10px; padding: 0; border: 0; background: none; font: inherit; font-weight: 700; color: inherit; text-decoration: underline; pointer-events: auto; cursor: pointer; }
</style>
