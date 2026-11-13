// ⑤ 웹 회고 → 유니티 3D 지도에 넘길 나무 목록 만들기 (11/12)
//  - 숲 나무: 임상도 숲 구역 안에 9m 간격으로 심음 (색은 여행 계절 + 그 시기 위성 NDVI, season.js 규칙 그대로)
//  - 가로수: 서울시 가로수 자료의 실제 위치·수종
//  - 길 위에는 심지 않음 (1인칭으로 걸을 때 길을 막지 않게)
// 웹 3D 회고(trees.js)는 "걸은 길 가까운 나무"만 그렸지만, 유니티는 지도 범위 전체를 그려요
import { autumnDrop, autumnStageOf, forestColor, pickNdvi, seasonOf } from './season'
import { bboxOf, isInRings, loadStreetTrees, seeded, shapeOf, streetTreeColor } from './trees'

const FOREST_SPACING_M = 9     // 숲 나무 간격
const ROAD_CELL_M = 4          // 길이 지나는 자리를 표시할 칸 크기
const DEFAULT_FOREST_HEIGHT_M = 12
const STREET_TREE_HEIGHT_M = 9
const SHAPES = { round: 0, cone: 1, pine: 2, tall: 3 } // 유니티 TreeMaker.cs의 모양 번호와 같아야 함
const CONIFER_SPECIES = ['소나무', '반송', '메타세쿼이아', '잣나무', '전나무', '주목']
const AUTUMN_RED = '#C0392B'
const AUTUMN_YELLOW = '#F2C037'

/**
 * 범위 안의 나무 목록
 * area: { center: [경도, 위도], box: [서, 남, 동, 북], lngScale, latScale(1도가 몇 m인지) }
 * roads: buildMapData가 만든 길 목록 (미터 좌표), forest: forest-seoul.json 내용(없으면 null), guNames: 가로수를 읽을 구 이름들
 * 돌려줌: [{ x, z, height, shape(0 둥근 활엽수 · 1 뾰족한 침엽수 · 2 소나무 · 3 길쭉한 나무), color: '#RRGGBB' }]
 */
export async function plantTrees(area, roads, dateText, forest, guNames) {
  const season = seasonOf(dateText)
  const isOnRoad = makeRoadChecker(roads)
  const trees = []
  if (forest) plantForest(trees, area, forest, season, dateText, isOnRoad)
  for (const tree of await loadStreetTrees(guNames)) {
    if (tree.lng < area.box[0] || tree.lng > area.box[2] || tree.lat < area.box[1] || tree.lat > area.box[3]) continue
    const shape = shapeOf(tree.species, CONIFER_SPECIES.includes(tree.species))
    const height = (shape === 'tall' ? 12 : STREET_TREE_HEIGHT_M) * (0.8 + seeded(tree.lng * 999, tree.lat * 999) * 0.45)
    trees.push(toTree(area, tree.lng, tree.lat, height, shape, streetTreeColor(tree.species, season)))
  }
  return trees
}

// 숲 구역마다: 범위에 걸치면 그 안을 간격대로 훑으며 심음
function plantForest(trees, area, forest, season, dateText, isOnRoad) {
  const stepLng = FOREST_SPACING_M / area.lngScale
  const stepLat = FOREST_SPACING_M / area.latScale
  for (const feature of forest.features) {
    const look = forestLook(feature.properties, season, dateText)
    for (const polygon of feature.geometry.coordinates) {
      const box = bboxOf(polygon[0])
      if (box[2] < area.box[0] || box[0] > area.box[2] || box[3] < area.box[1] || box[1] > area.box[3]) continue
      for (let gx = Math.floor(box[0] / stepLng); gx * stepLng <= box[2]; gx++) {
        for (let gy = Math.floor(box[1] / stepLat); gy * stepLat <= box[3]; gy++) {
          const lng = (gx + seeded(gx, gy) * 0.8) * stepLng // 줄 맞춰 서 보이지 않게 조금씩 흔듦
          const lat = (gy + seeded(gy, gx) * 0.8) * stepLat
          if (lng < area.box[0] || lng > area.box[2] || lat < area.box[1] || lat > area.box[3] || !isInRings(lng, lat, polygon)) continue
          const tree = forestTree(area, lng, lat, gx, gy, look)
          if (!isOnRoad(tree.x, tree.z)) trees.push(tree)
        }
      }
    }
  }
}

