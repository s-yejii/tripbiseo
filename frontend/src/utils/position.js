// 지금 위치를 한 번만 물어봐요 (거절·시간 초과면 null → 화면은 위치 없이 계속)
const POSITION_TIMEOUT_MS = 5000
const POSITION_MAX_AGE_MS = 10 * 60 * 1000 // 10분 안에 받은 위치면 다시 묻지 않고 씀

export function getPositionOnce() {
  return new Promise((resolve) => {
    if (!navigator.geolocation) {
      resolve(null)
      return
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve({ lat: pos.coords.latitude, lng: pos.coords.longitude }),
      () => resolve(null),
      { timeout: POSITION_TIMEOUT_MS, maximumAge: POSITION_MAX_AGE_MS },
    )
  })
}
