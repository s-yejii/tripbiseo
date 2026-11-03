// 위도·경도 계산 도우미 (길 안내에서 내 위치가 경로선의 어디쯤인지 구할 때 씀)
// 몇 km 안의 짧은 거리라서 지구를 평면으로 펴서 계산해요 (오차 1% 안쪽)
const EARTH_RADIUS_M = 6371000
const DEGREE = Math.PI / 180

// 두 점 사이 거리(m)
export function distanceMeters(lat1, lng1, lat2, lng2) {
  const x = (lng2 - lng1) * DEGREE * Math.cos(((lat1 + lat2) / 2) * DEGREE)
  const y = (lat2 - lat1) * DEGREE
  return Math.sqrt(x * x + y * y) * EARTH_RADIUS_M
}

// 경로선 길이(m). path: [[위도, 경도], ...]
export function pathLength(path) {
  let total = 0
  for (let i = 1; i < path.length; i++) total += distanceMeters(path[i - 1][0], path[i - 1][1], path[i][0], path[i][1])
  return total
}

// 한 점을 경로선에 붙였을 때: 처음부터 그 지점까지 간 거리(along)와 선에서 떨어진 거리(off), 붙은 좌표(point)
export function snapToPath(path, lat, lng) {
  let best = { along: 0, off: Infinity, point: path[0] }
  let walked = 0
  for (let i = 1; i < path.length; i++) {
    const [lat1, lng1] = path[i - 1]
    const [lat2, lng2] = path[i]
    const length = distanceMeters(lat1, lng1, lat2, lng2)
    const rate = segmentRate(lat1, lng1, lat2, lng2, lat, lng)
    const point = [lat1 + (lat2 - lat1) * rate, lng1 + (lng2 - lng1) * rate]
    const off = distanceMeters(lat, lng, point[0], point[1])
    if (off < best.off) best = { along: walked + length * rate, off, point }
    walked += length
  }
  return best
}

// 선분 위에서 점과 가장 가까운 곳의 비율 (0 = 시작, 1 = 끝)
function segmentRate(lat1, lng1, lat2, lng2, lat, lng) {
  const scale = Math.cos(lat1 * DEGREE)
  const dx = (lng2 - lng1) * scale
  const dy = lat2 - lat1
  const lengthSquared = dx * dx + dy * dy
  if (lengthSquared === 0) return 0
  const rate = (((lng - lng1) * scale) * dx + (lat - lat1) * dy) / lengthSquared
  return Math.min(1, Math.max(0, rate))
}

// 경로선 위에서 처음부터 meters만큼 간 지점 [위도, 경도] (시연용 이동에 씀)
export function pointAlongPath(path, meters) {
  let walked = 0
  for (let i = 1; i < path.length; i++) {
    const length = distanceMeters(path[i - 1][0], path[i - 1][1], path[i][0], path[i][1])
    if (walked + length >= meters) {
      const rate = length === 0 ? 0 : (meters - walked) / length
      return [path[i - 1][0] + (path[i][0] - path[i - 1][0]) * rate, path[i - 1][1] + (path[i][1] - path[i - 1][1]) * rate]
    }
    walked += length
  }
  return path[path.length - 1]
}
