<script setup>
// 여행 중 실시간 재분석 (목업 3)
//  - 내 위치(GPS)를 서버로 보내 도착·출발을 기록하고, 남은 일정을 지금 시각 기준으로 다시 계산
//  - 출발 10분 전부터 1분마다 / 출발 시각 초과 / 고정 일정 지각 / 영업 끝·휴무 / 지난 일정이면 배너 + 시스템 알림
//  - 지도 버튼: '지금 갈 수 있는 구역'(도보 10·20·30분, 20분 머물고 돌아와도 안 늦는 곳만 초록) / '일정 위험도'(정상·여유 부족·지연)
//  - 날씨: 비·눈 예보가 있으면 맨 위 배너 + 3초 비·눈 효과 (⚙에서 끄기)
//  - 채팅: "근처 카페 추천" → 반경 고르기 → 평점 상위 3곳 핀 → 하나 골라 일정에 넣기 / "비 와, 실내로" → 대안 A·B·C
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { live, trips } from '@/api'
import { useLive } from '@/stores/live'
import { dayCount, duration, metres, stamp, toMin } from '@/utils/format'
import { circlePolygon, distance } from '@/utils/geo'
import { locateOnce, notify, watchLocation } from '@/utils/locate'
import BottomSheet from '@/components/BottomSheet.vue'
import ChatInput from '@/components/ChatInput.vue'
import Confirm from '@/components/Confirm.vue'
import Itinerary from '@/components/Itinerary.vue'
import MapView from '@/components/map/MapView.vue'
import StopEditor from '@/components/StopEditor.vue'
import WeatherFx from '@/components/WeatherFx.vue'

const route = useRoute()
const router = useRouter()
const store = useLive()
const id = route.params.id
const trip = ref(null)
const status = ref(null)
const pos = ref(store.pos)
const error = ref('')
const listOpen = ref(false)
const sheet = ref('half')
const editing = ref(null)
const chat = ref({ busy: false, reply: '', askRadius: null, picks: [], selected: null })
const altBusy = ref(false)
const layer = ref('reach') // 'reach' | 'risk' | ''
const wx = ref({ on: localStorage.getItem('tb-weather') !== 'off', closed: '', fx: 0, settings: false })
const confirmPick = ref(null) // 마감 임박·영업 끝 장소를 고를 때 확인 창
const fitKey = ref(0)
const shown = new Set() // 이미 알린 알림
let lastSent = null, stopWatch, timer

