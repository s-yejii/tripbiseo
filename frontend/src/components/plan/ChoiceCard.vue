<script setup>
// 여행 정보 단계의 큰 선택 카드: 아이콘 + 제목 + 한 줄 설명. to가 있으면 그 화면으로 가는 링크
defineProps({
  icon: { type: String, required: true },
  title: { type: String, required: true },
  desc: { type: String, default: '' },
  selected: { type: Boolean, default: false },
  to: { type: String, default: '' },
  color: { type: String, default: '' }, // 카드 고유 색 (없으면 앱 남색)
})
</script>

<template>
  <RouterLink v-if="to" :to="to" class="choice">
    <span class="choice__ic"><i class="ic" :class="icon" aria-hidden="true"></i></span>
    <b class="choice__title">{{ title }}</b>
    <span v-if="desc" class="choice__desc">{{ desc }}</span>
  </RouterLink>
  <button v-else type="button" class="choice" :class="{ 'is-on': selected, 'choice--color': color }" :style="color ? { '--choice': color } : null" :aria-pressed="selected">
    <span class="choice__ic"><i class="ic" :class="icon" aria-hidden="true"></i></span>
    <b class="choice__title">{{ title }}</b>
    <span v-if="desc" class="choice__desc">{{ desc }}</span>
    <span v-if="selected" class="choice__check" aria-hidden="true"><i class="ic ic--s i-check"></i></span>
  </button>
</template>

<style>
.choice-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.choice { position: relative; display: flex; flex-direction: column; align-items: flex-start; gap: 6px; min-height: 96px; padding: 12px; border: 1px solid var(--line); border-radius: var(--radius-lg); background: var(--surface-raised); box-shadow: var(--shadow-card); font: inherit; color: var(--ink); text-align: left; text-decoration: none; cursor: pointer; }
.choice:hover { border-color: var(--navy); color: var(--ink); }
.choice.is-on { border: 1.5px solid var(--navy); background: var(--navy-soft); }
.choice__ic { width: 36px; height: 36px; display: flex; align-items: center; justify-content: center; border-radius: 50%; background: var(--navy-soft); color: var(--navy); }
.choice.is-on .choice__ic { background: var(--surface-raised); }
.choice__title { font-size: 15px; line-height: 20px; font-weight: 700; }
.choice__desc { font-size: 12px; line-height: 16px; color: var(--ink-muted); }
.choice__check { position: absolute; top: 12px; right: 12px; color: var(--navy); }
/* 카드 고유 색: 평소엔 흰 카드에 연한 색 원, 고르면 테두리·연한 배경·체크 원 */
.choice--color { border-radius: 18px; background: #fff; border-color: transparent; box-shadow: 0 2px 10px rgba(31, 58, 104, .06); transition: transform 150ms; }
.choice--color:hover { border-color: var(--choice); }
.choice--color:active { transform: scale(.97); }
.choice--color .choice__ic { width: 48px; height: 48px; background: color-mix(in srgb, var(--choice) 12%, transparent); color: var(--choice); }
.choice--color .choice__ic .ic { width: 24px; height: 24px; }
.choice--color.is-on { border: 2px solid var(--choice); background: color-mix(in srgb, var(--choice) 8%, #fff); }
.choice--color.is-on .choice__ic { background: color-mix(in srgb, var(--choice) 12%, transparent); }
.choice--color .choice__check { display: flex; align-items: center; justify-content: center; width: 22px; height: 22px; border-radius: 50%; background: var(--choice); color: #fff; }
.choice--color .choice__check .ic { width: 14px; height: 14px; }
</style>
