// ⑤ 웹 여행 메모 저장 (11/10 시연용). 서버 API가 아직 없어서 이 브라우저(localStorage)에만 저장해요
// TODO: ⑤ 발표 뒤 retro_memo 테이블 + API로 옮기기 (지금은 다른 PC·앱에서는 안 보여요)
// 여행마다 한 덩어리: { rating, diary, dayNotes, stopNotes, covers, tripCover, editedFrom, updatedAt }

const KEY_PREFIX = 'chakchak.webJournal.'

// 처음 여는 여행의 빈 메모
function emptyJournal() {
  return {
    rating: 0,          // 여행 별점 1~5 (0 = 아직 안 줌)
    diary: '',          // 여행 전체 일기
    dayNotes: {},       // 날짜별 메모 { 1: '…', 2: '…' }
    stopNotes: {},      // 장소별 메모 { 일정 칸 id: '…' }
    covers: {},         // 장소별 대표 사진 { 일정 칸 id: 사진 id }
    tripCover: null,    // 여행 대표 사진 id (지난 여행 목록 카드)
    editedFrom: {},     // 편집본 → 원본 { 편집본 사진 id: 원본 사진 id }
    updatedAt: null,    // 마지막 저장 시각 (글자)
  }
}

/** 이 여행의 메모 읽기. 없거나 못 읽으면 빈 메모 */
export function loadJournal(tripId) {
  const journal = emptyJournal()
  try {
    const saved = JSON.parse(localStorage.getItem(KEY_PREFIX + tripId) || 'null')
    if (saved) Object.assign(journal, saved) // 저장된 칸만 덮어쓰기 (새로 생긴 칸은 기본값 유지)
  } catch (e) {
    // 브라우저가 저장소를 막았거나 내용이 깨졌으면 빈 메모로 시작
  }
  return journal
}

/** 이 여행의 메모 저장. 저장했으면 true */
export function saveJournal(tripId, journal) {
  try {
    journal.updatedAt = new Date().toISOString()
    localStorage.setItem(KEY_PREFIX + tripId, JSON.stringify(journal))
    return true
  } catch (e) {
    return false // 시크릿 창 등에서 저장소를 못 쓰면 저장 안 됨
  }
}

/** 편집본이 있는 원본 사진 id 목록 (회고 지도에서는 편집본만 보여 주려고) */
export function editedOriginalIds(journal) {
  const ids = []
  for (const editedId in journal.editedFrom) ids.push(journal.editedFrom[editedId])
  return ids
}

/** 이 사진이 편집본인가 */
export function isEditedPhoto(journal, photoId) {
  return journal.editedFrom[photoId] !== undefined
}
