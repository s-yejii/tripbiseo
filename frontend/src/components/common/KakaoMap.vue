<script setup>
// 카카오 지도 (공통): 진짜 위도·경도로 장소 핀, 내 위치, 동선(장소끼리 직선), 길 안내 경로선, 반경 원을 그려요.
// 키가 없거나 SDK를 못 받으면 fail을 보내요 → 부모 화면이 안내 문구를 보여 줘요.
// 부모가 ref로 부를 수 있는 것: focusMe() 내 위치로 옮기기, fitAll() 모든 점이 보이게 다시 맞추기, moveTo(lat, lng, level) 가운데 옮기기,
//   focusAround(center, points) center를 가운데에 두고 points(앞뒤 경로)가 다 보이게 확대 (11/04)
// move: 지도가 움직이는 동안(끌기·확대·코드로 옮기기) 계속 보내요 → 부모가 핀 위 말풍선을 핀에 붙여 따라 옮겨요 (11/04)
// 화면 좌표 { x, y, isInside }: isInside는 그 점이 지도 칸 안에 보이는지 (밖이면 말풍선을 숨기게)
// 지도는 아래 시트 뒤까지 화면 전체에 깔아요 (시트를 내리면 지도가 보이게, 11/04).
//   그래서 화면 맞추기·가운데 옮기기는 padding(위 버튼·아래 시트에 가리는 만큼) 안쪽 기준이에요 (centerFor)
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { loadKakaoMap } from '@/utils/kakaoMap'

const props = defineProps({
  pins: { type: Array, default: () => [] }, // [{ key, lat, lng, text, icon, cls, label, route, fit }] 순서대로 동선을 이어요 (route: false면 동선에서 뺌, fit: false면 화면 맞출 때 뺌)
  me: { type: Object, default: null }, // 내 위치 { lat, lng }
  showRoute: { type: Boolean, default: true },
  path: { type: Array, default: () => [] }, // 길 안내 경로선 [[위도, 경도], ...] (도로를 따라가는 진짜 길)
  paths: { type: Array, default: () => [] }, // 구간별 진짜 길 [{ coords: [[위도, 경도], ...], mode, from, to }] (일정 점검, 11/04). 도보는 동그라미 점
  //   from·to(앞뒤 장소 [위도, 경도])가 있으면, 대중교통 선이 역·정류장에서 끝날 때 장소까지 걷는 점으로 이어요
  //   isGuess: 길찾기 실패로 직선 추정이면 진짜 길처럼 보이지 않게 점선 직선 (여행 중 화면, 11/04)
  ring: { type: Object, default: null }, // 반경 원 { lat, lng, radius(m) } (근처 찾기)
  follow: { type: Boolean, default: false }, // 길 안내: 처음부터 내 위치를 크게 확대해서 가운데에 두고, 내 위치가 바뀌면 지도가 따라감
  fitKey: { type: [String, Number], default: '' }, // 이 값이 바뀌면 모든 점이 보이게 다시 맞춤 (근처 찾기 결과가 바뀔 때)
  pickCenter: { type: Boolean, default: false }, // 배민 주소 등록처럼: 지도를 움직이면 멈춘 자리의 가운데 좌표를 center로 알려 줌
  // 처음 화면을 맞출 때 비워 둘 여백(px): 위 알림, 아래 일정 시트에 핀이 가리지 않게
  padding: { type: Object, default: () => ({ top: 150, right: 40, bottom: 444, left: 40 }) },
})
const emit = defineEmits(['pick', 'fail', 'me-point', 'map-click', 'center', 'ready', 'move'])

const START_CENTER = { lat: 37.5385, lng: 127.1238 } // 핀을 그리기 전 잠깐 보이는 중심 (천호역)
const SINGLE_POINT_SPAN = 0.003 // 점이 하나뿐일 때 주변으로 넓혀 보여 줄 범위(도)
const CLOSEST_LEVEL = 3 // focusAround로 이보다 더 가까이 확대하지 않음 (골목 몇 개만 보이지 않게)
const FOLLOW_LEVEL = 3 // 길 안내(follow) 처음 확대 단계 (작을수록 가까이, 3 ≈ 골목·건물이 보이는 정도). 그 뒤엔 사용자가 바꾼 단계 유지