// 빨강은 고정 일정에만 씀. 못 가는 곳은 회색
const REACH = { ok: ['green', '갈 수 있어요'], tight: ['orange', '시간 줄이면 OK'], no: ['gray', '복귀 어려워요'], closed: ['gray', '영업 끝'], missed: ['gray', '계획 시간 지남'] }
// 일정 위험도: 영업 끝·지난 일정·복귀 어려움·10분 넘게 늦으면 지연, 조금 늦거나 시간을 줄여야 하면 여유 부족
const RISK = { ok: ['green', '정상'], tight: ['orange', '여유 부족'], late: ['red', '지연'] }
const lateMin = (s) => toMin(s.eta) - toMin(s.time)
const risk = (s) => (['closed', 'missed', 'no'].includes(s.reach) || lateMin(s) > 10 ? 'late' : s.reach === 'tight' || lateMin(s) > 0 ? 'tight' : 'ok')
const riskStops = computed(() => (status.value?.stops ?? []).filter((s) => !['done', 'here'].includes(s.reach)))
const markers = computed(() => {
  const list = (status.value?.stops ?? []).filter((s) => s.reach !== 'done').map((s) => {
    const [color, sub] = layer.value === 'risk' && s.reach !== 'here' ? [RISK[risk(s)][0], lateMin(s) > 0 ? `${s.eta} 도착 (+${lateMin(s)}분)` : RISK[risk(s)][1]]
      : s.reach === 'closed' ? ['gray', s.closedReason || '영업 끝'] : s.fixed ? ['red', `${s.time} 시작`] : REACH[s.reach] ?? ['blue', s.eta]
    return { id: 's' + s.id, lat: s.lat, lng: s.lng, kind: s.fixed ? 'fixed' : 'pin', color, label: s.name, sub, subColor: color === 'gray' ? 'gray' : s.fixed ? 'red' : color, category: s.category }
  })
  chat.value.picks.forEach((p, i) => list.push({ id: 'p' + i, lat: p.lat, lng: p.lng, num: i + 1, color: p.closingSoon ? 'orange' : 'purple', label: p.name,
    sub: `★ ${p.rating ?? '-'} · ${metres(p.distance)}${p.closeAt ? ` · ${p.closeAt} 마감` : ''}`, subColor: p.closingSoon ? 'orange' : '', selected: chat.value.selected === p }))
  if (pos.value) list.push({ id: 'me', lat: pos.value.lat, lng: pos.value.lng, kind: 'me', label: '현재 위치', sub: status.value?.now })
  return list
})
const lines = computed(() => {
  const out = []
  const rest = (status.value?.stops ?? []).filter((s) => s.reach !== 'done')
  if (rest.length > 1) out.push({ coords: rest.map((s) => [s.lat, s.lng]), color: 'gray', dashed: true, width: 3 })
  if (status.value?.next?.coords?.length) out.push({ coords: status.value.next.coords, color: 'blue', width: 5 })
  return out
})
// 추천 핀이 있으면 내 위치 + 핀만, 구역을 켰으면 30분 구역 전체, 아니면 남은 일정 전체
const focus = computed(() => {
  if (chat.value.picks.length && pos.value) return [[pos.value.lat, pos.value.lng], ...chat.value.picks.map((p) => [p.lat, p.lng])]
  return polygons.value.length ? polygons.value[0].coords : null
})
// 지금 갈 수 있는 구역: 내 위치에서 도보 10·20·30분(직선 × 1.3 우회, 분당 75m). 기준 일정까지 여유로 '20분 머물고 돌아와도 안 늦는' 구역만 초록
//  서버가 준 여유(area.slackMin)를 5초마다 지난 시간만큼 줄여 다시 그림 → 시간이 지나면 초록 구역이 줄어듦
const statusAt = ref(Date.now())
const tick = ref(Date.now())
const area = computed(() => {
  const a = status.value?.area
  if (!a) return null
  const slack = Math.round(a.slackMin - (tick.value - statusAt.value) / 60000)
  return { ...a, slack, oneWay: Math.floor((slack - 20) / 2) }
})
// 원 색 = 걸어서 몇 분 (초록 10분 · 주황 20분 · 빨강 30분). 범례(왼쪽 위)에 원마다 다녀와도 되는지 표시, 다녀오면 늦는 원은 옅게
const RINGS = [{ min: 30, color: 'red' }, { min: 20, color: 'orange' }, { min: 10, color: 'green' }]
const LEVEL = { ok: '20분 머물고 와도 돼요', tight: '들렀다 바로 오면 돼요', no: '다녀오면 늦어요' }
const ringM = (m) => (m * 75) / 1.3 // 분당 75m, 길 우회 1.3배
const ringLevel = (m) => (!area.value || m <= area.value.oneWay ? 'ok' : m <= Math.floor(area.value.slack / 2) ? 'tight' : 'no')
const legendOpen = ref(false) // 처음엔 접힌 상태 (누르면 색별 설명)
const polygons = computed(() => {
  if (layer.value !== 'reach' || !pos.value || chat.value.picks.length) return []
  return RINGS.map((r) => ({ coords: circlePolygon(pos.value, ringM(r.min)), color: r.color, fill: ringLevel(r.min) === 'no' ? 0.04 : 0.12 }))
})
const reachText = computed(() => {
  const a = area.value
  if (!a) return '남은 일정이 없어 시간 제한 없이 다닐 수 있어요'
  if (a.slack < 0) return '이미 늦었어요. 아래 대안을 확인하세요'
  if (a.oneWay < 10) return '들렀다 오기엔 빠듯해요. 바로 출발하는 게 좋아요'
  return `도보 ${a.oneWay >= 30 ? 30 : a.oneWay >= 20 ? 20 : 10}분 안쪽은 20분 머물고 와도 늦지 않아요`
})

