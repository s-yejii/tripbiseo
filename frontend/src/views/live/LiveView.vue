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
import { useRoute, useRouter } from 'vue-router'
import { useLiveTrip } from '@/utils/useLiveTrip'
import { imageFor } from '@/utils/mapView'
import { toMinutes, toTime } from '@/utils/time'
import { showToast } from '@/utils/toast'
import { goBack } from '@/utils/back'
import { rememberAlert, shouldOpenAlert } from '@/utils/alertMemory'
import { finishTrip, getTripPhotos, skipStop } from '@/api/trips'
import { chatKey, clearChat } from '@/utils/askChat'
import { loadPhoto } from '@/utils/placePhoto'
import BottomSheet from '@/components/common/BottomSheet.vue'
import AppNoti from '@/components/common/AppNoti.vue'
import PageMessage from '@/components/common/PageMessage.vue'
import NotiBell from '@/components/member/NotiBell.vue'
import KakaoMap from '@/components/common/KakaoMap.vue'
import LiveCards from '@/components/live/LiveCards.vue'
import DevDot from '@/components/live/DevDot.vue'
import DemoDrive from '@/components/live/DemoDrive.vue'
import TripEndSheet from '@/components/live/TripEndSheet.vue'
import LostFixedCard from '@/components/live/LostFixedCard.vue'
import { closeLiveNotice, liveBanner, removeLiveNotices, showLiveNotice } from '@/utils/liveBanner'
import { daysBetween, todayText } from '@/utils/dates'
import { devMode, isFakeGps, setFakeGps } from '@/utils/devMode'
import { isBoardingPlace, missTrainTitle } from '@/utils/departure'
import SegSheet from '@/components/live/SegSheet.vue'
import AskPanel from '@/components/live/AskPanel.vue'
import InsertSlotSheet from '@/components/live/InsertSlotSheet.vue'
import NearSearch from '@/components/after/NearSearch.vue'
import SimilarSearch from '@/components/live/SimilarSearch.vue'
import LinkAddSheet from '@/components/live/LinkAddSheet.vue'
import { useReplanStore } from '@/stores/replan'
import { demoRoute } from '@/utils/demoRoute'

const MUTE_AGAIN_MIN = 10 // "그냥 갈게요" 뒤 이만큼 더 늦어지면 다시 알림
const LEG_MODE = { walk: 'walk', transit: 'subway', car: 'taxi' } // 서버 이동 수단 → 카드 칩 아이콘
const MODE_LABEL = { walk: '도보', transit: '대중교통', car: '택시' }

const router = useRouter()
const replan = useReplanStore()
const live = useLiveTrip()
const isDemo = ref(isFakeGps() && !devMode.isFlow) // 11/08: 시연 계정은 DemoDrive가 위치를 옮기니 예전 가짜 GPS 걷기는 안 씀 · 가짜 GPS: 개발자 모드(시연 계정)에서만 켤 수 있음 (11/06: 주소의 ?demo=1은 없앰)

// 시연 계정 "일정대로 흐르기"(DemoDrive가 위치를 옮김)면 진짜 GPS를 켜지 않아요
//   (11/09: 이 PC의 진짜 위치 = 학원(라크라센터)이 먼저 잡혀서, 대안 적용 뒤 돌아오면 점이 학원으로 갔다가 순간이동하던 문제)
const isNoRealGps = () => isFakeGps() && devMode.isFlow
onMounted(() => live.start(isDemo.value, isNoRealGps()))
onUnmounted(() => live.stop())

const mapRef = ref(null)
const status = computed(() => live.status.value)
const isReady = computed(() => live.phase.value === 'ready' && status.value !== null)

const selected = ref(null) // 고른 카드·핀 (칸 id)
const cardFocus = ref(null) // 지도 핀을 눌렀을 때 { no: 칸 id, at: 누른 시각 } → 아래 카드 줄이 그 카드를 가운데로 (11/12)
const previewId = ref(null) // 지도가 잠깐 보여 주는 칸 (이름표)
const info = ref(null) // 정보창을 연 다녀온 곳 { id, x, y }
const segCard = ref(null) // 이동 칩을 누른 카드
// 위쪽 배너는 한 장만 (11/08 주형: 알림이 겹쳐서 뭐가 뭔지 모르겠음). 저절로 사라지지 않고, 발표자가 위로 밀거나 눌러야 닫힘
//   (11/08 주형: "알림은 내가 눌러서 끄기 전에는 없애지 마") · 새 알림이 오면 이전 것과 바꿔치기
// 자동 일정 조정 안내 (서버가 늦게 떠난 걸 보고 뒤 일정을 밀거나 줄였을 때): 위쪽 배너 한 장 (닫을 때까지)
watch(() => (status.value && status.value.adjust ? status.value.adjust.key : null), (key) => {
  if (!key) return
  const adjust = status.value.adjust
  showLiveNotice({ key: adjust.key, title: adjust.title, body: adjust.text, warn: false, icon: 'i-route', at: status.value.now })
})
const asking = ref(false) // AI 채팅
const adding = ref(false) // 근처 찾기
const nearPlaces = ref([]) // 근처 찾기 결과
const askFound = ref(null) // AI에게 묻기로 찾은 결과 { places, band, ... } (없으면 null)
const askPickedId = ref(null) // 지도에서 누른 AI 결과 장소 id
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
// 지금 있는 곳·남은 일정의 장소 id: 검색 결과 카드에서 이미 일정에 있는 곳은 [일정에 넣기] 대신 '일정에 있어요' (11/13)
const plannedPlaceIds = computed(() => {
  const ids = []
  for (const stop of stops.value) {
    if (stop.reach !== 'done' && stop.placeId) ids.push(stop.placeId)
  }
  return ids
})

