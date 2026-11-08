<script setup>
// 일정 화면 공용 틀: 위 지도(핀·동선) + 아래 시트(제목·미리 확인할 것·일정 목록) + 맨 아래 버튼
// 일정 점검(S10-a), 여행 상세(S11, S11-b), 테마 코스(S04-d)가 같이 써요
//   slot right : 지도 위 오른쪽 버튼 (선택한 칸 번호를 받아요)
//   slot below : 일정 목록 아래 (예: 챙길 것)
//   slot cta   : 맨 아래 버튼
//   slot head  : 제목 아래 (예: 일차 고르기)
//   simMe·activeStop: 이동 미리보기의 움직이는 점과 강조할 칸 (일정 점검, 11/04)
//   reorderable: 목록 칸을 끌어 순서 바꾸기 → reorder 이벤트(원래 번호를 새 순서로 담은 목록) (11/04)
//   지도: 점검 결과에 구간별 경로(trip.legs)가 있으면 진짜 길로, 없으면 장소끼리 점선
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import BottomSheet from '@/components/common/BottomSheet.vue'
import TopBar from '@/components/common/TopBar.vue'
import TripBudget from '@/components/trip/TripBudget.vue'
import TripTimeline from '@/components/trip/TripTimeline.vue'
import CheckList from '@/components/trip/CheckList.vue'
import KakaoMap from '@/components/common/KakaoMap.vue'

const props = defineProps({
  trip: { type: Object, required: true },
  badge: { type: Object, default: null }, // { text: '오늘', cls: 'badge--coral' }
  sub: { type: String, default: '' }, // 제목 아래 줄을 바꾸고 싶을 때
  back: { type: String, default: '/trips' },
  rainSwap: { type: Boolean, default: false },
  reorderable: { type: Boolean, default: false },
  modeEditable: { type: Boolean, default: false }, // 이동 줄을 눌러 수단 고르기 (일정 점검 초안만)
  showBudget: { type: Boolean, default: false }, // 1인 예상 금액 줄 (일정 점검)
  simMe: { type: Object, default: null }, // 이동 미리보기의 점 { lat, lng }
  activeStop: { type: Number, default: null }, // 이동 미리보기로 지금 머무는 칸
})
const emit = defineEmits(['reorder', 'change-mode'])
const route = useRoute() // 지금 주소 (일정 점검은 ?day=N으로 일차를 알려 줘요)
const hasLegs = computed(() => (props.trip.legs || []).some((leg) => leg.coords && leg.coords.length > 1))

const selected = ref(null)
const scrollBox = ref(null)
const mapRef = ref(null) // 카카오 지도 (칸을 고르면 그 칸 쪽으로 확대)
const useRealMap = ref(true) // 카카오 지도를 못 쓰면(키 없음·불러오기 실패) false → 예전 지도 그림
const pickedPoint = ref(null) // 카카오 지도에서 누른 핀의 화면 위치 { x, y, isInside } (말풍선 위치)
const isTipOnPin = ref(false) // 핀을 눌러 말풍선을 띄웠는지 (지도를 옮기면 말풍선이 핀을 따라가요)
// 지도는 시트 뒤까지 화면 전체 (시트를 내리면 지도가 보여요). 핀은 위 버튼·아래 시트(404px)에 가리지 않는 곳에 맞춰요 (11/04)
const TRIP_MAP_PADDING = { top: 110, right: 40, bottom: 444, left: 40 }
const FOCUS_RADIUS_M = 400 // 칸을 고르면 핀에서 이만큼(m)까지의 앞뒤 경로만 보이게 확대 (지하철처럼 긴 길도 좁게)

// 모든 칸에 좌표가 있으면 카카오 지도 (좌표 없는 가짜 테마 코스는 그림 지도)
const hasCoords = computed(() => props.trip.stops.length > 0 && props.trip.stops.every((stop) => stop.lat != null && stop.lng != null))
const isKakao = computed(() => hasCoords.value && useRealMap.value)

// 지도를 다시 맞출 때를 알리는 값: 그날 장소 목록이 바뀌면(일차 바꾸기·장소 넣고 빼기) 달라짐. 정렬해서 순서만 바꿀 땐 그대로
const mapFitKey = computed(() => props.trip.stops.map((stop) => String(stop.id)).sort().join('|'))
const mapPins = computed(() => props.trip.stops.map((stop, i) => ({
  key: i,
  index: i,
  lat: stop.lat,
  lng: stop.lng,
  text: i + 1,
  icon: stop.isStay ? 'i-home' : stop.fixed ? 'i-lock' : '', // 숙소는 집 모양 핀 (11/04)
  cls: pinClass(stop, i),
  label: (stop.isStay ? '숙소 ' : i + 1 + ' ') + stop.name,
})))

