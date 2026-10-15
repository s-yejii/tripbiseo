<script setup>
// 직접 계획하기: 장소 검색 또는 지도에서 눌러 날짜별로 담기 → 검증
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { places } from '@/api'
import { usePlan } from '@/stores/plan'
import { dayCount, metres, placeIcon } from '@/utils/format'
import { sortDay, toStop } from '@/utils/stops'
import AppBar from '@/components/AppBar.vue'
import ConditionsCard from '@/components/ConditionsCard.vue'
import MapView from '@/components/map/MapView.vue'
import Modal from '@/components/Modal.vue'

const router = useRouter()
const plan = usePlan()
const d = computed(() => plan.draft)
const day = ref(1)
const days = computed(() => Math.max(1, dayCount(d.value.startDate, d.value.endDate)))
const dayStops = computed(() => d.value.stops.filter((s) => s.day === day.value))
const keyword = ref('')
const results = ref([])
const tapped = ref(null) // 지도에서 누른 곳 { lat, lng, places: [] }
const error = ref('')

// 담은 장소(파란 번호) + 검색 결과(보라 번호, 누르면 담기)
const markers = computed(() => [
  ...dayStops.value.map((s, i) => ({ id: 's' + i, lat: s.lat, lng: s.lng, num: i + 1, label: s.name, sub: s.time, kind: s.fixed ? 'fixed' : 'pin', color: s.fixed ? 'red' : 'blue' })),
  ...results.value.map((p, i) => ({ id: 'r' + i, lat: p.lat, lng: p.lng, num: i + 1, color: 'purple', label: p.name, sub: p.category })),
])
// 검색하면 결과가 다 보이게 지도를 옮기고, 결과를 닫으면 담은 일정으로 돌아옴
const focus = computed(() => (results.value.length ? results.value.map((p) => [p.lat, p.lng]) : null))
const fitKey = computed(() => `${day.value}-${dayStops.value.length}-${results.value.map((p) => p.id).join(',')}`)
function onMarker(id) {
  if (String(id).startsWith('r')) add(results.value[Number(String(id).slice(1))])
}
const lines = computed(() => (dayStops.value.length > 1 ? [{ coords: dayStops.value.map((s) => [s.lat, s.lng]), color: 'blue', dashed: true, width: 3 }] : []))

async function search() {
  if (!keyword.value.trim()) return
  error.value = ''
  try {
    results.value = await places.search(keyword.value.trim(), dayStops.value.at(-1)?.lat, dayStops.value.at(-1)?.lng)
    if (!results.value.length) error.value = `'${keyword.value.trim()}'(으)로 저장된 장소가 없어요. 지도를 눌러 직접 담을 수도 있어요.`
  } catch (e) {
    error.value = e.message
  }
}
async function onMapClick(p) {
  tapped.value = { ...p, places: [] }
  try {
    tapped.value.places = await places.nearby(p.lat, p.lng, 200)
  } catch (e) {
    error.value = e.message
  }
}
function add(place) {
  const prev = dayStops.value.at(-1)
  plan.draft.stops = sortDay([...d.value.stops, toStop(place, day.value, prev)], day.value)
  results.value = []
  keyword.value = ''
  tapped.value = null
}
const customName = ref('')
function addCustom() {
  const name = customName.value.trim()
  if (!name) return
  add({ name, lat: tapped.value.lat, lng: tapped.value.lng, category: '' })
  customName.value = ''
}
function edit(stop, patch) {
  Object.assign(stop, patch)
  plan.draft.stops = sortDay(d.value.stops, day.value)
}
// 여행 기간을 줄이면: 보고 있던 Day를 마지막 날로, 기간 밖에 담아 둔 장소도 마지막 날로 옮김
watch(days, (n) => {
  if (day.value > n) day.value = n
  const out = d.value.stops.filter((s) => s.day > n)
  if (out.length) {
    plan.draft.stops = sortDay(d.value.stops.map((s) => (s.day > n ? { ...s, day: n } : s)), n)
    error.value = `기간 밖에 있던 ${out.map((s) => s.name).join(', ')}을(를) Day ${n}로 옮겼어요.`
  }
})
const remove = (stop) => (plan.draft.stops = sortDay(d.value.stops.filter((s) => s !== stop), day.value))

function check() {
  if (!d.value.title.trim()) d.value.title = `${d.value.stops[0]?.name ?? '새'} 여행`
  plan.from = 'manual'
  router.push('/plan/check')
}
</script>

