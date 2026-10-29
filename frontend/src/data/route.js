// 가짜 데이터: 길 안내 (⑤) 성내동 카페 → 성내동 소품샵
// 백엔드가 붙으면 TMAP·ODsay 길찾기 결과로 바꿔요
//   lines: 지도에 그릴 선 { points, color, dash(점선) }
//   stops: 정류장 표시 { x, y, icon, off(내리는 곳), color, label, labelX, labelY }
//   steps: 안내 순서 { icon, title, busNo, desc, key(가장 중요한 단계) }

export const routeModes = {
  walk: {
    label: '도보', icon: 'i-walk', min: 15, eta: '16:07',
    lines: [{ points: '215,285 240,250 268,236 290,200', color: '#1F3A68' }],
    stops: [],
    steps: [
      { icon: 'i-walk', title: '성내동 카페 정문에서 오른쪽', desc: '천호이로를 따라 600m' },
      { icon: 'i-route', title: '성내동 카페거리로 들어가기', desc: '계단 없는 길 · 횡단보도 1번' },
      { icon: 'i-flag', title: '성내동 소품샵 도착', desc: '16:07' },
    ],
  },
  bus: {
    label: '버스', icon: 'i-bus', min: 11, eta: '16:03',
    lines: [
      { points: '215,285 248,262', color: '#646D81', dash: true },
      { points: '248,262 276,222', color: '#1D7A4C' },
      { points: '276,222 290,200', color: '#646D81', dash: true },
    ],
    stops: [
      { x: 248, y: 262, icon: 'i-busstop', label: '천호역 3번 출구 정류장', labelX: 120, labelY: 252 },
      { x: 276, y: 222, icon: 'i-busstop', off: true },
    ],
    steps: [
      { icon: 'i-walk', title: '정류장까지 3분 걷기', desc: '천호역 3번 출구 앞 · 편의점 맞은편' },
      { icon: 'i-bus', busNo: '2016', title: '강동역 방면 타기', desc: '4분 뒤 도착 · 2정거장 · 반대편(천호동 방면) 아님', key: true },
      { icon: 'i-flag', title: '천호동 성내동 소품샵 정류장에서 내리기', desc: '내려서 2분 걷기' },
    ],
  },
  subway: {
    label: '지하철', icon: 'i-subway', min: 14, eta: '16:06',
    lines: [
      { points: '215,285 232,300', color: '#646D81', dash: true },
      { points: '232,300 300,160', color: '#1D7A4C' },
      { points: '300,160 290,200', color: '#646D81', dash: true },
    ],
    stops: [{ x: 232, y: 300, icon: 'i-subway', color: '#1D7A4C', label: '천호역 5호선 · 3번 출구', labelX: 100, labelY: 300 }],
    steps: [
      { icon: 'i-walk', title: '천호역 3번 출구까지 4분', desc: '엘리베이터 있음' },
      { icon: 'i-subway', busNo: '5호선', title: '강동역 방면 1정거장', desc: '3분 뒤 도착 · 반대편(건대입구 방면) 아님', key: true },
      { icon: 'i-flag', title: '강동역 8번 출구로 나와 6분 걷기', desc: '가까운 거리라 걸어가는 게 더 빨라요' },
    ],
  },
  taxi: {
    label: '택시', icon: 'i-car', min: 7, eta: '15:59',
    lines: [{ points: '215,285 260,280 290,200', color: '#1F3A68' }],
    stops: [],
    steps: [
      { icon: 'i-car', title: '성내동 카페 정문 앞에서 타기', desc: '지금 부르면 2분 뒤 도착 · 예상 6,800원', key: true },
      { icon: 'i-flag', title: '성내동 소품샵 입구에서 내리기', desc: '퇴근 시간엔 막힐 수 있어요' },
    ],
  },
}

// 지금 위치가 움직이는 길 (지도 그림 위 점)
export const walkPath = [[215, 285], [240, 250], [268, 236], [290, 200]]
