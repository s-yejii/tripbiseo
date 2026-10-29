<script setup>
// 일정에 없던 곳에 20분 넘게 머물렀거나 결제가 있으면 회색 ? 핀 (⑤)
// 누르면 들렀는지 묻고, "네"면 남색 핀으로 바뀌어요(다시 누르면 info), "아니요"면 지워요
import { ref } from 'vue'
import { showToast } from '@/utils/toast'

defineProps({
  selected: { type: Boolean, default: false },
})
const emit = defineEmits(['info'])

const state = ref('ask') // ask: ? 핀 / yes: 들른 곳 / no: 지움
const asking = ref(false)

function answer(visited) {
  asking.value = false
  state.value = visited ? 'yes' : 'no'
  showToast(visited ? '들른 곳으로 남겼어요 · 핀 색이 바뀌었어요' : '지도에서 지웠어요')
  if (visited) emit('info')
}
</script>

<template>
  <button v-if="state === 'ask'" type="button" class="pin pin--q" style="left: 178px; top: 318px" aria-label="일정에 없던 곳, 들렀는지 확인하기" @click="asking = true">?</button>
  <button v-else-if="state === 'yes'" type="button" class="pin pin--visited" :class="{ 'pin--sel': selected }" style="left: 178px; top: 318px" aria-label="들른 곳 천호 소품 편집숍" @click="emit('info')">
    <i class="ic ic--s i-check" aria-hidden="true" style="width: 13px; height: 13px"></i>
    <span class="paydot" aria-hidden="true"><i class="ic i-won"></i></span>
  </button>

  <template v-if="asking">
    <div class="dim" style="z-index: 7" @click="asking = false"></div>
    <section class="modal" role="alertdialog" aria-modal="true" style="z-index: 8">
      <img src="/img/sopum.jpg" alt="" class="modal__img" />
      <div class="modal__text">
        <h2 class="h-title">천호 소품 편집숍에 들렀나요?</h2>
        <p class="t-body muted tn">일정에 없던 곳인데 14:22부터 8분 머물렀고 6,500원 결제가 있었어요</p>
      </div>
      <div class="modal__btns">
        <button type="button" class="btn btn--secondary btn--lg" @click="answer(false)">아니요</button>
        <button type="button" class="btn btn--primary btn--lg" @click="answer(true)">네, 들렀어요</button>
      </div>
    </section>
  </template>
</template>
