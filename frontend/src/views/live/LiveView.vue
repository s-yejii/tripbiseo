<script setup>
// S13-a 여행 중 (④) — 프로토타입 S13-a·S13-a2를 진짜 데이터로
//   진행 중(ongoing)인 여행 + 지금 GPS(또는 개발자 모드의 가짜 GPS)로 POST /live/track·/live/status를 불러 그려요.
//   지도: 카카오 지도에 다녀온 곳(체크)·지금 있는 곳·남은 곳·고정 일정(자물쇠) 핀, 근처 찾기 결과 핀
//   시트: 지금 있는 곳을 떠날 시각(도착 기록 + 머무는 시간) 또는 다음 장소까지 남은 시간, 고정 일정 여유
//   늦으면: 위 알림(접으면 +N분 알약) → 지연 화면, 아래 버튼 "대안 보기"
//   11/04: 위 알림은 같은 종류면 10분에 한 번만 펼쳐요 (다른 화면 갔다 와도 계속 펼쳐지지 않게, utils/alertMemory).
//     접혀 있으면 알약(늦으면 +N분, 아니면 "알림")을 눌러 펼쳐요
//   11/04: 지도에 지금 위치 → 다음 장소 진짜 길(서버 status.next: TMAP·ODsay 길찾기)을 그려요.
//     대중교통·차는 실선, 걷기는 동그라미 점, 길찾기 실패로 거리 추정이면 점선 직선. 그 뒤 남은 장소끼리는 점선 직선
// 진행 중인 여행이 없거나 위치를 못 받으면 가짜 화면 대신 안내 문구를 보여 줘요.
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useLiveTrip } from '@/utils/useLiveTrip'
import { imageFor } from '@/utils/mapView'
import { toMinutes, toTime } from '@/utils/time'
import { showToast } from '@/utils/toast'
import { goBack } from '@/utils/back'
import { rememberAlert, shouldOpenAlert } from '@/utils/alertMemory'
import { finishTrip } from '@/api/trips'
import BottomSheet from '@/components/common/BottomSheet.vue'
import AppNoti from '@/components/common/AppNoti.vue'
import PageMessage from '@/components/common/PageMessage.vue'
import NotiBell from '@/components/member/NotiBell.vue'
import KakaoMap from '@/components/common/KakaoMap.vue'
import LiveCards from '@/components/live/LiveCards.vue'
import DevDot from '@/components/live/DevDot.vue'
import TripEndSheet from '@/components/live/TripEndSheet.vue'
import { devMode, isFakeGps, setFakeGps } from '@/utils/devMode'
import SegSheet from '@/components/live/SegSheet.vue'
import AskPanel from '@/components/live/AskPanel.vue'
import NearSearch from '@/components/after/NearSearch.vue'
import LinkAddSheet from '@/components/live/LinkAddSheet.vue'
import { useReplanStore } from '@/stores/replan'

const MUTE_AGAIN_MIN = 10 // "그냥 갈게요" 뒤 이만큼 더 늦어지면 다시 알림
const LEG_MODE = { walk: 'walk', transit: 'subway', car: 'taxi' } // 서버 이동 수단 → 카드 칩 아이콘
const MODE_LABEL = { walk: '도보', transit: '대중교통', car: '택시' }

const router = useRouter()
const replan = useReplanStore()
const live = useLiveTrip()
const isDemo = ref(isFakeGps()) // 가짜 GPS: 개발자 모드(시연 계정)에서만 켤 수 있음 (11/06: 주소의 ?demo=1은 없앰)

onMounted(() => live.start(isDemo.value))
onUnmounted(() => live.stop())

const mapRef = ref(null)
const status = computed(() => live.status.value)
const isReady = computed(() => live.phase.value === 'ready' && status.value !== null)

const selected = ref(null) // 고른 카드·핀 (칸 id)
const previewId = ref(null) // 지도가 잠깐 보여 주는 칸 (이름표)
const info = ref(null) // 정보창을 연 다녀온 곳 { id, x, y }
const segCard = ref(null) // 이동 칩을 누른 카드
const notiOpen = ref(false) // 위 알림을 펼쳤는지 (알림이 오면 아래 watch가 정함)
const asking = ref(false) // AI 채팅
const adding = ref(false) // 근처 찾기
const nearPlaces = ref([]) // 근처 찾기 결과
const nearRadius = ref(null)
const nearPickedId = ref(null)
const mePoint = ref(null) // 지도 위 내 위치 px (AI 패널 위치에 씀)
const isMapFailed = ref(false)
const isFinishing = ref(false)
const fabOpen = ref(false) // + 버튼 메뉴
const linkOpen = ref(false) // 링크로 장소 추가

