<script setup>
// 공통 지도. 카카오 지도를 먼저 쓰고, 안 되면 오픈스트리트맵으로 바꿉니다.
// markers: [{ id, lat, lng, kind:'pin'|'me'|'fixed', color, num, label, sub, category, selected }]
// lines:   [{ coords:[[lat,lng]...], color, dashed, width }]   polygons: [{ coords, color }]
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { loadKakao, loadLeaflet } from './loader'
import { kakaoEngine, leafletEngine } from './engines'

const props = defineProps({
  markers: { type: Array, default: () => [] },
  lines: { type: Array, default: () => [] },
  polygons: { type: Array, default: () => [] },
  fitKey: { type: [String, Number], default: '' }, // 값이 바뀌면 전체가 보이게 다시 맞춤
  focus: { type: Array, default: null }, // 이 점들만 보이게 맞춤 [[lat,lng]...] (없으면 전체)
  pad: { type: Array, default: () => [50, 40, 50, 40] }, // 맞출 때 여백 [위, 오른쪽, 아래, 왼쪽] — 지도를 덮는 카드만큼
})
const emit = defineEmits(['marker-click', 'map-click'])
const el = ref(null)
const engineName = ref('')
let engine = null

function draw() {
  engine?.draw({ markers: props.markers, lines: props.lines, polygons: props.polygons })
}
function fit() {
  const pts = props.focus?.length ? props.focus : [...props.markers.map((m) => [m.lat, m.lng]), ...props.lines.flatMap((l) => l.coords)]
  engine?.fit(pts, props.pad)
}

onMounted(async () => {
  const onClick = (p) => emit('map-click', p)
  const onMarker = (id) => emit('marker-click', id)
  try {
    engine = kakaoEngine(await loadKakao(), el.value, onClick, onMarker)
    engineName.value = 'kakao'
  } catch {
    try {
      engine = leafletEngine(await loadLeaflet(), el.value, onClick, onMarker)
      engineName.value = 'osm'
    } catch {
      engineName.value = 'none'
      return
    }
  }
  draw()
  fit()
})
onBeforeUnmount(() => engine?.destroy())

watch(() => [props.markers, props.lines, props.polygons], draw, { deep: true })
watch(() => props.fitKey, fit)
defineExpose({ relayout: () => engine?.relayout(), fit })
</script>

<template>
  <div class="map-wrap">
    <div ref="el" class="map-canvas" />
    <p v-if="engineName === 'none'" class="map-fail">지도를 불러오지 못했어요. 인터넷 연결과 카카오 키를 확인하세요.</p>
  </div>
</template>

<style scoped>
.map-wrap { position: relative; width: 100%; height: 100%; background: #e8edf3; isolation: isolate; } /* 지도 안 z-index가 화면 위 버튼을 덮지 않게 */
.map-canvas { position: absolute; inset: 0; }
.map-fail { position: absolute; inset: auto 16px 16px; margin: 0; padding: 10px 12px; border-radius: 10px; background: #fff; font-size: 13px; color: var(--sub); }
</style>
