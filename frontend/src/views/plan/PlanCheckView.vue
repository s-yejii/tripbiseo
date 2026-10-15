<script setup>
// 일정 검증 (목업 2): 실제 이동시간·영업시간·휴무·고정 일정 여유를 서버가 계산해 지도와 함께 보여 줌
//  - 지도 위 검색 → 분류 아이콘 핀 → '일정에 추가' (가장 덜 돌아가는 자리에 넣음)
//  - 상세 일정: ⋮⋮ 끌어서 순서 바꾸기, 구간마다 도보·대중교통·택시 고르기 → 바로 다시 계산
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { places, plans, trips } from '@/api'
import { usePlan } from '@/stores/plan'
import { dateLabel, dayCount, duration, metres, placeIcon } from '@/utils/format'
import { distance } from '@/utils/geo'
import { toStop } from '@/utils/stops'
import AppBar from '@/components/AppBar.vue'
import BottomSheet from '@/components/BottomSheet.vue'
import MapView from '@/components/map/MapView.vue'

const route = useRoute()
const router = useRouter()
const plan = usePlan()
const tripId = route.query.trip // 저장된 여행을 다시 검증할 때
const draft = ref(null)
const day = ref(1)
const result = ref(null)
const busy = ref(false)
const error = ref('')
const sheet = ref('half')
const dirty = ref(false) // 저장된 여행을 이 화면에서 고쳤는지
const query = ref('')
const found = ref([])
const picked = ref(null)
const listEl = ref(null)
const drag = ref(null)

const MODES = [['walk', '🚶 도보'], ['transit', '🚇 대중교통'], ['taxi', '🚕 택시']]
const LINE = { walk: 'blue', transit: 'green', car: 'orange' }
const r = computed(() => result.value)
const days = computed(() => (draft.value ? Math.max(1, dayCount(draft.value.startDate, draft.value.endDate)) : 1))
const dayList = () => draft.value.stops.filter((s) => s.day === day.value)
const markers = computed(() => [
  ...(r.value?.stops ?? []).map((s, i) => ({
    id: 's' + i, lat: s.lat, lng: s.lng, num: s.fixed ? null : i + 1, kind: s.fixed ? 'fixed' : 'pin', color: s.issue ? 'orange' : s.fixed ? 'red' : 'blue',
    label: s.name, sub: s.fixed ? `${s.arrive} 도착` : s.arrive, subColor: s.issue ? 'orange' : '',
  })),
  ...found.value.map((p, i) => ({ id: 'f' + i, lat: p.lat, lng: p.lng, color: 'white', category: p.category, label: p === picked.value ? p.name : '', selected: p === picked.value })),
])
const lines = computed(() => (r.value?.legs ?? []).map((l) => ({ coords: l.coords, color: LINE[l.mode], width: 5, dashed: l.mode !== 'walk' })))