// 여행 칸 (id → 칸): state(skipped)·arrivedAt·departedAt을 봐요
const tripStopMap = computed(() => {
  const map = {}
  if (!live.trip.value) return map
  for (const stop of live.trip.value.stops) map[stop.id] = stop
  return map
})

// 오늘 칸 (뺀 칸은 지도·카드에서 숨김)
const stops = computed(() => {
  if (!isReady.value) return []
  // 다녀온 곳·지금 있는 곳은 실제 도착 순서로, 남은 곳은 서버 순서대로 (계획보다 일찍 다녀온 곳이 있어도 순서가 꼬이지 않게)
  const visited = []
  const remaining = []
  for (const stop of status.value.stops) {
    const tripStop = tripStopMap.value[stop.id]
    if (tripStop && tripStop.state === 'skipped') continue
    const item = { ...stop, arrivedAt: tripStop ? tripStop.arrivedAt : null, departedAt: tripStop ? tripStop.departedAt : null }
    if (stop.reach === 'done' || stop.reach === 'here') visited.push(item)
    else remaining.push(item)
  }
  visited.sort((a, b) => (a.reach === 'here') - (b.reach === 'here') || (toClock(a.arrivedAt) || a.time).localeCompare(toClock(b.arrivedAt) || b.time))
  const list = [...visited, ...remaining]
  for (let i = 0; i < list.length; i++) list[i].no = i + 1
  return list
})
const currentStop = computed(() => stops.value.find((stop) => stop.reach === 'here') || null)
const remainingStops = computed(() => stops.value.filter((stop) => stop.reach !== 'done' && stop.reach !== 'here'))
const nextStop = computed(() => remainingStops.value[0] || null)
const doneCount = computed(() => stops.value.filter((stop) => stop.reach === 'done').length)
const isAllDone = computed(() => isReady.value && !currentStop.value && remainingStops.value.length === 0)
const fixed = computed(() => (isReady.value ? status.value.fixed : null))
const lateMin = computed(() => (fixed.value && fixed.value.slackMin < 0 ? -fixed.value.slackMin : 0))
const isLate = computed(() => isReady.value && status.value.replan && lateMin.value > 0)

// "그냥 갈게요"를 눌렀고 그때보다 10분 넘게 더 늦지 않았으면 지연 알림을 접어 둠
const isAlertMuted = computed(() => {
  if (!isLate.value || replan.mutedLate === null) return false
  return lateMin.value < replan.mutedLate + MUTE_AGAIN_MIN
})
const alertLink = computed(() => (isReady.value && status.value.replan ? '/live/delay' : ''))
const currentAlert = computed(() => (isReady.value ? status.value.alert : null))

// 새 알림이 오면(또는 화면에 들어오면) 펼칠지 정함: 같은 종류는 10분에 한 번만 펼치고 나머지는 알약으로 접어 둠
watch(() => (currentAlert.value ? currentAlert.value.key || currentAlert.value.title : null), () => {
  const alert = currentAlert.value
  if (!alert) return
  notiOpen.value = shouldOpenAlert(alert)
  if (notiOpen.value) rememberAlert(alert)
}, { immediate: true })

// 첫 칸을 가운데에 고름 (지금 있는 곳 → 다음 곳)
watch(stops, (list) => {
  if (selected.value !== null && list.some((stop) => stop.id === selected.value)) return
  const first = currentStop.value || nextStop.value || list[0]
  selected.value = first ? first.id : null
})

// "2026-11-03T14:15:00" → "14:15"
function toClock(dateTime) {
  return dateTime ? dateTime.substring(11, 16) : null
}
function distText(meters) {
  if (meters == null) return ''
  return meters >= 1000 ? (meters / 1000).toFixed(1) + 'km' : meters + 'm'
}
function isWarnReach(reach) {
  return reach === 'tight' || reach === 'no' || reach === 'closed' || reach === 'missed'
}