function pinClass(stop, i) {
  let cls = ''
  if (stop.isStay) cls += ' pin--stay'
  else if (stop.fixed) cls += ' pin--fixed'
  if (stop.warn) cls += ' pin--warn'
  if (selected.value === i || props.activeStop === i) cls += ' pin--sel'
  return cls
}

// 카카오 지도 핀을 누름: 그 칸 쪽으로 옮긴 뒤, 옮긴 자리의 핀 위에 말풍선
function pickPin(pin) {
  select(pin.index)
  isTipOnPin.value = selected.value !== null
  pickedPoint.value = { x: pin.x, y: pin.y }
  followPin()
}

// 지도가 움직일 때마다 말풍선을 고른 핀의 지금 화면 위치로 (핀을 눌러 띄운 말풍선만)
function followPin() {
  if (!isTipOnPin.value || selected.value === null || !mapRef.value) return
  const stop = props.trip.stops[selected.value]
  const point = mapRef.value.pointOf(stop.lat, stop.lng)
  if (point) pickedPoint.value = point
}

// 고른 칸 쪽으로 지도 맞추기: 들어오는 길(legs[i-1])·나가는 길(legs[i]) 중 핀에서 FOCUS_RADIUS_M 안쪽만
// 길 좌표가 없으면 앞뒤 칸 쪽으로 그은 직선을 같은 거리만큼 잘라 씀
function focusMap(i) {
  if (!isKakao.value || !mapRef.value) return
  if (i === null) {
    mapRef.value.fitAll()
    return
  }
  const stops = props.trip.stops
  const legs = props.trip.legs || []
  const center = [stops[i].lat, stops[i].lng]
  const lines = []
  const comeIn = legs[i - 1] && legs[i - 1].coords && legs[i - 1].coords.length ? [...legs[i - 1].coords].reverse() : null
  const goOut = legs[i] && legs[i].coords && legs[i].coords.length ? legs[i].coords : null
  const prev = stops[i - 1]
  const next = stops[i + 1]
  if (comeIn) lines.push(comeIn)
  else if (prev && prev.lat != null) lines.push([[prev.lat, prev.lng]])
  if (goOut) lines.push(goOut)
  else if (next && next.lat != null) lines.push([[next.lat, next.lng]])
  const points = []
  for (const line of lines) points.push(...nearPart(center, line, FOCUS_RADIUS_M))
  mapRef.value.focusAround(center, points)
}

// line(핀 쪽에서 시작하는 [위도, 경도] 목록)에서 center로부터 radius(m) 안쪽 점만, 넘어가는 곳은 radius 지점으로 잘라 돌려줌
function nearPart(center, line, radius) {
  const result = []
  let before = center
  let beforeDist = 0
  for (const point of line) {
    const dist = metersBetween(center, point)
    if (dist <= radius) {
      result.push(point)
      before = point
      beforeDist = dist
      continue
    }
    const ratio = (radius - beforeDist) / (dist - beforeDist) // 앞 점과 이 점 사이에서 radius가 되는 비율 (대략)
    result.push([before[0] + (point[0] - before[0]) * ratio, before[1] + (point[1] - before[1]) * ratio])
    break
  }
  return result
}

// 두 [위도, 경도] 사이 거리(m) — 짧은 거리라 평면으로 계산
function metersBetween(a, b) {
  const dy = (b[0] - a[0]) * 111000
  const dx = (b[1] - a[1]) * 111000 * Math.cos((a[0] * Math.PI) / 180)
  return Math.hypot(dx, dy)
}

// 목록을 누름: 카카오 지도에서는 핀 화면 위치를 몰라 말풍선 없이 핀만 강조
function pickRow(i) {
  pickedPoint.value = null
  isTipOnPin.value = false
  select(i)
}

const subText = computed(() => props.sub || `${props.trip.date} · ${props.trip.stops.length}곳 · 이동 ${props.trip.move}분 · ${props.trip.who}`)
const routePoints = computed(() => props.trip.stops.map((s) => s.x + ',' + s.y).join(' '))

