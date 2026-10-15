// 브라우저 위치 (PC는 와이파이 기준 대략 위치). https 또는 localhost에서만 동작
export function locateOnce() {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) return reject(new Error('이 브라우저는 위치 찾기를 지원하지 않아요'))
    navigator.geolocation.getCurrentPosition(
      (p) => resolve({ lat: p.coords.latitude, lng: p.coords.longitude, accuracy: p.coords.accuracy }),
      (e) => reject(new Error(e.code === 1 ? '위치 권한을 허용해 주세요 (주소창 왼쪽 자물쇠 → 위치)' : '위치를 찾지 못했어요')),
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 30000 },
    )
  })
}

export function watchLocation(onPos, onError) {
  if (!navigator.geolocation) {
    onError(new Error('이 브라우저는 위치 찾기를 지원하지 않아요'))
    return () => {}
  }
  const id = navigator.geolocation.watchPosition(
    (p) => onPos({ lat: p.coords.latitude, lng: p.coords.longitude, accuracy: p.coords.accuracy }),
    (e) => onError(new Error(e.code === 1 ? '위치 권한을 허용해 주세요 (주소창 왼쪽 자물쇠 → 위치)' : '위치를 찾지 못했어요')),
    { enableHighAccuracy: true, maximumAge: 15000 },
  )
  return () => navigator.geolocation.clearWatch(id)
}

// 시스템 알림 (권한이 있을 때만). 화면 안 배너는 따로 보여 줌
export function notify(title, body, tag = title) {
  if (!('Notification' in window) || Notification.permission !== 'granted') return
  try {
    new Notification(title, { body, tag, renotify: true }) // 같은 tag면 앞 알림을 바꾸면서 다시 울림
  } catch {
    /* 일부 모바일 브라우저는 서비스 워커 알림만 허용 */
  }
}
