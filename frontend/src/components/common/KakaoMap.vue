<script setup>
// 카카오 지도 (공통): 진짜 위도·경도로 장소 핀, 내 위치, 동선(장소끼리 직선), 길 안내 경로선, 반경 원을 그려요.
// 키가 없거나 SDK를 못 받으면 fail을 보내요 → 부모 화면이 안내 문구를 보여 줘요.
// 부모가 ref로 부를 수 있는 것: focusMe() 내 위치로 옮기기, fitAll() 모든 점이 보이게 다시 맞추기, moveTo(lat, lng, level) 가운데 옮기기,
//   focusAround(center, points) center를 가운데에 두고 points(앞뒤 경로)가 다 보이게 확대 (11/04)
// move: 지도가 움직이는 동안(끌기·확대·코드로 옮기기) 계속 보내요 → 부모가 핀 위 말풍선을 핀에 붙여 따라 옮겨요 (11/04)
// zoom(확대 단계): 처음 그린 뒤 한 번, 확대·축소할 때마다 보내요 (작을수록 가까이) → 부모가 말풍선 크기를 맞춰요 (11/04)
// 화면 좌표 { x, y, isInside }: isInside는 그 점이 지도 칸 안에 보이는지 (밖이면 말풍선을 숨기게)
// 지도는 아래 시트 뒤까지 화면 전체에 깔아요 (시트를 내리면 지도가 보이게, 11/04).
//   그래서 화면 맞추기·가운데 옮기기는 padding(위 버튼·아래 시트에 가리는 만큼) 안쪽 기준이에요 (centerFor)
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { loadKakaoMap } from '@/utils/kakaoMap'

