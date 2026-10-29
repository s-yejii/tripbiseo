<script setup>
// S19-b 내 여행 스타일 (①): AI가 일정 짤 때 쓰는 기준(속도·좋아하는 곳·걷기·이동 수단·비)을 고쳐요
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'
import { showToast } from '@/utils/toast'

const router = useRouter()

// 고를 수 있는 값들
const paces = [
  { key: 'slow', label: '여유롭게', desc: '하루 3–4곳 · 한 곳에 오래', count: '하루 3–4곳' },
  { key: 'mid', label: '보통', desc: '하루 5–6곳', count: '하루 5–6곳' },
  { key: 'fast', label: '알차게', desc: '하루 7곳 이상 · 짧게 여러 곳', count: '하루 7곳 이상' },
]
const likeOptions = ['카페', '맛집', '쇼핑', '전시', '산책', '야경']
const walkOptions = [
  { min: 10, label: '10분까지' },
  { min: 20, label: '20분까지' },
  { min: 30, label: '30분도 OK' },
]
const moveOptions = [
  { name: '지하철', icon: 'i-subway' },
  { name: '버스', icon: 'i-bus' },
  { name: '택시', icon: 'i-car' },
  { name: '자동차', icon: 'i-car' },
]

// 가짜 데이터: 지금 내 스타일 (백엔드가 붙으면 member_style 테이블)
const pace = ref('mid')
const likes = ref(['카페', '맛집', '쇼핑'])
const walk = ref(20)
const moves = ref(['지하철'])
const rainIndoor = ref(true)

// 목록에 있으면 빼고, 없으면 넣어요 (여러 개 고르는 칩)
function toggleIn(list, item) {
  if (list.includes(item)) list.splice(list.indexOf(item), 1)
  else list.push(item)
}

function toggleMove(name) {
  if (moves.value.length === 1 && moves.value[0] === name) {
    showToast('이동 수단을 하나는 골라 주세요')
    return
  }
  toggleIn(moves.value, name)
}

// 아래 "이렇게 짜 드려요" 요약
const summary = computed(() => {
  const lines = [paces.find((p) => p.key === pace.value).count]
  if (likes.value.length) lines.push(likes.value.slice(0, 3).join('·') + ' 위주')
  if (walk.value >= 30) lines.push('30분까지 걸어도 괜찮음')
  else lines.push(walk.value + '분 넘게 걸으면 ' + moves.value[0])
  if (rainIndoor.value) lines.push('비 오면 실내 장소 먼저')
  return lines
})

function save() {
  router.push('/my')
  showToast('여행 스타일을 저장했어요')
}
</script>