const box = ref(null)
let kakao = null
let map = null
let overlays = [] // 지금 지도에 올린 핀·선 (다시 그릴 때 지움)
let hasFitted = false // 처음 한 번만 모든 핀이 보이게 맞춤 (그 뒤엔 사용자가 옮긴 화면을 유지)
let isMovingByCode = false // moveTo로 옮기는 중
let hasFocusedMe = false // 길 안내(follow): 내 위치로 처음 확대했는지 (위치가 늦게 와도 한 번은 확대)
const WALK_DOT_GAP_PX = 12 // 걷는 길 동그라미 점 사이 간격 (화면 px, 확대해도 같은 간격)
const WALK_END_GAP_M = 30 // 대중교통 선 끝과 장소가 이보다 멀면 걷는 점으로 이어요
let walkLegs = [] // 걷는 구간 좌표 목록 (확대·축소하면 점을 다시 찍음)
let dotOverlays = [] // 지금 찍힌 동그라미 점

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
  kakao.maps.event.addListener(map, 'zoom_changed', drawWalkDots) // 확대·축소하면 걷는 길 점 간격을 다시 맞춤
  kakao.maps.event.addListener(map, 'bounds_changed', () => emit('move')) // 보이는 영역이 바뀔 때마다 (끄는 동안에도 계속)
  draw()
  emit('ready') // 지도가 다 만들어졌어요 (부모가 moveTo 같은 걸 이때 부를 수 있음)
})

onUnmounted(clear)

// 핀(상태·선택)이나 내 위치가 바뀌면 다시 그림
watch(() => [props.pins, props.me, props.path, props.paths, props.ring], draw, { deep: true })
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
  walkLegs = []
  for (const leg of props.paths) {
    if (!leg.coords || leg.coords.length < 2) continue
    const legPoints = leg.coords.map((point) => new kakao.maps.LatLng(point[0], point[1]))
    if (leg.mode === 'walk') {
      walkLegs.push(legPoints) // 걷는 길은 동그라미 점으로 (화면을 맞춘 뒤 찍음)
    } else if (leg.isGuess) {
      addRoute(legPoints) // 추정 직선은 점선
    } else {
      addLeg(legPoints)
      addWalkEnds(leg, legPoints)
    }
  }
  if (props.ring) addRing(props.ring)

  if (props.me) {
    const mePosition = new kakao.maps.LatLng(props.me.lat, props.me.lng)
    allPoints.push(mePosition)
    addOverlay(mePosition, makeMe(), 3)
  }
  if (props.follow && props.me) focusMeFirst()
  else fitOnce(allPoints)
  drawWalkDots()
  sendMePoint()
}