const PIN_GAP_PX = 26 // 번호 핀끼리 이만큼은 떨어지게
const SPREAD_DIRECTIONS = 8
let pinItems = [] // 그린 번호 핀 { position, element }
const props = defineProps({
  pins: { type: Array, default: () => [] }, // [{ key, lat, lng, text, icon, cls, label, route, fit }] 순서대로 동선을 이어요 (route: false면 동선에서 뺌, fit: false면 화면 맞출 때 뺌)
  me: { type: Object, default: null }, // 내 위치 { lat, lng }
  showRoute: { type: Boolean, default: true },
  path: { type: Array, default: () => [] }, // 길 안내 경로선 [[위도, 경도], ...] (도로를 따라가는 진짜 길)
  paths: { type: Array, default: () => [] }, // 구간별 진짜 길 [{ coords: [[위도, 경도], ...], mode, from, to }] (일정 점검, 11/04). 도보는 동그라미 점
  //   from·to(앞뒤 장소 [위도, 경도])가 있으면, 대중교통 선이 역·정류장에서 끝날 때 장소까지 걷는 점으로 이어요
  //   isGuess: 길찾기 실패로 직선 추정이면 진짜 길처럼 보이지 않게 점선 직선 (여행 중 화면, 11/04)
  ring: { type: Object, default: null }, // 반경 원 { lat, lng, radius(m) } (근처 찾기)
  band: { type: Array, default: () => [] }, // 반투명 띠 테두리들 [[[위도, 경도], ...], ...] (AI에게 묻기: 가는 길 양옆, 11/08)
  links: { type: Array, default: () => [] }, // 얇은 점선들 [{ from: [위도, 경도], to: [위도, 경도] }] (AI에게 묻기: 가는 길 → 가게, 11/08)
  follow: { type: Boolean, default: false }, // 길 안내: 처음부터 내 위치를 크게 확대해서 가운데에 두고, 내 위치가 바뀌면 지도가 따라감
  smooth: { type: Boolean, default: false }, // 시연 계정 여행 중 (11/09): 미끄러지는 이동·점선 따라가기·부드러운 따라가기 시작·따라가는 동안 다른 이동·끌기 막기. false면 원래 동작
  heading: { type: Number, default: null }, // 진행 방향(도, 북=0·시계 방향). 있으면 내 위치 점에 화살표를 그 방향으로 (11/09). null이면 점만
  fitKey: { type: [String, Number], default: '' }, // 이 값이 바뀌면 모든 점이 보이게 다시 맞춤 (근처 찾기 결과가 바뀔 때)
  pickCenter: { type: Boolean, default: false }, // 배민 주소 등록처럼: 지도를 움직이면 멈춘 자리의 가운데 좌표를 center로 알려 줌
  // 처음 화면을 맞출 때 비워 둘 여백(px): 위 알림, 아래 일정 시트에 핀이 가리지 않게
  padding: { type: Object, default: () => ({ top: 150, right: 40, bottom: 444, left: 40 }) },
})
const emit = defineEmits(['pick', 'fail', 'me-point', 'map-click', 'center', 'ready', 'move', 'zoom', 'touch'])

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
const GLIDE_MIN_MS = 200 // 내 위치가 미끄러지는 시간: 위치가 오는 간격만큼, 이 범위 안에서 (11/09)
const GLIDE_MAX_MS = 1200
const GLIDE_MAX_JUMP = 0.005 // 약 500m보다 멀리 뛰면 미끄러지지 않고 바로 옮김 (순간 이동)
let glideFrame = 0 // 지금 돌고 있는 미끄러짐 (cancelAnimationFrame으로 멈출 때 씀)
const FOLLOW_PAN_MS = 450 // 이동 시작: 내 위치로 다가가는 시간
const FOLLOW_ZOOM_MS = 500 // 따라가기 시작: 확대 시간
let isStartingFollow = false // 이동 시작 전환 중
let followFrame = 0
let followTimer = null
let lastMeAt = 0 // 지난번 위치가 온 시각
let shownAngle = null // 화살표가 지금 가리키는 각도 (350° → 10°처럼 넘어갈 때 한 바퀴 돌지 않게 이어 셈)
let meOverlay = null // 지금 위치 점 (위치만 바뀌면 이것만 옮기고 나머지는 다시 안 그려요, 11/04)
const FLY_PAN_MS = 450 // flyTo 옮기는 시간
const FLY_ZOOM_MS = 300 // flyTo 확대·축소 시간
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
  kakao.maps.event.addListener(map, 'dragstart', () => emit('touch')) // 사용자가 지도를 만졌어요
  box.value.addEventListener('wheel', () => emit('touch'), { passive: true })
  kakao.maps.event.addListener(map, 'zoom_changed', sendCenter)
  kakao.maps.event.addListener(map, 'idle', spreadPins) // 화면이 정해지면 겹친 번호 핀을 띄움
  kakao.maps.event.addListener(map, 'zoom_changed', drawWalkDots) // 확대·축소하면 걷는 길 점 간격을 다시 맞춤
  kakao.maps.event.addListener(map, 'bounds_changed', () => emit('move')) // 보이는 영역이 바뀔 때마다 (끄는 동안에도 계속)
  kakao.maps.event.addListener(map, 'zoom_changed', () => emit('zoom', map.getLevel()))
  draw()
  emit('zoom', map.getLevel()) // 처음 맞춘 확대 단계
  emit('ready') // 지도가 다 만들어졌어요 (부모가 moveTo 같은 걸 이때 부를 수 있음)
})

onUnmounted(() => {
  cancelAnimationFrame(glideFrame) // 화면을 떠나면 미끄러짐도 멈춤
  cancelAnimationFrame(followFrame)
  clearTimeout(followTimer)
  clear()
})

// 핀(상태·선택)·선·원이 바뀌면 전부 다시 그림
watch(() => [props.pins, props.path, props.paths, props.ring, props.band, props.links], draw, { deep: true })
watch(() => props.fitKey, () => {
  hasFitted = false
  draw()
})
// 내 위치만 바뀌면 (GPS는 휴대폰에서 거의 1초마다) 내 위치 점만 옮김 → 핀·선을 매번 지웠다 그리지 않아서 버벅이지 않아요
watch(() => props.me, moveMe, { deep: true })
watch(() => props.heading, () => meOverlay && turnMe(meOverlay.getContent()))
// follow가 중간에 켜지면(첫 위치가 오거나 근처 찾기·AI 묻기를 닫으면): 내 위치로 부드럽게 다가가며 크게 확대 (11/09)
// 그 뒤로는 머무는 중에도 확대 단계 그대로 내 위치를 가운데에 고정해요 (상재: 여행 중엔 늘 고정)
// 따라가는 동안은 지도를 끌 수 없게 해서 화면이 내 위치에서 벗어나지 않아요. 끝나면 다시 끌 수 있어요
watch(() => props.follow, (isOn) => {
  if (!map || !props.smooth) return
  map.setDraggable(!isOn)
  clearTimeout(followTimer)
  if (!isOn) return
  hasFocusedMe = true
  hasFitted = true // 핀 다시 그리기가 화면을 덮어쓰지 않게
  if (props.me) startFollowSmoothly()
})
// 아래 시트를 올리거나 내려 보이는 칸이 바뀌면 그 칸의 가운데로 다시 맞춤
watch(() => props.padding, () => {
  if (map && props.smooth && props.follow && props.me && !isStartingFollow) map.setCenter(centerFor(new kakao.maps.LatLng(props.me.lat, props.me.lng)))
}, { deep: true })

