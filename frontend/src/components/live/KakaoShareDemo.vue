<script setup>
// 시연 계정용 카카오톡 공유 화면 (③, 11/08 주형): 발표 PC에서 카카오 로그인 창이 뜨거나 진짜 사람에게 가지 않게,
//   카카오톡 공유 흐름(대상 고르기 → 채팅방에 피드 메시지)을 앱 안에서 보여 줘요. 실제로 보내지는 않아요.
//   1) 공유할 대상 선택 (팀 채팅방 체크) → 2) 보내기 → 3) 채팅방에 피드 카드 + 팀원 답장
import { onUnmounted, ref } from 'vue'

const props = defineProps({
  message: { type: Object, required: true }, // { title, description, imageUrl }
})
const emit = defineEmits(['close'])

const ROOM = { name: '영등포 출장', count: 4, members: '김 팀장, 이 과장, 최 대리' }
const REPLY = { name: '이 과장', text: '팀장님 괜찮으세요? 1호선 타요' }
const REPLY_DELAY_MS = 1800 // 보낸 뒤 답장이 오는 시간

const step = ref('pick') // pick → room
const isPicked = ref(true)
const showReply = ref(false)
let replyTimer = null

function send() {
  step.value = 'room'
  replyTimer = setTimeout(() => (showReply.value = true), REPLY_DELAY_MS)
}
onUnmounted(() => clearTimeout(replyTimer))

function nowText() {
  const now = new Date()
  const hour = now.getHours()
  return (hour < 12 ? '오전 ' : '오후 ') + ((hour % 12) || 12) + ':' + String(now.getMinutes()).padStart(2, '0')
}
const sentAt = nowText()
</script>

<template>
  <div class="ks" role="dialog" aria-label="카카오톡 공유">
    <template v-if="step === 'pick'">
      <header class="ks__bar">
        <button type="button" class="ks__x" aria-label="닫기" @click="emit('close')">✕</button>
        <b>공유할 대상 선택</b>
        <span></span>
      </header>
      <div class="ks__search">이름 검색</div>
      <p class="ks__label">채팅방</p>
      <button type="button" class="ks__row" @click="isPicked = !isPicked">
        <span class="ks__ava ks__ava--group">{{ ROOM.count }}</span>
        <span class="ks__body"><b>{{ ROOM.name }} <span class="ks__muted">{{ ROOM.count }}</span></b><span class="ks__muted">{{ ROOM.members }}</span></span>
        <span class="ks__check" :class="{ 'is-on': isPicked }">✓</span>
      </button>
      <footer class="ks__foot">
        <button type="button" class="ks__send" :disabled="!isPicked" @click="send">보내기{{ isPicked ? ' 1' : '' }}</button>
      </footer>
    </template>

    <template v-else>
      <header class="ks__bar ks__bar--room">
        <button type="button" class="ks__x" aria-label="닫기" @click="emit('close')">←</button>
        <b>{{ ROOM.name }} <span class="ks__muted">{{ ROOM.count }}</span></b>
        <span></span>
      </header>
      <div class="ks__chat">
        <div class="ks__me">
          <span class="ks__time">{{ sentAt }}</span>
          <div class="ks__feed">
            <img v-if="message.imageUrl" :src="message.imageUrl" alt="" class="ks__feed-img" />
            <div class="ks__feed-body">
              <b>{{ message.title }}</b>
              <span>{{ message.description }}</span>
            </div>
            <span class="ks__feed-btn">바뀐 일정 보기</span>
            <span class="ks__feed-from">여행어때</span>
          </div>
        </div>
        <div v-if="showReply" class="ks__them">
          <span class="ks__ava">{{ REPLY.name[0] }}</span>
          <div>
            <span class="ks__name">{{ REPLY.name }}</span>
            <div class="ks__line"><span class="ks__bubble">{{ REPLY.text }}</span><span class="ks__time">{{ nowText() }}</span></div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style>
.ks { position: absolute; inset: 0; z-index: 80; display: flex; flex-direction: column; background: #fff; color: #191919; font-size: 14px; }
.ks__bar { display: grid; grid-template-columns: 40px 1fr 40px; align-items: center; height: 52px; padding: 0 8px; text-align: center; font-size: 16px; }
.ks__bar--room { background: #bacee0; }
.ks__x { border: 0; background: none; font-size: 18px; cursor: pointer; color: inherit; }
.ks__search { margin: 4px 16px 8px; padding: 9px 12px; border-radius: 8px; background: #f2f2f2; color: #9a9a9a; }
.ks__label { margin: 8px 16px 4px; font-size: 12px; color: #8a8a8a; }
.ks__row { display: flex; align-items: center; gap: 12px; width: 100%; padding: 8px 16px; border: 0; background: none; text-align: left; cursor: pointer; }
.ks__ava { flex: none; display: grid; place-items: center; width: 40px; height: 40px; border-radius: 15px; background: #c9d6e2; color: #4a5a6a; font-weight: 700; }
.ks__ava--group { background: #9bb4cc; color: #fff; }
.ks__body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.ks__muted { color: #8a8a8a; font-weight: 400; font-size: 12px; }
.ks__check { width: 22px; height: 22px; border-radius: 50%; border: 1.5px solid #cfcfcf; display: grid; place-items: center; color: transparent; font-size: 12px; }
.ks__check.is-on { background: #fee500; border-color: #fee500; color: #191919; }
.ks__foot { margin-top: auto; padding: 12px 16px 20px; }
.ks__send { width: 100%; height: 48px; border: 0; border-radius: 12px; background: #fee500; color: #191919; font-size: 15px; font-weight: 700; cursor: pointer; }
.ks__send:disabled { opacity: 0.5; }
.ks__chat { flex: 1; display: flex; flex-direction: column; gap: 12px; padding: 14px 12px; background: #bacee0; overflow-y: auto; }
.ks__me { display: flex; justify-content: flex-end; align-items: flex-end; gap: 6px; }
.ks__time { font-size: 10px; color: #556677; flex: none; }
.ks__feed { width: 230px; border-radius: 14px; overflow: hidden; background: #fff; display: flex; flex-direction: column; }
.ks__feed-img { width: 100%; height: 120px; object-fit: cover; display: block; }
.ks__feed-body { display: flex; flex-direction: column; gap: 4px; padding: 12px 12px 10px; line-height: 1.4; }
.ks__feed-body span { color: #555; font-size: 13px; }
.ks__feed-btn { margin: 0 12px 10px; padding: 9px 0; border-radius: 6px; background: #f2f2f2; text-align: center; font-size: 13px; }
.ks__feed-from { padding: 8px 12px; border-top: 1px solid #eee; font-size: 11px; color: #8a8a8a; }
.ks__them { display: flex; gap: 8px; align-items: flex-start; }
.ks__name { font-size: 12px; color: #3a4a5a; }
.ks__line { display: flex; align-items: flex-end; gap: 6px; margin-top: 4px; }
.ks__bubble { max-width: 220px; padding: 8px 11px; border-radius: 4px 14px 14px 14px; background: #fff; line-height: 1.4; }
</style>
