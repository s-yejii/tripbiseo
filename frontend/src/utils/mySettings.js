// 내 여행 스타일·알림 설정 (11/06): 서버 칸(① member_style·알림 설정 API)이 생기기 전까지 이 브라우저에 저장해요 (localStorage)
// 이동 수단(utils/transports.js)과 같은 방식. 서버 API가 생기면 이 파일의 load·save만 API 호출로 바꾸면 돼요
const STYLE_KEY = 'myStyle'
const ALARM_KEY = 'myAlarms'

export const DEFAULT_STYLE = { pace: 'mid', likes: ['카페', '맛집', '쇼핑'], walk: 20, moves: ['지하철'], rainIndoor: true }
export const DEFAULT_ALARMS = { depart: true, delay: true, weather: true, departBefore: 10 }

function load(key, fallback) {
  try {
    const saved = JSON.parse(localStorage.getItem(key) || 'null')
    return saved ? { ...fallback, ...saved } : { ...fallback }
  } catch (e) {
    return { ...fallback } // 저장한 값이 깨졌거나 저장소를 못 쓰면 기본값
  }
}

function save(key, value) {
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch (e) {
    // 저장소를 못 쓰는 브라우저(사생활 보호 창 등)면 이번 화면에서만 바뀜
  }
}

// 내 여행 스타일 { pace, likes, walk, moves, rainIndoor }
export const readStyle = () => load(STYLE_KEY, DEFAULT_STYLE)
export const saveStyle = (style) => save(STYLE_KEY, style)

// 알림 설정 { depart, delay, weather, departBefore }
export const readAlarms = () => load(ALARM_KEY, DEFAULT_ALARMS)
export const saveAlarms = (alarms) => save(ALARM_KEY, alarms)