// 이동 시작 전환: 지금 화면 → 내 위치를 가운데로 부드럽게 옮기고(FOLLOW_PAN_MS) → 그 자리를 기준으로 확대(FOLLOW_ZOOM_MS)
// 전환하는 동안은 미끄러지는 이동(glideMe)이 지도 가운데를 건드리지 않아요 (두 움직임이 겹쳐 튀지 않게)
function startFollowSmoothly() {
  isStartingFollow = true
  const from = map.getCenter()
  const to = centerFor(new kakao.maps.LatLng(props.me.lat, props.me.lng))
  const startedAt = performance.now()
  const pan = (time) => {
    if (!map) return
    const ratio = Math.min(1, (time - startedAt) / FOLLOW_PAN_MS)
    const eased = ratio * (2 - ratio) // 처음엔 빠르게, 끝에서 천천히
    map.setCenter(new kakao.maps.LatLng(from.getLat() + (to.getLat() - from.getLat()) * eased, from.getLng() + (to.getLng() - from.getLng()) * eased))
    if (ratio < 1) {
      followFrame = requestAnimationFrame(pan)
      return
    }
    const me = new kakao.maps.LatLng(props.me.lat, props.me.lng)
    if (map.getLevel() !== FOLLOW_LEVEL) map.setLevel(FOLLOW_LEVEL, { anchor: me, animate: { duration: FOLLOW_ZOOM_MS } }) // anchor: 내 위치는 화면에서 안 움직임
    followTimer = setTimeout(() => {
      isStartingFollow = false
      if (map && props.me) map.setCenter(centerFor(new kakao.maps.LatLng(props.me.lat, props.me.lng)))
    }, FOLLOW_ZOOM_MS + 50)
  }
  cancelAnimationFrame(followFrame)
  followFrame = requestAnimationFrame(pan)
}

// 핀·동선·내 위치를 지우고 새로 그림
function draw() {
  if (!map) return
  clear()
  pinItems = []
  const allPoints = []
  const routePoints = []
  for (const pin of props.pins) {
    if (pin.lat == null || pin.lng == null) continue
    const position = new kakao.maps.LatLng(pin.lat, pin.lng)
    if (pin.fit !== false) allPoints.push(position)
    if (pin.route !== false) routePoints.push(position)
    const element = makePin(pin)
    pinItems.push({ position, element })
    addOverlay(position, element, (pin.cls || '').includes('pin--sel') ? 3 : 2) // 고른 핀은 겹쳐도 맨 위
  }
  spreadPins()
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
  for (const ring of props.band) addBand(ring)
  for (const link of props.links) addLink(link)

  if (props.me) {
    const mePosition = new kakao.maps.LatLng(props.me.lat, props.me.lng)
    allPoints.push(mePosition)
    meOverlay = addOverlay(mePosition, makeMe(), 3)
  }
  if (props.follow && props.me) focusMeFirst()
  else fitOnce(allPoints)
  drawWalkDots()
  sendMePoint()
}

function clear() {
  for (const overlay of overlays) overlay.setMap(null)
  overlays = []
  meOverlay = null
  for (const dot of dotOverlays) dot.setMap(null)
  dotOverlays = []
}

