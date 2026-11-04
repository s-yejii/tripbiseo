<script setup>
// 대안 3가지 탭: 시간 줄이기 / 한 곳 빼기 / 장소 바꾸기 (세 화면이 같이 써요)
// 탭 아래 숫자는 서버 추천 대안(POST /live/alternatives)의 여유예요. 서버가 못 만든 대안은 "없음"
// active: trim | drop | swap
import { useReplanStore } from '@/stores/replan'

defineProps({
  active: { type: String, required: true },
})

const replan = useReplanStore()

// short: 서버 대안의 탭 이름 (A 시간 단축 · B 한 곳 빼기 · C 장소 변경)
const TABS = [
  { key: 'trim', to: '/live/alt', label: '시간 줄이기', short: '시간 단축' },
  { key: 'drop', to: '/live/alt/drop', label: '한 곳 빼기', short: '한 곳 빼기' },
  { key: 'swap', to: '/live/alt/swap', label: '장소 바꾸기', short: '장소 변경' },
]

function slackText(tab) {
  const option = replan.findOption(tab.short)
  if (!option) return '없음'
  return option.slackMin < 0 ? -option.slackMin + '분 늦음' : '여유 ' + option.slackMin + '분'
}
</script>

<template>
  <nav class="alt-tabs" aria-label="대안 종류">
    <RouterLink
      v-for="tab in TABS"
      :key="tab.key"
      :to="tab.to"
      replace
      :class="{ 'is-on': tab.key === active }"
      :aria-current="tab.key === active ? 'page' : null"
    >
      <b>{{ tab.label }}</b><small>{{ slackText(tab) }}</small>
    </RouterLink>
  </nav>
</template>
