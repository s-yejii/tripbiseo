<script setup>
// 여행 상세의 "챙길 것" 체크 목록 + 추가
// items: [{ text, done, why }] (why: 비 예보처럼 넣은 이유)
import { computed, ref } from 'vue'
import { showToast } from '@/utils/toast'

const props = defineProps({
  items: { type: Array, required: true },
})

const list = ref(props.items.map((item) => ({ ...item })))
const draft = ref('')
const doneCount = computed(() => list.value.filter((item) => item.done).length)

function add() {
  const text = draft.value.trim()
  if (!text) {
    showToast('챙길 것을 적어 주세요')
    return
  }
  list.value.push({ text, done: false })
  draft.value = ''
}
</script>

<template>
  <section class="pack" aria-label="챙길 것">
    <div class="sec-h" style="min-height: 32px">
      <h2 class="t-sub">챙길 것</h2>
      <span class="t-cap tn">{{ doneCount }}/{{ list.length }}</span>
    </div>
    <div class="pack__list">
      <button
        v-for="item in list"
        :key="item.text"
        type="button"
        class="pack__item"
        :class="{ 'is-on': item.done }"
        role="checkbox"
        :aria-checked="item.done"
        @click="item.done = !item.done"
      >
        <span class="check" :class="{ 'is-on': item.done }"><i class="ic ic--s i-check" aria-hidden="true"></i></span>
        <span>{{ item.text }}</span>
        <span v-if="item.why" class="badge badge--warning" style="height: 20px"><i class="ic ic--s i-rain" aria-hidden="true"></i>{{ item.why }}</span>
      </button>
    </div>
    <form class="pack__add" @submit.prevent="add">
      <label for="pack-in" class="sr">챙길 것 추가</label>
      <input id="pack-in" v-model="draft" type="text" placeholder="예: 여권, 상비약" autocomplete="off" />
      <button type="submit" class="btn btn--secondary btn--sm"><i class="ic ic--s i-plus" aria-hidden="true"></i>추가</button>
    </form>
  </section>
</template>
