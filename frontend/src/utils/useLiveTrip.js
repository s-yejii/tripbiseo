// 여행 중 화면 공통 동작 (④). "use…" 함수: 여러 화면이 같은 상태·동작을 같이 쓰게 묶은 함수(Vue composable)
//   - 진행 중인 여행을 찾고
//   - 브라우저 GPS가 바뀔 때마다 POST /live/track (같은 점을 너무 자주 보내지 않게 간격을 둠)
//   - 30초마다 POST /live/status로 지금 상황을 다시 계산
//     (11/04: GPS가 바뀔 때마다 다시 계산하던 걸, 위치를 저장할 때(20초 간격)만 같이 하게 바꿈.
//      휴대폰 GPS는 거의 1초마다 와서 매번 서버 계산·길찾기 API·지도 다시 그리기가 돌았어요)
//   - 시연 모드(?demo=1): 진짜 GPS 대신 오늘 일정 장소를 차례로 지나가는 가짜 GPS (발표장에서 걸어 다닐 수 없어서)
// 화면을 떠날 때 stop()을 꼭 불러야 타이머·GPS 감시가 꺼져요.
import { ref } from 'vue'
import { getLiveStatus, getTrip, getTripList, sendTrack } from '@/api/trips'
import { useTripStore } from '@/stores/trip'

const STATUS_INTERVAL_MS = 30000 // 지금 상황 다시 계산 간격
const TRACK_MIN_GAP_MS = 20000 // GPS 저장 최소 간격
const DEMO_STEP_MS = 4000 // 시연 모드: 한 걸음 간격
const DEMO_STEPS_PER_STOP = 3 // 시연 모드: 장소 사이를 몇 걸음에 가는지
const DEMO_STAY_POINTS = 4 // 시연 모드: 장소마다 찍는 점 수 (서버는 최근 4점이 멈춰 있어야 도착으로 봄)