async function refresh() {
  if (!pos.value) return
  try {
    status.value = await live.status(id, pos.value.lat, pos.value.lng)
    statusAt.value = tick.value = Date.now()
    const w = status.value.weather
    if (w && wx.value.on && wx.value.closed !== w.time && !shown.has('wx-' + w.time)) {
      shown.add('wx-' + w.time)
      wx.value.fx++ // 처음 뜰 때 한 번 비·눈 효과
      notify(`${w.time}부터 ${w.snow ? '눈' : '비'} 예보`, w.outdoor.length ? `야외 일정 ${w.outdoor.length}곳에 영향이 있어요` : '남은 일정은 실내예요', 'tripbiseo-weather')
    }
    const a = status.value.alert
    if (a && !shown.has(a.key)) {
      shown.add(a.key)
      notify(a.title, a.text, a.key.startsWith('leave-') ? 'tripbiseo-leave' : a.key)
    }
    // 출발 10분 전부터는 맨 위 알림이 다른 것이어도 1분마다 따로 울림
    const l = status.value.leave
    const lk = l && `leave-${l.stopId}-${l.minutesLeft}`
    if (l && !shown.has(lk) && a?.key !== lk) {
      shown.add(lk)
      notify(l.minutesLeft > 0 ? `${l.minutesLeft}분 뒤 출발해야 해요` : '출발할 시간이에요', `${l.name} → ${l.leaveAt} 출발`, 'tripbiseo-leave')
    }
  } catch (e) {
    error.value = e.message
  }
}

// GPS가 바뀌면 50m 이상 움직였거나 1분이 지났을 때만 서버에 보냄
async function onPos(p) {
  pos.value = store.pos = p
  error.value = ''
  const moved = !lastSent || distance(lastSent, p) > 50 || Date.now() - lastSent.at > 60000
  if (!moved) return
  lastSent = { ...p, at: Date.now() }
  await live.track(id, p.lat, p.lng, p.accuracy).catch(() => {})
  refresh()
}
async function locateMe() {
  try {
    await onPos(await locateOnce())
    fitKey.value++
  } catch (e) {
    error.value = e.message
  }
}

onMounted(async () => {
  try {
    trip.value = await trips.get(id)
  } catch (e) {
    return (error.value = e.message)
  }
  if (trip.value.status !== 'ongoing') return router.replace(`/trips/${id}`)
  if ('Notification' in window && Notification.permission === 'default') Notification.requestPermission()
  stopWatch = watchLocation(onPos, (e) => (error.value = e.message))
  timer = setInterval(() => (pos.value ? refresh() : (error.value = '위치를 받지 못해 다시 계산하지 못했어요. ◎ 내 위치를 눌러 주세요')), 60000)
  if (pos.value) refresh()
})
const ticker = setInterval(() => (tick.value = Date.now()), 5000) // 구역 색이 바뀌는 속도
onBeforeUnmount(() => (stopWatch?.(), clearInterval(timer), clearInterval(ticker)))