// 이동 중(장소를 떠나 다음 장소로 가는 길): 지도가 내 위치를 크게 확대해 따라가고, 내 위치 점에 진행 방향 화살표 (11/09 시연 피드백)
// 시연 가짜 GPS가 움직이는 중(demoRoute)이면 서버가 '떠났다'고 답하기 전이라도 바로 따라가요 (출발 직후 시점이 안 따라가던 문제)
const isMoving = computed(() => !!live.position.value && (!!demoRoute.value || (!currentStop.value && !!nextStop.value)) && !adding.value && !asking.value)
// 지도 시점: 내 위치가 있으면 머무는 중에도 늘 내 위치를 가운데에 고정 (11/09 상재). 근처 찾기·AI 묻기 중에만 풀어요
// 시연 계정(개발자 모드)에서만 (11/09 상재: 중간발표 시연용). 일반 계정은 원래 지도 동작 그대로
const isFollowing = computed(() => devMode.isOn && !!live.position.value && !adding.value && !asking.value)
const heading = ref(null) // 진행 방향(도, 북=0)
const HEADING_MIN_MOVE = 0.00004 // 이만큼(약 4m)보다 적게 움직이면 방향을 안 바꿔요 (GPS 떨림에 화살표가 흔들리지 않게)
let lastPoint = null
watch(() => live.position.value, (point) => {
  if (!point) return
  if (lastPoint && Math.abs(point.lat - lastPoint.lat) + Math.abs(point.lng - lastPoint.lng) < HEADING_MIN_MOVE) return
  if (lastPoint) heading.value = toBearing(lastPoint, point)
  lastPoint = { lat: point.lat, lng: point.lng }
})
// 두 점 사이 방향(도). 북쪽이 0, 동쪽이 90
function toBearing(from, to) {
  const rad = Math.PI / 180
  const y = Math.sin((to.lng - from.lng) * rad) * Math.cos(to.lat * rad)
  const x = Math.cos(from.lat * rad) * Math.sin(to.lat * rad) - Math.sin(from.lat * rad) * Math.cos(to.lat * rad) * Math.cos((to.lng - from.lng) * rad)
  return (Math.atan2(y, x) / rad + 360) % 360
}
const doneCount = computed(() => stops.value.filter((stop) => stop.reach === 'done').length)
const isAllDone = computed(() => isReady.value && !currentStop.value && remainingStops.value.length === 0)
// 여러 날 여행에서 오늘이 마지막 날이 아니면, 그날 마지막 장소(숙소)에 오면 "여행 마치기" 대신 다음 날 일정 보기 (11/08 주형: 1일차 숙소에서 여행이 끝나 버리던 문제)
const nextDayNo = computed(() => {
  const trip = live.trip.value
  const today = todayText()
  if (!trip || !trip.endDate || today >= trip.endDate) return null
  return daysBetween(trip.startDate, today) + 2
})
const fixed = computed(() => (isReady.value ? status.value.fixed : null))
const lateMin = computed(() => (fixed.value && fixed.value.slackMin < 0 ? -fixed.value.slackMin : 0))
const isLate = computed(() => isReady.value && status.value.replan && lateMin.value > 0)

// "그냥 갈게요"를 눌렀고 그때보다 10분 넘게 더 늦지 않았으면 지연 알림을 접어 둠
// 11/09: 대안을 고른 뒤 여유가 5분 이하로 남은 것(늦기 전)도 같이 접어 둠 → 같은 카드가 다시 [다음 장소로]를 가리지 않게
const isAlertMuted = computed(() => {
  if (!isReady.value || !fixed.value) return false
  return replan.isMutedFor(live.tripId.value, fixed.value.name, lateMin.value)
})
// [알림 끄기]로 이 예약의 늦음 배너를 껐는지 (놓침 카드는 그래도 뜸, 11/12)
const isLateOff = computed(() => isReady.value && !!fixed.value && replan.isLateOffFor(live.tripId.value, fixed.value.name))
const alertLink = computed(() => (isReady.value && status.value.replan ? '/live/delay' : ''))
const currentAlert = computed(() => (isReady.value ? status.value.alert : null))
// 11/12 ④ 지연 알림 정리 (고정 일정 기준만)
//   늦음(대안으로 만회 가능, late-·late-soon-) → 위 배너 + [10분 뒤 다시] [알림 끄기], 배너를 누르면 지연 화면(대안)
//   어떤 방법으로도 못 맞춤(lost-) → 가운데 놓침 카드 (그대로 가기 / 빼고 다음으로 / 식당·카페면 비슷한 가게)
//   [나중에]·닫기를 누른 놓침 카드는 알약으로만
const laterKey = ref('')
const pageRoute = useRoute()
const SNOOZE_MS = 10 * 60 * 1000 // [10분 뒤 다시]
let snoozeTimer = null
const similarStop = ref(null) // 비슷한 근처 가게를 찾는 중인 놓친 칸 (지도에 핀으로)
// 시연 계정(11/09 주형): KTX는 "몇 분 늦어요" 대신 놓치는지로 말함. 다른 계정은 서버 문구 그대로
function lateTitle(alert) {
  const fixedStatus = status.value && status.value.fixed
  if (devMode.isOn && fixedStatus && fixedStatus.slackMin < 0 && isBoardingPlace(fixedStatus.name)) return missTrainTitle(fixedStatus.name, fixedStatus.eta, fixedStatus.time)
  return alert.title
}
// 놓침 카드 (④ 11/12): 서버 lost- 알림(식당·카페 예약에 가장 빠른 방법으로도 못 닿음), 또는 고른 예약 카드의 "못 가게 됐어요"
const manualLostId = ref(null) // "못 가게 됐어요"로 연 칸 id
const isSkipping = ref(false)
const isLostAlert = computed(() => !!currentAlert.value && /^lost-/.test(currentAlert.value.key || ''))
const lostStop = computed(() => {
  const id = manualLostId.value || (isLostAlert.value ? Number(currentAlert.value.key.slice('lost-'.length)) : null)
  return id ? stops.value.find((stop) => stop.id === id) || null : null
})
const isLostOpen = computed(() => {
  if (!lostStop.value || pageRoute.path !== '/live' || adding.value) return false
  return manualLostId.value !== null || (!isAlertMuted.value && laterKey.value !== currentAlert.value.key)
})
const lostText = computed(() => (manualLostId.value ? '예약한 곳에 못 가게 됐나요?' : currentAlert.value.text))
const isCardOpen = isLostOpen // 가운데 카드는 놓침 카드 하나뿐 (11/12)
// 고른 카드가 아직 안 간 식당·카페 예약이면 "못 가게 됐어요" 버튼
const selectedMealFixed = computed(() => stops.value.find((stop) => stop.id === selected.value && stop.fixed && stop.meal && stop.reach !== 'done' && stop.reach !== 'here') || null)

