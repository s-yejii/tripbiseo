// 여행 중 화면 위쪽 알림을 언제 펼쳐 보여 줬는지 기억 (11/04)
// 다른 화면에 갔다가 돌아올 때마다 같은 알림이 다시 펼쳐지지 않게, 같은 종류 알림은 10분에 한 번만 펼쳐요.
//   - 처음 보는 종류(다른 장소·다른 이유)면 바로 펼침
//   - 같은 종류라도 주의(warn)였다가 위험(danger)이 되면 바로 펼침
//   - 그 밖에는 접어 두고 "알림" 알약만 (누르면 펼침)
// 새로고침해도 기억하게 sessionStorage(이 탭이 열려 있는 동안만 남는 브라우저 저장소)에 둬요
const AGAIN_AFTER_MS = 10 * 60 * 1000 // 같은 종류 알림을 다시 펼치는 간격 (10분)
const STORAGE_KEY = 'live-alert-shown'
// 서버 알림 key 끝에 붙는 숫자(늦는 분·남은 분 묶음)는 상황이 조금씩 바뀌어도 같은 종류로 봐요
//   예: "late-12-1" → "late-12", "leave-7-3" → "leave-7" (늦는 고정 일정·떠날 장소가 같으면 같은 종류)
const KIND_WITH_NUMBER = /^(overdue|late|leave|next)-(\d+)-/

// 알림 종류 이름
function kindOf(alert) {
  const key = alert.key || alert.title || ''
  const match = key.match(KIND_WITH_NUMBER)
  return match ? match[1] + '-' + match[2] : key
}

// 저장해 둔 기록 { 종류: { at: 보여 준 시각(ms), level } } (못 읽으면 빈 기록)
function readShown() {
  try {
    return JSON.parse(sessionStorage.getItem(STORAGE_KEY)) || {}
  } catch {
    return {}
  }
}

// 이 알림을 펼쳐 보여 줄지
export function shouldOpenAlert(alert) {
  const shown = readShown()[kindOf(alert)]
  if (!shown) return true
  if (alert.level === 'danger' && shown.level !== 'danger') return true
  return Date.now() - shown.at >= AGAIN_AFTER_MS
}

// 이 알림을 지금 펼쳐 보여 줬다고 기록
export function rememberAlert(alert) {
  const shown = readShown()
  shown[kindOf(alert)] = { at: Date.now(), level: alert.level }
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(shown))
  } catch {
    // 저장소를 못 쓰면 기억 없이 (이 화면에 있는 동안만 접혀 있음)
  }
}
