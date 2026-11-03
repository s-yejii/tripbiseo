<script setup>
// 카카오 지도 (공통): 진짜 위도·경도로 장소 핀, 내 위치, 동선(장소끼리 직선), 길 안내 경로선, 반경 원을 그려요.
// 키가 없거나 SDK를 못 받으면 fail을 보내요 → 부모 화면이 안내 문구를 보여 줘요.
// 부모가 ref로 부를 수 있는 것: focusMe() 내 위치로 옮기기, fitAll() 모든 점이 보이게 다시 맞추기, moveTo(lat, lng, level) 가운데 옮기기
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { loadKakaoMap } from '@/utils/kakaoMap'

const props = defineProps({
  pins: { type: Array, default: () => [] }, // [{ key, lat, lng, text, icon, cls, label, route, fit }] 순서대로 동선을 이어요 (route: false면 동선에서 뺌, fit: false면 화면 맞출 때 뺌)
  me: { type: Object, default: null }, // 내 위치 { lat, lng }
  showRoute: { type: Boolean, default: true },
  path: { type: Array, default: () => [] }, // 길 안내 경로선 [[위도, 경도], ...] (도로를 따라가는 진짜 길)
  ring: { type: Object, default: null }, // 반경 원 { lat, lng, radius(m) } (근처 찾기)
  follow: { type: Boolean, default: false }, // 내 위치가 바뀌면 지도를 따라 옮김 (길 안내)
  fitKey: { type: [String, Number], default: '' }, // 이 값이 바뀌면 모든 점이 보이게 다시 맞춤 (근처 찾기 결과가 바뀔 때)
  pickCenter: { type: Boolean, default: false }, // 배민 주소 등록처럼: 지도를 움직이면 멈춘 자리의 가운데 좌표를 center로 알려 줌
  // 처음 화면을 맞출 때 비워 둘 여백(px): 위 알림, 아래 일정 시트에 핀이 가리지 않게
  padding: { type: Object, default: () => ({ top: 150, right: 40, bottom: 444, left: 40 }) },
})
const emit = defineEmits(['pick', 'fail', 'me-point', 'map-click', 'center'])

const START_CENTER = { lat: 37.5385, lng: 127.1238 } // 핀을 그리기 전 잠깐 보이는 중심 (천호역)
const SINGLE_POINT_SPAN = 0.003 // 점이 하나뿐일 때 주변으로 넓혀 보여 줄 범위(도)

const box = ref(null)
let kakao = null
let map = null
let overlays = [] // 지금 지도에 올린 핀·선 (다시 그릴 때 지움)
let hasFitted = false // 처음 한 번만 모든 핀이 보이게 맞춤 (그 뒤엔 사용자가 옮긴 화면을 유지)
let isMovingByCode = false // moveTo로 옮기는 중

onMounted(async () => {
  try {
    kakao = await loadKakaoMap()
  } catch {
    emit('fail')
    return
  }
  if (!box.value) return // 불러오는 사이 다른 화면으로 넘어갔으면 그리지 않음
  map = new kakao.maps.Map(box.value, { center: new kakao.maps.LatLng(START_CENTER.lat, START_CENTER.lng), level: 5 })
  kakao.maps.event.addListener(map, 'idle', sendMePoint) // 지도를 옮기거나 확대한 뒤 내 위치 화면 좌표를 다시 알려 줌
  // 지도 빈 곳을 누르면 그 자리의 위도·경도를 알려 줌 (숙소 핀 옮기기)
  kakao.maps.event.addListener(map, 'click', (e) => emit('map-click', { lat: e.latLng.getLat(), lng: e.latLng.getLng() }))
  // 사용자가 지도를 끌어 옮기고 손을 뗀 뒤 가운데 좌표 (pickCenter일 때만)
  kakao.maps.event.addListener(map, 'dragend', sendCenter)
  kakao.maps.event.addListener(map, 'zoom_changed', sendCenter)
  draw()
})

onUnmounted(clear)

// 핀(상태·선택)이나 내 위치가 바뀌면 다시 그림
watch(() => [props.pins, props.me, props.path, props.ring], draw, { deep: true })
watch(() => props.fitKey, () => {
  hasFitted = false
  draw()
})
watch(() => props.me, () => {
  if (props.follow) focusMe()
}, { deep: true })

// 핀·동선·내 위치를 지우고 새로 그림
function draw() {
  if (!map) return
  clear()
  const allPoints = []
  const routePoints = []
  for (const pin of props.pins) {
    if (pin.lat == null || pin.lng == null) continue
    const position = new kakao.maps.LatLng(pin.lat, pin.lng)
    if (pin.fit !== false) allPoints.push(position)
    if (pin.route !== false) routePoints.push(position)
    addOverlay(position, makePin(pin), 2)
  }
  if (props.showRoute && routePoints.length > 1) addRoute(routePoints)
  if (props.path.length > 1) {
    const pathPoints = props.path.map((point) => new kakao.maps.LatLng(point[0], point[1]))
    addPath(pathPoints)
    allPoints.push(...pathPoints)
  }
  if (props.ring) addRing(props.ring)

  if (props.me) {
    const mePosition = new kakao.maps.LatLng(props.me.lat, props.me.lng)
    allPoints.push(mePosition)
    addOverlay(mePosition, makeMe(), 3)
  }
  fitOnce(allPoints)
  sendMePoint()
}

function clear() {
  for (const overlay of overlays) overlay.setMap(null)
  overlays = []
}