// 비슷한 근처 가게: 지도 위에 핀으로 (근처 찾기와 같은 자리, 지금 위치 기준)
function openSimilar() {
  const stop = lostStop.value
  if (!live.position.value) return showToast('아직 지금 위치를 받지 못했어요')
  closeLost()
  asking.value = false
  info.value = null
  similarStop.value = { id: stop.id, placeId: stop.placeId, name: stop.name }
  adding.value = true
}

// 빼고 다음 장소로: 그 칸을 빼고 남은 일정을 당겨 다시 계산
async function skipLost() {
  const position = live.position.value
  if (!position) return showToast('아직 지금 위치를 받지 못했어요')
  if (isSkipping.value) return
  isSkipping.value = true
  try {
    const res = await skipStop(live.tripId.value, lostStop.value.id, position.lat, position.lng)
    closeLost()
    showToast(res.message)
    await live.refresh()
  } catch (e) {
    showToast(e.message)
  }
  isSkipping.value = false
}

// 그래도 갈게요: 그대로 가기로 함 → 이 예약의 지연 알림은 더 안 띄움 (11/12). 직접 연 거면 닫기만
function keepLost() {
  const stop = lostStop.value
  const isManual = manualLostId.value !== null
  closeLost()
  if (!isManual && stop) replan.muteFixed(live.tripId.value, stop.name, lateMin.value, true)
}

function closeLost() {
  if (isLostAlert.value) laterKey.value = currentAlert.value.key
  manualLostId.value = null
}

watch(isCardOpen, (isOpen) => (liveBanner.isCardOpen = isOpen), { immediate: true }) // 카드가 떠 있는 동안 시연 [+분] 버튼을 막음
onUnmounted(() => {
  liveBanner.isCardOpen = false
  clearTimeout(snoozeTimer)
})

// 새 알림이 오면(또는 화면에 들어오면) 펼칠지 정함: 같은 종류는 10분에 한 번만 펼치고 나머지는 알약으로 접어 둠
watch(() => (currentAlert.value ? currentAlert.value.key || currentAlert.value.title : null), () => {
  const alert = currentAlert.value
  if (!alert) return
  if (isAlertMuted.value && isAboutFixed(alert.key)) return // 그대로 가기로 한 예약은 더 안 알림 (11/12)
  if (isLateOff.value && isLateKey(alert.key) && isAboutFixed(alert.key)) return // [알림 끄기]를 누른 예약의 늦음 배너
  if (/^(late|lost)-/.test(alert.key || '')) {
    // 같은 예약의 옛 배너(여유 N분뿐·계획 시간 지남·덜 늦었을 때 늦음)는 뺌: 서로 다른 말을 하는 알림이 같이 보이던 문제 (11/12)
    removeLiveNotices((notice) => notice.key !== alert.key && isAboutFixed(notice.key))
  }
  if (/^lost-/.test(alert.key || '')) return // 놓침은 가운데 카드로
  if (!shouldOpenAlert(alert)) return
  rememberAlert(alert)
  queueAlert(alert)
}, { immediate: true })
// 이 알림 key가 지금 고정 일정(예약)에 대한 지연 알림인지: tight-·missed-·late-soon-·late-·lost- 뒤 번호가 그 칸 id (11/12)
function isAboutFixed(key) {
  if (!fixed.value) return false
  const match = (key || '').match(/^(?:tight|missed|late-soon|late|lost)-(\d+)/)
  return !!match && Number(match[1]) === fixed.value.id
}
// 늦음 배너 알림인지 (빠듯 tight-, 늦음 late-·late-soon-). 놓침 lost-는 아님
function isLateKey(key) {
  return /^(?:tight|late)-/.test(key || '')
}
// 알약 누르기: 놓침이면 가운데 카드를 다시, 그 밖(늦음 포함)은 위 알림 줄에
function openAlert(alert) {
  if (/^lost-/.test(alert.key || '')) {
    laterKey.value = ''
    return
  }
  queueAlert(alert)
}
// 서버 알림을 위쪽 알림 줄에 넣기 (닫을 때까지 남고, 앞 알림을 닫으면 이어서 보임). 늦음은 누르면 지연 화면(대안) + 버튼 두 개
function queueAlert(alert) {
  const isLateAlert = /^late-/.test(alert.key || '')
  showLiveNotice({ key: alert.key || alert.title, title: isLateAlert ? lateTitle(alert) : alert.title, body: alert.text || '', warn: alert.level === 'danger', icon: 'i-warning',
    to: isLateAlert ? '/live/delay' : alertLink.value, at: status.value.now, isLate: isLateAlert })
}
// [10분 뒤 다시]: 배너를 닫고 10분 뒤에도 늦음이면 다시 띄움
function snoozeLate() {
  closeLiveNotice()
  clearTimeout(snoozeTimer)
  snoozeTimer = setTimeout(() => {
    const alert = currentAlert.value
    if (!alert || !/^late-/.test(alert.key || '') || isAlertMuted.value || isLateOff.value) return
    rememberAlert(alert)
    queueAlert(alert)
  }, SNOOZE_MS)
}
// [알림 끄기]: 이 예약의 늦음 배너는 더 안 띄움. 어떤 방법으로도 못 맞추게 되면 놓침 카드는 뜸 (11/12)
function turnOffLateAlert() {
  closeLiveNotice()
  clearTimeout(snoozeTimer)
  if (fixed.value) replan.turnOffLate(live.tripId.value, fixed.value.name)
}

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
    const isFit = !(adding.value && nearPlaces.value.length) && !askPlaces.value.length // 근처 찾기·AI 결과가 있으면 결과·내 위치만 보이게 맞춤
    // 다녀온 곳·지금 있는 곳은 점선 동선에서 빼요 (지금 위치 → 다음 장소는 nextPath 진짜 길로)
    const isRoute = stop.reach !== 'done' && stop.reach !== 'here'
    const no = shownNo(stop) // 넣을 순서를 고르는 중이면 넣은 뒤 번호
    pins.push({ key: stop.id, id: stop.id, tag: previewId.value === stop.id ? stop.name : '', lat: stop.lat, lng: stop.lng, text: no, icon: pinIcon(stop), cls: pinClass(stop), label: no + ' ' + stop.name, fit: isFit, route: isRoute })
  }
  if (slotPlace.value) {
    // 넣을 장소: 일정 핀처럼 번호로 (코랄). 순서를 옮기면 번호도 같이 바뀜
    const no = slotFirstNo.value + slotIndex.value
    pins.push({ key: 'slot-' + slotPlace.value.id, lat: slotPlace.value.lat, lng: slotPlace.value.lng, text: no, cls: ' pin--new pin--sel', label: no + ' ' + slotPlace.value.name + ' (새로)', route: false })
    return pins
  }
  for (const place of nearPlaces.value) {
    if (slotPlace.value && place.id !== slotPlace.value.id) continue // 자리 고르기 중에는 넣을 장소 핀만
    const cls = ' pin--res' + (place.id === nearPickedId.value ? ' pin--sel' : '')
    pins.push({ key: 'near-' + place.id, nearId: place.id, lat: place.lat, lng: place.lng, icon: nearIcon(place.category), cls, label: place.name + ', ' + distText(place.distance), route: false })
  }
  for (const place of askPlaces.value) {
    if (slotPlace.value && place.id !== slotPlace.value.id) continue
    const cls = ' pin--res' + (place.id === askPickedId.value ? ' pin--sel' : '')
    const tag = place.detourMinutes != null ? '+' + place.detourMinutes + '분' : '' // 가는 길에서 몇 분 더 (번호는 일정 번호와 헷갈려서 아이콘으로)
    pins.push({ key: 'ask-' + place.id, askId: place.id, lat: place.lat, lng: place.lng, icon: nearIcon(place.category), tag, cls, label: place.name + (tag ? ', 가는 길 ' + tag : ''), route: false })
  }
  return pins
})

