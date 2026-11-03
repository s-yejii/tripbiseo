// 여행 중 화면 공통 동작 (④). "use…" 함수: 여러 화면이 같은 상태·동작을 같이 쓰게 묶은 함수(Vue composable)
//   - 진행 중인 여행을 찾고
//   - 브라우저 GPS가 바뀔 때마다 POST /live/track (같은 점을 너무 자주 보내지 않게 간격을 둠)
//   - 30초마다 POST /live/status로 지금 상황을 다시 계산
//   - 시연 모드(?demo=1): 진짜 GPS 대신 오늘 일정 장소를 차례로 지나가는 가짜 GPS (발표장에서 걸어 다닐 수 없어서)
// 화면을 떠날 때 stop()을 꼭 불러야 타이머·GPS 감시가 꺼져요.
import { ref } from 'vue'
import { getLiveStatus, sendTrack } from '@/api/trips'
import { useTripStore } from '@/stores/trip'

const STATUS_INTERVAL_MS = 30000 // 지금 상황 다시 계산 간격
const TRACK_MIN_GAP_MS = 20000 // GPS 저장 최소 간격
const DEMO_STEP_MS = 4000 // 시연 모드: 한 걸음 간격
const DEMO_STEPS_PER_STOP = 3 // 시연 모드: 장소 사이를 몇 걸음에 가는지 (도착 판정은 두 번 연속이라 장소마다 2번 머묾)

export function useLiveTrip() {
  const store = useTripStore()
  const tripId = ref(null)
  const status = ref(null) // live/status 응답 (없으면 null → 화면은 시연용 각본)
  const position = ref(null) // { lat, lng }
  const error = ref('')
  let watchId = null
  let statusTimer = null
  let demoTimer = null
  let lastTrackAt = 0

  // 진행 중인 여행이 있으면 시작 (없으면 false → 화면은 각본 그대로)
  async function start(isDemo = false) {
    const id = await store.findOngoingTripId()
    if (!id) return false
    tripId.value = id
    if (isDemo) {
      await startDemo()
    } else {
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

  // 지금 상황 다시 계산
  async function refresh() {
    if (!tripId.value || !position.value) return
    try {
      status.value = await getLiveStatus(tripId.value, position.value.lat, position.value.lng)
      error.value = ''
    } catch (e) {
      error.value = e.message
      if (e.status === 400 || e.status === 404) status.value = null // 진행 중이 아님 → 각본으로
    }
  }

  // 위치가 바뀌면 저장(간격 두고) + 상황 다시 계산
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
    }
    await refresh()
  }

  // 진짜 GPS: watchPosition은 위치가 바뀔 때마다 알려 줌
  function watchGps() {
    if (!navigator.geolocation) {
      error.value = '이 브라우저는 위치를 지원하지 않아요'
      return
    }
    watchId = navigator.geolocation.watchPosition(
      (pos) => moveTo(pos.coords.latitude, pos.coords.longitude, pos.coords.accuracy),
      () => (error.value = '위치 권한을 허용해 주세요'),
      { enableHighAccuracy: true, maximumAge: 10000 },
    )
  }

  // 시연용 가짜 GPS: 오늘 남은 장소를 차례로 지나감 (장소마다 2번 찍어서 도착 판정)
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
        route.push({ lat: stops[i].lat, lng: stops[i].lng }, { lat: stops[i].lat, lng: stops[i].lng })
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

  return { tripId, status, position, error, start, stop, refresh }
}
