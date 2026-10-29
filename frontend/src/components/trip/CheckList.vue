<script setup>
// "미리 확인할 것 N개" 접었다 펴는 상자 (비·퇴근길·행사·늦음)
// items: [{ kind, icon, title, desc }] / rainSwap: 비 칸에 "실내로 바꾸기" 버튼 보이기
import { computed, ref } from 'vue'

const props = defineProps({
  items: { type: Array, required: true },
  rainSwap: { type: Boolean, default: false },
})

const open = ref(false)
const bad = computed(() => props.items.some((item) => item.kind === 'late'))
</script>

<template>
  <div v-if="items.length" class="chk" :class="{ 'chk--bad': bad }">
    <button type="button" class="chk__head" :aria-expanded="open" @click="open = !open">
      <i class="ic" :class="bad ? 'i-danger' : 'i-info'" aria-hidden="true"></i>
      <b>미리 확인할 것 {{ items.length }}개</b>
      <span class="chk__icons" aria-hidden="true"><i v-for="item in items" :key="item.title" class="ic ic--s" :class="item.icon"></i></span>
      <i class="ic ic--s" :class="open ? 'i-up' : 'i-down'" aria-hidden="true"></i>
    </button>
    <ul v-if="open" class="chk__list">
      <li v-for="item in items" :key="item.title">
        <i class="ic ic--s" :class="item.icon" aria-hidden="true"></i>
        <span>
          <b>{{ item.title }}</b>
          <small>{{ item.desc }}</small>
          <RouterLink v-if="item.kind === 'rain' && rainSwap" to="/live/alt/swap?why=rain" class="btn btn--secondary btn--sm" style="margin-top: 6px">실내로 바꾸기</RouterLink>
        </span>
      </li>
    </ul>
  </div>
</template>
