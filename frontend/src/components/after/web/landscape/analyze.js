// ⑤ 웹 회고 풍경 분석 (이동 경로 객체 분석, 11/11): 1 구간 묶기 → 2 특징값 → 3 분류 → 4 풍경 구성
// 원칙: 숫자·위치는 공간 분석이 계산하고, 분류기(지금은 규칙, 나중에 모델·AI)는 고르기만 해요

import { buildSegments } from './segments'
import { measureSegment } from './features'
import { classifySegments, LANDSCAPE_TYPES } from './classify'

const SLEEP_WORDS = /(숙소|호텔|모텔|게스트하우스|레지던스|스테이)/ // 숙소에서 잔 시간은 풍경 구성에서 뺌 (밤새 머문 시간이 너무 커서)

/**
 * nodes: 웹 회고 재생용 지점 (머문 곳은 이름·분류 포함)
 * options: { layers: 나중 자료 읽기 함수들, classifier: 분류기, onProgress(끝난 수, 전체) }
 * 돌려줌: { items: [{ segment, features, type, confidence, reasons }], composition: [{ type, minutes, percent }] }
 */
export async function analyzeLandscape(nodes, options) {
  const settings = options || {}
  const segments = buildSegments(nodes)
  const measured = []
  for (let i = 0; i < segments.length; i++) {
    const features = await measureSegment(segments[i], settings.layers)
    measured.push({ segment: segments[i], features: features })
    if (settings.onProgress) settings.onProgress(i + 1, segments.length)
  }
  const items = await classifySegments(measured, settings.classifier)
  return { items: items, composition: compositionOf(items) }
}

/** 숙소에 머문 구간인가 */
export function isSleepStay(segment) {
  return segment.kind === 'stay' && SLEEP_WORDS.test((segment.name || '') + ' ' + (segment.category || ''))
}

/** 풍경 구성: 성격마다 보낸 시간(분)과 비율. day를 주면 그날만. 숙소에 머문 시간은 뺌 */
export function compositionOf(items, day) {
  const minutesOf = {}
  let total = 0
  for (const item of items) {
    if ((day && item.segment.day !== day) || isSleepStay(item.segment)) continue
    const minutes = Math.max(1, item.segment.minutes) // 0분 구간도 조금은 셈
    minutesOf[item.type.id] = (minutesOf[item.type.id] || 0) + minutes
    total += minutes
  }
  const list = []
  for (const type of LANDSCAPE_TYPES) {
    const minutes = minutesOf[type.id] || 0
    if (minutes > 0) list.push({ type: type, minutes: minutes, percent: Math.round((minutes / total) * 100) })
  }
  list.sort((a, b) => b.minutes - a.minutes)
  return list
}
