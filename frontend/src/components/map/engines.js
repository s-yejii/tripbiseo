// 지도 엔진 2개(카카오·Leaflet)를 같은 모양으로 감쌉니다. MapView.vue는 이 함수들만 씁니다.
import { placeIcon } from '@/utils/format'

// 마커 HTML: 점(번호·아이콘) + 옆 말풍선(이름·보조 문구)
export function markerHtml(m) {
  const dot = m.kind === 'me' ? '' : m.num ?? (m.kind === 'fixed' ? '★' : placeIcon(m.category))
  const label = m.label
    ? `<div class="mk-label"><b>${esc(m.label)}</b>${m.sub ? `<small class="${m.subColor || ''}">${esc(m.sub)}</small>` : ''}</div>`
    : ''
  return `<div class="mk mk-${m.kind || 'pin'} mk-${m.color || 'blue'}${m.selected ? ' mk-on' : ''}"><span class="mk-dot">${dot}</span>${label}</div>`
}
const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c])

const COLORS = { blue: '#2f5bff', gray: '#9aa3b2', purple: '#8b5cf6', green: '#16a34a', red: '#ef4444', orange: '#f59e0b' }
const color = (c) => COLORS[c] || c || COLORS.blue

export function kakaoEngine(kakao, el, onClick, onMarker) {
  const map = new kakao.maps.Map(el, { center: new kakao.maps.LatLng(37.5665, 126.978), level: 6 })
  kakao.maps.event.addListener(map, 'click', (e) => onClick({ lat: e.latLng.getLat(), lng: e.latLng.getLng() }))
  let items = []
  const ll = ([lat, lng]) => new kakao.maps.LatLng(lat, lng)
  return {
    draw({ markers, lines, polygons }) {
      items.forEach((o) => o.setMap(null))
      items = []
      for (const p of polygons) {
        items.push(new kakao.maps.Polygon({ map, path: p.coords.map(ll), strokeWeight: 1.5, strokeColor: color(p.color),
          strokeStyle: 'dash', fillColor: color(p.color), fillOpacity: p.fill ?? 0.12 }))
      }
      for (const l of lines) {
        items.push(new kakao.maps.Polyline({ map, path: l.coords.map(ll), strokeWeight: l.width || 5, strokeColor: color(l.color),
          strokeOpacity: l.opacity ?? 0.9, strokeStyle: l.dashed ? 'shortdash' : 'solid' }))
      }
      for (const m of markers) {
        const box = document.createElement('div')
        box.innerHTML = markerHtml(m)
        box.firstChild.addEventListener('click', (e) => (e.stopPropagation(), onMarker(m.id)))
        items.push(new kakao.maps.CustomOverlay({ map, position: ll([m.lat, m.lng]), content: box.firstChild, xAnchor: 0, yAnchor: 0, clickable: true, zIndex: m.selected ? 5 : 1 }))
      }
    },
    fit(points, [t, r, b, l]) {
      if (!points.length) return
      if (points.length === 1) return map.setCenter(ll(points[0]))
      const bounds = new kakao.maps.LatLngBounds()
      points.forEach((p) => bounds.extend(ll(p)))
      map.setBounds(bounds, t, r, b, l)
    },
    relayout: () => map.relayout(),
    destroy() { items.forEach((o) => o.setMap(null)) },
  }
}

export function leafletEngine(L, el, onClick, onMarker) {
  const map = L.map(el, { zoomControl: false }).setView([37.5665, 126.978], 13)
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19, attribution: '© OpenStreetMap' }).addTo(map)
  map.on('click', (e) => onClick({ lat: e.latlng.lat, lng: e.latlng.lng }))
  const layer = L.layerGroup().addTo(map)
  return {
    draw({ markers, lines, polygons }) {
      layer.clearLayers()
      polygons.forEach((p) => L.polygon(p.coords, { color: color(p.color), weight: 1.5, dashArray: '6 6', fillOpacity: p.fill ?? 0.12 }).addTo(layer))
      lines.forEach((l) => L.polyline(l.coords, { color: color(l.color), weight: l.width || 5, opacity: l.opacity ?? 0.9, dashArray: l.dashed ? '2 8' : null }).addTo(layer))
      markers.forEach((m) => {
        const icon = L.divIcon({ html: markerHtml(m), className: 'mk-host', iconSize: [0, 0] })
        L.marker([m.lat, m.lng], { icon, zIndexOffset: m.selected ? 500 : 0 }).on('click', () => onMarker(m.id)).addTo(layer)
      })
    },
    fit(points, [t, r, b, l]) {
      if (!points.length) return
      if (points.length === 1) return map.setView(points[0], 15)
      map.fitBounds(L.latLngBounds(points), { paddingTopLeft: [l, t], paddingBottomRight: [r, b] })
    },
    relayout: () => map.invalidateSize(),
    destroy: () => map.remove(),
  }
}
