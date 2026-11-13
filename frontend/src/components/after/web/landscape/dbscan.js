// ⑤ 웹 회고 공간분석: 밀도 기반 군집(DBSCAN)으로 머문 곳 찾기 (11/11)
// DBSCAN: 점들이 빽빽하게 모인 곳을 한 덩어리(군집)로 묶는 표준 알고리즘
//  - eps(반경) 안에 이웃이 minPts개 이상인 점 = "중심점" → 중심점끼리 이웃이면 같은 군집으로 이어 붙임
//  - 어느 군집에도 못 든 점 = 잡음(noise) → 여기서는 "이동 중인 점"
// GPS는 시간도 있어서, 이웃 조건에 "시간이 가까움"을 더한 ST-DBSCAN(시공간 DBSCAN)으로 써요
//  → 오전에 지나간 길과 오후에 다시 지나간 길이 한 군집으로 묶이지 않게
// 왜 쓰나: "반경 60m에 8분" 같은 한 줄 규칙은 첫 점 기준이라 GPS가 흔들리면 끊김. 군집은 점들의 밀도로 판단해서 덜 끊김

import { distanceMeters } from '@/utils/geo'

export const DBSCAN_EPS_M = 35        // 이웃으로 보는 거리(m). GPS 흔들림(10~30m)보다 조금 크게, 천천히 걷는 1분(약 50m)보다는 작게
export const DBSCAN_TIME_MIN = 12     // 이웃으로 보는 시간 차(분)
export const DBSCAN_MIN_POINTS = 3    // 이웃이 이만큼(자기 포함) 있어야 중심점
export const DBSCAN_CORE_SPAN_MIN = 4 // 그리고 이웃들이 이 시간(분) 넘게 걸쳐 있어야 중심점
//   왜: 앱은 움직이는 동안 20초마다 GPS를 저장 → 걷는 길도 35m 안에 앞뒤 점이 있어 "빽빽"해 보임
//   걷는 점의 이웃은 1분 안팎에 몰려 있고, 머문 점의 이웃은 몇 분에 걸쳐 있음 → 시간 폭으로 구별 (걷는 길이 앞뒤 머문 곳을 잇지 않게)
export const STAY_MIN_MINUTES = 8     // 군집이 이 시간보다 길어야 "머문 곳"

const NOISE = -1
const UNSEEN = 0

// 점 i의 이웃 번호들 (자기 포함): 거리 eps 안 + 시간 차 timeEps 안
function neighborsOf(points, i) {
  const list = []
  for (let j = 0; j < points.length; j++) {
    if (Math.abs(points[j].minute - points[i].minute) > DBSCAN_TIME_MIN) continue
    if (distanceMeters(points[i].lat, points[i].lng, points[j].lat, points[j].lng) <= DBSCAN_EPS_M) list.push(j)
  }
  return list
}

/**
 * ST-DBSCAN. points: [{ lng, lat, minute(여행 시작부터 몇 분) }]
 * 돌려줌: 점마다 군집 번호 배열 (1, 2, 3 … / −1 = 잡음)
 */
export function clusterPoints(points) {
  const labels = new Array(points.length).fill(UNSEEN)
  let cluster = 0
  for (let i = 0; i < points.length; i++) {
    if (labels[i] !== UNSEEN) continue
    const neighbors = neighborsOf(points, i)
    if (!isCore(points, neighbors)) {
      labels[i] = NOISE // 일단 잡음 (나중에 다른 군집의 가장자리 점이 될 수 있음)
      continue
    }
    cluster++
    labels[i] = cluster
    expand(points, labels, neighbors, cluster)
  }
  return labels
}

// 중심점인가: 이웃이 DBSCAN_MIN_POINTS개 이상이고, 그 이웃들의 시각이 DBSCAN_CORE_SPAN_MIN분 넘게 걸쳐 있음
// 예) 머묾: 이웃 6개가 10:00 ~ 10:09 → 중심점 / 걷기: 이웃 3개가 10:00 ~ 10:01 → 아님
function isCore(points, neighbors) {
  if (neighbors.length < DBSCAN_MIN_POINTS) return false
  let first = Infinity, last = -Infinity
  for (const index of neighbors) {
    first = Math.min(first, points[index].minute)
    last = Math.max(last, points[index].minute)
  }
  return last - first >= DBSCAN_CORE_SPAN_MIN
}

// 군집 넓히기: 이웃의 이웃을 차례로 확인 (줄 서서 기다리는 목록 = 큐)
function expand(points, labels, queue, cluster) {
  for (let k = 0; k < queue.length; k++) {
    const j = queue[k]
    if (labels[j] === NOISE) labels[j] = cluster // 잡음이던 점 → 이 군집의 가장자리 점
    if (labels[j] !== UNSEEN) continue
    labels[j] = cluster
    const more = neighborsOf(points, j)
    if (isCore(points, more)) {
      for (const index of more) queue.push(index) // 이 점도 중심점이면 그 이웃까지 넓힘
    }
  }
}

/**
 * 머문 곳 군집 목록. points: [{ lng, lat, minute }] (시간 순)
 * 돌려줌: { labels, stays: [{ id, lng, lat(가운데), from, to, minutes, count(점 수), radiusM(가운데에서 가장 먼 점) }] }
 */
export function findStays(points) {
  const labels = clusterPoints(points)
  const groups = {}
  for (let i = 0; i < points.length; i++) {
    if (labels[i] === NOISE) continue
    if (!groups[labels[i]]) groups[labels[i]] = []
    groups[labels[i]].push(points[i])
  }
  const stays = []
  for (const id in groups) {
    const stay = toStay(Number(id), groups[id])
    if (stay.minutes >= STAY_MIN_MINUTES) stays.push(stay)
  }
  stays.sort((a, b) => a.from - b.from)
  return { labels: labels, stays: stays }
}

function toStay(id, group) {
  let lng = 0, lat = 0, from = Infinity, to = -Infinity
  for (const point of group) {
    lng += point.lng / group.length
    lat += point.lat / group.length
    from = Math.min(from, point.minute)
    to = Math.max(to, point.minute)
  }
  let radius = 0
  for (const point of group) radius = Math.max(radius, distanceMeters(lat, lng, point.lat, point.lng))
  return { id: id, lng: lng, lat: lat, from: from, to: to, minutes: to - from, count: group.length, radiusM: Math.round(radius) }
}