function clear() {
  for (const overlay of overlays) overlay.setMap(null)
  overlays = []
  for (const dot of dotOverlays) dot.setMap(null)
  dotOverlays = []
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

function addLeg(points) {
  // 대중교통·차 구간의 진짜 길: 남색 실선. 지도 색에 묻히지 않게 아래에 흰 테두리 선을 먼저 깔아요
  const under = new kakao.maps.Polyline({ path: points, strokeWeight: 9, strokeColor: '#FFFFFF', strokeOpacity: 0.9, strokeStyle: 'solid' })
  const line = new kakao.maps.Polyline({ path: points, strokeWeight: 6, strokeColor: '#1F3A68', strokeOpacity: 1, strokeStyle: 'solid' })
  under.setMap(map)
  line.setMap(map)
  overlays.push(under, line)
}

// 대중교통 길찾기는 타는 구간 선만 줘서, 장소 → 역·정류장, 역·정류장 → 장소 걷는 부분이 비어요 (11/04)
// 선의 처음·끝이 장소에서 30m 넘게 떨어져 있으면 그 사이를 걷는 점으로 이어요 (걷는 길 모양은 몰라서 곧게)
function addWalkEnds(leg, legPoints) {
  const first = legPoints[0]
  const last = legPoints[legPoints.length - 1]
  if (leg.from && isFar(leg.from, first)) walkLegs.push([new kakao.maps.LatLng(leg.from[0], leg.from[1]), first])
  if (leg.to && isFar(leg.to, last)) walkLegs.push([last, new kakao.maps.LatLng(leg.to[0], leg.to[1])])
}

// [위도, 경도]와 지도 좌표가 WALK_END_GAP_M보다 먼지 (짧은 거리라 평면으로 계산)
function isFar(point, position) {
  const dy = (position.getLat() - point[0]) * 111000
  const dx = (position.getLng() - point[1]) * 111000 * Math.cos((point[0] * Math.PI) / 180)
  return Math.hypot(dx, dy) > WALK_END_GAP_M
}

// 걷는 길: 선 대신 동그라미 점을 화면에서 12px마다 찍어요 (지도 점선은 길쭉하게 나와서)
function drawWalkDots() {
  for (const dot of dotOverlays) dot.setMap(null)
  dotOverlays = []
  if (!map || walkLegs.length === 0) return
  const projection = map.getProjection() // 위도·경도 ↔ 지도 화면 위치(px) 바꾸는 도구
  for (const points of walkLegs) {
    for (const position of dotPositions(projection, points)) {
      const dot = document.createElement('span')
      dot.className = 'walk-dot'
      const overlay = new kakao.maps.CustomOverlay({ position, content: dot, zIndex: 1 }) // 가운데가 그 좌표에 오게 (기본값)
      overlay.setMap(map)
      dotOverlays.push(overlay)
    }
  }
}

// 선을 따라 화면 거리 12px마다 점 자리 (위도·경도). carry: 앞 조각에서 남은 거리를 다음 조각으로 넘김
function dotPositions(projection, points) {
  const result = []
  let carry = 0
  for (let i = 1; i < points.length; i++) {
    const from = projection.containerPointFromCoords(points[i - 1])
    const to = projection.containerPointFromCoords(points[i])
    const length = Math.hypot(to.x - from.x, to.y - from.y)
    let distance = carry
    while (distance < length) {
      const ratio = distance / length
      const point = new kakao.maps.Point(from.x + (to.x - from.x) * ratio, from.y + (to.y - from.y) * ratio)
      result.push(projection.coordsFromContainerPoint(point))
      distance += WALK_DOT_GAP_PX
    }
    carry = distance - length
  }
  return result
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
  map.panTo(centerFor(new kakao.maps.LatLng(props.me.lat, props.me.lng)))
  return true
}

// 길 안내 처음 한 번: 경로 전체 대신 내 위치를 크게 확대해서 (시트에 안 가리는 칸) 가운데에 둠 (11/04)
function focusMeFirst() {
  if (hasFocusedMe) return
  hasFocusedMe = true
  hasFitted = true // 그 뒤엔 핀 다시 그리기가 화면을 덮어쓰지 않게 (위치가 바뀌면 focusMe가 따라감)
  map.setLevel(FOLLOW_LEVEL)
  map.setCenter(centerFor(new kakao.maps.LatLng(props.me.lat, props.me.lng)))
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

// center([위도, 경도])를 가운데에 두고 points([[위도, 경도], ...])가 다 보이게 화면을 맞춤
// 가운데를 지키려고 각 점을 center 반대쪽으로 뒤집은 점도 같이 넣어요 (영역이 center를 중심으로 대칭)
function focusAround(center, points) {
  if (!map) return
  hasFitted = true // 맞춘 화면을 핀 다시 그리기가 덮어쓰지 않게
  isMovingByCode = true
  const bounds = new kakao.maps.LatLngBounds()
  bounds.extend(new kakao.maps.LatLng(center[0], center[1]))
  for (const point of points) {
    bounds.extend(new kakao.maps.LatLng(point[0], point[1]))
    bounds.extend(new kakao.maps.LatLng(2 * center[0] - point[0], 2 * center[1] - point[1]))
  }
  const pad = props.padding
  map.setBounds(bounds, pad.top, pad.right, pad.bottom, pad.left)
  if (map.getLevel() < CLOSEST_LEVEL) map.setLevel(CLOSEST_LEVEL)
  map.setCenter(centerFor(new kakao.maps.LatLng(center[0], center[1])))
  isMovingByCode = false
}

// position이 가려지지 않는 칸(padding 안쪽)의 가운데에 보이려면 지도 가운데를 어디에 둬야 하는지
//   예: 아래 시트가 404px 가리면 보이는 칸 가운데는 지도 가운데보다 위 → 그만큼 지도 가운데를 아래로
function centerFor(position) {
  const pad = props.padding
  const shiftX = (pad.left - pad.right) / 2 // 보이는 칸 가운데가 지도 가운데보다 오른쪽(+)으로 몇 px
  const shiftY = (pad.top - pad.bottom) / 2 // 아래(+)로 몇 px
  const projection = map.getProjection()
  const point = projection.containerPointFromCoords(position)
  return projection.coordsFromContainerPoint(new kakao.maps.Point(point.x - shiftX, point.y - shiftY))
}

defineExpose({ focusMe, fitAll, pointOf, moveTo, focusAround })

// 내 위치가 지도 칸의 어디(px)인지 알려 줌 → 화장실 칩·AI 패널이 내 위치 옆에 붙어요
function sendMePoint() {
  if (!map || !props.me) return
  emit('me-point', toScreenPoint(props.me.lat, props.me.lng))
}

// 위도·경도 → 지도 칸 안의 px 위치 { x, y }
function toScreenPoint(lat, lng) {
  const point = map.getProjection().containerPointFromCoords(new kakao.maps.LatLng(lat, lng))
  const x = Math.round(point.x)
  const y = Math.round(point.y)
  const isInside = x >= 0 && y >= 0 && x <= box.value.clientWidth && y <= box.value.clientHeight
  return { x, y, isInside }
}
</script>

<template>
  <div ref="box" class="map kmap"></div>
</template>

<style>
/* z-index 0: 지도 안쪽 층이 위의 알림·일정 시트를 덮지 않게 지도끼리만 겹치게 함 */
.kmap{z-index:0}
/* 걷는 길 동그라미 점 (남색 + 흰 테두리, 11/04) */
.walk-dot{display:block;width:7px;height:7px;border-radius:50%;background:#1F3A68;box-shadow:0 0 0 2px #fff}
</style>
