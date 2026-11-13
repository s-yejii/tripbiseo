// ⑤ 나무 자료 도우미 (11/11, 11/13 웹 3D 나무 코드를 빼고 유니티 3D 지도가 쓰는 것만 남김)
//  - 가로수: 서울시 가로수 위치정보의 실제 좌표·수종 (public/geo/trees/{구}.json) + 수종별 계절 색
//  - 숲 구역 안인지 재는 도구, 수종 → 나무 모양
//  쓰는 곳: unityTrees.js, unityExport.js


// 가로수 수종별 계절 색 (없는 수종은 DEFAULT). 겨울에 잎이 지는 나무는 bare 색
const BARE = '#9A8F84'
const DEFAULT_COLORS = { spring: '#8BC34A', summer: '#43A047', autumn: '#D9A441', winter: BARE }
const SPECIES_COLORS = {
  은행나무: { spring: '#9CCC65', summer: '#558B2F', autumn: '#F2C037', winter: BARE },
  양버즘나무: { spring: '#AED581', summer: '#689F38', autumn: '#B98A4E', winter: BARE },
  느티나무: { spring: '#9CCC65', summer: '#388E3C', autumn: '#D2691E', winter: BARE },
  벚나무류: { spring: '#F8BBD0', summer: '#4CAF50', autumn: '#D9603B', winter: BARE },
  이팝나무: { spring: '#F5F5F0', summer: '#4CAF50', autumn: '#C9A227', winter: BARE },
  회화나무: { spring: '#AED581', summer: '#4CAF50', autumn: '#C0A040', winter: BARE },
  메타세쿼이아: { spring: '#8BC34A', summer: '#2E7D32', autumn: '#B5532C', winter: BARE },
  소나무: { spring: '#2E7D32', summer: '#1B5E20', autumn: '#1B5E20', winter: '#2F5D3A' },
  반송: { spring: '#2E7D32', summer: '#1B5E20', autumn: '#1B5E20', winter: '#2F5D3A' },
  단풍나무: { spring: '#9CCC65', summer: '#43A047', autumn: '#C62828', winter: BARE },
  중국단풍: { spring: '#9CCC65', summer: '#43A047', autumn: '#D84315', winter: BARE },
  복자기: { spring: '#9CCC65', summer: '#43A047', autumn: '#C62828', winter: BARE },
  대왕참나무: { spring: '#9CCC65', summer: '#388E3C', autumn: '#A0522D', winter: '#8D6E63' },
}

// ============================== 가로수
/** 구 이름 목록의 가로수 파일을 읽어 [{ lng, lat, species }] */
export async function loadStreetTrees(guNames) {
  const trees = []
  for (const gu of guNames) {
    try {
      const response = await fetch('/geo/trees/' + encodeURIComponent(gu) + '.json')
      if (!response.ok || !(response.headers.get('content-type') || '').includes('json')) continue
      const data = await response.json()
      for (const [lng, lat, speciesIndex] of data.trees) trees.push({ lng: lng, lat: lat, species: data.species[speciesIndex] })
    } catch (e) {
      // 그 구 파일이 없으면 건너뜀
    }
  }
  return trees
}

export function streetTreeColor(species, season) {
  return (SPECIES_COLORS[species] || DEFAULT_COLORS)[season]
}

// ============================== 숲 구역 (임상도)
// 0~1 사이의 "정해진 난수": 같은 자리면 늘 같은 값 (새로 열어도 나무가 같은 자리에)
export function seeded(a, b) {
  const x = Math.sin(a * 127.1 + b * 311.7) * 43758.5453
  return x - Math.floor(x)
}

export function isInRings(lng, lat, rings) {
  let inside = false
  for (const ring of rings) {
    for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
      const [x1, y1] = ring[i]
      const [x2, y2] = ring[j]
      if ((y1 > lat) !== (y2 > lat) && lng < ((x2 - x1) * (lat - y1)) / (y2 - y1) + x1) inside = !inside
    }
  }
  return inside
}

export function bboxOf(ring) {
  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity
  for (const [x, y] of ring) {
    if (x < minX) minX = x
    if (x > maxX) maxX = x
    if (y < minY) minY = y
    if (y > maxY) maxY = y
  }
  return [minX, minY, maxX, maxY]
}

const TALL_SPECIES = ['은행나무', '메타세쿼이아', '양버즘나무', '튜울립나무']

/** 수종 이름 → 모양 이름: round 둥근 활엽수 · cone 뾰족한 침엽수 · pine 위가 넓적한 소나무 · tall 길쭉한 은행나무류 */
export function shapeOf(species, isConifer) {
  if ((species && species.includes('소나무')) || species === '반송') return 'pine'
  if (isConifer) return 'cone'
  return TALL_SPECIES.includes(species) ? 'tall' : 'round'
}