// ------------------------------------------------------------ 지도 핀
const mapPins = computed(() => {
  const pins = []
  for (const stop of stops.value) {
    const isFit = !(adding.value && nearPlaces.value.length) // 근처 찾기 결과가 있으면 결과·내 위치만 보이게 맞춤
    // 다녀온 곳·지금 있는 곳은 점선 동선에서 빼요 (지금 위치 → 다음 장소는 nextPath 진짜 길로)
    const isRoute = stop.reach !== 'done' && stop.reach !== 'here'
    pins.push({ key: stop.id, id: stop.id, tag: previewId.value === stop.id ? stop.name : '', lat: stop.lat, lng: stop.lng, text: stop.no, icon: pinIcon(stop), cls: pinClass(stop), label: stop.no + ' ' + stop.name, fit: isFit, route: isRoute })
  }
  for (const place of nearPlaces.value) {
    const cls = ' pin--res' + (place.id === nearPickedId.value ? ' pin--sel' : '')
    pins.push({ key: 'near-' + place.id, nearId: place.id, lat: place.lat, lng: place.lng, icon: nearIcon(place.category), cls, label: place.name + ', ' + distText(place.distance), route: false })
  }
  return pins
})

// 지금 위치 → 다음 장소 진짜 길 (KakaoMap paths 모양). 다음 장소가 없거나 길이 없으면 []
//   status.next.source가 estimate면 길찾기를 못 해서 직선으로 추정한 것 → isGuess (점선으로)
//   from(출발 쪽 걷는 점)은 상황을 받은 그때의 내 위치로 고정 → GPS가 바뀔 때마다 길을 다시 그리지 않게 (30초마다 상황이 오면 바뀜)
const statusFrom = ref(null)
watch(status, () => {
  const position = live.position.value
  statusFrom.value = position ? [position.lat, position.lng] : null
}, { immediate: true })
const nextPath = computed(() => {
  const next = isReady.value ? status.value.next : null
  if (!next || !statusFrom.value || !next.coords || next.coords.length < 2) return []
  const stop = stops.value.find((item) => item.id === next.id)
  if (!stop) return []
  return [{
    coords: next.coords,
    mode: stop.moveMode || 'walk',
    isGuess: next.source === 'estimate',
    from: statusFrom.value,
    to: [stop.lat, stop.lng],
  }]
})

function pinIcon(stop) {
  if (stop.reach === 'done') return 'i-check'
  if (stop.reach === 'here') return 'i-pin'
  if (stop.fixed) return 'i-lock'
  return ''
}
function pinClass(stop) {
  let cls = ''
  if (stop.reach === 'done') cls += ' pin--done'
  else if (stop.reach === 'here') cls += ' pin--here'
  else if (stop.fixed) cls += ' pin--fixed'
  if (stop.reach !== 'done' && stop.reach !== 'here' && (isWarnReach(stop.reach) || (isLate.value && !stop.fixed))) cls += ' pin--warn'
  if (selected.value === stop.id && stop.reach !== 'here') cls += ' pin--sel'
  return cls
}
function nearIcon(category) {
  const text = category || ''
  if (/카페|커피|디저트/.test(text)) return 'i-cup'
  if (/술|주점|바|호프|포차/.test(text)) return 'i-flag'
  return 'i-star'
}

function onPickPin(pin) {
  if (pin.nearId) {
    nearPickedId.value = pin.nearId
    return
  }
  const stop = stops.value.find((item) => item.id === pin.id)
  if (stop) pickStop(stop, pin)
}

// ------------------------------------------------------------ 시트 위 큰 글자
const head = computed(() => {
  if (!isReady.value) return null
  const now = toMinutes(status.value.now)
  const current = currentStop.value
  if (current) {
    const start = current.arrivedAt ? toMinutes(toClock(current.arrivedAt)) : toMinutes(current.time)
    const leaveAt = start + (current.stay || 0)
    const left = leaveAt - now
    if (left >= 0) {
      return { cap: current.name + ' 출발', big: toTime(leaveAt), strong: left + '분 남음', cls: '' }
    }
    return { cap: '출발 시간 지남', big: '+' + -left + '분', small: toTime(leaveAt) + ' 출발', cls: 'c-warning' }
  }
  const next = nextStop.value
  if (next) {
    const label = doneCount.value === 0 ? '첫 장소 ' : '다음 '
    const move = next.moveMin != null ? ' ' + (MODE_LABEL[next.moveMode] || '') + ' ' + next.moveMin + '분' : ''
    return { cap: label + next.name + '까지' + move, big: next.eta, strong: '도착 예정', cls: isWarnReach(next.reach) ? 'c-warning' : '' }
  }
  return { cap: '오늘 일정을 모두 다녀왔어요', big: status.value.now, strong: '지금', cls: '' }
})

const fixedBadge = computed(() => {
  if (!fixed.value) return null
  const slack = fixed.value.slackMin
  return { cls: slack < 0 ? 'badge--warning' : 'badge--success', text: fixed.value.name + (slack < 0 ? ' ' + -slack + '분 늦음' : ' 여유 ' + slack + '분') }
})

