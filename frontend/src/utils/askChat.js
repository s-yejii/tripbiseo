// AI에게 묻기 대화 기억 (④ 11/13): 한 여행에서 주고받은 말·찾은 곳을 이 브라우저에 남겨요 (localStorage)
// 패널을 닫았다 열거나 새로고침해도 그대로. 너무 길어지지 않게 마지막 MAX_MESSAGES개만
//   구분: 저장 이름 = tripbiseo.askChat.{여행 id}-{시작 날짜} (DB를 다시 만들어 여행 id가 겹쳐도 다른 여행으로)
//   지우기: 여행을 끝낼 때 그 여행 대화(clearChat), 로그아웃할 때 전부(clearAllChats, ① 로그아웃에서 불러 달라고 요청)
const KEY_PREFIX = 'tripbiseo.askChat.'
const MAX_MESSAGES = 60

/** 여행 하나의 대화 이름 (여행 id + 시작 날짜) */
export function chatKey(trip) {
  return trip ? trip.id + '-' + trip.startDate : 'demo'
}

/** 이 여행의 대화 (key: chatKey 값, 없으면 빈 목록) */
export function loadChat(key) {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY_PREFIX + key) || '[]')
    return Array.isArray(saved) ? saved : []
  } catch (e) {
    return [] // 저장소를 못 쓰는 브라우저(사생활 보호 모드 등)면 이번에만 기억
  }
}

/** 이 여행의 대화 저장 (답을 기다리는 중인 말풍선은 빼고) */
export function saveChat(key, messages) {
  const list = messages.filter((message) => !message.isTyping).slice(-MAX_MESSAGES)
  try {
    localStorage.setItem(KEY_PREFIX + key, JSON.stringify(list))
  } catch (e) {
    // 저장을 못 해도 화면은 그대로 씀
  }
}

/** 여행 하나의 대화 지우기 (여행을 끝낼 때) */
export function clearChat(key) {
  try {
    localStorage.removeItem(KEY_PREFIX + key)
  } catch (e) {
    // 저장소를 못 쓰면 지울 것도 없음
  }
}

/** 이 브라우저의 AI 대화 전부 지우기 (로그아웃할 때) */
export function clearAllChats() {
  try {
    const keys = []
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i)
      if (key.startsWith(KEY_PREFIX)) keys.push(key)
    }
    for (const key of keys) localStorage.removeItem(key)
  } catch (e) {
    // 저장소를 못 쓰면 지울 것도 없음
  }
}
