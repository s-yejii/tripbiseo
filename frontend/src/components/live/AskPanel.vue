<script setup>
// 여행 중 AI 채팅 (④): 지도 아래 입력창에 말하면 AI가 세 가지 중 하나로 나눠요
//   근처 추천 → 반경 고르기 → 평점 상위 3곳 핀 → 골라서 일정에 넣기
//   일정 바꾸기 → 비·실내면 "비 올 때 바꾸기", 그 밖은 대안 A·B·C 화면으로
//   일정 질문 → 오늘 일정 안에서 답
// 지금은 data/ask.js의 가짜 응답을 써요. 백엔드가 붙으면 POST /api/trips/{id}/live/assist·recommend로 바꿔요
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { examples, radiusOptions, isRainRequest, askAssist, askRecommend } from '@/data/ask'

const props = defineProps({
  me: { type: Object, required: true }, // 지금 위치 { x, y }
  late: { type: Boolean, default: false }, // 늦은 상황인지
})
const emit = defineEmits(['close', 'add'])

const router = useRouter()
const THINK_MS = 500 // AI가 생각하는 것처럼 잠깐 기다림 (가짜)

const draft = ref('') // 입력칸 글자
const myMessage = ref('') // 방금 보낸 말
const reply = ref('') // AI 답
const isBusy = ref(false)
const category = ref(null) // 추천할 종류 (반경을 물을 때만)
const radius = ref(null) // 고른 반경(m)
const places = ref([]) // 추천 결과
const pickedId = ref(null)

const picked = computed(() => places.value.find((p) => p.id === pickedId.value))
const hasStarted = computed(() => myMessage.value !== '')

// 보내기: 가짜 AI 응답을 받아 kind에 따라 나눠요
function send(text) {
  const message = text.trim()
  if (!message || isBusy.value) return
  myMessage.value = message
  draft.value = ''
  reply.value = ''
  resetRecommend()
  isBusy.value = true
  setTimeout(() => {
    isBusy.value = false
    handleAssist(askAssist(message, props.late), message)
  }, THINK_MS)
}

function handleAssist(res, message) {
  if (res.kind === 'ask-radius') {
    category.value = res.category
    reply.value = `${res.category}, 어느 범위에서 찾을까요?`
  } else if (res.kind === 'alternatives') {
    goAlternatives(message)
  } else {
    reply.value = res.text
  }
}

// 비·실내 요청은 장소 하나를 실내로 바꾸는 화면, 그 밖은 대안 A·B·C 화면
function goAlternatives(message) {
  if (isRainRequest(message)) return router.push('/live/alt/swap?why=rain')
  router.push('/live/alt')
}

// 반경을 고르면 평점 상위 3곳을 찾아요
function pickRadius(meters) {
  radius.value = meters
  pickedId.value = null
  const res = askRecommend(category.value, meters, props.me)
  places.value = res.places
  if (res.places.length) {
    reply.value = `${distText(meters)} 안 ${category.value} 평점 상위 ${res.places.length}곳이에요. 핀을 눌러 골라 주세요.`
  } else {
    reply.value = `${distText(meters)} 안에서는 지금 열려 있는 곳을 찾지 못했어요. 반경을 넓혀 볼까요?`
  }
}

function resetRecommend() {
  category.value = null
  radius.value = null
  places.value = []
  pickedId.value = null
}

function distText(m) {
  return m >= 1000 ? m / 1000 + 'km' : m + 'm'
}
</script>