// ------------------------------------------------------------ AI에게 묻기 결과 (지도에 그리기)
const askPlaces = computed(() => (asking.value && askFound.value ? askFound.value.places : []))
// 가는 길 띠: 서버가 준 GeoJSON(경도, 위도) → KakaoMap band 모양 [[[위도, 경도], ...]] (바깥 테두리만)
const askBand = computed(() => (askPlaces.value.length && askFound.value.band ? toBandRings(askFound.value.band) : []))
// 가는 길 위 가장 가까운 점 → 가게 점선
const askLinks = computed(() => {
  const links = []
  for (const place of askPlaces.value) {
    if (place.joinLat == null || place.joinLng == null) continue
    links.push({ from: [place.joinLat, place.joinLng], to: [place.lat, place.lng] })
  }
  return links
})
const askFitKey = computed(() => (askPlaces.value.length ? 'ask|' + askPlaces.value.map((place) => place.id).join(',') : ''))
const ASK_MAP_PADDING = { top: 100, right: 40, bottom: 440, left: 40 } // AI 채팅: 아래 채팅 패널(대화 + 카드 줄)만큼 비움

function toBandRings(geojson) {
  let shape = null
  try {
    shape = JSON.parse(geojson)
  } catch (e) {
    return []
  }
  const polygons = shape.type === 'MultiPolygon' ? shape.coordinates : shape.type === 'Polygon' ? [shape.coordinates] : []
  const rings = []
  for (const polygon of polygons) {
    if (!polygon[0]) continue
    rings.push(polygon[0].map((point) => [point[1], point[0]])) // [경도, 위도] → [위도, 경도]
  }
  return rings
}
// 핀을 고르면 그 장소 카드의 진짜 사진을 받아 둠 (11/13). 길은 넣을 자리를 고를 때 전체 경로로 보여 줘요
const pickedPlace = computed(() => {
  if (askPickedId.value) return askPlaces.value.find((place) => place.id === askPickedId.value) || null
  if (nearPickedId.value) return nearPlaces.value.find((place) => place.id === nearPickedId.value) || null
  return null
})
watch(pickedPlace, (place) => {
  if (place) loadPhoto(place.id)
})