// 번호 핀이 겹치면 살짝 띄워요: 앞 핀들과 PIN_GAP_PX보다 가까우면 둘레 8방향 중 빈 쪽으로 (좌표는 그대로, 화면에서만)
function spreadPins() {
  if (!map || !pinItems.length) return
  const projection = map.getProjection()
  const placed = []
  for (const item of pinItems) {
    const point = projection.containerPointFromCoords(item.position)
    let x = point.x
    let y = point.y
    for (let k = 0; k < SPREAD_DIRECTIONS && isCrowded(placed, x, y); k++) {
      const angle = (k * 2 * Math.PI) / SPREAD_DIRECTIONS
      x = point.x + Math.cos(angle) * PIN_GAP_PX
      y = point.y + Math.sin(angle) * PIN_GAP_PX
    }
    placed.push({ x, y })
    item.element.style.translate = `${x - point.x}px ${y - point.y}px`
  }
}
function isCrowded(placed, x, y) {
  return placed.some((other) => Math.hypot(other.x - x, other.y - y) < PIN_GAP_PX)
}

function addOverlay(position, element, zIndex) {
  // CustomOverlay: 지도 위 좌표에 우리가 만든 HTML(핀)을 올려요. 핀 모양은 예전 그림 지도와 같은 .pin 클래스
  const overlay = new kakao.maps.CustomOverlay({ position, content: element, zIndex, clickable: true })
  overlay.setMap(map)
  overlays.push(overlay)
  return overlay
}

// 내 위치가 바뀜: 점이 이미 있으면 그 점만 옮기고, 처음 생기거나 사라질 때만 전부 다시 그림
// 길 안내(follow)면 지도도 내 위치로 따라감
// 11/09: 한 번에 점프하지 않고 다음 위치가 올 때까지 조금씩 미끄러지듯 옮겨요 (glideMe)
function moveMe() {
  if (!map) return
  if (!props.me || !meOverlay) {
    draw()
    if (props.follow) focusMe()
    return
  }
  turnMe(meOverlay.getContent())
  if (props.smooth) {
    glideMe(props.me)
    return
  }
  meOverlay.setPosition(new kakao.maps.LatLng(props.me.lat, props.me.lng)) // 원래 동작: 한 번에 옮기고, 길 안내면 따라감
  sendMePoint()
  if (props.follow) focusMe()
}

// 앞 위치 → 새 위치를 "지난번 위치가 온 뒤 걸린 시간"만큼 나눠 매 화면마다 조금씩 옮겨요 (선형 보간)
// 따라가기면 매 화면마다 지도 가운데도 같이 맞춰서 화면이 내 위치에 딱 붙어 있어요 (panTo는 애니메이션이 겹쳐 끊겨 보여서 안 씀)
function glideMe(target) {
  cancelAnimationFrame(glideFrame)
  const start = meOverlay.getPosition()
  const from = { lat: start.getLat(), lng: start.getLng() }
  const startedAt = performance.now()
  const duration = Math.min(GLIDE_MAX_MS, Math.max(GLIDE_MIN_MS, startedAt - lastMeAt))
  lastMeAt = startedAt
  const along = findAlongPath(from, target) // 두 점이 다 그려진 경로 위면 직선 대신 경로(점선)를 따라가요
  // 경로 위 이동은 아무리 멀어도 미끄러지게 (택시처럼 긴 구간은 한 칸이 500m를 넘어 순간이동하던 문제, 11/09)
  const isFar = !along && Math.abs(target.lat - from.lat) + Math.abs(target.lng - from.lng) > GLIDE_MAX_JUMP
  const step = (time) => {
    if (!map || !meOverlay) return
    const ratio = isFar ? 1 : Math.min(1, (time - startedAt) / duration)
    const point = along ? pointOnLine(along.line, along.from + (along.to - along.from) * ratio) : [from.lat + (target.lat - from.lat) * ratio, from.lng + (target.lng - from.lng) * ratio]
    const position = new kakao.maps.LatLng(point[0], point[1])
    if (along) turnAlong(meOverlay.getPosition(), position) // 경로를 따라갈 때는 화살표도 지금 지나는 길 방향으로
    meOverlay.setPosition(position)
    if (props.follow && !isStartingFollow) map.setCenter(centerFor(position))
    if (ratio < 1) {
      // requestAnimationFrame: 브라우저가 다음 화면을 그리기 직전에 이 함수를 다시 불러 줌 (1초에 약 60번, 부드러운 움직임용)
      glideFrame = requestAnimationFrame(step)
    } else {
      sendMePoint()
    }
  }
  glideFrame = requestAnimationFrame(step)
}

