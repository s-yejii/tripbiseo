// 여행 시간 추천 (③, 11/06): "시간 추천" 버튼이 쓰는 시작·끝 시각
// TODO: 방문 시간 통계(정리 중)가 오면 장소 종류·요일별 많이 다니는 시간으로 바꾸기. 지금은 자리만 잡은 기본값이에요
const RECOMMENDED = { start: '11:00', end: '20:00' } // 점심부터 저녁까지 (임시 기본값, 통계 아님)

// 추천 시작·끝 시각 { start, end }
export function recommendTime() {
  return { ...RECOMMENDED }
}
