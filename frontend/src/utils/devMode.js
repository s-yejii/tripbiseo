// 개발자 모드 (③, 11/06): 발표 시연 계정만 켤 수 있어요.
//   시연 계정(DEMO_ACCOUNTS)은 로그인하면 바로 켜져요 (11/08). 다른 계정은 마이 "버전 1.0.0" 5번 → 서버가 시연 계정인지 확인
//   켜지면 가짜 GPS + 빠르게 도는 시연 시계로 여행이 일정대로 흘러가요. 설정은 마이·여행 중 화면의 작은 반투명 점에서
// 이 브라우저에만 기억해요 (localStorage). 시계 자체(시각·배속)는 서버가 계정마다 기억해요.
import { reactive } from 'vue'
import { clearDevRain, getDevScenario, getDevStatus, setDevClock, setDevSpeed } from '@/api/dev'
import { finishTrip, getTripList } from '@/api/trips'
import { readAllNotifications } from '@/api/notifications'
import { isPracticeTrip } from '@/utils/practice'
import { clearLiveNotices } from '@/utils/liveBanner'

const KEY = 'devMode'
const LOGIN_SPEED = 1 // 로그인하면 15:00에서 보통 속도 (일정을 짜는 동안 시간이 흘러가 버리지 않게). 여행 중 화면에서 자동 배속이 빠르게 돌림
const DEFAULT = { isOn: false, isFakeGps: false, clockOffsetMs: 0, speed: 1, syncedAt: 0, showPractice: false, isFlow: false, isFlowTouched: false, isAutoSpeed: false, command: null } // 11/08 주형: 기본은 발표자가 "다음 장소로"로 직접 (자동 배속은 설정에서 켜기)

function load() {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) || 'null')
    if (saved) return { ...DEFAULT, ...saved }
  } catch (e) {
    // 저장한 값이 깨졌으면 꺼진 상태로
  }
  return { ...DEFAULT }
}

export const devMode = reactive(load())

function save() {
  try {
    localStorage.setItem(KEY, JSON.stringify(devMode))
  } catch (e) {
    // 저장소를 못 쓰면 이번 화면에서만
  }
}

// 개발자 모드 켜기 (시연 계정 확인이 끝난 뒤에만 부름)
export function turnOnDevMode() {
  devMode.isOn = true
  save()
}

// 개발자 모드 끄기 (가짜 GPS·시연 시계도 같이 꺼짐)
export function turnOffDevMode() {
  Object.assign(devMode, DEFAULT)
  save()
}

// 서버 시연 시계 상태(GET /dev/status 등의 답)를 화면 시계에도: 차이·배속·받은 시각
export function syncClock(status) {
  devMode.clockOffsetMs = status.offsetMinutes * 60000
  devMode.speed = status.speed == null ? 1 : status.speed
  devMode.syncedAt = Date.now()
  save()
}

// 예전 이름 (분만 아는 곳에서 부름)
export function setClockOffset(minutes) {
  devMode.clockOffsetMs = minutes * 60000
  devMode.syncedAt = Date.now()
  save()
}

// 화면이 쓰는 "지금": 개발자 모드면 시연 시계(배속까지), 아니면 이 PC 시각
export function appNow() {
  if (!devMode.isOn) return new Date()
  const passed = devMode.syncedAt ? Date.now() - devMode.syncedAt : 0
  return new Date(Date.now() + devMode.clockOffsetMs + passed * (devMode.speed - 1))
}

// 연습 기록 보기 켜고 끄기: 켜면 시연 연습 복사본·그 알림도 일반 화면에 보임 (11/06)
export function setShowPractice(isShown) {
  devMode.showPractice = devMode.isOn && isShown
  save()
}

// 가짜 GPS 켜고 끄기 (개발자 모드일 때만)
export function setFakeGps(isFake) {
  devMode.isFakeGps = devMode.isOn && isFake
  save()
}

// 지금 가짜 GPS로 움직이는지
export function isFakeGps() {
  return devMode.isOn && devMode.isFakeGps
}

// 일정대로 흐르기 켜고 끄기: 켜면 여행 중 화면에서 시연 시각에 맞는 자리로 "나"가 움직여요 (11/08)
export function setFlow(isFlow, isByUser = false) {
  devMode.isFlow = devMode.isOn && isFlow
  if (isByUser) devMode.isFlowTouched = true // 설정에서 직접 끈 것만 기억 (그 밖엔 여행 중 화면이 다시 켬)
  save()
}

// 여행 중 화면을 열 때: 개발자 모드인데 흐르기·가짜 GPS가 꺼져 있으면(예전에 끄고 다시 켠 경우) 다시 켬. 설정에서 직접 끈 건 그대로
export function ensureDemoFlow() {
  if (!devMode.isOn || devMode.isFlowTouched) return
  if (!devMode.isFlow) setFlow(true)
  if (!devMode.isFakeGps) setFakeGps(true)
}

