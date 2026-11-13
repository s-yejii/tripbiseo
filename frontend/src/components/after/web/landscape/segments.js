// ⑤ 웹 회고 풍경 분석 1단계: GPS 점을 "의미 있는 구간"(객체)으로 묶기 (11/11)
// 사례(OBIA)에서 픽셀을 나무 한 그루로 묶듯이, GPS 점을 머문 곳 · 걸은 구간으로 묶어요
// 입력 nodes: 웹 회고 재생용 지점 (toRouteNodes 결과). 머문 곳은 한 지점으로 이미 묶여 있어요
//   { lng, lat, from, to, isStay, name?, category? }   from·to = 여행 시작부터 몇 분째

import { distanceMeters } from '@/utils/geo'

export const SEGMENT_LENGTH_M = 250   // 걸은 구간을 이 길이 안팎으로 자름 (풍경이 바뀌는 단위)
const MOVE_GAP_MINUTES = 30           // 점 사이가 이보다 멀면 기록이 끊긴 것 (밤 등) → 구간을 나눔
const MINUTES_PER_DAY = 24 * 60

/** 머문 곳 + 걸은 구간 목록 (시간 순) */
export function buildSegments(nodes) {
  const segments = []
  let move = null // 만드는 중인 걸은 구간 { points: [[경도, 위도]], times: [분] }
  for (const node of nodes) {
    if (node.isStay) {
      if (move) {
        move.points.push([node.lng, node.lat])
        move.times.push(node.from)
        addMoveSegments(segments, move)
      }
      segments.push(toStaySegment(node, segments.length))
      move = { points: [[node.lng, node.lat]], times: [node.to] }
      continue
    }
    const isCut = move && node.from - move.times[move.times.length - 1] > MOVE_GAP_MINUTES
    if (!move || isCut) {
      if (move) addMoveSegments(segments, move)
      move = { points: [], times: [] }
    }
    move.points.push([node.lng, node.lat])
    move.times.push(node.from)
  }
  if (move) addMoveSegments(segments, move)
  return segments
}

function toStaySegment(node, index) {
  return { id: 's' + index, kind: 'stay', day: dayOf(node.from), from: node.from, to: node.to, minutes: Math.max(0, node.to - node.from),
    points: [[node.lng, node.lat]], lengthM: 0, name: node.name || null, category: node.category || null }
}

// 걸은 점들을 SEGMENT_LENGTH_M마다 잘라 구간으로 (자른 자리 점은 앞뒤 구간이 같이 씀)
function addMoveSegments(segments, move) {
  if (move.points.length < 2) return
  let start = 0
  let length = 0
  for (let i = 1; i < move.points.length; i++) {
    length += distanceMeters(move.points[i - 1][1], move.points[i - 1][0], move.points[i][1], move.points[i][0])
    const isLast = i === move.points.length - 1
    if (length >= SEGMENT_LENGTH_M || isLast) {
      if (length > 0) segments.push(toMoveSegment(move, start, i, length, segments.length))
      start = i
      length = 0
    }
  }
}

function toMoveSegment(move, start, end, length, index) {
  const from = move.times[start]
  const to = move.times[end]
  return { id: 'm' + index, kind: 'move', day: dayOf(from), from: from, to: to, minutes: Math.max(0, to - from),
    points: move.points.slice(start, end + 1), lengthM: length, name: null, category: null }
}

function dayOf(minutes) {
  return Math.floor(minutes / MINUTES_PER_DAY) + 1
}

/** 구간 가운데 점 [경도, 위도] (선이면 길이로 가운데쯤의 점) */
export function middleOf(segment) {
  return segment.points[Math.floor((segment.points.length - 1) / 2)]
}
