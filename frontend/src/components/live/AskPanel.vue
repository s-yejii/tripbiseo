<script setup>
// 여행 중 AI 채팅 (④): 지도 아래 입력창에 말하면 AI가 세 가지 중 하나로 나눠요
//   근처 추천 → 말을 SQL로 바꿔 바로 찾기(places, 11/08) → 핀은 진짜 지도에(found로 LiveView에 넘김) → 골라서 일정에 넣기
//             AI로 못 찾으면 예전처럼 반경 고르기 → 평점 상위 3곳
//   일정 바꾸기 → 대안 A·B·C 화면으로 (비 올 때 실내로 바꾸기는 하지 않음, 11/02 결정)
//   일정 질문 → 오늘 일정 안에서 답
// 11/13: 한 여행에서 주고받은 말은 계속 쌓여요 (utils/askChat.js, 패널을 닫거나 새로고침해도 그대로)
//   찾은 곳은 그 답 말풍선 아래 가로 카드 줄 (네이버·카카오 지도처럼). 지도 핀은 "지금 보고 있는 답"의 결과만:
//   새 답이 오면 그 답, 예전 답의 카드를 누르면 그 답으로 바뀜. "이 중에 …"도 지금 보고 있는 답을 가리켜요
// tripId·position을 받으면 서버(POST /live/assist·recommend)를 부르고, 없으면 data/ask.js의 시연용 가짜 응답
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { examples, radiusOptions, askAssist, askRecommend } from '@/data/ask'
import * as api from '@/api/trips'
import { distanceMeters } from '@/utils/geo'
import { loadPhotos, photoFor } from '@/utils/placePhoto'
import { loadChat, saveChat } from '@/utils/askChat'
import '@/assets/css/live-panel.css' // 지도 위 패널 생김새 (근처 찾기·비슷한 가게와 같이)

const props = defineProps({
  me: { type: Object, required: true }, // 지금 위치 (지도 그림 위 { x, y }, 가짜 모드)
  late: { type: Boolean, default: false }, // 늦은 상황인지
  tripId: { type: [Number, String], default: null }, // 있으면 서버 모드
  position: { type: Object, default: null }, // 지금 GPS { lat, lng } (서버 모드)
  selected: { type: String, default: null }, // 진짜 지도에서 누른 결과 핀 id (LiveView가 알려 줌)
  chatKey: { type: String, default: 'demo' }, // 대화를 남길 이름 (utils/askChat.js chatKey: 여행 id + 시작 날짜)
  planned: { type: Array, default: () => [] }, // 지금 있는 곳·남은 일정의 장소 id (이미 일정에 있으면 [일정에 넣기] 대신 '일정에 있어요')
})
const SCROLL_END_MS = 120 // 카드 줄을 넘기고 이만큼 멈추면 왼쪽 카드를 고름
const THINK_MS = 500 // AI가 생각하는 것처럼 잠깐 기다림 (가짜)
// found: 지금 보고 있는 답의 결과(없으면 null) → LiveView가 지도에 핀, pick: 카드를 고름 → 지도 핀도 같이, choose: 일정에 넣기 → 순서 정하기
const emit = defineEmits(['close', 'add', 'found', 'pick', 'choose'])

const router = useRouter()
// 말풍선 목록: { id, role: 'me' | 'ai', text, places?, band?, question?(이 결과를 찾은 질문), radiusFor?({ category, tags, question }), radiusPicked?, isTyping? }
const messages = ref(loadChat(props.chatKey))
const draft = ref('') // 입력칸 글자
const isBusy = ref(false)
const activeId = ref(null) // 지금 지도에 핀으로 보이는 답 (말풍선 id)
const pickedId = ref(null) // 고른 카드 (= 지도에서 강조된 핀)
const threadBox = ref(null)
const cardBoxes = {} // 답마다 카드 줄 요소 { 말풍선 id: 요소 }
let nextId = Date.now()

const isServer = computed(() => props.tripId != null && props.position != null)
const activeMessage = computed(() => messages.value.find((message) => message.id === activeId.value) || null)
const placeholder = computed(() => (activeMessage.value ? '이 중에 더 조용한 곳은?' : '가는 길에 들를 카페'))

watch(messages, () => saveChat(props.chatKey, messages.value), { deep: true })

