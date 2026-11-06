// 시연 연습 복사본 숨기기 (③, 11/06): "▶ 시연 재생"이 만드는 여행은 제목 끝에 "(연습)"이 붙어요 (DemoBar)
// 내 여행·위치 기록·홈·여행 기록·알림함 같은 일반 화면에서는 안 보이게 해요. 지금 진행 중인 연습 여행은 시연 중이라 보여요.
// 개발자 모드 점 패널의 "연습 기록 보기"를 켜면 다 보여요. DB 칸은 따로 없고 제목 표시로만 구분해요
import { devMode } from '@/utils/devMode'

export const PRACTICE_MARK = '(연습)'

// 연습 복사본 여행인지
export function isPracticeTrip(trip) {
  return !!trip && typeof trip.title === 'string' && trip.title.includes(PRACTICE_MARK)
}

// 일반 화면에 보일 여행만 (진행 중인 연습 여행은 남김)
export function withoutPractice(trips) {
  if (devMode.isOn && devMode.showPractice) return trips
  return trips.filter((trip) => !isPracticeTrip(trip) || trip.status === 'ongoing')
}

// 알림함: 연습 여행 이름이 들어간 알림, 연습 여행으로 가는 알림은 숨김
export function withoutPracticeNotices(notices, practiceIds) {
  if (devMode.isOn && devMode.showPractice) return notices
  return notices.filter((notice) => {
    const text = (notice.title || '') + ' ' + (notice.text || '')
    if (text.includes(PRACTICE_MARK)) return false
    const match = /[?&]id=(\d+)/.exec(notice.link || '')
    return !(match && practiceIds.includes(Number(match[1])))
  })
}
