import { defineStore } from 'pinia'
import { ref } from 'vue'

// 여행 중 화면 → 대안 비교 → 적용 결과 화면이 주고받는 값
export const useLive = defineStore('live', () => {
  const pos = ref(null) // 마지막 내 위치 { lat, lng }
  const alternatives = ref(null) // 서버가 만든 대안 A·B·C
  const applied = ref(null) // 적용 결과
  return { pos, alternatives, applied }
})