// ---------- 채팅 (주고받은 말은 이 여행 대화 기록에 저장) ----------
const keep = (...messages) => trips.addChats(id, messages).catch(() => {})
async function send(text) {
  if (!pos.value) return (error.value = '먼저 내 위치를 찾아 주세요')
  chat.value = { ...chat.value, busy: true, reply: '', askRadius: null }
  const me = { role: 'me', text, at: stamp() }
  try {
    const res = await live.assist(id, text, pos.value.lat, pos.value.lng)
    if (res.kind === 'ask-radius') chat.value.askRadius = res.category
    if (res.kind === 'alternatives') {
      keep(me, { role: 'ai', text: '남은 일정 대안 A·B·C를 만들었어요.', at: stamp() })
      store.alternatives = res.data
      return router.push(`/trips/${id}/alternatives`)
    }
    if (res.kind === 'text') chat.value.reply = res.text
    keep(me, { role: 'ai', text: res.kind === 'text' ? res.text : `${res.category}을(를) 어느 범위에서 찾을까요?`, at: stamp() })
  } catch (e) {
    chat.value.reply = e.message
  } finally {
    chat.value.busy = false
  }
}
async function recommend(radius) {
  const category = chat.value.askRadius
  chat.value = { ...chat.value, busy: true, askRadius: null }
  try {
    const res = await live.recommend(id, pos.value.lat, pos.value.lng, radius, category)
    chat.value.picks = res.places
    const soon = res.places.filter((p) => p.closingSoon)
    chat.value.reply = res.places.length
      ? `${metres(radius)} 안 ${category} 평점 상위 ${res.places.length}곳이에요. 핀을 눌러 골라 주세요.` +
        (soon.length ? `\n⚠ ${soon.map((p) => `${p.name}(${p.closeAt} 마감)`).join(', ')}은(는) 곧 문을 닫아요.` : '') +
        (res.closedSkipped ? `\n지금 문을 닫은 ${res.closedSkipped}곳은 뺐어요.` : '')
      : `${metres(radius)} 안에 지금 열려 있는 ${category}가 없어요${res.closedSkipped ? ` (문 닫은 ${res.closedSkipped}곳 제외)` : ''}. 반경을 넓혀 볼까요?`
    if (!res.places.length) chat.value.askRadius = category
    keep({ role: 'ai', text: chat.value.reply, at: stamp() })
    fitKey.value++
  } catch (e) {
    chat.value.reply = e.message
  } finally {
    chat.value.busy = false
  }
}
function onMarker(mid) {
  if (mid.startsWith('p')) chat.value.selected = chat.value.picks[Number(mid.slice(1))]
  else if (mid.startsWith('s')) {
    const s = trip.value.stops.find((x) => 's' + x.id === mid)
    if (s?.placeId) router.push(`/places/${s.placeId}`)
  }
}
// 곧 문을 닫거나(머무는 시간보다 남은 영업시간이 짧음) 영업시간을 모르면 한 번 더 확인
function pickWarning(p) {
  if (p.open === false) return `${p.name}은(는) 도착할 때 영업 중이 아니에요.`
  if (p.closingSoon) return `${p.name}은(는) ${p.closeAt}에 문을 닫아요. 도착하면 ${p.minutesLeft}분밖에 못 머물러요.`
  return null
}
function askInsert() {
  if (pickWarning(chat.value.selected)) confirmPick.value = chat.value.selected
  else insertPick()
}
async function insertPick() {
  confirmPick.value = null
  const p = chat.value.selected
  chat.value.busy = true
  try {
    const res = await live.insert(id, p.id, pos.value.lat, pos.value.lng)
    trip.value = res.trip
    chat.value = { busy: false, reply: res.message, askRadius: null, picks: [], selected: null }
    refresh()
  } catch (e) {
    chat.value.reply = e.message
    chat.value.busy = false
  }
}
async function showAlternatives(reason = 'delay', request = '') {
  if (altBusy.value) return
  altBusy.value = true
  try {
    store.alternatives = await live.alternatives(id, pos.value.lat, pos.value.lng, reason, request)
    router.push(`/trips/${id}/alternatives`)
  } catch (e) {
    error.value = e.message
  } finally {
    altBusy.value = false
  }
}
const hoursText = (p) => (p.open === false ? '도착할 때 영업 안 함' : p.closeAt ? `오늘 ${p.closeAt}까지 · 도착 후 ${duration(p.minutesLeft)}` : '영업시간 정보 없음')

