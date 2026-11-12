<script setup>
// 예약 놓침 카드 (④ 11/12): 식당·카페 예약에 가장 빠른 방법으로도 못 닿을 때(서버 lost- 알림), 또는 "못 가게 됐어요"를 눌렀을 때.
// CriticalAlertCard와 같은 모양(가운데 카드, 뒤 어둡게). 앱은 가게가 실제로 취소했는지 몰라서 사용자가 버튼으로 정해요
defineProps({
  name: { type: String, required: true },           // 놓친 가게 이름
  text: { type: String, default: '' },              // "가장 빠른 방법으로 가도 …" / "예약한 곳에 못 가게 됐나요?"
  canFindSimilar: { type: Boolean, default: true }, // 장소 id가 없으면(직접 입력한 곳) 비슷한 가게를 못 찾음
  isSkipping: { type: Boolean, default: false },
})
const emit = defineEmits(['similar', 'skip', 'keep'])
</script>

<template>
  <div class="crit" role="alertdialog" aria-modal="true" :aria-label="name + ' 예약 놓침'">
    <div class="crit__card">
      <span class="crit__icon lost__icon"><i class="ic ic--l i-warning" aria-hidden="true"></i></span>
      <b class="crit__title">{{ name }}</b>
      <span v-if="text" class="crit__text">{{ text }}</span>
      <span v-if="canFindSimilar" class="crit__text">비슷한 근처 가게를 찾아 드릴까요?</span>
      <div class="lost__buttons">
        <button v-if="canFindSimilar" type="button" class="btn btn--primary btn--lg btn--block" @click="emit('similar')">비슷한 근처 가게 찾기</button>
        <button type="button" class="btn btn--lg btn--block" :class="canFindSimilar ? 'btn--secondary' : 'btn--primary'" :disabled="isSkipping" @click="emit('skip')">빼고 다음 장소로</button>
      </div>
      <button type="button" class="textlink" @click="emit('keep')">그래도 갈게요</button>
    </div>
  </div>
</template>

<style>
/* .crit · .crit__card · .crit__icon · .crit__title · .crit__text는 CriticalAlertCard.vue의 스타일을 같이 씀 (LiveView가 둘 다 불러옴) */
.lost__icon { background: var(--danger-soft, #fdecec); color: var(--danger, #d33a3a); }
.lost__buttons { display: grid; gap: 8px; width: 100%; margin-top: 6px; }
</style>
