<script setup>
// 아래에서 올라오는 선택 창 (날짜·인원·이동수단 등)
defineProps({ title: String })
defineEmits(['close'])
</script>

<template>
  <div class="dim" @click.self="$emit('close')">
    <section class="modal" role="dialog" :aria-label="title">
      <header class="row"><h2 class="grow">{{ title }}</h2><button class="icon-btn" aria-label="닫기" @click="$emit('close')">✕</button></header>
      <slot />
    </section>
  </div>
</template>

<style scoped>
.modal { position: absolute; left: 0; right: 0; bottom: 0; max-height: 85%; overflow-y: auto; background: #fff; border-radius: 22px 22px 0 0; padding: 18px 18px 24px; }
.modal h2 { font-size: 18px; font-weight: 800; }
.icon-btn { width: 32px; height: 32px; font-size: 16px; }
</style>