// ------------------------------------------------------------ 카드
const cards = computed(() => stops.value.map((stop) => {
  const card = { no: stop.id, stopId: stop.id, img: imageFor(stop.category), title: stop.fixed ? stop.name : stop.no + ' ' + stop.name, fixed: stop.fixed, leg: null, legState: '', state: '', sub: '', subCls: '' }
  if (stop.moveMin != null) {
    card.leg = { mode: LEG_MODE[stop.moveMode] || 'walk', min: stop.moveMin, dist: distText(stop.moveMeters) }
    card.legState = stop.id === (nextStop.value && nextStop.value.id) ? 'now' : ''
  }
  if (stop.reach === 'done') {
    card.state = 'done'
    card.sub = (toClock(stop.arrivedAt) || stop.time) + ' 다녀옴'
  } else if (stop.reach === 'here') {
    card.state = 'now'
    card.sub = '지금 여기' + (stop.arrivedAt ? ' · ' + toClock(stop.arrivedAt) + ' 도착' : '')
    card.subCls = 'c-coral'
  } else if (stop.reach === 'closed') {
    card.sub = stop.closedReason || '도착할 때 문을 닫아요'
    card.subCls = 'c-danger'
  } else if (stop.reach === 'missed') {
    card.sub = stop.time + ' 계획 시간이 지났어요'
    card.subCls = 'c-warning'
  } else if (stop.fixed && !isLate.value) {
    card.sub = stop.time + ' 고정 · ' + stop.eta + ' 도착 예상'
  } else {
    card.sub = stop.eta + ' 도착 예상'
    if (isLate.value) card.subCls = stop.fixed ? 'c-danger' : 'c-warning'
    else if (isWarnReach(stop.reach)) card.subCls = 'c-warning'
  }
  return card
}))

// 카드·핀을 눌렀을 때: 다녀온 곳 → 정보창 / 지금 있는 곳 → 내 위치로 / 남은 곳 → 고르기, 한 번 더 누르면 미리 바꾸기
function pickStop(stop, point) {
  if (stop.reach === 'done') return toggleInfo(stop, point)
  info.value = null
  if (stop.reach === 'here') return locate()
  if (selected.value === stop.id && !stop.fixed) return router.push('/live/pin?id=' + live.tripId.value + '&stopId=' + stop.id)
  selected.value = stop.id
  previewStop(stop)
}

// 카드를 옆으로 넘겼을 때도 그 곳을 잠깐 보여 줘요 (같은 카드면 다시 안 움직임)
function onCardScrolled(id) {
  if (selected.value === id) return
  selected.value = id
  const stop = stops.value.find((item) => item.id === id)
  clearTimeout(scrollTimer)
  if (stop && stop.reach !== 'done' && stop.reach !== 'here') scrollTimer = setTimeout(() => previewStop(stop), PREVIEW_SCROLL_WAIT_MS)
}

// 그 곳으로 부드럽게 가서 약 2초 보여 주고 지금 위치로 돌아옴. 그 사이 사용자가 지도를 만지면 안 돌아감
function previewStop(stop) {
  if (!mapRef.value || stop.lat == null || adding.value) return
  clearTimeout(backTimer)
  if (previewId.value === null) levelBefore = mapRef.value.getLevel()
  previewId.value = stop.id
  mapRef.value.flyTo(stop.lat, stop.lng, Math.min(levelBefore, PREVIEW_LEVEL))
  backTimer = setTimeout(flyBack, PREVIEW_HOLD_MS)
}
function flyBack() {
  previewId.value = null
  if (!mapRef.value) return
  const me = live.position.value
  if (me) mapRef.value.flyTo(me.lat, me.lng, levelBefore)
  else mapRef.value.fitAll() // 위치를 모르면 전체 일정이 보이게
}
function onMapTouched() {
  clearTimeout(backTimer)
  previewId.value = null
}
onUnmounted(() => {
  clearTimeout(backTimer)
  clearTimeout(scrollTimer)
})
function pickCard(card) {
  const stop = stops.value.find((item) => item.id === card.stopId)
  if (stop) pickStop(stop, null)
}

function toggleInfo(stop, point) {
  if (info.value && info.value.id === stop.id) {
    info.value = null
    return
  }
  const xy = point && point.x != null ? point : mapRef.value && mapRef.value.pointOf(stop.lat, stop.lng)
  if (!xy) return
  const arrived = toClock(stop.arrivedAt)
  const departed = toClock(stop.departedAt)
  const time = arrived ? arrived + (departed ? '–' + departed : '') + ' 다녀옴' : stop.time + ' 계획 · 도착 기록 없음'
  info.value = { id: stop.id, name: stop.name, time, x: xy.x, y: xy.y }
}

