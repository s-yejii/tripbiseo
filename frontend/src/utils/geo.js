// 거리(m) — 하버사인
export function distance(a, b) {
  const R = 6371e3, r = Math.PI / 180
  const dLat = (b.lat - a.lat) * r, dLng = (b.lng - a.lng) * r
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * r) * Math.cos(b.lat * r) * Math.sin(dLng / 2) ** 2
  return 2 * R * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h))
}

// 시공간 프리즘: 두 초점(지금 위치, 고정 일정)까지 거리 합이 maxMeters 이하인 영역 = 타원.
// 지도에 그릴 다각형 좌표를 돌려줍니다.
export function prismPolygon(from, to, maxMeters, steps = 48) {
  const c = distance(from, to) / 2
  const a = Math.max(maxMeters / 2, c + 50)
  const b = Math.sqrt(a * a - c * c)
  const mid = { lat: (from.lat + to.lat) / 2, lng: (from.lng + to.lng) / 2 }
  const mLat = 111320, mLng = 111320 * Math.cos((mid.lat * Math.PI) / 180)
  const angle = Math.atan2((to.lat - from.lat) * mLat, (to.lng - from.lng) * mLng)
  const pts = []
  for (let i = 0; i <= steps; i++) {
    const t = (i / steps) * 2 * Math.PI
    const x = a * Math.cos(t), y = b * Math.sin(t)
    const dx = x * Math.cos(angle) - y * Math.sin(angle)
    const dy = x * Math.sin(angle) + y * Math.cos(angle)
    pts.push([mid.lat + dy / mLat, mid.lng + dx / mLng])
  }
  return pts
}

// 원(반경 m)을 지도 다각형 좌표로
export function circlePolygon(center, meters, steps = 64) {
  const mLat = 111320, mLng = 111320 * Math.cos((center.lat * Math.PI) / 180)
  const pts = []
  for (let i = 0; i <= steps; i++) {
    const t = (i / steps) * 2 * Math.PI
    pts.push([center.lat + (meters * Math.sin(t)) / mLat, center.lng + (meters * Math.cos(t)) / mLng])
  }
  return pts
}
