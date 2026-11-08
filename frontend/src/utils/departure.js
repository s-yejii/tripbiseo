// 시연 "다음 장소로"의 떠나는 시각·예약까지 여유 계산 (③ 11/09) — 출발 안내와 출발 전 검사가 이 함수 하나만 써요
// 분 단위 숫자만 다루는 순수 함수라 테스트로 바로 확인할 수 있어요 (utils/departure.test.mjs)

export const TIGHT_SLACK_MIN = 5 // 예약까지 여유가 이 이하면 돌발(가운데 카드). 서버 LiveAlertMaker LEAVE_NOW_SLACK_MIN과 같음

/**
 * 떠나는 시각과 예약까지 여유
 * @param nowMin      지금 (분)
 * @param stayEndMin  지금 칸에서 머무는 시간이 끝나는 분 (출발지면 null)
 * @param extraMin    시나리오에서 조금 더 머무는 분
 * @param travelMin   다음 칸까지 이동 분 (여행 중 화면의 예상 도착과 같은 값)
 * @param nextTimeMin 다음 칸 계획 시각 (분)
 * @param isNextFixed 다음 칸이 예약(고정)인지
 * @param isNextDay   다음 칸이 다음 날인지
 * @returns { departMin, arriveMin, slack(예약이 아니면 null), isTight }
 */
export function planDeparture({ nowMin, stayEndMin, extraMin = 0, travelMin, nextTimeMin, isNextFixed, isNextDay = false }) {
  let departMin
  if (stayEndMin === null || isNextDay) {
    departMin = Math.max(nowMin, nextTimeMin - travelMin) // 출발지·다음 날 첫 곳은 계획 시각에 맞춰 도착
  } else if (isNextFixed) {
    departMin = Math.max(nowMin, stayEndMin) // 다음이 예약: 머무는 시간이 끝나면(이미 지났으면 바로) 떠남 = 서버의 나갈 시각과 같음
  } else {
    departMin = Math.max(nowMin, stayEndMin + extraMin) // 느슨한 곳: 머무는 시간을 다 채우고
  }
  const arriveMin = departMin + travelMin
  const slack = isNextFixed ? nextTimeMin - arriveMin : null
  return { departMin, arriveMin, slack, isTight: slack !== null && slack <= TIGHT_SLACK_MIN }
}