<template>
  <AppBar title="직접 계획하기" />
  <div class="page manual">
    <input v-model="plan.draft.title" class="field" placeholder="여행 이름 (예: 서울 하루 여행)" maxlength="40" />
    <ConditionsCard v-model="plan.draft" />

    <div class="days">
      <button v-for="n in days" :key="n" :class="{ on: day === n }" @click="day = n">Day {{ n }}</button>
    </div>

    <form class="search row" @submit.prevent="search">
      <input v-model="keyword" class="field grow" placeholder="장소 이름 검색" />
      <button class="btn primary sm">검색</button>
    </form>
    <div v-if="results.length" class="row res-head"><span class="muted small grow">검색 결과 {{ results.length }}곳 · 지도의 보라 핀을 눌러도 담겨요</span><button class="link small" @click="results = []">닫기</button></div>
    <ul v-if="results.length" class="results">
      <li v-for="p in results" :key="p.id" @click="add(p)">
        <span>{{ placeIcon(p.category) }}</span>
        <div class="grow"><b>{{ p.name }}</b><small class="muted ellipsis">{{ p.category }} · {{ p.address }}</small></div>
        <span class="add">＋</span>
      </li>
    </ul>

    <div class="map"><MapView :markers="markers" :lines="lines" :focus="focus" :fit-key="fitKey" @map-click="onMapClick" @marker-click="onMarker" /></div>
    <p class="muted small">지도를 누르면 그 근처 장소를 담을 수 있어요</p>

    <ol class="stops">
      <li v-for="(s, i) in dayStops" :key="s.name + i" class="card">
        <div class="row">
          <span class="num">{{ i + 1 }}</span>
          <b class="grow ellipsis">{{ s.name }}</b>
          <button class="x" aria-label="빼기" @click="remove(s)">✕</button>
        </div>
        <div class="row opts">
          <label>시작 <input type="time" :value="s.time" @change="edit(s, { time: $event.target.value })" /></label>
          <label>머무름 <input type="number" min="10" max="600" step="10" :value="s.stay" @change="edit(s, { stay: Number($event.target.value) })" />분</label>
          <label class="fix"><input type="checkbox" :checked="s.fixed" @change="edit(s, { fixed: $event.target.checked })" /> 시간 고정</label>
        </div>
      </li>
    </ol>
    <p v-if="!dayStops.length" class="empty">Day {{ day }}에 담은 장소가 없어요</p>
    <p v-if="error" class="error">{{ error }}</p>
    <button class="btn primary block" :disabled="!d.stops.length" @click="check">일정 검증하기</button>
  </div>

  <Modal v-if="tapped" title="이 근처 장소" @close="tapped = null">
    <ul class="results">
      <li v-for="p in tapped.places" :key="p.id" @click="add(p)">
        <span>{{ placeIcon(p.category) }}</span>
        <div class="grow"><b>{{ p.name }}</b><small class="muted">{{ p.category }} · {{ metres(p.distance) }}</small></div>
        <span class="add">＋</span>
      </li>
    </ul>
    <p v-if="!tapped.places.length" class="empty">200m 안에 저장된 장소가 없어요</p>
    <form class="row custom" @submit.prevent="addCustom">
      <input v-model="customName" class="field grow" placeholder="이 위치에 이름 붙여 담기 (예: 친구 집)" maxlength="40" />
      <button class="btn line sm" :disabled="!customName.trim()">담기</button>
    </form>
  </Modal>
</template>

<style scoped>
.manual { display: flex; flex-direction: column; gap: 12px; }
.manual > * { flex-shrink: 0; } /* 화면이 길어져도 목록·카드가 눌려 사라지지 않게 (스크롤로 봄) */
.days { display: flex; gap: 8px; overflow-x: auto; }
.days button { flex: none; padding: 8px 16px; border-radius: 999px; background: var(--bg); font-weight: 700; }
.days button.on { background: var(--ink); color: #fff; }
.search { gap: 8px; }
.results { flex: none; max-height: 260px; overflow-y: auto; list-style: none; margin: 0; padding: 0; border: 1px solid var(--line); border-radius: 14px; } /* flex 안에서 높이가 0으로 눌리지 않게 */
.res-head { gap: 8px; }
.res-head .link { color: var(--blue); font-weight: 700; }
.results li { display: flex; align-items: center; gap: 10px; padding: 12px 14px; border-bottom: 1px solid var(--line); cursor: pointer; }
.results li:last-child { border: 0; }
.results b { display: block; font-size: 15px; }
.results small { display: block; font-size: 12px; }
.add { color: var(--blue); font-size: 20px; }
.map { height: 240px; border-radius: 16px; overflow: hidden; flex: none; }
.stops { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px; }
.stops .card { padding: 12px 14px; }
.num { width: 24px; height: 24px; border-radius: 50%; background: var(--blue); color: #fff; font-size: 12px; font-weight: 800; display: grid; place-items: center; }
.x { color: var(--sub); }
.opts { flex-wrap: wrap; gap: 12px; margin-top: 8px; font-size: 13px; color: var(--sub); }
.opts input[type='time'], .opts input[type='number'] { height: 32px; border: 1px solid var(--line); border-radius: 8px; padding: 0 6px; margin-left: 4px; }
.opts input[type='number'] { width: 64px; margin-right: 2px; }
.fix { display: flex; align-items: center; gap: 4px; }
.custom { gap: 8px; margin-top: 12px; }
</style>
