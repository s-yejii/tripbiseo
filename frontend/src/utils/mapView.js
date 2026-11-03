// 지도 그림 위 위치 계산: 화면의 지도는 그림(mapB.svg)이라, 진짜 좌표(위도·경도)를 그림 안 x, y(px)로 바꿔요.
// 장소들이 지도 칸(box) 안에 고르게 들어오게 늘이고 줄여요. 위도는 위로 갈수록 커서 y는 거꾸로예요.

const PAD = 0.15 // 가장자리 여백 비율

// points: [{ lat, lng }] → [{ x, y }] (같은 순서). box: { left, top, width, height }
export function toMapPositions(points, box) {
  const valid = points.filter((p) => p && p.lat != null && p.lng != null)
  if (valid.length === 0) return points.map(() => ({ x: box.left + box.width / 2, y: box.top + box.height / 2 }))
  let minLat = Infinity, maxLat = -Infinity, minLng = Infinity, maxLng = -Infinity
  for (const p of valid) {
    minLat = Math.min(minLat, p.lat)
    maxLat = Math.max(maxLat, p.lat)
    minLng = Math.min(minLng, p.lng)
    maxLng = Math.max(maxLng, p.lng)
  }
  const latSpan = Math.max(maxLat - minLat, 0.002) // 한 곳뿐이어도 0으로 나누지 않게
  const lngSpan = Math.max(maxLng - minLng, 0.002)
  return points.map((p) => {
    if (!p || p.lat == null) return { x: box.left + box.width / 2, y: box.top + box.height / 2 }
    const rx = (p.lng - minLng) / lngSpan
    const ry = (maxLat - p.lat) / latSpan
    return {
      x: Math.round(box.left + box.width * (PAD + rx * (1 - PAD * 2))),
      y: Math.round(box.top + box.height * (PAD + ry * (1 - PAD * 2))),
    }
  })
}

// 분류로 고르는 기본 사진 (DB 장소 사진이 없을 때)
export function imageFor(category) {
  const c = category || ''
  if (/카페|커피|디저트|베이커리/.test(c)) return '/img/daelim.jpg'
  if (/공연|문화|콘서트|극장/.test(c)) return '/img/concert.svg'
  if (/쇼핑|소품|편집/.test(c)) return '/img/sopum.jpg'
  if (/관광|거리|공원|산책/.test(c)) return '/img/alley.jpg'
  return '/img/gamjatang.jpg'
}
