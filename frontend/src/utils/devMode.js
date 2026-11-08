// 개발자 모드 (③, 11/06): 발표 시연 계정만 켤 수 있어요.
//   켜는 법: 마이 화면 맨 아래 "버전 1.0.0"을 5번 연달아 누름 → 서버가 시연 계정인지 확인(GET /dev/status)
//   켜지면 여행 중 화면에 반투명 점이 보이고, 누르면 가짜 GPS · 시연 시계 · 빨리 감기를 쓸 수 있어요.
// 이 브라우저에만 기억해요 (localStorage). 로그아웃하거나 끄면 사라져요.
import { reactive } from 'vue'
import { getDevStatus } from '@/api/dev'

const KEY = 'devMode'

function load() {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) || 'null')
    if (saved) return { isOn: !!saved.isOn, isFakeGps: !!saved.isFakeGps, clockOffsetMs: saved.clockOffsetMs || 0, showPractice: !!saved.showPractice }
  } catch (e) {
    // 저장한 값이 깨졌으면 꺼진 상태로
  }
  return { isOn: false, isFakeGps: false, clockOffsetMs: 0, showPractice: false }
}

export const devMode = reactive(load())

function save() {
  localStorage.setItem(KEY, JSON.stringify({ isOn: devMode.isOn, isFakeGps: devMode.isFakeGps, clockOffsetMs: devMode.clockOffsetMs, showPractice: devMode.showPractice }))
}

// 개발자 모드 켜기 (시연 계정 확인이 끝난 뒤에만 부름)
export function turnOnDevMode() {
  devMode.isOn = true
  save()
}

// 개발자 모드 끄기 (가짜 GPS·시연 시계도 같이 꺼짐)
export function turnOffDevMode() {
  devMode.isOn = false
  devMode.isFakeGps = false
  devMode.clockOffsetMs = 0
  devMode.showPractice = false
  save()
}

// 서버 시연 시계와 같은 차이를 화면 시계에도 (DevDot이 서버 상태를 받을 때 부름, 11/06)
export function setClockOffset(minutes) {
  devMode.clockOffsetMs = minutes * 60000
  save()
}

// 화면이 쓰는 "지금": 개발자 모드면 시연 시계, 아니면 이 PC 시각
export function appNow() {
  return new Date(Date.now() + (devMode.isOn ? devMode.clockOffsetMs : 0))
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

// 로그인 바로 뒤에 부름 (11/08 주형: 시연 계정은 버전 5번 안 눌러도 시나리오대로)
//   시연 계정(DEMO_ACCOUNTS)이면 개발자 모드 + 가짜 GPS를 켜고, 아니면 이 브라우저에 남은 개발자 모드를 끔
export async function applyDemoAccount() {
  try {
    const status = await getDevStatus()
    if (status.allowed) {
      turnOnDevMode()
      setFakeGps(true)
    } else {
      turnOffDevMode()
    }
  } catch (e) {
    // 서버가 답을 못 하면 지금 상태 그대로
  }
}