// ---------- 일정 목록 ----------
const days = computed(() => (trip.value ? dayCount(trip.value.startDate, trip.value.endDate) : 1))
const listStops = computed(() => (trip.value?.stops ?? []).map((s) => ({ ...s, arrive: status.value?.stops.find((x) => x.id === s.id)?.eta ?? s.time })))
function onSheet(v) {
  if (v === 'peek') listOpen.value = false // 끝까지 내리면 닫힘
}
async function saveStop(f) {
  try {
    await trips.updateStop(id, editing.value.id, { ...editing.value, ...f })
    trip.value = await trips.get(id)
    editing.value = null
    refresh()
  } catch (e) {
    error.value = e.message
  }
}
async function removeStop() {
  try {
    await trips.removeStop(id, editing.value.id)
    trip.value = await trips.get(id)
    editing.value = null
    refresh()
  } catch (e) {
    error.value = e.message
  }
}
const askFinish = ref(false)
async function finish() {
  try {
    await trips.finish(id)
    router.replace(`/trips/${id}/retro`)
  } catch (e) {
    error.value = e.message
    askFinish.value = false
  }
}
const s = computed(() => status.value)
// 맨 위 알림: ✕로 지우면 같은 알림은 다시 안 뜸 (새 알림은 뜸). 알림이 떠 있으면 아래 '늦어요' 카드는 숨김(중복)
const dismissed = ref(new Set())
const topAlert = computed(() => (s.value?.alert && !dismissed.value.has(s.value.alert.key) ? s.value.alert : null))
const late = computed(() => !!s.value && (s.value.replan || s.value.fixed?.slackMin < 0))
const dismiss = () => (dismissed.value = new Set([...dismissed.value, topAlert.value.key]))
const weather = computed(() => (wx.value.on && s.value?.weather && wx.value.closed !== s.value.weather.time ? s.value.weather : null))
function toggleWeather() {
  wx.value.on = !wx.value.on
  try { localStorage.setItem('tb-weather', wx.value.on ? 'on' : 'off') } catch { /* 저장 못 해도 이번 화면에선 적용 */ }
}
</script>

