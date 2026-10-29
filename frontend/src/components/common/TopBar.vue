<script setup>
// 화면 위쪽 바: 왼쪽 버튼(뒤로가기·닫기) + 제목 + 오른쪽 칸(slot)
// 예) <TopBar title="대안 고르기" back="/live/delay"><span class="badge">14분 늦음</span></TopBar>
import { useRouter } from 'vue-router'
import { goBack } from '@/utils/back'

const props = defineProps({
  title: { type: String, default: '' },
  icon: { type: String, default: 'back' }, // back | close | none
  back: { type: String, default: '/' }, // 뒤로 갈 화면이 없을 때 갈 곳
  to: { type: String, default: '' }, // 정해진 화면으로만 갈 때 (예: 닫기 → 지도)
  float: { type: Boolean, default: false }, // 지도 위에 떠 있는 바
})

const router = useRouter()

function onLeft() {
  if (props.to) router.push(props.to)
  else goBack(props.back)
}
</script>

<template>
  <header class="topbar" :class="{ 'topbar--float': float }">
    <button
      v-if="icon !== 'none'"
      type="button"
      class="iconbtn"
      :class="{ 'iconbtn--float': float }"
      :aria-label="icon === 'close' ? '닫기' : '뒤로가기'"
      @click="onLeft"
    >
      <i class="ic ic--l" :class="icon === 'close' ? 'i-close' : 'i-back'" aria-hidden="true"></i>
    </button>
    <h1 v-if="title" class="topbar__title">{{ title }}</h1>
    <span v-else style="flex: 1"></span>
    <slot />
  </header>
</template>