// 이동 칩 시트: 앞 칸(첫 남은 칸이면 지금 위치) → 이 칸
const segFrom = computed(() => {
  if (!segCard.value) return null
  const index = stops.value.findIndex((stop) => stop.id === segCard.value.stopId)
  const before = stops.value[index - 1]
  const isFromMe = !before || before.reach === 'done'
  if (isFromMe && live.position.value) return { name: '지금 위치', img: '/img/mapB.svg', lat: live.position.value.lat, lng: live.position.value.lng }
  return { name: before.name, img: imageFor(before.category), lat: before.lat, lng: before.lng }
})
const segTo = computed(() => {
  if (!segCard.value) return null
  const stop = stops.value.find((item) => item.id === segCard.value.stopId)
  return { name: stop.name, img: imageFor(stop.category), lat: stop.lat, lng: stop.lng }
})

// ------------------------------------------------------------ 오른쪽 버튼·칩
function locate() {
  if (mapRef.value && mapRef.value.focusMe()) return
  showToast('아직 지금 위치를 받지 못했어요')
}

// 같이 간 사람에게 남은 일정 보내기 (휴대폰 공유 창, 없으면 글자 복사)
async function share() {
  const lines = [(live.trip.value ? live.trip.value.title : '여행') + ' · 지금 ' + status.value.now]
  if (currentStop.value) lines.push('지금 ' + currentStop.value.name)
  for (const stop of remainingStops.value) lines.push(stop.eta + ' ' + stop.name + (stop.fixed ? ' (고정 ' + stop.time + ')' : ''))
  if (fixed.value) lines.push(fixed.value.name + (fixed.value.slackMin < 0 ? ' ' + -fixed.value.slackMin + '분 늦을 것 같아요' : ' 여유 ' + fixed.value.slackMin + '분'))
  const text = lines.join('\n')
  try {
    if (navigator.share) {
      await navigator.share({ title: '남은 일정', text })
      return
    }
    await navigator.clipboard.writeText(text)
    showToast('남은 일정을 복사했어요 · 메신저에 붙여 넣어 보내세요')
  } catch (e) {
    if (e.name !== 'AbortError') showToast('공유하지 못했어요')
  }
}

// + 버튼 메뉴: 하나 누르면 메뉴를 닫고 실행
function runFab(action) {
  fabOpen.value = false
  action()
}
function openToday() {
  router.push('/trips/today?id=' + live.tripId.value + '&back=' + encodeURIComponent('/live'))
}
function openLink() {
  linkOpen.value = true
}
async function onLinkAdded(message) {
  linkOpen.value = false
  showToast(message)
  await live.refresh()
}

function openAsk() {
  asking.value = true
  adding.value = false
  info.value = null
}
function openNear() {
  adding.value = true
  asking.value = false
  info.value = null
}
function closeNear() {
  adding.value = false
  nearPlaces.value = []
  nearRadius.value = null
  nearPickedId.value = null
}
function onNearResults(result) {
  nearPlaces.value = result.places
  nearRadius.value = result.radius
}
const nearRing = computed(() => (adding.value && nearRadius.value && live.position.value ? { ...live.position.value, radius: nearRadius.value } : null))
const nearFitKey = computed(() => (nearPlaces.value.length ? nearPlaces.value.map((place) => place.id).join(',') + '|' + nearRadius.value : ''))
const NEAR_MAP_PADDING = { top: 100, right: 40, bottom: 230, left: 40 } // 근처 찾기: 아래 검색 카드만큼 비움
const PREVIEW_LEVEL = 3 // 카드를 눌러 볼 때 확대 단계 (겹친 핀이 벌어져 보이는 정도)
const PREVIEW_HOLD_MS = 2300 // 이동·확대 포함, 보여 주는 시간
const PREVIEW_SCROLL_WAIT_MS = 250 // 카드를 넘기다 멈춘 뒤에 움직임
let backTimer = null
let scrollTimer = null
let levelBefore = null // 보여 주기 전 확대 단계
const LIVE_MAP_PADDING = { top: 150, right: 70, bottom: 444, left: 40 }

// 근처 찾기·AI 채팅에서 일정에 넣으면 지금 상황을 다시 받아요
async function onAdded(message) {
  closeNear()
  asking.value = false
  showToast(message)
  await live.refresh()
}
async function onAskAdd(place) {
  await onAdded(place.message || place.name + '을(를) 다음 일정으로 넣었어요')
}

