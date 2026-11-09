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

// ---- 11/09 주형: 2일차 KTX는 출발 시각(19:00)을 넘겨야 진짜 놓침 ----
export const DINNER_LATE_MIN = 12 // 돌발: 회식 같은 예약은 12분 늦게
export const TRAIN_LATE_MIN = 2 // 돌발: KTX는 출발 시각보다 2분 늦게 = 놓침 (18:20 출발 → 지하철 42분 → 19:02)

// 기차·버스·비행기 타는 곳인지 (이름이 "역"으로 끝나거나 터미널·공항)
export function isBoardingPlace(name) {
  return /역$|터미널|공항/.test((name || '').trim())
}

// 돌발로 늦게 할 분: 타는 곳은 출발 시각을 2분 넘기게, 그 밖의 예약은 12분
export function lateMinutesFor(name) {
  return isBoardingPlace(name) ? TRAIN_LATE_MIN : DINNER_LATE_MIN
}

// 돌발 시계: 지금 칸에서 나갈 시각(base) + 여유 + 늦게 할 분 → 그 시각에 원래 수단으로 떠나면 정확히 그만큼 늦음
export function incidentClockMin(baseMin, slackMin, lateMin) {
  return baseMin + slackMin + lateMin
}

// 타는 곳 늦음 카드 제목: "지금대로 가면 서울역 19:02 도착 · 19:00 KTX를 놓쳐요"
export function missTrainTitle(name, eta, departTime) {
  return '지금대로 가면 ' + name + ' ' + eta + ' 도착 · ' + departTime + ' KTX를 놓쳐요'
}

// 여행 끝 시트 한 줄: 타는 곳이면 출발 시각 기준, 그 밖은 목표 시각 기준
export function arrivalGapText(placeName, arrivedMin, plannedMin, plannedText) {
  const gap = arrivedMin - plannedMin
  if (isBoardingPlace(placeName)) {
    if (gap > 0) return 'KTX ' + plannedText + ' 출발 ' + gap + '분 뒤 도착 · 놓침'
    return 'KTX ' + plannedText + ' 출발 ' + -gap + '분 전 도착'
  }
  if (gap > 0) return '목표 ' + plannedText + '보다 ' + gap + '분 늦게 도착'
  if (gap < 0) return '목표 ' + plannedText + '보다 ' + -gap + '분 일찍 도착'
  return '목표 ' + plannedText + ' 딱 맞게 도착'
}
