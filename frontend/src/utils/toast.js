// 화면 아래에 잠깐 뜨는 안내 문구
// 사용: import { showToast } from '@/utils/toast'  →  showToast('저장했어요')
import { reactive } from 'vue'

export const toast = reactive({ text: '', actionText: '', action: null, isTop: false })

let timer = null

const TOAST_MS = 2400
const ACTION_TOAST_MS = 6000 // 글자 버튼을 누를 시간을 더 줘요

// 사용: showToast('이동 72분 → 54분', { actionText: '되돌리기', action: 함수, isTop: true })
export function showToast(text, options = {}) {
  toast.text = text
  toast.actionText = options.actionText || ''
  toast.action = options.action || null
  toast.isTop = !!options.isTop
  clearTimeout(timer)
  timer = setTimeout(() => {
    toast.text = ''
  }, toast.action ? ACTION_TOAST_MS : TOAST_MS)
}

// 토스트의 글자 버튼을 눌렀을 때
export function runToastAction() {
  const action = toast.action
  toast.text = ''
  if (action) action()
}