async function finish() {
  if (isFinishing.value) return
  isFinishing.value = true
  try {
    await finishTrip(live.tripId.value)
    router.replace('/retro?id=' + live.tripId.value)
  } catch (e) {
    showToast(e.message)
    isFinishing.value = false
  }
}

// ------------------------------------------------------------ 여행 끝 (11/06)
// 마지막 날의 마지막 칸이 고정 일정(KTX 같은)이고 거기 도착하면 → 끝 시트(총 지출) → "회고 보기"로 여행 끝내기
const endStop = ref(null) // 끝 시트를 띄운 칸 { name, eta }
let isEndShown = false // 닫아도 다시 안 띄움

watch(status, () => {
  if (isEndShown || !status.value || !live.trip.value) return
  const last = status.value.stops[status.value.stops.length - 1]
  if (!last || !last.fixed || (last.reach !== 'here' && last.reach !== 'done')) return
  if (!isLastDayStop(last.id)) return
  isEndShown = true
  endStop.value = { name: last.name, eta: status.value.now }
})

// 이 칸이 여행 마지막 날의 칸인지
function isLastDayStop(stopId) {
  const stops = live.trip.value.stops
  let lastDay = 0
  for (const stop of stops) lastDay = Math.max(lastDay, stop.day || 0)
  const stop = stops.find((item) => item.id === stopId)
  return !!stop && stop.day === lastDay
}

function showDemo() {
  setFakeGps(true)
  restartMoving()
}

// 개발자 모드에서 가짜 GPS를 켜고 끄면 처음부터 다시 받아요
function restartMoving() {
  live.stop()
  isDemo.value = isFakeGps()
  live.start(isDemo.value)
}
</script>