// 자동 배속 켜고 끄기: 켜면 볼 것 없는 구간(오래 머무는 중·밤)은 빠르게, 돌발 앞에서는 천천히 (11/08 주형)
//   설정에서 배속을 직접 고르면 꺼짐
export function setAutoSpeed(isAuto) {
  devMode.isAutoSpeed = isAuto
  save()
}

// 여행 중 화면에 시킬 일 (마이의 설정에서 누름 → 여행 중 화면이 열려 있거나 열리면 실행)
//   { type: 'scene', index } 장면으로 바로 · { type: 'stop', step: 1 | -1 } 다음·이전 장소 · { type: 'restart' } 처음부터
export function sendCommand(command) {
  devMode.command = { ...command, at: Date.now() }
  save()
}
export function clearCommand() {
  devMode.command = null
  save()
}

// 로그인 바로 뒤에 부름 (11/08 주형: 시연 계정은 버전 5번 안 눌러도 시나리오대로)
//   시연 계정이면 개발자 모드 + 가짜 GPS + 일정대로 흐르기를 켜고, 시연 시계를 오늘 시나리오 시작 시각(15:00)으로
//   아니면 이 브라우저에 남은 개발자 모드를 끔
export async function applyDemoAccount() {
  try {
    const status = await getDevStatus()
    if (!status.allowed) {
      turnOffDevMode()
      return
    }
    turnOnDevMode()
    setFakeGps(true)
    setFlow(true)
    const setup = await getDevScenario()
    const real = new Date() // 시연 시계가 전에 다음 날로 넘어가 있어도 로그인하면 진짜 오늘 15:00으로 (11/08)
    const today = real.getFullYear() + '-' + String(real.getMonth() + 1).padStart(2, '0') + '-' + String(real.getDate()).padStart(2, '0')
    await setDevClock(today, setup.startTime)
    syncClock(await setDevSpeed(LOGIN_SPEED))
  } catch (e) {
    // 서버가 답을 못 하면 지금 상태 그대로
  }
}

// "시연 준비" (11/08): 끝나지 않은 연습 여행("(연습)")을 앱 흐름대로 끝내고, 시계 = 진짜 오늘 시나리오 시작 시각(15:00)·보통 속도,
//   시연 비 빼기, 가짜 GPS·일정대로 흐르기·자동 배속 켜기, 쌓인 알림 읽음 처리. 진짜 예정 여행(86 등)은 안 건드림
// 시연 계정 여행 시작 (11/08 주형): 언제 눌러도(시계·날짜와 상관없이) 그 여행 1일차 출발 시각부터 시작.
//   진행 중인 다른 시연 여행(연습·시나리오 제목)은 먼저 끝냄. 다른 팀원이 쓰는 여행 번호는 건드리지 않음
const PROTECTED_TRIP_IDS = [51, 81, 82, 83, 84, 85, 86]
const DEMO_TRIP_TITLE = '영등포 워크숍' // 시나리오 여행 제목 앞부분 (서버 DemoScenario의 TITLE)
const isDemoTrip = (trip) => isPracticeTrip(trip) || (typeof trip.title === 'string' && trip.title.startsWith(DEMO_TRIP_TITLE))
export async function prepareTripStart(tripId) {
  clearLiveNotices() // 지난 여행의 위쪽 알림 줄 비우기
  const trips = await getTripList()
  for (const other of trips) {
    const isOtherDemo = other.status === 'ongoing' && other.id !== tripId && isDemoTrip(other)
    if (isOtherDemo && !PROTECTED_TRIP_IDS.includes(other.id)) await finishTrip(other.id)
  }
  const trip = trips.find((item) => item.id === tripId)
  if (!trip) return
  const setup = await getDevScenario().catch(() => null)
  await setDevClock(trip.startDate, (setup && setup.startTime) || '15:00')
  syncClock(await setDevSpeed(1))
}

export async function prepareDemo() {
  clearLiveNotices()
  const trips = await getTripList()
  for (const trip of trips) {
    if (trip.status === 'ongoing' && isPracticeTrip(trip)) await finishTrip(trip.id)
  }
  const setup = await getDevScenario()
  const real = new Date()
  const today = real.getFullYear() + '-' + String(real.getMonth() + 1).padStart(2, '0') + '-' + String(real.getDate()).padStart(2, '0')
  await clearDevRain()
  await setDevClock(today, setup.startTime)
  syncClock(await setDevSpeed(1))
  devMode.isFlowTouched = false
  devMode.isAutoSpeed = false
  devMode.command = null
  setFakeGps(true)
  setFlow(true)
  await readAllNotifications().catch(() => {})
  return trips.filter((trip) => trip.status === 'ongoing' && !isPracticeTrip(trip)).length // 연습이 아닌 진행 중 여행 수 (있으면 안내)
}