<template>
  <div class="live">
    <div class="map"><MapView :markers="markers" :lines="lines" :polygons="polygons" :focus="focus" :pad="polygons.length ? [300, 20, 150, 20] : [90, 150, 300, 30]"
      :fit-key="fitKey + '-' + (s ? 1 : 0) + layer" @marker-click="onMarker" /></div>


    <WeatherFx v-if="weather && wx.fx" :key="wx.fx" :snow="weather.snow" />
    <div class="stack">
      <header class="top">
        <button class="back" aria-label="뒤로" @click="router.push('/')">‹</button>
        <div v-if="topAlert" class="alert" :class="topAlert.level">
          <span class="ic">!</span>
          <div class="grow">
            <b>{{ topAlert.title }}</b><small>{{ topAlert.text }}</small>
            <button v-if="late" class="btn primary sm alt" :disabled="altBusy" @click="showAlternatives()">{{ altBusy ? '찾는 중…' : '대안 보기' }}</button>
          </div>
          <button class="x" aria-label="알림 지우기" @click="dismiss">✕</button>
        </div>
        <div v-else class="alert calm"><span class="ic">✓</span><div><b>{{ trip?.title }}</b><small>{{ s ? `${s.now} 기준으로 다시 계산했어요` : '내 위치를 찾는 중…' }}</small></div></div>
      </header>
      <div v-if="weather" class="wx">
        <span class="wx-ic">{{ weather.snow ? '🌨' : '🌧' }}</span>
        <div class="grow">
          <b>{{ weather.time }}부터 {{ weather.snow ? '눈' : '비' }} (강수확률 {{ weather.pop }}%)</b>
          <small>{{ weather.outdoor.length ? `야외 일정 ${weather.outdoor.join(', ')}에 영향이 있어요` : '남은 일정은 실내예요' }}</small>
        </div>
        <button v-if="weather.outdoor.length" class="btn primary sm" :disabled="altBusy" @click="showAlternatives('weather', '비가 와서 실내로 바꿔줘')">실내 대안</button>
        <button class="x" aria-label="날씨 알림 닫기" @click="wx.closed = weather.time">✕</button>
      </div>
      <div class="tools">
        <!-- 구역 범례: 누르면 접기/펴기 -->
        <button v-if="polygons.length" class="legend" :class="{ folded: !legendOpen }" @click="legendOpen = !legendOpen">
          <span class="lg-head"><i v-for="r in RINGS" :key="r.min" class="dot" :class="r.color" />{{ area ? (area.slack >= 0 ? `여유 ${area.slack}분` : `${-area.slack}분 늦음`) : '걸어서 갈 수 있는 거리' }} {{ legendOpen ? '▴' : '▾' }}</span>
          <template v-if="legendOpen">
            <small v-if="area" class="lg-basis">{{ area.basis }} {{ area.basisTime }}까지 · {{ reachText }}</small>
            <span v-for="r in [...RINGS].reverse()" :key="r.min" class="lg-row" :class="{ faded: ringLevel(r.min) === 'no' }">
              <i class="sw" :class="r.color" /><b>도보 {{ r.min }}분</b>{{ LEVEL[ringLevel(r.min)] }}
            </span>
          </template>
        </button>
        <div class="floating">
          <button class="pill" @click="listOpen = true">📋 일정 보기</button>
          <button class="pill" @click="locateMe">◎ 내 위치</button>
          <button class="pill" aria-label="날씨 알림 설정" @click="wx.settings = !wx.settings">⚙</button>
          <label v-if="wx.settings" class="pill setting"><input type="checkbox" :checked="wx.on" @change="toggleWeather" /> 날씨 알림·비/눈 효과</label>
        </div>
      </div>
    </div>

    <section class="panel">
      <p v-if="error" class="error small">{{ error }}</p>
      <div v-if="layer === 'risk' && s && !chat.picks.length" class="info card">
        <b>남은 일정 상태</b>
        <div v-for="x in riskStops" :key="x.id" class="risk-row small">
          <span class="grow ellipsis">{{ x.name }} · {{ x.time }}</span>
          <span class="chip" :class="RISK[risk(x)][0]">{{ lateMin(x) > 0 ? `+${lateMin(x)}분` : RISK[risk(x)][1] }}</span>
        </div>
        <span v-if="!riskStops.length" class="small muted">남은 일정이 없어요</span>
      </div>

      <div v-if="chat.selected" class="pick card">
        <div class="grow">
          <b>{{ chat.selected.name }}</b>
          <p class="muted small">★ {{ chat.selected.rating ?? '-' }} (리뷰 {{ chat.selected.reviewCount ?? 0 }}) · {{ metres(chat.selected.distance) }} · {{ chat.selected.category }}</p>
          <p class="small hours" :class="{ soon: chat.selected.closingSoon }">🕘 {{ hoursText(chat.selected) }}<template v-if="chat.selected.closingSoon"> · 곧 마감</template></p>
        </div>
        <button class="btn primary sm" :disabled="chat.busy" @click="askInsert">일정에 넣기</button>
        <button class="x" aria-label="추천 닫기" @click="chat = { ...chat, picks: [], selected: null, reply: '' }">✕</button>
      </div>
      <template v-else-if="s?.fixed">
        <div v-if="s.fixed.slackMin < 0" v-show="!topAlert" class="result bad">
          <span class="ic">⏱</span>
          <div class="grow"><b>지금 일정대로면 {{ s.fixed.name }}에 늦어요</b><small>도착 예상 {{ s.fixed.eta }} · <em>{{ -s.fixed.slackMin }}분 지각</em></small></div>
          <button class="btn primary sm" :disabled="altBusy" @click="showAlternatives()">{{ altBusy ? '찾는 중…' : '대안 보기' }}</button>
        </div>
        <div v-else class="result good">
          <span class="ic">✓</span>
          <div class="grow"><b>{{ s.fixed.name }}에 늦지 않아요</b><small>도착 예상 {{ s.fixed.eta }} · {{ s.fixed.slackMin }}분 여유</small></div>
        </div>
      </template>
      <div v-if="s?.replan && !topAlert && !chat.selected && !(s.fixed && s.fixed.slackMin < 0)" class="result bad">
        <span class="ic">⏱</span>
        <div class="grow"><b>일정이 틀어졌어요</b><small>남은 일정을 지금 시각 기준으로 다시 짜 볼까요?</small></div>
        <button class="btn primary sm" :disabled="altBusy" @click="showAlternatives()">{{ altBusy ? '찾는 중…' : '대안 보기' }}</button>
      </div>
      <p v-if="altBusy" class="muted small">지금 위치·시각에서 대안 A·B·C를 계산하는 중이에요 (최대 20초)</p>
      <p v-if="s?.leave && !(s.alert?.key || '').startsWith('leave-')" class="leave">⏰ {{ s.leave.minutesLeft > 0 ? `${s.leave.minutesLeft}분 뒤` : '지금' }} {{ s.leave.atStop ? `${s.leave.name}에서 출발` : `${s.leave.name}(으)로 출발` }} ({{ s.leave.leaveAt }})</p>
      <p v-if="s?.hint && !chat.selected" class="hint">💡 {{ s.hint }}</p>

      <p v-if="chat.reply" class="reply">{{ chat.reply }}</p>
      <div v-if="chat.askRadius" class="radius">
        <span class="small muted">어느 범위에서 찾을까요?</span>
        <button v-for="r in [300, 500, 1000]" :key="r" class="chip blue" @click="recommend(r)">{{ metres(r) }}</button>
      </div>
      <div class="layers">
        <button :class="{ on: layer === 'reach' }" @click="layer = layer === 'reach' ? '' : 'reach'">⏱ 지금 갈 수 있는 구역</button>
        <button :class="{ on: layer === 'risk' }" @click="layer = layer === 'risk' ? '' : 'risk'">⚠ 일정 위험도</button>
      </div>
      <ChatInput :busy="chat.busy" placeholder="예) 근처 카페 추천해줘 / 비 와서 실내로 바꿔줘" @send="send" />
    </section>

    <BottomSheet v-if="listOpen" v-model:state="sheet" @update:state="onSheet">
      <div class="row list-head">
        <h2 class="grow">{{ trip.title }}</h2>
        <button class="btn ghost sm" @click="askFinish = true">여행 마치기</button>
      </div>
      <Itinerary :stops="listStops" :start-date="trip.startDate" :current-id="s?.currentStopId" editable
        @select="(x) => x.placeId && router.push(`/places/${x.placeId}`)" @edit="(x) => (editing = x)" />
    </BottomSheet>
    <StopEditor v-if="editing" :stop="editing" :days="days" @save="saveStop" @remove="removeStop" @close="editing = null" />
    <Confirm v-if="confirmPick" title="그래도 선택하시겠습니까?" :text="pickWarning(confirmPick)" ok="그래도 넣기" @ok="insertPick" @close="confirmPick = null" />
    <Confirm v-if="askFinish" title="여행을 마칠까요?" text="위치 기록을 멈추고, 다닌 길은 회고에서 볼 수 있어요." ok="여행 마치기" @ok="finish" @close="askFinish = false" />
  </div>