<template>
  <div class="scr scr-Live">
    <div class="app">
      <DevDot @clock="live.refresh()" @gps="restartMoving" />
      <TripEndSheet v-if="endStop && live.trip.value" :trip="live.trip.value" :place-name="endStop.name" :arrived-at="endStop.eta" :is-busy="isFinishing" @finish="finish" @close="endStop = null" />
      <!-- 진행 중인 여행이 없거나 위치를 기다릴 때 -->
      <template v-if="!isReady">
        <header class="topbar">
          <button type="button" class="iconbtn" aria-label="뒤로가기" @click="goBack('/')"><i class="ic ic--l i-back" aria-hidden="true"></i></button>
          <h1 class="topbar__title">여행 중</h1>
          <span style="width: 44px"></span>
        </header>
        <div class="live-msg">
        <PageMessage v-if="live.phase.value === 'loading'" is-loading title="진행 중인 여행을 찾는 중이에요" />
        <PageMessage v-else-if="live.phase.value === 'none'" icon="i-route" title="진행 중인 여행이 없어요" text="여행 상세에서 '여행 시작'을 누르면 지금 위치로 남은 일정을 계산해 드려요" to="/trips" button="내 여행 보기" />
        <section v-else-if="live.phase.value === 'gps-error'" class="page-msg">
          <span class="ico-tile ico-tile--warn" style="width: 56px; height: 56px"><i class="ic ic--xl i-locate" aria-hidden="true"></i></span>
          <h2 class="h-title">지금 위치를 받을 수 없어요</h2>
          <p class="t-cap">{{ live.gpsError.value }}</p>
          <button type="button" class="btn btn--primary btn--lg" @click="live.retryGps()">다시 시도</button>
          <button v-if="devMode.isOn" type="button" class="btn btn--secondary btn--lg" @click="showDemo">시연용 이동으로 보기</button>
        </section>
        <PageMessage v-else is-loading title="지금 위치를 찾는 중이에요" text="위치를 받으면 남은 일정과 도착 예상 시간을 계산해요" />
        </div>
      </template>

      <template v-else>
        <KakaoMap
          v-if="!isMapFailed"
          ref="mapRef"
          :pins="mapPins"
          :me="live.position.value"
          :paths="nextPath"
          :ring="nearRing"
          :fit-key="adding ? nearFitKey : ''"
          :padding="adding && nearPlaces.length ? NEAR_MAP_PADDING : LIVE_MAP_PADDING"
          @pick="onPickPin"
          @touch="onMapTouched"
          @me-point="mePoint = $event"
          @fail="isMapFailed = true"
        />
        <p v-else class="t-cap" style="position: absolute; top: 200px; left: 24px; right: 80px; text-align: center">지도를 불러오지 못했어요</p>

        <!-- 다녀온 곳 정보창 -->
        <div v-if="info" class="payinfo" :style="{ left: Math.max(12, info.x - 30) + 'px', top: info.y - 24 + 'px', transform: 'translateY(-100%)' }" role="dialog" aria-label="다녀온 곳 정보">
          <b>{{ info.name }}</b>
          <span class="t-cap tn">{{ info.time }}</span>
        </div>

        <header class="topbar topbar--float">
          <button type="button" class="iconbtn iconbtn--float" aria-label="뒤로가기" @click="goBack('/')"><i class="ic ic--l i-back" aria-hidden="true"></i></button>
          <span style="flex: 1"></span>
          <NotiBell float />
        </header>

        <!-- 위쪽 알림: 늦으면 접어서 +N분 알약으로 -->
        <template v-if="status.alert && !isAlertMuted && !adding">
          <AppNoti
            v-if="notiOpen"
            :warn="status.alert.level === 'danger' || isLate"
            icon="i-warning"
            :title="status.alert.title"
            :to="alertLink"
            @close="notiOpen = false"
          />
          <button v-else type="button" class="pill-alert live-pill" :aria-label="isLate ? '지연 알림 펼치기' : '알림 펼치기'" @click="notiOpen = true">
            <i class="ic ic--s i-warning" aria-hidden="true"></i><span class="tn">{{ isLate ? '+' + lateMin + '분' : '알림' }}</span><i class="ic ic--s i-down" aria-hidden="true"></i>
          </button>
        </template>

        <!-- 오른쪽 떠 있는 버튼 -->
        <div v-if="fabOpen" class="live-fab-dim" @click="fabOpen = false"></div>
        <div v-if="!adding && !asking" class="live-fab" data-sheet-follow>
          <div v-if="fabOpen" class="live-fab__list" role="menu" aria-label="더 보기">
            <button type="button" role="menuitem" class="live-fab__item" @click="runFab(openLink)"><span class="live-fab__text">링크 추가</span><span class="live-fab__ic live-fab__ic--link"><i class="ic i-link" aria-hidden="true"></i></span></button>
            <button type="button" role="menuitem" class="live-fab__item" @click="runFab(openToday)"><span class="live-fab__text">내 일정 {{ doneCount }}/{{ stops.length }}</span><span class="live-fab__ic live-fab__ic--plan"><i class="ic i-calendar" aria-hidden="true"></i></span></button>
            <button type="button" role="menuitem" class="live-fab__item" @click="runFab(share)"><span class="live-fab__text">일정 공유</span><span class="live-fab__ic live-fab__ic--share"><i class="ic i-share" aria-hidden="true"></i></span></button>
            <button type="button" role="menuitem" class="live-fab__item" @click="runFab(locate)"><span class="live-fab__text">내 위치</span><span class="live-fab__ic live-fab__ic--me"><i class="ic i-locate" aria-hidden="true"></i></span></button>
          </div>
          <button type="button" class="live-fab__main" :class="{ 'is-open': fabOpen }" :aria-label="fabOpen ? '메뉴 닫기' : '메뉴 열기'" :aria-expanded="fabOpen" @click="fabOpen = !fabOpen"><i class="ic ic--l i-plus" aria-hidden="true"></i></button>
        </div>

        <!-- 링크로 장소 추가 -->
        <LinkAddSheet v-if="linkOpen" :trip-id="live.tripId.value" :position="live.position.value" @close="linkOpen = false" @added="onLinkAdded" />

        <!-- 근처 찾기 (⑤) -->
        <NearSearch
          v-if="adding"
          v-model:picked-id="nearPickedId"
          :trip-id="live.tripId.value"
          :position="live.position.value"
          @results="onNearResults"
          @added="onAdded"
          @close="closeNear"
        />

        <template v-else>
          <!-- AI 채팅(④) -->
          <AskPanel v-if="asking" :me="mePoint || { x: 195, y: 300 }" :late="isLate" :trip-id="live.tripId.value" :position="live.position.value" @close="asking = false" @add="onAskAdd" />
          <div v-else class="quick live-quick" data-sheet-follow role="group" aria-label="AI 채팅과 근처 찾기">
            <button type="button" class="chip chip--sm chip--add live-chip" @click="openAsk"><i class="ic ic--s i-sparkle" style="color: #7C3AED" aria-hidden="true"></i>AI에게 묻기</button>
            <button type="button" class="chip chip--sm chip--add live-chip" @click="openNear"><i class="ic ic--s i-search" style="color: var(--coral)" aria-hidden="true"></i>근처 찾기</button>
          </div>

          <!-- 아래 시트: 오늘 일정 카드 -->
          <BottomSheet v-if="!asking" class="sheet--peek live-sheet" :aria-label="doneCount ? '다음 일정' : '오늘 일정'">
            <div class="live-head" data-drag>
              <div style="flex: 1; min-width: 0">
                <p class="t-cap">{{ head.cap }}</p>
                <p class="t-big tn" :class="head.cls" style="margin: 0">
                  {{ head.big }}
                  <span v-if="head.strong" class="t-strong c-navy">{{ head.strong }}</span>
                  <span v-if="head.small" class="t-cap tn">{{ head.small }}</span>
                </p>
              </div>
              <span v-if="fixedBadge" class="badge" :class="fixedBadge.cls"><i class="ic ic--s i-lock" aria-hidden="true"></i>{{ fixedBadge.text }}</span>
            </div>

            <LiveCards :cards="cards" :selected="selected" @pick="pickCard" @seg="segCard = $event" @scrolled="onCardScrolled" />

            <p v-if="status.weather" class="t-cap live-event"><i class="ic ic--s i-info" aria-hidden="true"></i>{{ status.weather.time }}부터 비</p>
          </BottomSheet>

          <div v-if="!asking" class="cta cta--white" style="box-shadow: none">
            <div v-if="isAllDone" class="cta__row">
              <RouterLink :to="'/trips/today?id=' + live.tripId.value + '&back=/live'" class="btn btn--secondary btn--lg">일정 보기</RouterLink>
              <button type="button" class="btn btn--primary btn--lg" :disabled="isFinishing" @click="finish"><i class="ic i-flag" aria-hidden="true"></i>여행 마치기</button>
            </div>
            <div v-else class="cta__row">
              <RouterLink v-if="isLate" to="/live/delay" class="btn btn--secondary btn--lg">대안 보기</RouterLink>
              <RouterLink v-else :to="'/plan/edit?id=' + live.tripId.value" class="btn btn--secondary btn--lg"><i class="ic i-edit" aria-hidden="true"></i>일정 변경</RouterLink>
              <RouterLink to="/live/route" class="btn btn--primary btn--lg"><i class="ic i-route" aria-hidden="true"></i>길 안내</RouterLink>
            </div>
          </div>

          <SegSheet v-if="segCard && segFrom && segTo" :from="segFrom" :to="segTo" :leg="segCard.leg" @close="segCard = null" />
        </template>
      </template>
    </div>
  </div>
