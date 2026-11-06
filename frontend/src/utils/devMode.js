// 개발자 모드 (③, 11/06): 발표 시연 계정만 켤 수 있어요.
//   켜는 법: 마이 화면 맨 아래 "버전 1.0.0"을 5번 연달아 누름 → 서버가 시연 계정인지 확인(GET /dev/status)
//   켜지면 여행 중 화면에 반투명 점이 보이고, 누르면 가짜 GPS · 시연 시계 · 빨리 감기를 쓸 수 있어요.
// 이 브라우저에만 기억해요 (localStorage). 로그아웃하거나 끄면 사라져요.
import { reactive } from 'vue'

const KEY = 'devMode'

function load() {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) || 'null')
    if (saved) return { isOn: !!saved.isOn, isFakeGps: !!saved.isFakeGps }
  } catch (e) {
    // 저장한 값이 깨졌으면 꺼진 상태로
  }
  return { isOn: false, isFakeGps: false }
}

export const devMode = reactive(load())

function save() {
  localStorage.setItem(KEY, JSON.stringify({ isOn: devMode.isOn, isFakeGps: devMode.isFakeGps }))
}

// 개발자 모드 켜기 (시연 계정 확인이 끝난 뒤에만 부름)
export function turnOnDevMode() {
  devMode.isOn = true
  save()
}

// 개발자 모드 끄기 (가짜 GPS도 같이 꺼짐)
export function turnOffDevMode() {
  devMode.isOn = false
  devMode.isFakeGps = false
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