// 숲 구역 하나의 모습: 종류, 나무 높이, 활엽수 색, 침엽수 색, 단풍이 들었는지
function forestLook(props, season, dateText) {
  const kind = props.kind === 'conifer' || props.kind === 'broadleaf' ? props.kind : 'mixed'
  const drop = season === 'autumn' ? autumnDrop(props, dateText) : null
  const stage = kind !== 'conifer' ? autumnStageOf(drop) : null
  return {
    kind: kind,
    species: props.species || '',
    height: typeof props.height === 'number' ? props.height : DEFAULT_FOREST_HEIGHT_M,
    color: forestColor(kind, season, pickNdvi(props, dateText), drop),
    coniferColor: forestColor('conifer', season, pickNdvi(props, dateText), null),
    isChanging: season === 'autumn' && stage !== null && stage !== 'green',
  }
}

// 숲 나무 한 그루. 혼효림은 열 그루 중 네 그루를 침엽수로, 단풍 든 활엽수는 넷 중 하나씩 붉게·노랗게 섞음
function forestTree(area, lng, lat, gx, gy, look) {
  const isConifer = look.kind === 'conifer' || (look.kind === 'mixed' && seeded(gx + 7, gy + 3) < 0.4)
  const height = look.height * (0.75 + seeded(gx + 1, gy + 2) * 0.5)
  const shape = shapeOf(isConifer && look.kind === 'conifer' ? look.species : '', isConifer)
  let color = isConifer ? look.coniferColor : look.color
  if (!isConifer && look.isChanging) {
    const dice = seeded(gx * 17, gy * 13)
    if (dice < 0.25) color = blend(color, AUTUMN_RED, 0.45)
    else if (dice < 0.5) color = blend(color, AUTUMN_YELLOW, 0.45)
  }
  return toTree(area, lng, lat, height, shape, color)
}

function toTree(area, lng, lat, height, shape, color) {
  return {
    x: Math.round((lng - area.center[0]) * area.lngScale * 10) / 10,
    z: Math.round((lat - area.center[1]) * area.latScale * 10) / 10,
    height: Math.round(height * 10) / 10,
    shape: SHAPES[shape],
    color: color,
  }
}

// 두 색을 섞음 (ratio 0 = a, 1 = b)
function blend(a, b, ratio) {
  const x = parseInt(a.slice(1), 16)
  const y = parseInt(b.slice(1), 16)
  const mix = (shift) => Math.round(((x >> shift) & 255) * (1 - ratio) + ((y >> shift) & 255) * ratio)
  return '#' + ((1 << 24) + (mix(16) << 16) + (mix(8) << 8) + mix(0)).toString(16).slice(1)
}

// "이 자리가 길 위인가"를 알려 주는 함수 만들기
// 길을 4m마다 점으로 찍어 그 점이 든 칸(과 길 폭만큼 주변 칸)을 표시해 두고, 나무 자리의 칸이 표시돼 있는지 봄
function makeRoadChecker(roads) {
  const cells = new Set() // "칸x,칸z" 글자 모음
  for (const road of roads) {
    const reach = Math.ceil(road.width / 2 / ROAD_CELL_M) // 길 폭의 절반이 몇 칸인지
    for (let i = 0; i + 3 < road.points.length; i += 2) {
      const [x1, z1, x2, z2] = [road.points[i], road.points[i + 1], road.points[i + 2], road.points[i + 3]]
      const pieces = Math.max(1, Math.ceil(Math.hypot(x2 - x1, z2 - z1) / ROAD_CELL_M))
      for (let piece = 0; piece <= pieces; piece++) {
        const cellX = Math.floor((x1 + ((x2 - x1) * piece) / pieces) / ROAD_CELL_M)
        const cellZ = Math.floor((z1 + ((z2 - z1) * piece) / pieces) / ROAD_CELL_M)
        for (let dx = -reach; dx <= reach; dx++) {
          for (let dz = -reach; dz <= reach; dz++) cells.add(cellX + dx + ',' + (cellZ + dz))
        }
      }
    }
  }
  return (x, z) => cells.has(Math.floor(x / ROAD_CELL_M) + ',' + Math.floor(z / ROAD_CELL_M))
}