<template>
  <div class="scr scr-StyleEdit">
    <div class="app app--white">
      <TopBar title="내 여행 스타일" back="/my" />

      <div class="scroll" style="padding-top: 8px; gap: 26px; bottom: 102px">
        <p class="t-cap" style="margin: 0; display: flex; gap: 6px; align-items: flex-start"><i class="ic ic--s i-sparkle c-navy" aria-hidden="true" style="margin-top: 2px"></i>AI가 일정을 짜고 장소를 추천할 때 이 답을 기준으로 해요. 여행마다 따로 바꿀 수도 있어요.</p>

        <section class="st-q" aria-labelledby="q1">
          <div class="st-q__h"><b id="q1">어떤 여행을 좋아하세요?</b><small>하루에 들르는 장소 수와 머무는 시간이 달라져요</small></div>
          <div class="st-pace" role="radiogroup" aria-labelledby="q1">
            <button v-for="p in paces" :key="p.key" type="button" role="radio" :class="{ 'is-on': pace === p.key }" :aria-checked="pace === p.key" @click="pace = p.key"><b>{{ p.label }}</b><small>{{ p.desc }}</small></button>
          </div>
        </section>

        <section class="st-q" aria-labelledby="q2">
          <div class="st-q__h"><b id="q2">좋아하는 곳</b><small>추천할 때 먼저 보여줘요 · 여러 개 골라요</small></div>
          <div class="chips" role="group" aria-labelledby="q2">
            <button v-for="like in likeOptions" :key="like" type="button" class="chip" :class="{ 'is-on': likes.includes(like) }" :aria-pressed="likes.includes(like)" @click="toggleIn(likes, like)">{{ like }}</button>
          </div>
        </section>

        <section class="st-q" aria-labelledby="q3">
          <div class="st-q__h"><b id="q3">걷는 건 한 번에 어디까지 괜찮아요?</b><small>이보다 길면 대중교통으로 안내해요</small></div>
          <div class="st-walk" role="radiogroup" aria-labelledby="q3">
            <button v-for="w in walkOptions" :key="w.min" type="button" role="radio" class="chip" :class="{ 'is-on': walk === w.min }" :aria-checked="walk === w.min" @click="walk = w.min">{{ w.label }}</button>
          </div>
        </section>

        <section class="st-q" aria-labelledby="q4">
          <div class="st-q__h"><b id="q4">주로 타는 이동 수단</b><small>이동 시간 계산과 길 안내에 써요</small></div>
          <div class="chips" role="group" aria-labelledby="q4">
            <button v-for="m in moveOptions" :key="m.name" type="button" class="chip" :class="{ 'is-on': moves.includes(m.name) }" :aria-pressed="moves.includes(m.name)" @click="toggleMove(m.name)"><i class="ic ic--s" :class="m.icon" aria-hidden="true"></i>{{ m.name }}</button>
          </div>
        </section>

        <div style="display: flex; align-items: center; gap: 12px">
          <i class="ic i-rain c-navy" aria-hidden="true"></i>
          <span style="flex: 1"><span class="t-strong" style="display: block">비 오면 실내 장소 먼저</span><span class="t-cap">비 예보가 있으면 야외 대신 실내를 추천해요</span></span>
          <button type="button" class="toggle" :class="{ 'is-on': rainIndoor }" role="switch" :aria-checked="rainIndoor" aria-label="비 오면 실내 장소 먼저" @click="rainIndoor = !rainIndoor"></button>
        </div>

        <div class="st-sum" aria-live="polite">
          <b><i class="ic ic--s i-sparkle" aria-hidden="true"></i> 이렇게 짜 드려요</b>
          <ul><li v-for="line in summary" :key="line">{{ line }}</li></ul>
        </div>
      </div>

      <div class="cta cta--white">
        <button type="button" class="btn btn--primary btn--lg btn--block" @click="save">저장</button>
      </div>
    </div>
  </div>
</template>

<style>
.scr-StyleEdit .st-q{display:flex;flex-direction:column;gap:10px}
.scr-StyleEdit .st-q__h{display:flex;flex-direction:column;gap:2px}
.scr-StyleEdit .st-q__h b{font-size:16px;line-height:24px}
.scr-StyleEdit .st-q__h small{font-size:12px;line-height:16px;color:var(--ink-muted)}
.scr-StyleEdit .st-pace{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}
.scr-StyleEdit .st-pace button{display:flex;flex-direction:column;align-items:flex-start;gap:2px;padding:12px;border:1px solid var(--line);border-radius:var(--radius-md);background:#fff;font:inherit;color:var(--ink);text-align:left;cursor:pointer}
.scr-StyleEdit .st-pace button:hover{border-color:var(--navy)}
.scr-StyleEdit .st-pace button.is-on{border:2px solid var(--navy);padding:11px;background:var(--navy-soft)}
.scr-StyleEdit .st-pace b{font-size:15px}
.scr-StyleEdit .st-pace small{font-size:12px;line-height:16px;color:var(--ink-muted)}
.scr-StyleEdit .st-sum{display:flex;flex-direction:column;gap:6px;padding:14px;border-radius:var(--radius-md);background:var(--navy-soft);color:var(--navy)}
.scr-StyleEdit .st-sum b{font-size:14px}
.scr-StyleEdit .st-sum ul{margin:0;padding-left:18px;font-size:13px;line-height:20px;color:var(--ink)}
.scr-StyleEdit .st-walk{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}
.scr-StyleEdit .st-walk .chip{justify-content:center}
</style>