// ---------------------------------------------------------------- 경로 따라 미끄러지기 (11/09)
// 지도에 그린 구간 길(paths)을 한 줄로 이어, 앞 위치·새 위치가 길에서 몇 m째인지 찾아요.
// 둘 다 길 가까이(PATH_SNAP_M 안)이고 앞으로 가는 중이면 { line, from, to } (from·to는 길 시작에서 센 거리), 아니면 null
const PATH_SNAP_M = 25
const METERS_PER_DEGREE = 111320
function findAlongPath(from, to) {
  const line = []
  for (const leg of props.paths) {
    if (leg.coords) line.push(...leg.coords)
  }
  if (line.length < 2) return null
  const start = locateOnLine(line, from)
  const end = locateOnLine(line, to)
  if (start.gap > PATH_SNAP_M || end.gap > PATH_SNAP_M || end.distance < start.distance) return null
  return { line, from: start.distance, to: end.distance }
}

// 점에서 가장 가까운 길 위 자리: { distance: 길 시작에서 그 자리까지 m, gap: 점과 길 사이 m }
function locateOnLine(line, point) {
  const scale = Math.cos((point.lat * Math.PI) / 180) // 경도 1도는 위도 1도보다 짧아서 맞춰 줌
  let best = { distance: 0, gap: Infinity }
  let walked = 0
  for (let i = 1; i < line.length; i++) {
    const ay = line[i - 1][0], ax = line[i - 1][1] * scale
    const by = line[i][0], bx = line[i][1] * scale
    const py = point.lat, px = point.lng * scale
    const segment = Math.hypot(by - ay, bx - ax)
    const t = segment === 0 ? 0 : Math.max(0, Math.min(1, ((py - ay) * (by - ay) + (px - ax) * (bx - ax)) / (segment * segment)))
    const gap = Math.hypot(py - (ay + (by - ay) * t), px - (ax + (bx - ax) * t)) * METERS_PER_DEGREE
    if (gap < best.gap) best = { distance: (walked + segment * t) * METERS_PER_DEGREE, gap }
    walked += segment
  }
  return best
}

// 화살표를 앞 자리 → 새 자리 방향으로 (화살표를 보여 주는 중이고 1m 넘게 움직였을 때만, 흔들림 방지)
const TURN_MIN_M = 1
function turnAlong(before, after) {
  if (props.heading == null || !meOverlay) return
  const dy = after.getLat() - before.getLat()
  const dx = (after.getLng() - before.getLng()) * Math.cos((after.getLat() * Math.PI) / 180)
  if (Math.hypot(dy, dx) * METERS_PER_DEGREE < TURN_MIN_M) return
  const angle = (Math.atan2(dx, dy) * 180) / Math.PI // 북=0, 동=90
  const turn = shownAngle == null ? 0 : ((angle - shownAngle) % 360 + 540) % 360 - 180
  shownAngle = shownAngle == null ? angle : shownAngle + turn
  meOverlay.getContent().style.transform = 'rotate(' + Math.round(shownAngle) + 'deg)'
}