</template>

<style scoped>
.live { position: relative; flex: 1; min-height: 0; }
.map { position: absolute; inset: 0; }
.top { display: flex; gap: 8px; align-items: flex-start; }
.back { width: 40px; height: 40px; flex: none; border-radius: 50%; background: #fff; font-size: 22px; box-shadow: 0 2px 8px rgba(0, 0, 0, .15); }
.alert { flex: 1; display: flex; gap: 10px; align-items: center; padding: 10px 12px; border-radius: 14px; background: #fff; box-shadow: 0 2px 8px rgba(0, 0, 0, .12); }
.alert .ic { width: 30px; height: 30px; flex: none; border-radius: 50%; display: grid; place-items: center; color: #fff; font-weight: 900; background: var(--green); }
.alert b { display: block; font-size: 15px; }
.alert small { display: block; font-size: 12px; color: var(--sub); }
.alert.warn { background: var(--orange-soft); } .alert.warn .ic { background: var(--orange); }
.alert.danger { background: var(--red-soft); } .alert.danger .ic { background: var(--red); }
.stack { position: absolute; top: 8px; left: 10px; right: 10px; z-index: 11; display: flex; flex-direction: column; gap: 8px; pointer-events: none; } /* 알림 → 날씨 → 범례·버튼 순서로 쌓임 */
.stack > :not(.tools), .tools > * { pointer-events: auto; }
.floating { align-self: flex-end; display: flex; flex-direction: column; align-items: flex-end; gap: 8px; }
.pill { padding: 9px 14px; border-radius: 999px; background: #fff; font-weight: 700; font-size: 13px; box-shadow: 0 2px 8px rgba(0, 0, 0, .15); }
.panel { position: absolute; left: 0; right: 0; bottom: 0; z-index: 15; padding: 12px 0 0; background: linear-gradient(transparent, #fff 18px); }
.panel > *:not(.chat-input) { margin: 0 12px 8px; }
.result { display: flex; gap: 10px; align-items: center; padding: 12px; border-radius: 14px; background: #fff; box-shadow: 0 2px 10px rgba(0, 0, 0, .1); }
.result .ic { width: 34px; height: 34px; flex: none; border-radius: 50%; display: grid; place-items: center; color: #fff; }
.result.bad .ic { background: var(--red); } .result.good .ic { background: var(--green); }
.result b { display: block; font-size: 15px; }
.result.bad b { color: var(--red); }
.result small { color: var(--sub); font-size: 12px; }
.result em { color: var(--red); font-style: normal; font-weight: 700; }
.hint { padding: 10px 12px; border-radius: 12px; background: var(--orange-soft); font-size: 13px; }
.reply { padding: 10px 12px; border-radius: 14px; background: var(--blue-soft); font-size: 14px; white-space: pre-line; }
.radius { display: flex; align-items: center; gap: 6px; }
.pick { display: flex; gap: 10px; align-items: center; box-shadow: 0 2px 10px rgba(0, 0, 0, .1); }
.pick b { font-size: 16px; }
.pick .x { color: var(--sub); padding: 4px; }
.list-head h2 { font-size: 20px; font-weight: 800; }
.hours { margin-top: 2px; color: var(--sub); }
.hours.soon { color: #b45309; font-weight: 700; }
.leave { padding: 8px 12px; border-radius: 12px; background: var(--orange-soft); font-size: 13px; font-weight: 700; }
.wx { display: flex; gap: 10px; align-items: center; padding: 10px 12px; border-radius: 14px; background: #eff6ff; box-shadow: 0 2px 8px rgba(0, 0, 0, .12); }
.wx-ic { font-size: 22px; }
.wx b { display: block; font-size: 14px; }
.wx small { display: block; font-size: 12px; color: var(--sub); }
.wx .x { color: var(--sub); padding: 4px; }
.setting { display: flex; gap: 6px; align-items: center; }
.info { display: flex; flex-direction: column; gap: 6px; padding: 12px; background: rgba(255, 255, 255, .96); box-shadow: 0 2px 10px rgba(0, 0, 0, .1); }
.info b { font-size: 14px; }
.tools { display: flex; justify-content: space-between; align-items: flex-start; gap: 8px; }
.tools .floating { margin-left: auto; }
.legend { display: flex; flex-direction: column; align-items: flex-start; gap: 6px; max-width: 66%; padding: 9px 12px; border-radius: 14px; background: rgba(255, 255, 255, .95);
  box-shadow: 0 2px 8px rgba(0, 0, 0, .12); text-align: left; }
.legend.folded { border-radius: 999px; padding: 7px 12px; }
.lg-head { display: flex; align-items: center; gap: 4px; font-size: 13px; font-weight: 800; }
.lg-basis { font-size: 11px; color: var(--sub); }
.lg-row { display: flex; align-items: center; gap: 6px; font-size: 12px; }
.lg-row b { font-size: 12px; }
.lg-row.faded { color: var(--sub); }
.dot { width: 9px; height: 9px; border-radius: 50%; display: inline-block; }
.dot.green, .sw.green { background: rgba(22, 163, 74, .35); border: 1.5px solid var(--green); }
.dot.orange, .sw.orange { background: rgba(245, 158, 11, .35); border: 1.5px solid var(--orange); }
.dot.red, .sw.red { background: rgba(239, 68, 68, .3); border: 1.5px solid var(--red); }
.sw { width: 14px; height: 14px; border-radius: 4px; display: inline-block; flex: none; }
.alert .x { color: var(--sub); padding: 0 2px; align-self: flex-start; }
.alert .alt { margin-top: 6px; }
.risk-row { display: flex; align-items: center; gap: 8px; }
.risk-row .chip { padding: 2px 8px; font-size: 12px; }
.layers { display: flex; gap: 6px; }
.layers button { padding: 7px 12px; border-radius: 999px; background: rgba(255, 255, 255, .85); backdrop-filter: blur(4px); font-size: 13px; font-weight: 700; box-shadow: 0 1px 6px rgba(0, 0, 0, .12); }
.layers button.on { background: var(--ink); color: #fff; }
</style>