// 다시 열면: 대화 맨 아래로, 마지막으로 찾은 결과를 지도에 다시 보여 줌
onMounted(() => {
  scrollThreadToEnd()
  for (let i = messages.value.length - 1; i >= 0; i--) {
    if (messages.value[i].places && messages.value[i].places.length) {
      activate(messages.value[i])
      break
    }
  }
})

// 지도 핀을 누르면 지금 보고 있는 답의 그 카드로 넘어감
watch(() => props.selected, (id) => {
  if (!id || id === pickedId.value || !activeMessage.value) return
  pickedId.value = id
  scrollToCard(activeMessage.value.id, id)
})

// ------------------------------------------------------------ 보내기·답
function addMessage(message) {
  const item = { id: ++nextId, ...message }
  messages.value.push(item)
  scrollThreadToEnd()
  return messages.value[messages.value.length - 1] // 반응형(ref 안) 객체로 돌려줌
}

function send(text) {
  const message = text.trim()
  if (!message || isBusy.value) return
  draft.value = ''
  addMessage({ role: 'me', text: message })
  const reply = addMessage({ role: 'ai', text: '', isTyping: true })
  isBusy.value = true
  if (isServer.value) return sendToServer(message, reply)
  setTimeout(() => {
    isBusy.value = false
    handleAssist(askAssist(message, props.late), reply, message)
  }, THINK_MS)
}

async function sendToServer(message, reply) {
  try {
    const res = await api.askAssist(props.tripId, message, props.position.lat, props.position.lng, lastAsk())
    handleAssist(res, reply, message)
  } catch (e) {
    finishReply(reply, e.message)
  } finally {
    isBusy.value = false
  }
}

function handleAssist(res, reply, question) {
  if (res.kind === 'places') {
    showPlaces(reply, res.found.message, res.found.places, res.found.band, question)
  } else if (res.kind === 'ask-radius') {
    finishReply(reply, `${res.category}, 어느 범위에서 찾을까요?`)
    reply.radiusFor = { category: res.category, tags: res.tags || [], question }
  } else if (res.kind === 'alternatives') {
    finishReply(reply, '남은 일정을 바꾸는 방법을 보여 드릴게요.')
    router.push('/live/alt') // 일정 바꾸기는 대안 A·B·C 화면
  } else {
    finishReply(reply, res.text)
  }
}

function finishReply(reply, text) {
  reply.isTyping = false
  reply.text = text
  scrollThreadToEnd()
}

/** 답 말풍선에 찾은 곳을 붙이고, 지도도 이 답으로 */
function showPlaces(reply, text, places, band, question) {
  finishReply(reply, text)
  if (!places || places.length === 0) return
  reply.places = places
  reply.band = band || null
  reply.question = question
  activate(reply)
}

// 반경 칩을 누르면 평점 상위 3곳 (내 말풍선으로 "500m 안에서"를 남김)
async function pickRadius(ask, meters) {
  if (isBusy.value || ask.radiusPicked) return
  ask.radiusPicked = meters
  addMessage({ role: 'me', text: distText(meters) + ' 안에서' })
  const reply = addMessage({ role: 'ai', text: '', isTyping: true })
  isBusy.value = true
  const { category, tags, question } = ask.radiusFor
  try {
    const res = isServer.value
      ? await api.askRecommend(props.tripId, props.position.lat, props.position.lng, meters, category, tags)
      : askRecommend(category, meters, props.me)
    const text = res.places.length
      ? `${distText(meters)} 안 ${category} ${res.places.length}곳이에요`
      : `${distText(meters)} 안에서는 지금 열려 있는 곳을 찾지 못했어요. 더 넓게 찾아 볼까요?`
    showPlaces(reply, text, res.places, null, question)
  } catch (e) {
    finishReply(reply, e.message)
  } finally {
    isBusy.value = false
  }
}

/** "이 중에 …"가 가리킬 앞 결과 = 지금 지도에 보이는 답 */
function lastAsk() {
  const active = activeMessage.value
  return active ? { message: active.question, ids: active.places.map((place) => place.id) } : null
}

// ------------------------------------------------------------ 지금 보고 있는 답 ↔ 지도 핀 ↔ 카드
/** 이 답의 결과를 지도에 (첫 카드를 골라 둠, 네이버 지도처럼) */
function activate(message, cardId = null) {
  activeId.value = message.id
  loadPhotos(message.places.map((place) => place.id))
  if (isServer.value) emit('found', { places: message.places, band: message.band })
  select(message, cardId || message.places[0].id, cardId != null)
}

