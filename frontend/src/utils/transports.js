// 마이 > 이동 수단 (11/06): 평소 타는 이동 수단을 이 브라우저에 저장해요. 서버 칸이 생기면 여기만 바꾸면 돼요
const KEY = 'myTransports'
export const DEFAULT_TRANSPORTS = ['지하철', '도보', '버스']

// 저장된 이동 수단 (없거나 읽기가 막히면 기본값)
export function readTransports() {
  try {
    const list = JSON.parse(localStorage.getItem(KEY))
    return Array.isArray(list) && list.length ? list : [...DEFAULT_TRANSPORTS]
  } catch {
    return [...DEFAULT_TRANSPORTS]
  }
}

export function saveTransports(list) {
  try {
    localStorage.setItem(KEY, JSON.stringify(list))
  } catch {
    // 저장이 막혀도 이번 화면에서는 고른 대로 보여 줌
  }
}