async function load() {
  busy.value = true
  error.value = ''
  try {
    result.value = await plans.check(draft.value, day.value)
    dayList().forEach((s, i) => (s.time = result.value.stops[i].time)) // 서버가 이어서 정한 시각을 초안에 반영
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
onMounted(async () => {
  try {
    draft.value = tripId ? await trips.get(tripId) : plan.draft
  } catch (e) {
    return (error.value = e.message)
  }
  if (!draft.value?.stops?.length) return router.replace('/')
  load()
})
watch(day, () => ((found.value = []), (picked.value = null), load()))

// 하루 순서를 바꿨을 때: 첫 장소 시작 시각만 두고 나머지(고정 일정 빼고)는 서버가 앞 일정에 이어서 다시 정함
function setDay(list) {
  const start = dayList().filter((s) => !s.fixed).map((s) => s.time).sort()[0] ?? '10:00'
  list.forEach((s, i) => !s.fixed && (s.time = i === 0 ? start : null))
  draft.value.stops = [...draft.value.stops.filter((s) => s.day !== day.value), ...list].sort((a, b) => a.day - b.day)
  dirty.value = true
  load()
}
function setMode(i, mode) {
  dayList()[i].mode = mode
  dirty.value = true
  load()
}

// ---------- 끌어서 순서 바꾸기 ----------
function dragStart(e, i) {
  const mids = [...listEl.value.querySelectorAll('.stop')].map((c) => c.getBoundingClientRect()).map((b) => b.top + b.height / 2)
  drag.value = { from: i, startY: e.clientY, dy: 0, mids }
  e.currentTarget.setPointerCapture(e.pointerId)
}
const dragMove = (e) => drag.value && (drag.value.dy = e.clientY - drag.value.startY)
function dragEnd() {
  const d = drag.value
  drag.value = null
  if (!d) return
  const to = d.mids.filter((m, i) => i !== d.from && m < d.mids[d.from] + d.dy).length // 옮긴 카드보다 위에 남는 카드 수 = 들어갈 자리
  if (to === d.from) return
  const list = dayList()
  list.splice(to, 0, list.splice(d.from, 1)[0])
  setDay(list)
}

// ---------- 지도 위 검색 → 일정에 추가 ----------
const center = computed(() => {
  const l = r.value?.stops ?? []
  return l.length ? { lat: l.reduce((a, s) => a + s.lat, 0) / l.length, lng: l.reduce((a, s) => a + s.lng, 0) / l.length } : null
})
async function search() {
  picked.value = null
  error.value = ''
  if (!query.value.trim()) return (found.value = [])
  try {
    found.value = (await places.search(query.value.trim(), center.value?.lat, center.value?.lng)).filter((p) => !center.value || p.distance <= 3000).slice(0, 15)
    if (!found.value.length) error.value = `일정 근처(3km)에 '${query.value}'이(가) 없어요`
    else sheet.value = 'peek' // 지도에서 핀을 고를 수 있게 내림
  } catch (e) {
    error.value = e.message
  }
}
// 넣을 자리: 앞뒤 장소 사이로 돌아가는 거리가 가장 짧은 곳
function bestSlot(p) {
  const list = dayList()
  let best = { at: list.length, extra: list.length ? distance(list.at(-1), p) : 0 }
  for (let i = 1; i < list.length; i++) {
    const extra = distance(list[i - 1], p) + distance(p, list[i]) - distance(list[i - 1], list[i])
    if (extra < best.extra) best = { at: i, extra }
  }
  return best
}
function add() {
  const list = dayList()
  list.splice(bestSlot(picked.value).at, 0, toStop(picked.value, day.value, null))
  found.value = []
  picked.value = null
  query.value = ''
  setDay(list)
}
const onMarker = (id) => id.startsWith('f') && ((picked.value = found.value[Number(id.slice(1))]), (sheet.value = 'peek'))

// ---------- 저장 ----------
async function save() {
  busy.value = true
  try {
    if (tripId) { // 저장된 여행: 바뀐 시각·수단은 고치고, 새 장소는 추가
      for (const s of draft.value.stops) await (s.id ? trips.updateStop(tripId, s.id, s) : trips.addStop(tripId, s))
      draft.value = await trips.get(tripId)
      dirty.value = false
      return load()
    }
    const saved = await trips.create(draft.value)
    const talk = plan.from === 'ai' ? plan.chat.messages.filter((m) => m.at) : [] // AI 계획 대화를 이 여행의 대화 기록으로
    if (talk.length) await trips.addChats(saved.id, talk).catch(() => {})
    plan.reset()
    router.replace(`/trips/${saved.id}`)
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
const backToPlan = () => router.push(plan.from === 'manual' ? '/plan/manual' : '/plan/ai')
const legText = (l) => `${duration(l.minutes)} · ${l.detail || (l.source === 'estimate' ? '거리로 계산한 예상 시간' : metres(l.meters))}`
const modeOf = (s) => s.mode ?? (s.leg.mode === 'car' ? 'taxi' : s.leg.mode)
</script>

<template>
  <AppBar title="일정 검증" />
  <div class="check">
    <div class="top">
      <div class="bar row">
        <span>📅 {{ dateLabel(r?.date || draft?.startDate) }}</span>
        <span v-if="r?.fixed" class="fixed">📍 {{ r.fixed.name }} {{ r.fixed.time }}</span>
      </div>
      <form class="search" @submit.prevent="search">
        <input v-model="query" placeholder="장소·종류 검색 (예: 분식집, 카페)" maxlength="40" />
        <button class="btn primary sm">검색</button>
      </form>
      <div v-if="days > 1" class="days">
        <button v-for="n in days" :key="n" :class="{ on: day === n }" @click="day = n">Day {{ n }}</button>
      </div>
    </div>
    <div class="map"><MapView :markers="markers" :lines="lines" :fit-key="day + '-' + (r?.stops?.length || 0) + '-' + found.length" :pad="[150, 130, 320, 30]" @marker-click="onMarker" /></div>

    <div v-if="picked" class="pick card">
      <span class="thumb">{{ placeIcon(picked.category) }}</span>
      <div class="grow">
        <b class="ellipsis">{{ picked.name }}</b>
        <small class="muted">{{ picked.category }}<template v-if="picked.rating"> · ★ {{ picked.rating }}</template> · 일정에서 {{ metres(bestSlot(picked).extra) }} 더 돌아가요</small>
      </div>
      <button class="btn primary sm" :disabled="busy" @click="add">일정에 추가</button>
    </div>

    <BottomSheet v-model:state="sheet" :peek="r ? 300 : 120">
      <p v-if="busy" class="muted">실제 이동시간과 영업시간을 확인하는 중…</p>
      <p v-if="error" class="error">{{ error }}</p>
      <template v-if="r">
        <section class="verdict">
          <span class="mark" :class="{ bad: !r.ok }">{{ r.ok ? '✓' : '!' }}</span>
          <div>
            <h2>{{ r.ok ? '이 일정대로 갈 수 있어요!' : r.fixed && r.slackMin < 0 ? `이대로는 ${r.fixed.name}에 늦어요` : '확인이 필요한 곳이 있어요' }}</h2>
            <p class="small">
              <template v-if="r.fixed">{{ r.fixed.name }} 도착 예상 {{ r.fixed.arrive }} ·
                <b :class="r.slackMin >= 0 ? 'ok' : 'late'">{{ r.slackMin >= 0 ? `${r.slackMin}분 여유` : `${-r.slackMin}분 지각` }}</b></template>
              <template v-else>마지막 장소 도착 예상 {{ r.finalArrive }}</template>
            </p>
          </div>
        </section>
        <div class="chips">
          <span class="chip" :class="r.checks.move ? 'green' : 'red'">{{ r.checks.move ? '✓' : '✕' }} 이동시간</span>
          <span class="chip" :class="r.checks.hours ? 'green' : 'red'">{{ r.checks.hours ? '✓' : '✕' }} 영업시간</span>
          <span class="chip" :class="r.checks.closed ? 'green' : 'red'">{{ r.checks.closed ? '✓' : '✕' }} 휴무일</span>
        </div>
        <p v-for="n in draft.notes || []" :key="n.text" class="note">
          ⚠ {{ n.text }} <a v-if="n.url" :href="n.url" target="_blank" rel="noopener">네이버 지도에서 찾기 ↗</a>
        </p>
        <button v-if="!tripId" class="btn primary block" :disabled="busy" @click="save">이 일정으로 저장</button>
        <button v-else-if="dirty" class="btn primary block" :disabled="busy" @click="save">바꾼 일정 저장</button>
        <button v-else class="btn primary block" @click="router.back()">확인</button>

        <h3 class="sub">상세 일정 <small class="muted">⋮⋮를 끌어서 순서 바꾸기</small></h3>
        <ol ref="listEl" class="list">
          <template v-for="(s, i) in r.stops" :key="i">
            <li v-if="s.leg" class="leg">
              <div class="modes">
                <button v-for="[k, t] in MODES" :key="k" :class="{ on: modeOf(s) === k }" :disabled="busy" @click="setMode(i, k)">{{ t }}</button>
              </div>
              <small class="muted">{{ legText(s.leg) }}</small>
            </li>
            <li class="stop" :class="{ dragging: drag?.from === i }" :style="drag?.from === i ? { transform: `translateY(${drag.dy}px)` } : null">
              <span class="idx">{{ i + 1 }}</span>
              <button class="body" @click="s.placeId && router.push(`/places/${s.placeId}`)">
                <span class="thumb">{{ placeIcon(s.category) }}</span>
                <span class="grow">
                  <b class="ellipsis">{{ s.name }}</b>
                  <small class="time">🕑 {{ s.arrive }} · {{ duration(s.stay) }} <span v-if="s.fixed" class="chip red">고정</span></small>
                  <small v-if="s.issue" class="issue">⚠ {{ s.issue }}</small>
                </span>
              </button>
              <span class="handle" aria-label="끌어서 순서 바꾸기" @pointerdown="dragStart($event, i)" @pointermove="dragMove" @pointerup="dragEnd" @pointercancel="dragEnd">⋮⋮</span>
            </li>
          </template>
        </ol>
        <button v-if="!tripId" class="link" @click="backToPlan">{{ plan.from === 'manual' ? '직접 계획으로 돌아가기' : 'AI 대화로 돌아가 다시 짜기' }}</button>
      </template>
    </BottomSheet>
  </div>
</template>

<style scoped>
.check { position: relative; flex: 1; min-height: 0; }
.top { position: absolute; top: 8px; left: 12px; right: 12px; z-index: 10; display: flex; flex-direction: column; gap: 8px; align-items: flex-start; }
.bar { align-self: stretch; justify-content: space-between; padding: 10px 14px; border-radius: 12px; background: #fff; box-shadow: 0 2px 8px rgba(0, 0, 0, .1); font-size: 14px; font-weight: 700; }
.fixed { color: var(--red); }
.search { align-self: stretch; display: flex; gap: 6px; padding: 4px; border-radius: 12px; background: #fff; box-shadow: 0 2px 8px rgba(0, 0, 0, .1); }
.search input { flex: 1; min-width: 0; height: 36px; padding: 0 10px; border: 0; background: none; }
.days { display: flex; gap: 6px; }
.days button { padding: 6px 12px; border-radius: 999px; background: #fff; font-weight: 700; font-size: 13px; box-shadow: 0 1px 4px rgba(0, 0, 0, .15); }
.days button.on { background: var(--ink); color: #fff; }
.map { position: absolute; inset: 0; }
.pick { position: absolute; left: 12px; right: 12px; bottom: 312px; z-index: 25; display: flex; gap: 10px; align-items: center; padding: 10px 12px; box-shadow: 0 4px 14px rgba(0, 0, 0, .15); }
.pick small { display: block; font-size: 12px; }
.verdict { display: flex; gap: 12px; align-items: center; }
.mark { width: 40px; height: 40px; flex: none; border-radius: 50%; background: var(--green); color: #fff; font-weight: 900; font-size: 20px; display: grid; place-items: center; }
.mark.bad { background: var(--red); }
.verdict h2 { font-size: 18px; font-weight: 800; }
.verdict p { color: var(--sub); margin-top: 2px; }
.ok { color: var(--green); } .late { color: var(--red); }
.chips { display: flex; gap: 6px; margin: 12px 0; }
.note { padding: 10px 12px; border-radius: 12px; background: var(--orange-soft); font-size: 13px; margin-bottom: 6px; }
.note a { color: #03c75a; font-weight: 700; }
.sub { font-size: 16px; margin: 20px 0 6px; }
.sub small { font-size: 12px; font-weight: 500; }
.list { list-style: none; margin: 0; padding: 0; }
.leg { padding: 6px 0 6px 32px; }
.modes { display: flex; gap: 4px; margin-bottom: 4px; }
.modes button { padding: 4px 9px; border-radius: 999px; background: var(--bg); font-size: 12px; font-weight: 700; color: var(--sub); }
.modes button.on { background: var(--blue-soft); color: var(--blue); box-shadow: inset 0 0 0 1.5px var(--blue); }
.leg small { display: block; font-size: 12px; }
.stop { display: flex; align-items: center; gap: 10px; margin: 4px 0; background: #fff; }
.stop.dragging { position: relative; z-index: 2; opacity: .9; }
.stop.dragging .body { box-shadow: 0 6px 16px rgba(0, 0, 0, .15); }
.idx { width: 22px; height: 22px; flex: none; border-radius: 50%; background: var(--bg); color: var(--sub); font-size: 12px; display: grid; place-items: center; }
.body { flex: 1; min-width: 0; display: flex; gap: 12px; align-items: center; padding: 10px 12px; border: 1px solid var(--line); border-radius: 14px; text-align: left; background: #fff; }
.thumb { width: 44px; height: 44px; flex: none; border-radius: 10px; background: var(--bg); display: grid; place-items: center; font-size: 22px; }
.body b, .body small { display: block; }
.body b { font-size: 15px; }
.body small { font-size: 12px; margin-top: 2px; }
.time { display: flex !important; align-items: center; gap: 6px; }
.time .chip { padding: 1px 6px; font-size: 11px; }
.issue { color: #b45309; }
.handle { flex: none; padding: 10px 6px; color: var(--sub); font-weight: 900; letter-spacing: -2px; cursor: grab; touch-action: none; user-select: none; }
.link { display: block; margin: 16px auto 0; color: var(--sub); font-size: 13px; text-decoration: underline; }
</style>
