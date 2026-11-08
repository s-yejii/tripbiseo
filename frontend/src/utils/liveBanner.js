// 여행 중 화면 위쪽 알림 줄 (11/08 주형)
//  - 한 번에 한 장만 보이고(겹치지 않게), 사용자가 위로 밀거나 닫아야 다음 알림이 이어서 떠요 ("내가 끄기 전에는 없애지 마")
//  - 같은 종류(같은 장소의 출발 알림 등)는 새 내용으로 바꿔 한 칸만 차지해요
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

export const liveBanner = reactive({ queue: load() }) // [{ key, title, body, warn, icon, to }]

// 알림 줄에 넣기 (같은 종류가 이미 있으면 그 자리의 내용만 새것으로)
export function showLiveNotice(notice) {
  const item = { ...notice, kind: kindOf(notice.key || notice.title) }
  const index = liveBanner.queue.findIndex((queued) => queued.kind === item.kind)
  if (index >= 0) liveBanner.queue.splice(index, 1, item)
  else liveBanner.queue.push(item)
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