</template>

<style>
.scr-Live .live-msg { padding-top: 96px; }
.scr-Live .live-fab { position: absolute; right: 16px; top: 376px; z-index: 6; }
.scr-Live .live-fab-dim { position: absolute; inset: 0; z-index: 5; background: rgba(24, 33, 58, .28); }
.scr-Live .live-fab__main { width: 52px; height: 52px; border: 0; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: #fff; background: var(--navy); box-shadow: 0 6px 16px rgba(24, 33, 58, .3); cursor: pointer; transition: transform .18s, background .18s; }
.scr-Live .live-fab__main.is-open { transform: rotate(45deg); background: var(--ink); }
.scr-Live .live-fab__list { position: absolute; right: 0; bottom: calc(100% + 10px); display: flex; flex-direction: column; align-items: flex-end; gap: 8px; }
.scr-Live .live-fab__item { display: flex; align-items: center; gap: 8px; padding: 0; border: 0; background: none; font: inherit; cursor: pointer; white-space: nowrap; }
.scr-Live .live-fab__text { padding: 7px 12px; border-radius: 999px; font-size: 13px; font-weight: 700; color: var(--ink); background: #fff; box-shadow: 0 2px 8px rgba(24, 33, 58, .18); }
.scr-Live .live-fab__ic { width: 44px; height: 44px; margin-right: 4px; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: #fff; box-shadow: 0 2px 8px rgba(24, 33, 58, .25); }
.scr-Live .live-fab__ic--link { background: linear-gradient(135deg, #F58529, #DD2A7B 55%, #8134AF); }
.scr-Live .live-fab__ic--plan { background: var(--navy); }
.scr-Live .live-fab__ic--share { background: var(--success); }
.scr-Live .live-fab__ic--me { background: #2F7BEA; }
.scr-Live .live-chip { font-weight: 600; }
.scr-Live .live-quick { position: absolute; top: 392px; left: 0; right: 70px; z-index: 2; }
.scr-Live .live-sheet { top: 440px; padding: 8px 0 0; gap: 12px; }
.scr-Live .live-head { display: flex; align-items: flex-end; gap: 12px; padding: 0 16px; }
.scr-Live .live-pill { position: absolute; top: 112px; left: 16px; z-index: 3; }
</style>