<template>
  <!-- 지도 위: 반경 원 + 추천 핀 (번호) -->
  <template v-if="radius">
    <span
      class="near-ring"
      :style="{ left: me.x + 'px', top: me.y + 'px', width: radius / 2 + 'px', height: radius / 2 + 'px', margin: `-${radius / 4}px 0 0 -${radius / 4}px` }"
      aria-hidden="true"
    ></span>
    <button
      v-for="(p, i) in places"
      :key="p.id"
      type="button"
      class="pin pin--res"
      :class="{ 'pin--sel': p.id === pickedId }"
      :style="{ left: p.x + 'px', top: p.y + 'px' }"
      :aria-label="`추천 ${i + 1} ${p.name}, ${distText(p.distance)}`"
      @click="pickedId = p.id"
    >
      {{ i + 1 }}
    </button>
  </template>

  <div class="addbar">
    <!-- 고른 장소 카드 -->
    <div v-if="picked" class="addcard">
      <div class="ask-place">
        <img class="thumb" :src="picked.img" alt="" />
        <div style="flex: 1; min-width: 0">
          <b class="t-strong">{{ picked.name }}</b>
          <p class="t-cap tn ask-line"><i class="ic ic--s i-star c-warning" aria-hidden="true"></i> {{ picked.rating }} · {{ picked.category }} · {{ picked.closeAt }} 마감</p>
          <p class="t-cap tn ask-line">지금 위치에서 {{ distText(picked.distance) }} · 걸어서 {{ Math.max(1, Math.round(picked.distance / 70)) }}분</p>
        </div>
        <button type="button" class="iconbtn" aria-label="추천 목록으로" @click="pickedId = null"><i class="ic i-close muted" aria-hidden="true"></i></button>
      </div>
      <div class="cta__row">
        <button type="button" class="btn btn--secondary btn--md" @click="router.push('/place')">자세히</button>
        <button type="button" class="btn btn--primary btn--md" @click="emit('add', picked)"><i class="ic ic--s i-plus" aria-hidden="true"></i>일정에 넣기</button>
      </div>
    </div>

    <!-- 주고받은 말 -->
    <div v-else-if="hasStarted" class="addcard" aria-live="polite">
      <p class="ask-me">{{ myMessage }}</p>
      <p v-if="isBusy" class="ask-ai t-cap"><i class="ic ic--s i-sparkle" aria-hidden="true"></i>생각하는 중이에요…</p>
      <p v-else-if="reply" class="ask-ai"><i class="ic ic--s i-sparkle c-navy" aria-hidden="true"></i>{{ reply }}</p>
      <div v-if="category && !isBusy" class="chips ask-chips">
        <button
          v-for="r in radiusOptions"
          :key="r"
          type="button"
          class="chip chip--sm"
          :class="{ 'is-on': radius === r }"
          :aria-pressed="radius === r"
          @click="pickRadius(r)"
        >
          {{ distText(r) }}
        </button>
      </div>
    </div>

    <!-- 처음: 안내 + 예시 칩 -->
    <div v-else class="addcard addcard--hint">
      <p class="t-cap" style="margin: 0">여행 중에 필요한 걸 말해 주세요. 근처 추천, 일정 바꾸기, 오늘 일정 질문을 도와드려요.</p>
      <div class="chips ask-chips">
        <button v-for="text in examples" :key="text" type="button" class="chip chip--sm" @click="send(text)">{{ text }}</button>
      </div>
    </div>

    <form class="addform" @submit.prevent="send(draft)">
      <label for="ask-q" class="sr">AI에게 물어보기</label>
      <input id="ask-q" v-model="draft" type="text" maxlength="300" placeholder="예) 근처 카페 / 비 와서 실내로" autocomplete="off" :disabled="isBusy" />
      <button type="submit" class="iconbtn" aria-label="보내기" style="color: var(--navy)" :disabled="isBusy"><i class="ic i-send" aria-hidden="true"></i></button>
      <button type="button" class="iconbtn" aria-label="AI 채팅 닫기" @click="emit('close')"><i class="ic i-close muted" aria-hidden="true"></i></button>
    </form>
  </div>
</template>

<style>
.ask-place { display: flex; gap: 12px; align-items: center; }
.ask-place .thumb { width: 64px; height: 64px; }
.ask-line { margin: 2px 0 0; }
.ask-chips { flex-wrap: nowrap; overflow-x: auto; scrollbar-width: none; }
.ask-chips::-webkit-scrollbar { display: none; }
.ask-me { align-self: flex-end; max-width: 85%; margin: 0; padding: 8px 12px; border-radius: 14px 14px 4px 14px; background: var(--navy); color: #fff; font-size: 14px; }
.ask-ai { display: flex; gap: 6px; align-items: flex-start; margin: 0; font-size: 14px; line-height: 1.5; }
.ask-ai .ic { flex: none; margin-top: 3px; }
</style>
