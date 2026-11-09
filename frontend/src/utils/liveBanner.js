// 여행 중 화면 위쪽 알림 줄 (11/08 주형)
//  - 한 번에 한 장만 보이고(겹치지 않게), 사용자가 위로 밀거나 닫아야 다음 알림이 이어서 떠요 ("내가 끄기 전에는 없애지 마")
//  - 새 알림은 줄 맨 뒤에 서요. 제목·내용이 완전히 같은 알림만 한 번으로
//  - 화면을 옮기거나 새로고침해도 남게 sessionStorage(이 탭이 열려 있는 동안만 남는 저장소)에 둬요
import { reactive } from 'vue'

const STORAGE_KEY = 'live-banner-queue'
// 서버 알림 key 끝의 숫자(남은 분·늦는 분 묶음)는 빼고 같은 종류로 봄: "leave-12-3" → "leave-12"
const KIND_WITH_NUMBER = /^(overdue|late|leave|next|tight)-(\d+)-/

function load() {
  try {
    return JSON.parse(sessionStorage.getItem(STORAGE_KEY)) || []
  } catch {
    return []
  }
}
function save() {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(liveBanner.queue))
  } catch {
    // 저장소를 못 쓰면 이 화면에 있는 동안만
  }
}
function kindOf(key) {
  const match = (key || '').match(KIND_WITH_NUMBER)
  return match ? match[1] + '-' + match[2] : key
}

export const liveBanner = reactive({ queue: load(), isCardOpen: false }) // queue: [{ key, title, body, warn, icon, urgent, timer, to }] (urgent·timer가 없는 예전 항목은 화면에서 false로 봄) · isCardOpen: 가운데 늦음 카드가 떠 있는지 (떠 있으면 [+분]을 막음)

// 알림 줄 맨 뒤에 넣기. 앞 알림을 덮어쓰지 않고, 제목·내용이 완전히 같은 알림만 한 번으로 (11/08 주형: "끄기 전에는 없애지 마")
export function showLiveNotice(notice) {
  const item = { ...notice, kind: kindOf(notice.key || notice.title) }
  const isSame = liveBanner.queue.some((queued) => queued.title === item.title && queued.body === item.body)
  if (isSame) return
  liveBanner.queue.push(item)
  save()
}

// 맨 앞 알림 닫기 (사용자가 위로 밀거나 눌렀을 때만) → 다음 알림이 이어서 보임
export function closeLiveNotice() {
  liveBanner.queue.shift()
  save()
}

// 여행이 끝났거나 다른 여행일 때 줄 비우기
export function clearLiveNotices() {
  liveBanner.queue.splice(0)
  save()
}
