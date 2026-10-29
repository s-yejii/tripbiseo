// 화면 아래에 잠깐 뜨는 안내 문구
// 사용: import { showToast } from '@/utils/toast'  →  showToast('저장했어요')
import { reactive } from 'vue'

export const toast = reactive({ text: '' })

let timer = null

export function showToast(text) {
  toast.text = text
  clearTimeout(timer)
  timer = setTimeout(() => {
    toast.text = ''
  }, 2400)
}