// 고른 칸의 사진·이름 말풍선 위치 (11/04: 이름이 길면 두 줄, 말풍선은 화면 안에, 꼬리는 늘 핀을 가리키게)
const APP_WIDTH = 390 // 앱 화면 폭(px)
const TIP_EDGE = 12 // 말풍선을 화면 가장자리에서 띄울 거리(px)
const TIP_ARROW_EDGE = 18 // 꼬리를 말풍선 모서리에서 띄울 거리(px)
const tipBox = ref(null) // 말풍선 (그려진 폭을 재요)
const tipWidth = ref(240) // 말풍선 폭(px). 처음엔 어림값, 그려진 뒤 실제 폭으로
// 지도를 축소하면 말풍선·글씨도 작게 (11/04): 확대 단계 4까지는 그대로, 한 단계 멀어질 때마다 10%씩, 70%까지
const TIP_FULL_LEVEL = 4
const TIP_SHRINK_PER_LEVEL = 0.1
const TIP_MIN_SCALE = 0.7
const mapLevel = ref(null) // 카카오 지도 확대 단계 (작을수록 가까이)
const tipScale = computed(() => {
  if (!isKakao.value || mapLevel.value === null) return 1
  const scale = 1 - Math.max(0, mapLevel.value - TIP_FULL_LEVEL) * TIP_SHRINK_PER_LEVEL
  return Math.max(TIP_MIN_SCALE, scale)
})
const tooltip = computed(() => {
  if (selected.value === null) return null
  const stop = props.trip.stops[selected.value]
  const point = isKakao.value ? pickedPoint.value : stop
  if (!point || point.isInside === false) return null // 핀이 지도 밖으로 나가면 말풍선도 숨김
  const width = tipWidth.value
  const left = Math.min(Math.max(point.x - width / 2, TIP_EDGE), APP_WIDTH - TIP_EDGE - width)
  const isBelow = point.y - 82 < 104 // 위쪽 버튼에 가리면 핀 아래에
  return {
    stop,
    left,
    y: isBelow ? point.y + 26 : point.y - 82,
    isBelow,
    arrowX: Math.min(Math.max(point.x - left, TIP_ARROW_EDGE), width - TIP_ARROW_EDGE), // 꼬리 위치 = 핀 x − 말풍선 왼쪽
  }
})
// 고른 칸이 바뀌면(이름 길이가 다르면) 그려진 말풍선의 실제 폭을 다시 잼
watch(() => (tooltip.value ? tooltip.value.stop.name : null), async () => {
  await nextTick()
  const element = tipBox.value && tipBox.value.$el // RouterLink 컴포넌트 → 실제 a 태그
  if (element) tipWidth.value = element.offsetWidth
})

// 이동 미리보기가 어떤 칸에 머물면 목록에서 그 칸이 보이게
watch(() => props.activeStop, async (i) => {
  if (i === null) return
  await nextTick()
  const el = scrollBox.value && scrollBox.value.querySelector(`[data-stop="${i}"]`)
  if (el) el.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
})

// 일차를 바꾸면(주소의 ?day= 가 바뀌면) 시트 안 목록을 맨 위로 → 다음 날도 첫 칸부터 보이게
watch(() => route.query.day, () => {
  if (scrollBox.value) scrollBox.value.scrollTop = 0
})

// 핀이나 목록을 누르면 같이 강조하고, 목록은 그 칸이 보이게 스크롤
// 카카오 지도면 그 칸 핀을 가운데에 두고 앞뒤 경로가 보이게 확대, 고르기를 풀면 전체 보기로 (11/04)
async function select(i) {
  selected.value = selected.value === i ? null : i
  focusMap(selected.value)
  if (selected.value === null) return
  await nextTick()
  const el = scrollBox.value.querySelector(`[data-stop="${i}"]`)
  if (el) el.scrollIntoView({ block: 'center', behavior: 'smooth' })
}
// 예약 여유 표시 색: 늦으면 빨강, 10분 이하면 노랑, 넉넉하면 초록 (11/06: 여유가 마이너스인데 초록 체크로 뜨던 문제)
const SLACK_TIGHT_MIN = 10 // 여행 중 알림(LiveAlertMaker)의 "여유가 얼마 없어요"와 같은 기준
function slackBadge(slack) {
  if (slack < 0) return { cls: 'badge--danger', icon: 'i-warning' }
  if (slack <= SLACK_TIGHT_MIN) return { cls: 'badge--warning', icon: 'i-warning' }
  return { cls: 'badge--success', icon: 'i-check' }
}
</script>