export function useLiveTrip() {
  const store = useTripStore()
  const tripId = ref(null)
  const status = ref(null) // live/status 응답
  const trip = ref(null) // 여행 전체 (칸 state·arrivedAt·departedAt을 씀)
  const position = ref(null) // { lat, lng }
  const error = ref('')
  // 화면 상태: loading 여행 찾는 중 / none 진행 중인 여행 없음 / gps 위치 기다리는 중 / gps-error 위치 못 받음 / ready
  const phase = ref('loading')
  const gpsError = ref('')
  let watchId = null
  let statusTimer = null
  let demoTimer = null
  let lastTrackAt = 0

  // 진행 중인 여행이 있으면 시작 (없으면 phase가 none)
  // isNoGps: 시연 계정 일정대로 흐르기 — 진짜 GPS를 켜지 않고 DemoDrive가 placeAt으로 위치를 정함 (11/09)
  async function start(isDemo = false, isNoGps = false) {
    phase.value = 'loading'
    const id = await findOngoingTripId()
    if (!id) {
      phase.value = 'none'
      return false
    }
    tripId.value = id
    store.tripId = id
    phase.value = 'gps'
    if (isDemo) {
      await startDemo()
    } else if (!isNoGps) {
      watchGps()
    }
    statusTimer = setInterval(refresh, STATUS_INTERVAL_MS)
    return true
  }

  function stop() {
    if (watchId !== null && navigator.geolocation) navigator.geolocation.clearWatch(watchId)
    clearInterval(statusTimer)
    clearInterval(demoTimer)
    watchId = null
  }

  // 상태가 ongoing인 여행만 (오늘 날짜인 planned 여행은 아직 시작 전이라 여행 중 화면에 오지 않음)
  async function findOngoingTripId() {
    try {
      const trips = await getTripList()
      const ongoing = trips.find((item) => item.status === 'ongoing')
      return ongoing ? ongoing.id : null
    } catch (e) {
      error.value = e.message
      return null
    }
  }

  // 지금 상황 다시 계산 + 여행(칸 기록) 다시 받기
  async function refresh() {
    if (!tripId.value || !position.value) return
    try {
      const [nextStatus, nextTrip] = await Promise.all([ // Promise.all: 두 요청을 같이 보내고 둘 다 올 때까지 기다림
        getLiveStatus(tripId.value, position.value.lat, position.value.lng),
        getTrip(tripId.value),
      ])
      status.value = nextStatus
      trip.value = nextTrip
      error.value = ''
      phase.value = 'ready'
    } catch (e) {
      error.value = e.message
      if (e.status === 400 || e.status === 404) phase.value = 'none' // 그새 여행이 끝났거나 지워짐
    }
  }

  // 위치가 바뀌면: 지도 위 내 위치는 바로, 저장 + 상황 다시 계산은 간격(20초)을 두고
  async function moveTo(lat, lng, accuracy = null, isForced = false) {
    position.value = { lat, lng }
    store.position = position.value // 대안 화면도 같은 위치를 씀
    const now = Date.now()
    if (isForced || now - lastTrackAt >= TRACK_MIN_GAP_MS) {
      lastTrackAt = now
      try {
        await sendTrack(tripId.value, lat, lng, accuracy)
      } catch (e) {
        error.value = e.message
      }
      await refresh() // 저장한 뒤(도착·출발 기록이 바뀌었을 수 있음) 상황 다시 계산
      return
    }
    if (!status.value) await refresh() // 아직 상황을 한 번도 못 받았으면 바로
  }

  // 진짜 GPS: watchPosition은 위치가 바뀔 때마다 알려 줌
  function watchGps() {
    if (!navigator.geolocation) {
      gpsError.value = '이 브라우저는 위치를 지원하지 않아요'
      phase.value = 'gps-error'
      return
    }
    watchId = navigator.geolocation.watchPosition(
      (pos) => {
        gpsError.value = ''
        moveTo(pos.coords.latitude, pos.coords.longitude, pos.coords.accuracy)
      },
      (e) => {
        gpsError.value = e.code === 1 ? '위치 권한이 꺼져 있어요. 브라우저 주소창 옆 자물쇠에서 위치를 허용해 주세요.' : '지금 위치를 받지 못했어요. 잠시 뒤 다시 시도해 주세요.'
        if (!position.value) phase.value = 'gps-error' // 이미 위치가 있으면 마지막 위치로 계속 보여 줌
      },
      { enableHighAccuracy: true, maximumAge: 10000, timeout: 20000 },
    )
  }

  // 시연용 가짜 GPS: 오늘 남은 장소를 차례로 지나감 (장소마다 4번 찍어서 도착 판정)
  async function startDemo() {
    const route = await makeDemoRoute()
    if (!route.length) return
    let step = 0
    await moveTo(route[0].lat, route[0].lng, 10, true)
    demoTimer = setInterval(async () => {
      step = Math.min(step + 1, route.length - 1)
      await moveTo(route[step].lat, route[step].lng, 10, true)
      if (step === route.length - 1) clearInterval(demoTimer)
    }, DEMO_STEP_MS)
  }

  // 시연 경로: 첫 장소 좌표로 상황을 한 번 받아서, 오늘 칸 좌표 사이를 몇 걸음으로 나눔
  async function makeDemoRoute() {
    const route = []
    try {
      const first = await getLiveStatus(tripId.value, 37.5665, 126.978) // 서울시청 (아무 곳이나 한 번)
      const stops = first.stops.filter((stop) => stop.lat != null && stop.reach !== 'done')
      for (let i = 0; i < stops.length; i++) {
        for (let k = 0; k < DEMO_STAY_POINTS; k++) {
          route.push({ lat: stops[i].lat, lng: stops[i].lng })
        }
        const next = stops[i + 1]
        if (!next) break
        for (let k = 1; k < DEMO_STEPS_PER_STOP; k++) {
          const rate = k / DEMO_STEPS_PER_STOP
          route.push({ lat: stops[i].lat + (next.lat - stops[i].lat) * rate, lng: stops[i].lng + (next.lng - stops[i].lng) * rate })
        }
      }
    } catch (e) {
      error.value = e.message
    }
    return route
  }

  // GPS 다시 시도
  function retryGps() {
    stop()
    phase.value = 'gps'
    watchGps()
    statusTimer = setInterval(refresh, STATUS_INTERVAL_MS)
  }

  // 시연 장면 (개발자 모드, 11/06): 진짜 GPS를 멈추고 그 자리로 옮김. isArrive면 도착 판정에 필요한 점을 다 찍음
  async function placeAt(lat, lng, isArrive = false) {
    if (watchId !== null && navigator.geolocation) navigator.geolocation.clearWatch(watchId)
    watchId = null
    clearInterval(demoTimer)
    if (isArrive) {
      for (let i = 0; i < DEMO_STAY_POINTS - 1; i++) await sendTrack(tripId.value, lat, lng, 10)
    }
    await moveTo(lat, lng, 10, true)
  }

  // 지도 위 내 위치만 옮김 (장소 사이를 걸어가는 모습, 저장 안 함)
  function showAt(lat, lng) {
    position.value = { lat, lng }
    store.position = position.value
  }

  return { tripId, status, trip, position, error, phase, gpsError, start, stop, refresh, retryGps, placeAt, showAt }
}