// 길 시작에서 meters만큼 간 자리 [위도, 경도]
function pointOnLine(line, meters) {
  const scale = Math.cos((line[0][0] * Math.PI) / 180)
  let left = meters / METERS_PER_DEGREE
  for (let i = 1; i < line.length; i++) {
    const dy = line[i][0] - line[i - 1][0]
    const dx = (line[i][1] - line[i - 1][1]) * scale
    const segment = Math.hypot(dy, dx)
    if (left <= segment || i === line.length - 1) {
      const t = segment === 0 ? 0 : Math.min(1, left / segment)
      return [line[i - 1][0] + dy * t, line[i - 1][1] + (line[i][1] - line[i - 1][1]) * t]
    }
    left -= segment
  }
  return line[line.length - 1]
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

function addBand(ring) {
  // Polygon: 좌표로 둘러싼 면. 가는 길에서 몇 분 안에 들를 수 있는 범위를 반투명하게
  const polygon = new kakao.maps.Polygon({
    path: ring.map((point) => new kakao.maps.LatLng(point[0], point[1])),
    strokeWeight: 1, strokeColor: '#1F3A68', strokeOpacity: 0.3, fillColor: '#1F3A68', fillOpacity: 0.08,
  })
  polygon.setMap(map)
  overlays.push(polygon)
}

function addLink(link) {
  // 가는 길 위 가장 가까운 점 → 가게: 얼마나 돌아가는지 보이게 얇은 점선
  const line = new kakao.maps.Polyline({
    path: [new kakao.maps.LatLng(link.from[0], link.from[1]), new kakao.maps.LatLng(link.to[0], link.to[1])],
    strokeWeight: 2, strokeColor: '#1F3A68', strokeOpacity: 0.8, strokeStyle: 'shortdot',
  })
  line.setMap(map)
  overlays.push(line)
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
  if (pin.tag) { // 핀 옆 이름표
    const tag = document.createElement('span')
    tag.className = 'pin__tag'
    tag.textContent = pin.tag
    button.append(tag)
  }
  // 누른 핀 + 그 핀이 지도 칸의 어디(px)인지 같이 보냄 (말풍선 위치에 씀)
  button.addEventListener('click', () => emit('pick', { ...pin, ...toScreenPoint(pin.lat, pin.lng) }))
  return button
}

function makeMe() {
  const dot = document.createElement('span')
  dot.className = 'me'
  dot.setAttribute('aria-label', '지금 위치')
  const arrow = document.createElement('i')
  arrow.className = 'me__arrow'
  dot.appendChild(arrow)
  turnMe(dot)
  return dot
}

// 진행 방향 화살표: heading이 있으면 보이고 점째로 그 방향으로 돌려요 (카카오 지도는 지도 자체를 못 돌려서, 11/09)
function turnMe(dot) {
  const arrow = dot.querySelector('.me__arrow')
  if (!arrow) return
  arrow.hidden = props.heading == null
  if (props.heading == null) {
    shownAngle = null
    dot.style.transform = ''
    return
  }
  // 지금 각도에서 가까운 쪽으로 돌게 (-180 ~ 180도만큼). 실제 회전은 CSS transition이 부드럽게 해요
  const turn = shownAngle == null ? 0 : ((props.heading - shownAngle) % 360 + 540) % 360 - 180
  shownAngle = shownAngle == null ? props.heading : shownAngle + turn
  dot.style.transform = 'rotate(' + Math.round(shownAngle) + 'deg)'
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
  if (props.smooth && props.follow) return true // 시연 따라가는 중에는 이미 내 위치가 가운데
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
  if (props.smooth && props.follow) return // 시연 따라가는 동안은 따라가기만 지도를 옮겨요 (11/09)
  hasFitted = false
  draw()
}

// 지도 가운데를 이 좌표로 옮김 (level: 확대 단계, 작을수록 가까이. 없으면 그대로)
function moveTo(lat, lng, level = null) {
  if (!map || (props.smooth && props.follow)) return
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
  if (!map || (props.smooth && props.follow)) return
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

// 부드럽게 이 좌표로 옮기고(가려지지 않는 칸의 가운데), 그다음 그 자리를 기준으로 확대·축소
function flyTo(lat, lng, level = null) {
  if (!map || (props.smooth && props.follow)) return
  hasFitted = true
  const position = new kakao.maps.LatLng(lat, lng)
  map.panTo(centerFor(position))
  if (level === null || level === map.getLevel()) return
  setTimeout(() => {
    if (map) map.setLevel(level, { anchor: position, animate: { duration: FLY_ZOOM_MS } }) // anchor: 이 점은 화면에서 안 움직임
  }, FLY_PAN_MS)
}

function getLevel() {
  return map ? map.getLevel() : null
}

defineExpose({ focusMe, fitAll, pointOf, moveTo, focusAround, flyTo, getLevel })

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
/* 내 위치 점 위쪽의 진행 방향 화살표 (점째로 돌아가요, 11/09) */
.me__arrow{position:absolute;left:50%;top:-15px;margin-left:-7px;width:0;height:0;border-left:7px solid transparent;border-right:7px solid transparent;border-bottom:10px solid var(--coral)}
.me__arrow[hidden]{display:none}
.kmap .me{transition:transform .4s ease}
</style>
