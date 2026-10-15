import { defineStore } from 'pinia'
import { ref } from 'vue'
import { today } from '@/utils/format'

const GREETING = '어디로, 언제 가고 싶으세요? 공연·예약처럼 꼭 지켜야 할 시간이 있으면 같이 알려 주세요.'

// 저장 전 일정 초안 (AI로 짜기·직접 짜기 → 검증 화면이 같이 씀)
// chat: AI로 계획하기 대화와 조건 — 검증 화면에서 돌아와도 그대로 남아 있게 스토어에 둠. 저장하면 여행 대화 기록으로 넘어감
export const usePlan = defineStore('plan', () => {
  const blank = () => ({ title: '', startDate: today(), endDate: today(), people: 1, transports: ['subway', 'bus', 'walk'], stops: [] })
  const blankChat = () => ({ messages: [{ role: 'ai', text: GREETING }], conditions: null })
  const draft = ref(blank())
  const chat = ref(blankChat())
  const from = ref('ai') // 'ai' | 'manual' — 검증 화면에서 '수정' 누르면 돌아갈 곳
  const reset = () => ((draft.value = blank()), (chat.value = blankChat()))
  return { draft, chat, from, reset }
})