function addOverlay(position, element, zIndex) {
  // CustomOverlay: 지도 위 좌표에 우리가 만든 HTML(핀)을 올려요. 핀 모양은 예전 그림 지도와 같은 .pin 클래스
  const overlay = new kakao.maps.CustomOverlay({ position, content: element, zIndex, clickable: true })
  overlay.setMap(map)
  overlays.push(overlay)
}

function addRoute(points) {
  // Polyline: 좌표를 순서대로 이은 선 (도로를 따라가는 길이 아니라 장소끼리 직선)
  const line = new kakao.maps.Polyline({ path: points, strokeWeight: 5, strokeColor: '#1F3A68', strokeOpacity: 0.7, strokeStyle: 'shortdash' })
  line.setMap(map)
  overlays.push(line)
}

function addPath(points) {
  // 길 안내 경로선: 길찾기 API가 준 좌표를 이은 진한 실선
  const line = new kakao.maps.Polyline({ path: points, strokeWeight: 7, strokeColor: '#1F3A68', strokeOpacity: 0.9, strokeStyle: 'solid' })
  line.setMap(map)
  overlays.push(line)
}

function addRing(ring) {
  // Circle: 가운데 좌표와 반지름(m)으로 그리는 원
  const circle = new kakao.maps.Circle({
    center: new kakao.maps.LatLng(ring.lat, ring.lng), radius: ring.radius,
    strokeWeight: 2, strokeColor: '#1F3A68', strokeOpacity: 0.5, fillColor: '#1F3A68', fillOpacity: 0.06,
  })
  circle.setMap(map)
  overlays.push(circle)
}

function makePin(pin) {
  const button = document.createElement('button')
  button.type = 'button'
  button.className = 'pin ' + (pin.cls || '')
  button.setAttribute('aria-label', pin.label || '')
  if (pin.icon) {
    button.innerHTML = `<i class="ic ic--s ${pin.icon}" aria-hidden="true" style="width: 13px; height: 13px"></i>`
  } else {
    button.textContent = pin.text
  }
  // 누른 핀 + 그 핀이 지도 칸의 어디(px)인지 같이 보냄 (말풍선 위치에 씀)
  button.addEventListener('click', () => emit('pick', { ...pin, ...toScreenPoint(pin.lat, pin.lng) }))
  return button
}

function makeMe() {
  const dot = document.createElement('span')
  dot.className = 'me'
  dot.setAttribute('aria-label', '지금 위치')
  return dot
}

// 처음 한 번: 모든 점이 여백 안에 보이게 지도 범위를 맞춤
function fitOnce(points) {
  if (hasFitted || points.length === 0) return
  hasFitted = true
  const bounds = new kakao.maps.LatLngBounds()
  for (const point of points) bounds.extend(point)
  if (points.length === 1) {
    const lat = points[0].getLat()
    const lng = points[0].getLng()
    bounds.extend(new kakao.maps.LatLng(lat + SINGLE_POINT_SPAN, lng + SINGLE_POINT_SPAN))
    bounds.extend(new kakao.maps.LatLng(lat - SINGLE_POINT_SPAN, lng - SINGLE_POINT_SPAN))
  }
  const pad = props.padding
  map.setBounds(bounds, pad.top, pad.right, pad.bottom, pad.left)
}

// 내 위치로 지도를 옮김 (부모의 "내 위치로" 버튼)
function focusMe() {
  if (!map || !props.me) return false
  map.panTo(new kakao.maps.LatLng(props.me.lat, props.me.lng))
  return true
}

// 모든 핀·경로·내 위치가 보이게 다시 맞춤
function fitAll() {
  hasFitted = false
  draw()
}

// 지도 가운데를 이 좌표로 옮김 (level: 확대 단계, 작을수록 가까이. 없으면 그대로)
function moveTo(lat, lng, level = null) {
  if (!map) return
  hasFitted = true // 옮긴 화면을 핀 다시 그리기가 덮어쓰지 않게
  isMovingByCode = true // 코드로 옮기는 동안의 zoom_changed는 사용자가 옮긴 게 아니라서 center를 보내지 않음
  map.setCenter(new kakao.maps.LatLng(lat, lng))
  if (level != null) map.setLevel(level)
  isMovingByCode = false
}

function sendCenter() {
  if (!props.pickCenter || !map || isMovingByCode) return
  const center = map.getCenter()
  emit('center', { lat: center.getLat(), lng: center.getLng() })
}

// 위도·경도가 지금 지도 칸의 어디(px)인지 (지도가 아직 없으면 null)
function pointOf(lat, lng) {
  return map ? toScreenPoint(lat, lng) : null
}

defineExpose({ focusMe, fitAll, pointOf, moveTo })

// 내 위치가 지도 칸의 어디(px)인지 알려 줌 → 화장실 칩·AI 패널이 내 위치 옆에 붙어요
function sendMePoint() {
  if (!map || !props.me) return
  emit('me-point', toScreenPoint(props.me.lat, props.me.lng))
}

// 위도·경도 → 지도 칸 안의 px 위치 { x, y }
function toScreenPoint(lat, lng) {
  const point = map.getProjection().containerPointFromCoords(new kakao.maps.LatLng(lat, lng))
  return { x: Math.round(point.x), y: Math.round(point.y) }
}
</script>

<template>
  <div ref="box" class="map kmap"></div>
</template>

<style>
/* z-index 0: 지도 안쪽 층이 위의 알림·일정 시트를 덮지 않게 지도끼리만 겹치게 함 */
.kmap{z-index:0}
</style>
