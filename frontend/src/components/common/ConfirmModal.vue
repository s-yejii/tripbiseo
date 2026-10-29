<script setup>
// 한 번 더 묻는 창 (예: "성내동 소품샵을 뺄까요?")
// 가운데 내용이 더 필요하면 slot에 넣어요
defineProps({
  img: { type: String, default: '' },
  title: { type: String, required: true },
  text: { type: String, default: '' },
  okText: { type: String, default: '확인' },
  okIcon: { type: String, default: '' },
  cancelText: { type: String, default: '취소' },
  danger: { type: Boolean, default: false },
})
const emit = defineEmits(['ok', 'cancel'])
</script>

<template>
  <div class="dim" style="z-index: 8" @click="emit('cancel')"></div>
  <section class="modal" role="alertdialog" aria-modal="true" style="z-index: 9">
    <img v-if="img" :src="img" alt="" class="modal__img" />
    <div class="modal__text">
      <h2 class="h-title">{{ title }}</h2>
      <p v-if="text" class="t-body muted">{{ text }}</p>
    </div>
    <slot />
    <div class="modal__btns">
      <button type="button" class="btn btn--secondary btn--lg" @click="emit('cancel')">{{ cancelText }}</button>
      <button type="button" class="btn btn--lg" :class="danger ? 'btn--danger' : 'btn--primary'" @click="emit('ok')">
        <i v-if="okIcon" class="ic" :class="okIcon" aria-hidden="true"></i>{{ okText }}
      </button>
    </div>
  </section>
</template>

