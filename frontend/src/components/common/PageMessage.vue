<script setup>
// 화면 가운데 안내 (불러오는 중 · 데이터 없음 · 서버 오류)
// 가짜 데이터 대신 지금 상태를 그대로 알려 줘요. 버튼은 to(이동) 또는 retry(다시 시도) 중 하나
defineProps({
  icon: { type: String, default: 'i-info' },
  title: { type: String, required: true },
  text: { type: String, default: '' },
  to: { type: String, default: '' }, // 버튼을 누르면 갈 곳
  button: { type: String, default: '' }, // 버튼 글자
  isLoading: { type: Boolean, default: false },
})
const emit = defineEmits(['retry'])
</script>

<template>
  <section class="page-msg" :aria-busy="isLoading">
    <span class="ico-tile" style="width: 56px; height: 56px"><i class="ic ic--xl" :class="isLoading ? 'i-clock' : icon" aria-hidden="true"></i></span>
    <h2 class="h-title">{{ title }}</h2>
    <p v-if="text" class="t-cap">{{ text }}</p>
    <RouterLink v-if="to && button" :to="to" class="btn btn--primary btn--lg">{{ button }}</RouterLink>
    <button v-else-if="button" type="button" class="btn btn--secondary btn--lg" @click="emit('retry')">{{ button }}</button>
  </section>
</template>

<style>
.page-msg { display: flex; flex-direction: column; align-items: center; gap: 12px; text-align: center; padding: 48px 24px; }
.page-msg .t-cap { margin: 0; }
</style>