function onAskFound(found) {
  askFound.value = found
  askPickedId.value = null
}
// AI 채팅을 닫으면 지도에 그린 결과도 지움
watch(asking, (isOpen) => {
  if (isOpen) return
  askFound.value = null
  askPickedId.value = null
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
    coords: demoRoute.value || next.coords, // 시연 가짜 GPS가 움직이는 중이면 그 길 그대로 (점과 선이 같은 길, 11/09 ①)
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
  if (pin.askId) {
    askPickedId.value = pin.askId
    return
  }
  if (pin.nearId) {
    nearPickedId.value = pin.nearId
    return
  }
  const stop = stops.value.find((item) => item.id === pin.id)
  if (!stop) return
  cardFocus.value = { no: stop.id, at: Date.now() } // 아래 카드 줄도 이 장소 카드를 가운데로 (11/12)
  pickStop(stop, pin)
}

// ------------------------------------------------------------ 시트 위 큰 글자
const head = computed(() => {
  if (!isReady.value) return null
  const now = toMinutes(status.value.now)
  const current = currentStop.value
  if (current) {
    const arrived = current.arrivedAt ? toMinutes(toClock(current.arrivedAt)) : toMinutes(current.time)
    const isFromPlan = current.fixed || /숙소|호텔|숙박|모텔|게스트하우스/.test(current.category || '') // 예약·숙소는 일찍 와도 계획 시각부터 (서버 LiveStopFinder와 같음)
    const start = isFromPlan ? Math.max(arrived, toMinutes(current.time)) : arrived
    // 시연 계정 서울역(KTX) 칸은 열차 출발 시각을 출발로 보여 줌 (11/09 상재: 19:30 → 19:00)
    const isTrain = devMode.isOn && current.fixed && isBoardingPlace(current.name)
    const leaveAt = isTrain ? toMinutes(current.time) : start + (current.stay || 0)
    const left = leaveAt - now
    if (left >= 0) {
      return { cap: current.name + ' 출발', big: toTime(leaveAt), strong: left + '분 남음', cls: '' }
    }
    const next = nextStop.value
    if (!next || !next.fixed) { // 다음이 예약이 아니면 더 있어도 경고하지 않음 (뒤 일정이 밀릴 뿐, 11/08 주형)
      return { cap: current.name + '에서', big: '+' + -left + '분', small: '계획보다 더 머무는 중', cls: '' }
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

// ------------------------------------------------------------ 장소 사진 (11/09 상재 요청)
// 여행 상세·장소 상세와 같은 사진 (POST /places/photos/trip, 칸 이름 → 사진 주소). 여행마다 한 번만 받아 두고 다시 씀
const photos = ref({})
let photosTripId = null
watch(() => live.trip.value, async (trip) => {
  if (!trip || !trip.stops || trip.id === photosTripId) return
  photosTripId = trip.id
  photos.value = await getTripPhotos(trip.stops).catch(() => ({}))
}, { immediate: true })
// 그 장소 사진, 없으면 분류 기본 그림
const DEMO_STATION_PHOTO = '/img/demo/seoul-station.webp' // 시연 계정 서울역 카드 사진 (11/09 상재: 국밥 기본 그림 대신)
function photoOf(stop) {
  if (devMode.isOn && stop.name === '서울역' && !photos.value[stop.name]) return DEMO_STATION_PHOTO
  return photos.value[stop.name] || imageFor(stop.category)
}

// ------------------------------------------------------------ 카드
const cards = computed(() => stops.value.map((stop) => {
  const card = { no: stop.id, stopId: stop.id, img: photoOf(stop), title: stop.fixed ? stop.name : stop.no + ' ' + stop.name, fixed: stop.fixed, leg: null, legState: '', state: '', sub: '', subCls: '' }
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

// 그 곳으로 부드럽게 가서 이름표와 함께 보여 줘요. 저절로 돌아오지 않음 (11/08: 보다가 내 위치로 튀어서 뺌 → "내 위치" 버튼으로)
function previewStop(stop) {
  if (!mapRef.value || stop.lat == null || adding.value) return
  previewId.value = stop.id
  mapRef.value.flyTo(stop.lat, stop.lng, Math.min(mapRef.value.getLevel(), PREVIEW_LEVEL))
}
// 지도를 만지면 이름표를 지움
function onMapTouched() {
  previewId.value = null
}
onUnmounted(() => {
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
  return { name: before.name, img: photoOf(before), lat: before.lat, lng: before.lng }
})
const segTo = computed(() => {
  if (!segCard.value) return null
  const stop = stops.value.find((item) => item.id === segCard.value.stopId)
  return { name: stop.name, img: photoOf(stop), lat: stop.lat, lng: stop.lng }
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
  similarStop.value = null
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
const PREVIEW_SCROLL_WAIT_MS = 250 // 카드를 넘기다 멈춘 뒤에 움직임
let scrollTimer = null
const LIVE_MAP_PADDING = { top: 150, right: 70, bottom: 444, left: 40 }
const mapPadding = computed(() => {
  if (adding.value && nearPlaces.value.length) return NEAR_MAP_PADDING
  if (askPlaces.value.length) return ASK_MAP_PADDING
  return LIVE_MAP_PADDING
})

// 근처 찾기·AI 채팅에서 일정에 넣으면 지금 상황을 다시 받아요
async function onAdded(message) {
  closeNear()
  asking.value = false
  showToast(message)
  await live.refresh()
}
// 일정에 넣기 → 넣을 자리 고르기 (11/13). 시트가 고른 자리로 계산한 전체 경로를 지도에 그리고 화면을 맞춤
const slotPlace = ref(null) // 넣을 장소 (시트가 열려 있으면)
const slotPath = ref([]) // 고른 자리로 계산한 전체 경로선
const slotIndex = ref(0) // 새 장소 자리 (0이면 남은 첫 칸 앞)
const slotFirstNo = computed(() => (remainingStops.value.length ? remainingStops.value[0].no : stops.value.length + 1)) // 남은 첫 칸 번호
// 지도 핀 번호: 넣을 순서를 고르는 중이면 새 장소 자리부터 뒤 칸은 하나씩 밀림
function shownNo(stop) {
  if (!slotPlace.value) return stop.no
  const index = remainingStops.value.findIndex((item) => item.id === stop.id)
  return index >= 0 && index >= slotIndex.value ? stop.no + 1 : stop.no
}
const SLOT_MAP_PADDING = { top: 110, right: 56, bottom: 440, left: 56 } // 아래 자리 고르기 시트만큼 비움
function openSlotSheet(place) {
  slotPlace.value = place
  slotPath.value = []
  slotIndex.value = 0
}
function closeSlotSheet() {
  slotPlace.value = null
  slotPath.value = []
}
function onSlotRoute(coords) {
  slotPath.value = coords
  if (coords.length > 1 && mapRef.value) mapRef.value.fitPoints(coords, SLOT_MAP_PADDING)
}
async function onSlotAdded(message) {
  closeSlotSheet()
  await onAdded(message)
}

async function onAskAdd(place) {
  await onAdded(place.message || place.name + '을(를) 다음 일정으로 넣었어요')
}

async function finish() {
  if (isFinishing.value) return
  isFinishing.value = true
  try {
    await finishTrip(live.tripId.value)
    clearChat(chatKey(live.trip.value)) // 끝난 여행의 AI 대화는 이 브라우저에서 지움
    router.replace('/retro?id=' + live.tripId.value)
  } catch (e) {
    showToast(e.message)
    isFinishing.value = false
  }
}

// ------------------------------------------------------------ 여행 끝 (11/06)
// 마지막 날의 마지막 칸에 도착하면 → 끝 시트(총 지출) → "회고 보기"로 여행 끝내기
// 11/12 ④: 고정 일정이 아니어도 띄움 (마지막이 카페처럼 느슨한 칸이면 여행이 안 끝나던 문제). 닫았거나 일찍 끝낼 때는 카드 줄 끝 [여행 끝내기]
const endStop = ref(null) // 끝 시트를 띄운 칸 { name, eta }
let isEndShown = false // 닫아도 다시 안 띄움
const isAskingEnd = ref(false) // [여행 끝내기] 카드를 눌러 한 번 더 묻는 중

// [여행 끝내기] 확인: 여행을 끝내고 회고로
function confirmEnd() {
  isAskingEnd.value = false
  finish()
}

watch(status, () => {
  if (isEndShown || !status.value || !live.trip.value) return
  const last = status.value.stops[status.value.stops.length - 1]
  if (!last || (last.reach !== 'here' && last.reach !== 'done')) return
  if (!isLastDayStop(last.id)) return
  isEndShown = true
  endStop.value = { name: last.name, eta: status.value.now, time: last.time }
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
  isDemo.value = isFakeGps() && !devMode.isFlow
  live.start(isDemo.value, isNoRealGps())
}
</script>

<template>
  <div class="scr scr-Live">
    <div class="app">
      <DevDot @clock="live.refresh()" @gps="restartMoving" />
      <DemoDrive :live="live" /><!-- 시연 계정만: 작은 "다음 장소로" 버튼 (GPS 이동 + 시계 배속만 자동, 11/08) -->
      <TripEndSheet v-if="endStop && live.trip.value" :trip="live.trip.value" :place-name="endStop.name" :arrived-at="endStop.eta" :planned-at="endStop.time" :is-busy="isFinishing" @finish="finish" @close="endStop = null" />
      <!-- 여행 끝내기 확인 (11/12): 여행을 마친 트리피가 맞아 주는 창. 위 그림 → 글 → 버튼 -->
      <template v-if="isAskingEnd">
        <div class="dim" style="z-index: 8" @click="isAskingEnd = false"></div>
        <section class="modal end-modal" role="alertdialog" aria-modal="true" aria-labelledby="end-modal-title" style="z-index: 9">
          <div class="end-modal__art" aria-hidden="true">
            <span class="end-modal__glow"></span>
            <img src="/img/trippie-flag.webp" alt="" class="end-modal__img" />
          </div>
          <div class="end-modal__text">
            <span class="end-modal__eyebrow">여행 수고했어요!</span>
            <h2 id="end-modal-title" class="h-title">여기서 여행을 마무리할까요?</h2>
            <p class="end-modal__desc">다녀온 길과 남긴 기록을<br />회고에서 돌아볼 수 있어요</p>
          </div>
          <div class="modal__btns">
            <button type="button" class="btn btn--secondary btn--lg" @click="isAskingEnd = false">계속 여행</button>
            <button type="button" class="btn btn--primary btn--lg" :disabled="isFinishing" @click="confirmEnd"><i class="ic i-flag" aria-hidden="true"></i>마무리하기</button>
          </div>
        </section>
      </template>
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
          :follow="isFollowing"
          :smooth="devMode.isOn"
          :heading="devMode.isOn && isMoving ? heading : null"
          :paths="slotPlace ? [] : nextPath"
          :ring="nearRing"
          :band="askBand"
          :links="askLinks"
          :preview="slotPath"
          :fit-key="adding ? nearFitKey : askFitKey"
          :padding="mapPadding"
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

        <!-- 중요한 알림: 가운데 카드 (이게 떠 있으면 위쪽 배너는 안 띄움) -->
        <!-- 예약 놓침: 가운데 카드 (11/12) -->
        <LostFixedCard v-if="isLostOpen" :name="lostStop.name" :text="lostText" :can-find-similar="!!lostStop.placeId && !!lostStop.meal" :is-skipping="isSkipping" @similar="openSimilar" @skip="skipLost" @keep="keepLost" />
        <!-- 위쪽 알림 줄: 한 장씩, 닫아야(위로 밀기) 다음 알림 · 저절로 안 사라짐 (11/08 주형) -->
        <AppNoti
          v-if="!isCardOpen && !isAskingEnd && liveBanner.queue.length && !adding"
          :key="liveBanner.queue[0].title + liveBanner.queue[0].body"
          :title="liveBanner.queue[0].title"
          :body="liveBanner.queue[0].body + (liveBanner.queue.length > 1 ? ' · 알림 ' + (liveBanner.queue.length - 1) + '개 더' : '')"
          :icon="liveBanner.queue[0].icon || 'i-route'"
          :warn="liveBanner.queue[0].warn"
          :to="liveBanner.queue[0].to || ''"
          @close="closeLiveNotice"
          @open="closeLiveNotice"
        >
          <template v-if="liveBanner.queue[0].isLate">
            <button type="button" class="btn btn--secondary btn--sm" @click.stop="snoozeLate">10분 뒤 다시</button>
            <button type="button" class="btn btn--primary btn--sm" @click.stop="turnOffLateAlert">알림 끄기</button>
          </template>
        </AppNoti>
        <!-- PC 마우스로도 한 번에 닫게 작은 닫기 버튼 (위로 밀기도 그대로) -->
        <span v-if="!isCardOpen && !isAskingEnd && liveBanner.queue.length && !adding" class="live-noti-at">{{ liveBanner.queue[0].at || '' }}</span>
        <button v-if="!isCardOpen && !isAskingEnd && liveBanner.queue.length && !adding" type="button" class="live-noti-x" aria-label="알림 닫기" @click="closeLiveNotice"><i class="ic ic--s i-close" aria-hidden="true"></i></button>
        <!-- 다 닫으면 알약: 누르면 지금 알림을 다시 위에 -->
        <template v-else-if="status.alert && !isAlertMuted && !(isLateOff && isLateKey(status.alert.key)) && !adding">
          <button type="button" class="pill-alert live-pill" :aria-label="isLate ? '지연 알림 펼치기' : '알림 펼치기'" @click="openAlert(status.alert)">
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
          </div>
          <!-- 내 위치: + 위에 떠 있다가 메뉴를 열면 아래로 내려가며 사라짐 (메뉴가 + 바로 위에 뜨게) -->
          <button type="button" class="live-fab__me" :class="{ 'is-hidden': fabOpen }" aria-label="내 위치로" :aria-hidden="fabOpen" :tabindex="fabOpen ? -1 : 0" @click="locate"><i class="ic i-locate" aria-hidden="true"></i></button>
          <button type="button" class="live-fab__main" :class="{ 'is-open': fabOpen }" :aria-label="fabOpen ? '메뉴 닫기' : '메뉴 열기'" :aria-expanded="fabOpen" @click="fabOpen = !fabOpen"><i class="ic i-plus" aria-hidden="true"></i></button>
        </div>

        <!-- 링크로 장소 추가 -->
        <LinkAddSheet v-if="linkOpen" :trip-id="live.tripId.value" :position="live.position.value" @close="linkOpen = false" @added="onLinkAdded" />

        <!-- 근처 찾기 (⑤) -->
        <!-- 일정에 넣기 → 넣을 자리 고르기 (④ 11/13): 패널 자리에 띄우고, 패널은 숨기기만 (닫으면 대화·검색 결과 그대로) -->
        <InsertSlotSheet
          v-if="slotPlace"
          :trip-id="live.tripId.value"
          :position="live.position.value"
          :place="slotPlace"
          :stops="remainingStops"
          :from-name="currentStop ? currentStop.name : '지금 위치'"
          :first-no="slotFirstNo"
          @route="onSlotRoute"
          @slot="slotIndex = $event"
          @added="onSlotAdded"
          @close="closeSlotSheet"
        />
        <!-- 놓친 예약과 비슷한 근처 가게 (④ 11/12): 근처 찾기와 같은 자리·같은 지도 핀 -->
        <SimilarSearch
          v-if="adding && similarStop"
          v-model:picked-id="nearPickedId"
          :trip-id="live.tripId.value"
          :position="live.position.value"
          :stop="similarStop"
          @results="onNearResults"
          @added="onAdded"
          @close="closeNear"
        />
        <NearSearch
          v-else-if="adding"
          v-show="!slotPlace"
          v-model:picked-id="nearPickedId"
          :planned="plannedPlaceIds"
          :trip-id="live.tripId.value"
          :position="live.position.value"
          @choose="openSlotSheet"
          @results="onNearResults"
          @added="onAdded"
          @close="closeNear"
        />

        <template v-else>
          <!-- AI 채팅(④) -->
          <!-- 자리 고르기 중에는 숨기기만 (AskPanel은 뿌리 요소가 둘이라 v-show를 감싸는 div에) -->
          <div v-if="asking" v-show="!slotPlace">
            <AskPanel :me="mePoint || { x: 195, y: 300 }" :late="isLate" :trip-id="live.tripId.value" :position="live.position.value" @close="asking = false" @add="onAskAdd" :selected="askPickedId" :chat-key="chatKey(live.trip.value)" :planned="plannedPlaceIds" @choose="openSlotSheet" @found="onAskFound" @pick="askPickedId = $event" />
          </div>
          <div v-else class="quick live-quick" data-sheet-follow role="group" aria-label="AI 채팅과 근처 찾기">
            <button type="button" class="chip chip--sm chip--add live-chip" @click="openAsk"><span class="live-chip__ic live-chip__ic--ai" aria-hidden="true"><i class="ic ic--s i-sparkle"></i></span>AI에게 묻기</button>
            <button type="button" class="chip chip--sm chip--add live-chip" @click="openNear"><span class="live-chip__ic live-chip__ic--near" aria-hidden="true"><i class="ic ic--s i-search"></i></span>근처 찾기</button>
            <!-- 결제 메모 입구 (11/12): 화면은 ⑤ PayMemoView (/live/pay) -->
            <RouterLink to="/live/pay" class="chip chip--sm chip--add live-chip"><span class="live-chip__ic live-chip__ic--pay" aria-hidden="true"><i class="ic ic--s i-receipt"></i></span>결제 메모</RouterLink>
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

            <button v-if="selectedMealFixed && !isLostOpen" type="button" class="btn btn--secondary btn--md" style="align-self: flex-start" @click="manualLostId = selectedMealFixed.id">못 가게 됐어요</button>
            <LiveCards :cards="cards" :selected="selected" :auto-focus="devMode.isOn" :focus="cardFocus" @pick="pickCard" @seg="segCard = $event" @scrolled="onCardScrolled" show-end @end="isAskingEnd = true" />

            <p v-if="status.weather" class="t-cap live-event"><i class="ic ic--s i-info" aria-hidden="true"></i>{{ status.weather.time }}부터 비</p>
          </BottomSheet>

          <div v-if="!asking" class="cta cta--white" style="box-shadow: none">
            <div v-if="nextDayNo && remainingStops.length === 0" class="cta__row">
              <RouterLink :to="'/trips/today?id=' + live.tripId.value + '&back=/live'" class="btn btn--secondary btn--lg">일정 보기</RouterLink>
              <RouterLink :to="'/trips/today?id=' + live.tripId.value + '&day=' + nextDayNo + '&back=/live'" class="btn btn--primary btn--lg"><i class="ic i-calendar" aria-hidden="true"></i>{{ nextDayNo }}일차 일정 보기</RouterLink>
            </div>
            <div v-else-if="isAllDone" class="cta__row">
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
/* 여행 끝내기 확인 창 (11/12): 트리피 뒤로 은은한 빛 + 작은 색 점(축하), 글은 가운데 */
.end-modal { align-items: stretch; text-align: center; padding-top: 16px; }
.end-modal__art { position: relative; height: 190px; display: flex; align-items: flex-end; justify-content: center; }
.end-modal__art::before { content: ''; position: absolute; left: 50%; top: 18px; width: 7px; height: 7px; border-radius: 50%; box-shadow: -112px 34px 0 #F2C230, -84px 0 0 var(--coral), -128px 104px 0 var(--navy), 104px 6px 0 #8CC63F, 126px 78px 0 #F2C230, 84px 128px 0 var(--coral), -96px 150px 0 #8CC63F; opacity: .85; }
.end-modal__glow { position: absolute; left: 50%; bottom: 0; width: 230px; height: 190px; transform: translateX(-50%); border-radius: 50%; background: radial-gradient(closest-side, var(--navy-soft) 0, var(--navy-soft) 62%, rgba(232, 238, 248, 0) 100%); }
.end-modal__img { position: relative; width: 220px; height: 182px; object-fit: contain; transform-origin: 30% 100%; animation: endTrippieBob 2.6s ease-in-out infinite; }
.end-modal__text { display: flex; flex-direction: column; align-items: center; gap: 6px; }
.end-modal__eyebrow { font-size: 14px; line-height: 20px; font-weight: 700; color: var(--coral-ink); }
.end-modal__desc { margin: 0; font-size: 15px; line-height: 22px; color: var(--ink-muted); }
@keyframes endTrippieBob { 0%, 100% { transform: translateY(0); } 50% { transform: translateY(-4px); } }
@media (prefers-reduced-motion: reduce) { .end-modal__img { animation: none; } }
.scr-Live .noti { padding-right: 46px; } /* 닫기(X) 자리를 비움 (11/09) */
.scr-Live .noti .noti__meta > span { visibility: hidden; } /* "지금" 대신 알림이 생긴 시각을 옆에 따로 (지난 알림이 지금 일처럼 안 보이게) */
.live-noti-at { position: absolute; top: 62px; right: 52px; z-index: 56; font-size: 12px; line-height: 16px; color: var(--ink-muted); font-variant-numeric: tabular-nums; pointer-events: none; }
.live-noti-x { position: absolute; top: 58px; right: 16px; z-index: 56; display: grid; place-items: center; width: 28px; height: 28px; padding: 0; border: 0; border-radius: 50%; background: rgba(24, 33, 58, 0.08); color: var(--ink-muted); cursor: pointer; }
.scr-Live .live-msg { padding-top: 96px; }
.scr-Live .live-fab { position: absolute; right: 16px; top: 380px; z-index: 6; } /* + 가운데는 예전(52px, 376px)과 같은 높이 */
.scr-Live .live-fab__me { position: absolute; left: 0; bottom: calc(100% + 10px); width: 44px; height: 44px; padding: 0; border: 0; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: #2F7BEA; background: #fff; box-shadow: 0 2px 8px rgba(24, 33, 58, .25); cursor: pointer; transition: opacity .18s, transform .18s; }
.scr-Live .live-fab__me.is-hidden { opacity: 0; transform: translateY(16px) scale(.8); pointer-events: none; }
.scr-Live .live-fab-dim { position: absolute; inset: 0; z-index: 5; background: rgba(24, 33, 58, .28); }
.scr-Live .live-fab__main { width: 44px; height: 44px; border: 0; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: #fff; background: var(--navy); box-shadow: 0 6px 16px rgba(24, 33, 58, .3); cursor: pointer; transition: transform .18s, background .18s; }
.scr-Live .live-fab__main.is-open { transform: rotate(45deg); background: var(--ink); }
.scr-Live .live-fab__list { position: absolute; right: 0; bottom: calc(100% + 10px); display: flex; flex-direction: column; align-items: flex-end; gap: 8px; }
.scr-Live .live-fab__item { display: flex; align-items: center; gap: 8px; padding: 0; border: 0; background: none; font: inherit; cursor: pointer; white-space: nowrap; }
.scr-Live .live-fab__text { padding: 7px 12px; border-radius: 999px; font-size: 13px; font-weight: 700; color: var(--ink); background: #fff; box-shadow: 0 2px 8px rgba(24, 33, 58, .18); }
.scr-Live .live-fab__ic { width: 44px; height: 44px; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: #fff; box-shadow: 0 2px 8px rgba(24, 33, 58, .25); }
.scr-Live .live-fab__ic--link { background: linear-gradient(135deg, #F58529, #DD2A7B 55%, #8134AF); }
.scr-Live .live-fab__ic--plan { background: var(--navy); }
.scr-Live .live-fab__ic--share { background: var(--success); }
.scr-Live .live-chip { font-weight: 600; height: 34px; padding-left: 10px; padding-right: 10px; gap: 3px; font-size: 13px; letter-spacing: -.02em; }
/* 11/12: 지도 앱 칩처럼 흰 바탕 + 겹 그림자, 글씨는 진하게 (아이콘 색만 칩마다 다르게) */
.scr-Live .live-quick .live-chip { border: 0; padding-left: 5px; padding-right: 10px; gap: 4px; background: #fff; color: var(--ink); box-shadow: 0 0 0 1px rgba(24, 33, 58, .05), 0 1px 2px rgba(24, 33, 58, .08), 0 4px 12px rgba(24, 33, 58, .12); }
.scr-Live .live-quick .live-chip:hover { background: var(--surface); }
/* 아이콘은 연한 색 동그라미 안에 (AI 보라 · 근처 산호 · 결제 남색) */
.scr-Live .live-chip__ic { flex: none; display: inline-flex; align-items: center; justify-content: center; width: 22px; height: 22px; border-radius: 50%; }
.scr-Live .live-chip__ic .ic { width: 13px; height: 13px; }
.scr-Live .live-chip__ic--ai { background: #F1EAFE; color: #7C3AED; }
.scr-Live .live-chip__ic--near { background: var(--coral-soft); color: var(--coral-ink); }
.scr-Live .live-chip__ic--pay { background: var(--navy-soft); color: var(--navy); } /* 11/12: 결제 메모까지 칩 3개가 + 버튼 왼쪽에 다 들어가게 */
.scr-Live .live-quick { position: absolute; top: 392px; left: 0; right: 58px; z-index: 2; gap: 5px; padding-left: 12px; padding-right: 4px; }
.scr-Live .live-sheet { top: 440px; padding: 8px 0 0; gap: 12px; }
.scr-Live .live-head { display: flex; align-items: flex-end; gap: 12px; padding: 0 16px; }
.scr-Live .live-pill { position: absolute; top: 112px; left: 16px; z-index: 3; }
</style>
