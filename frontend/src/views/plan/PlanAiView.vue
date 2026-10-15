<script setup>
// AI로 계획하기 (목업 1): 말로 입력 → 제미나이가 조건을 뽑아 카드로 보여 줌 → 조건 확인 후 일정 만들기
// 지금 위치를 같이 보내 "내 위치 근처로"를 알아들음 (위치 권한이 없으면 지역 이름으로만)
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { plans } from '@/api'
import { usePlan } from '@/stores/plan'
import { stamp } from '@/utils/format'
import { locateOnce } from '@/utils/locate'
import AppBar from '@/components/AppBar.vue'
import ChatInput from '@/components/ChatInput.vue'
import ConditionsCard from '@/components/ConditionsCard.vue'

const router = useRouter()
const plan = usePlan()
// 대화·조건은 스토어에 둬서 검증 화면에서 '수정'으로 돌아와도 처음 입력한 조건이 그대로 남음
const messages = computed(() => plan.chat.messages)
const conditions = computed({ get: () => plan.chat.conditions, set: (v) => (plan.chat.conditions = v) })
const busy = ref(false)
const error = ref('')
const box = ref(null)
let pos = null

const scroll = () => nextTick(() => box.value?.scrollTo({ top: box.value.scrollHeight, behavior: 'smooth' }))
onMounted(() => (scroll(), locateOnce().then((p) => (pos = p)).catch(() => {})))

async function send(text) {
  messages.value.push({ role: 'me', text, at: stamp() })
  busy.value = true
  error.value = ''
  scroll()
  try {
    const res = await plans.chat(text, conditions.value)
    const { stops, notes, ...base } = plan.draft // 초안의 일정은 조건에 섞지 않음
    conditions.value = { ...base, ...conditions.value, ...res.conditions }
    messages.value.push({ role: 'ai', text: res.reply, at: stamp() })
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
    scroll()
  }
}

async function generate() {
  busy.value = true
  error.value = ''
  try {
    const userTexts = messages.value.filter((m) => m.role === 'me').map((m) => m.text)
    plan.draft = await plans.generate(conditions.value, userTexts, pos)
    plan.from = 'ai'
    router.push('/plan/check')
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <AppBar title="AI로 계획하기" />
  <div ref="box" class="page chat">
    <div v-for="(m, i) in messages" :key="i" class="bubble" :class="m.role">{{ m.text }}</div>
    <template v-if="conditions">
      <ConditionsCard v-model="conditions" />
      <button class="btn primary block" :disabled="busy" @click="generate">{{ busy ? '일정 짜는 중…' : '이 조건으로 일정 만들기' }}</button>
    </template>
    <div v-if="busy && !conditions" class="bubble ai typing">· · ·</div>
    <p v-if="error" class="error">{{ error }}</p>
  </div>
  <ChatInput :busy="busy" :placeholder="conditions ? '추가로 원하는 조건이 있나요?' : '예) 저녁 7시 잠실 공연 전에 경복궁, 익선동, 성수 카페'" @send="send" />
</template>

<style scoped>
.chat { display: flex; flex-direction: column; gap: 10px; background: var(--bg); padding-top: 14px; }
.bubble { max-width: 82%; padding: 12px 14px; border-radius: 18px; font-size: 15px; line-height: 1.5; white-space: pre-line; }
.bubble.ai { align-self: flex-start; background: #fff; border-top-left-radius: 4px; }
.bubble.me { align-self: flex-end; background: var(--blue); color: #fff; border-top-right-radius: 4px; }
.typing { letter-spacing: 3px; color: var(--sub); }
</style>
