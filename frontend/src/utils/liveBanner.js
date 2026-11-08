// 여행 중 화면 위쪽 배너를 한 장만 (11/08 주형: 알림이 겹쳐서 뭐가 뭔지 모르겠음)
// 시연 "다음 장소로"의 출발 안내처럼 화면 안에서 잠깐 띄우는 안내를 여기 넣으면, 여행 중 화면이 서버 알림 대신 이걸 한 장으로 보여 줘요
import { reactive } from 'vue'

export const liveBanner = reactive({ notice: null }) // { title, body, warn } 또는 null

// 안내 한 장 띄우기 (이전 것은 바꿔치기)
export function showLiveNotice(notice) {
  liveBanner.notice = notice
}

// 안내 내리기. from을 주면 그 쪽이 띄운 안내일 때만 (예: 시연 이동 안내가 자동 조정 안내를 지우지 않게)
export function clearLiveNotice(from) {
  if (!from || (liveBanner.notice && liveBanner.notice.from === from)) liveBanner.notice = null
}