/** 카드를 고름 → 지도 핀도 강조. 예전 답의 카드면 지도를 그 답으로 바꿈 */
function select(message, id, scroll) {
  if (message.id !== activeId.value) return activate(message, id)
  pickedId.value = id
  emit('pick', id)
  if (scroll) scrollToCard(message.id, id)
}

function setCardBox(messageId, el) {
  if (el) cardBoxes[messageId] = el
  else delete cardBoxes[messageId]
}

let scrollTimer = null
// 지금 보고 있는 답의 카드 줄을 손으로 넘기고 멈추면 왼쪽에 걸린 카드를 고름 (끝까지 넘겼으면 마지막 카드)
function onCardsScroll(message) {
  if (message.id !== activeId.value) return
  clearTimeout(scrollTimer)
  scrollTimer = setTimeout(() => pickShownCard(message), SCROLL_END_MS)
}
function pickShownCard(message) {
  const box = cardBoxes[message.id]
  if (!box || box.children.length === 0) return
  const isAtEnd = box.scrollLeft + box.clientWidth >= box.scrollWidth - 2
  let shown = isAtEnd ? box.children[box.children.length - 1] : box.children[0]
  if (!isAtEnd) {
    for (const card of box.children) {
      if (Math.abs(card.offsetLeft - box.scrollLeft) < Math.abs(shown.offsetLeft - box.scrollLeft)) shown = card
    }
  }
  if (shown.dataset.id !== pickedId.value) select(message, shown.dataset.id, false)
}
function scrollToCard(messageId, id) {
  nextTick(() => {
    const box = cardBoxes[messageId]
    const card = box ? [...box.children].find((el) => el.dataset.id === id) : null
    if (card) box.scrollTo({ left: card.offsetLeft, behavior: 'smooth' })
  })
}
function scrollThreadToEnd() {
  nextTick(() => {
    if (threadBox.value) threadBox.value.scrollTop = threadBox.value.scrollHeight
  })
}

// ------------------------------------------------------------ 카드 글자·버튼
function shortCategory(text) {
  return (text || '장소').split(',')[0]
}
/** 지금 위치에서 거리 (지금 GPS로 다시 계산: 예전 답도 지금 기준) */
function distanceOf(place) {
  if (props.position) return Math.round(distanceMeters(props.position.lat, props.position.lng, place.lat, place.lng))
  return place.distance || 0
}
/** 카드 아래 줄: 가는 길로 찾았으면 몇 분 더·여유, 아니면 지금 위치에서 거리 */
function infoText(place) {
  if (place.detourMinutes == null) return '지금 위치에서 ' + distText(distanceOf(place))
  let text = '가는 길 +' + place.detourMinutes + '분' + (place.isEstimate ? ' (어림)' : '')
  if (place.slackAfter != null) text += place.slackAfter >= 0 ? ' · 들러도 여유 ' + place.slackAfter + '분' : ' · 들르면 늦어요'
  return text
}
function isLate(place) {
  return place.slackAfter != null && place.slackAfter < 0
}

function openDetail(place) {
  router.push('/place?id=' + encodeURIComponent(place.id))
}
// 일정에 넣기: 서버 모드면 넣을 순서 정하기로 (LiveView의 InsertSlotSheet, 11/13). 가짜 모드는 바로 알림
function addPlace(message, place) {
  select(message, place.id, false)
  if (!isServer.value) return emit('add', place)
  emit('choose', { ...place, distance: distanceOf(place) })
}

function distText(m) {
  return m >= 1000 ? (m / 1000).toFixed(1) + 'km' : m + 'm'
}
</script>