<template>
  <div class="app">
    <KakaoMap v-if="isKakao" ref="mapRef" :pins="mapPins" :me="simMe" @move="followPin" @zoom="mapLevel = $event" :paths="hasLegs ? trip.legs : []" :show-route="!hasLegs" :padding="TRIP_MAP_PADDING" :fit-key="mapFitKey" @pick="pickPin" @fail="useRealMap = false" />
    <!-- 카카오 지도를 못 쓸 때: 예전 지도 그림 -->
    <template v-else>
    <div class="map" aria-hidden="true"><img class="base live" src="/img/mapB.svg" alt="" style="top: -186px" /></div>
    <svg class="route trip-route" viewBox="0 0 390 344">
      <polyline :points="routePoints" fill="none" stroke="#1F3A68" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" stroke-dasharray="1 8" />
    </svg>
    <button
      v-for="(stop, i) in trip.stops"
      :key="stop.id + i"
      type="button"
      class="pin"
      :class="{ 'pin--stay': stop.isStay, 'pin--fixed': stop.fixed && !stop.isStay, 'pin--warn': stop.warn, 'pin--sel': selected === i }"
      :style="{ left: stop.x + 'px', top: stop.y + 'px' }"
      :aria-label="(stop.isStay ? '숙소 ' : i + 1 + ' ') + stop.name"
      @click="select(i)"
    >
      <i v-if="stop.isStay" class="ic ic--s i-home" aria-hidden="true" style="width: 13px; height: 13px"></i>
      <i v-else-if="stop.fixed" class="ic ic--s i-lock" aria-hidden="true" style="width: 13px; height: 13px"></i>
      <template v-else>{{ i + 1 }}</template>
    </button>
    </template>
    <RouterLink
      v-if="tooltip"
      ref="tipBox"
      :to="tooltip.stop.placeId ? '/place?id=' + tooltip.stop.placeId : '/place'"
      class="tooltip trip-tip"
      :class="{ 'is-below': tooltip.isBelow }"
      :style="{ left: tooltip.left + 'px', top: tooltip.y + 'px', '--arrow-x': tooltip.arrowX + 'px', transform: 'scale(' + tipScale + ')' }"
    >
      <img :src="tooltip.stop.img" alt="" /><b class="t-strong trip-tip__name">{{ tooltip.stop.name }}</b><i class="ic ic--s i-next subtle" aria-hidden="true"></i>
    </RouterLink>

    <TopBar float :back="back">
      <slot name="right" :selected="selected"></slot>
    </TopBar>

    <BottomSheet class="sheet--peek trip-sheet" expandable aria-labelledby="trip-title">
      <div class="trip-head">
        <div style="flex: 1; min-width: 0">
          <h1 id="trip-title" class="h-title">{{ trip.title }}</h1>
          <p class="t-cap tn">
            <span v-if="badge" class="badge" :class="badge.cls" style="height: 20px; margin-right: 6px">{{ badge.text }}</span>{{ subText }}
          </p>
        </div>
        <span v-if="trip.slack !== undefined" class="badge tn" :class="slackBadge(trip.slack).cls"><i class="ic ic--s" :class="slackBadge(trip.slack).icon" aria-hidden="true"></i>{{ trip.slack < 0 ? '예약에 ' + -trip.slack + '분 늦어요' : '예약 여유 ' + trip.slack + '분' }}</span>
      </div>
      <TripBudget v-if="showBudget && trip.stops.length" :stops="trip.stops" />
      <slot name="head"></slot>
      <div ref="scrollBox" class="trip-scroll">
        <CheckList :items="trip.checks" :rain-swap="rainSwap" />
        <TripTimeline :stops="trip.stops" :selected="activeStop !== null ? activeStop : selected" :reorderable="reorderable" @select="pickRow" :mode-editable="modeEditable" @reorder="emit('reorder', $event)" @change-mode="(i, mode) => emit('change-mode', i, mode)" />
        <p v-if="!trip.stops.length" class="t-cap" style="margin: 0; padding: 16px 0">아직 넣은 일정이 없어요. 일정 편집에서 장소를 넣어 주세요.</p>
        <slot name="below"></slot>
      </div>
    </BottomSheet>

    <div v-if="$slots.cta" class="cta cta--white">
      <slot name="cta"></slot>
    </div>
  </div>
</template>

<style>
.trip-route { position: absolute; left: 0; top: 0; width: 390px; height: 344px; pointer-events: none; }
/* 핀 말풍선: 폭은 250px까지, 이름이 길면 두 줄까지 (낱말 단위로 줄바꿈), 꼬리는 핀 쪽(--arrow-x) */
.trip-tip { max-width: 250px; white-space: normal; z-index: 4; transform-origin: var(--arrow-x, 50%) calc(100% + 6px); } /* 작아질 때 꼬리 끝(핀 쪽)을 기준으로 */
.trip-tip.is-below { transform-origin: var(--arrow-x, 50%) -6px; }
.trip-tip img { flex: none; }
.trip-tip .i-next { flex: none; }
.trip-tip__name { flex: 1; min-width: 0; font-size: 14px; line-height: 18px; word-break: keep-all; overflow-wrap: anywhere; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.trip-tip::after { left: var(--arrow-x, 50%); }
.trip-tip.is-below::after { bottom: auto; top: -6px; }
</style>
