// 장소들의 주소에서 가장 많이 나온 구를 일정 만들기 지역으로 (③ 인스타로 담기·다시 짜기)
// places: [{ address, lat, lng }] → { name, fullName, type, lat, lng, isAuto } 좌표는 그 구 장소들의 가운데. 못 찾으면 null
export function areaFromPlaces(places) {
  const groups = {}
  for (const place of places) {
    const match = /^(\S+)\s+(\S+(?:구|군|시))(?:\s|$)/.exec(place.address || '')
    if (match && place.lat != null) {
      const key = match[1] + ' ' + match[2]
      if (!groups[key]) groups[key] = { name: match[2], fullName: key, list: [] }
      groups[key].list.push(place)
    }
  }
  let best = null
  for (const key in groups) {
    if (!best || groups[key].list.length > best.list.length) best = groups[key]
  }
  if (!best) return null
  const count = best.list.length
  const lat = best.list.reduce((sum, place) => sum + place.lat, 0) / count
  const lng = best.list.reduce((sum, place) => sum + place.lng, 0) / count
  return { name: best.name, fullName: best.fullName, type: 'gu', lat, lng, isAuto: true }
}