<template>
  <div class="addbar">
    <section class="addcard lp-panel" aria-label="AI 여행 도우미">
      <header class="lp-head">
        <span class="lp-avatar lp-avatar--ai" aria-hidden="true"><i class="ic ic--s i-sparkle"></i></span>
        <b class="lp-head__title">AI 여행 도우미</b>
        <button type="button" class="iconbtn" aria-label="AI 채팅 닫기" @click="emit('close')"><i class="ic i-close muted" aria-hidden="true"></i></button>
      </header>

      <!-- 주고받은 말 (이 여행에서 한 말이 다 남아요) -->
      <div ref="threadBox" class="lp-thread lp-thread--chat" aria-live="polite">
        <div class="lp-row">
          <span class="lp-avatar lp-avatar--ai lp-avatar--sm" aria-hidden="true"><i class="ic ic--s i-sparkle"></i></span>
          <p class="lp-bubble">여행 중에 필요한 걸 말해 주세요. 근처 추천, 일정 바꾸기, 오늘 일정 질문을 도와드려요.</p>
        </div>
        <div v-if="!messages.length" class="lp-chips lp-chips--indent">
          <button v-for="text in examples" :key="text" type="button" class="chip chip--sm" @click="send(text)">{{ text }}</button>
        </div>

        <template v-for="m in messages" :key="m.id">
          <p v-if="m.role === 'me'" class="lp-me">{{ m.text }}</p>
          <template v-else>
            <div class="lp-row">
              <span class="lp-avatar lp-avatar--ai lp-avatar--sm" aria-hidden="true"><i class="ic ic--s i-sparkle"></i></span>
              <p v-if="m.isTyping" class="lp-bubble lp-typing" aria-label="생각하는 중이에요"><span></span><span></span><span></span></p>
              <p v-else class="lp-bubble">{{ m.text }}</p>
            </div>

            <!-- 반경 고르기 (AI가 질문을 다 못 알아들었을 때) -->
            <div v-if="m.radiusFor && !m.radiusPicked" class="lp-chips lp-chips--indent">
              <button v-for="r in radiusOptions" :key="r" type="button" class="chip chip--sm" :disabled="isBusy" @click="pickRadius(m, r)">{{ distText(r) }}</button>
            </div>

            <!-- 찾은 곳: 가로로 넘기는 카드 줄 (지금 보고 있는 답이면 지도 핀과 같이 움직임) -->
            <div
              v-if="m.places && m.places.length"
              :ref="(el) => setCardBox(m.id, el)"
              class="lp-cards"
              :class="{ 'is-past': m.id !== activeId }"
              role="list"
              aria-label="찾은 곳, 옆으로 넘겨 보기"
              @scroll.passive="onCardsScroll(m)"
            >
              <article
                v-for="p in m.places"
                :key="p.id"
                class="lp-card"
                :class="{ 'is-sel': m.id === activeId && p.id === pickedId }"
                :data-id="p.id"
                role="listitem"
              >
                <button type="button" class="lp-card__main" :aria-pressed="m.id === activeId && p.id === pickedId" @click="select(m, p.id, true)">
                  <img class="lp-card__img" :src="photoFor(p)" alt="" />
                  <span class="lp-card__body">
                    <b class="lp-card__name">{{ p.name }}</b>
                    <span class="lp-card__meta tn">
                      <template v-if="p.rating != null"><i class="ic ic--s i-star c-warning" aria-hidden="true"></i>{{ p.rating }} · </template>{{ shortCategory(p.category) }}<template v-if="p.closeAt"> · {{ p.closeAt }} 마감</template>
                    </span>
                    <span class="lp-card__info tn" :class="{ 'is-late': isLate(p) }">{{ infoText(p) }}</span>
                  </span>
                </button>
                <div class="lp-card__actions">
                  <button type="button" class="btn btn--secondary btn--sm" @click="openDetail(p)">상세</button>
                  <span v-if="planned.includes(p.id)" class="lp-card__planned"><i class="ic ic--s i-check" aria-hidden="true"></i>일정에 있어요</span>
                  <button v-else type="button" class="btn btn--primary btn--sm" @click="addPlace(m, p)"><i class="ic ic--s i-plus" aria-hidden="true"></i>일정에 넣기</button>
                </div>
              </article>
            </div>
          </template>
        </template>
      </div>

      <form class="lp-form" @submit.prevent="send(draft)">
        <label for="ask-q" class="sr">AI에게 물어보기</label>
        <input id="ask-q" v-model="draft" type="text" maxlength="300" :placeholder="placeholder" autocomplete="off" :disabled="isBusy" />
        <button type="submit" class="lp-send" :class="{ 'is-empty': !draft.trim() }" aria-label="보내기" :disabled="isBusy"><i class="ic ic--s i-send" aria-hidden="true"></i></button>
      </form>
    </section>
  </div>
</template>
