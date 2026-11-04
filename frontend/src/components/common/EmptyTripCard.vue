<script setup>
// "아직 여행이 없어요" 카드 (내 여행 빈 상태, 로그인 전 홈)
defineProps({
  to: { type: String, required: true }, // 여행 만들기 버튼이 갈 곳
  linkTo: { type: String, default: '' }, // 링크 넣기 버튼이 갈 곳 (없으면 버튼 없음)
})
</script>

<template>
  <section class="card card--pad empty-trip">
    <span class="ico-tile" style="width: 56px; height: 56px"><i class="ic ic--xl i-route" aria-hidden="true"></i></span>
    <h2 class="h-title">아직 여행이 없어요</h2>
    <p class="t-cap">지역과 날짜만 고르면 하루 일정이 나와요</p>
    <slot />
    <RouterLink :to="to" class="btn btn--primary btn--lg btn--block"><i class="ic i-plus" aria-hidden="true"></i>여행 만들기</RouterLink>
    <RouterLink v-if="linkTo" :to="linkTo" class="textlink"><i class="ic ic--s i-link" aria-hidden="true"></i>링크 넣기</RouterLink>
  </section>
</template>

<style>
.empty-trip { display: flex; flex-direction: column; align-items: center; gap: 12px; text-align: center; padding: 24px 16px; }
</style>
