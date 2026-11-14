<script setup>
// 일정 목록 (시간 · 장소 카드 · 사이 이동). 일정 점검·여행 상세·테마 코스가 같이 써요
// modeEditable이면 이동 줄을 눌러 도보·대중교통·택시 중에 골라요 → change-mode(칸 번호, 수단) (11/04)
//   고르면 점검 화면이 그 칸 수단을 바꾸고 다시 점검해서 시간·경로가 진짜로 바뀌어요. 아니면 글자만 보여 줘요
// 메모가 있으면 카드 아래 한 줄 (AI 일정: 고른 이유 · 리뷰 근거 "조용해요 · 리뷰 10개 중 4개")
// reorderable이면 카드 오른쪽 손잡이를 끌어 다른 칸에 놓으면 두 칸 자리가 바뀌어요 (고정·숙소 칸은 손잡이 없음) → reorder(새 순서의 원래 번호 목록) (11/04)
//   고정 칸(예약) 위에 놓으면 그 앞(위로 끌 때)·뒤(아래로 끌 때)에 끼워 넣어요 → 예약 전에 다른 곳을 먼저 갈 수 있게
import StopThumb from '@/components/trip/StopThumb.vue'
import { onUnmounted, ref, watch } from 'vue'
import { importance, modes } from '@/data/places'
import { dropPlaceOf, orderAfterDrop } from '@/utils/draftEdit'

const props = defineProps({
  stops: { type: Array, required: true },
  selected: { type: Number, default: null },
  reorderable: { type: Boolean, default: false },
  modeEditable: { type: Boolean, default: false },
  showHoursHelp: { type: Boolean, default: false }, // 영업시간 모르는 칸 아래 안내 상자 + [네이버에서 확인]·[확인했어요] (일정 점검만)
})
const emit = defineEmits(['select', 'reorder', 'change-mode', 'remove', 'hours-checked'])
const MODE_CHOICES = ['walk', 'transit', 'taxi'] // 고를 수 있는 수단 (서버 Stop.mode가 아는 값만)

// 끌기: 다른 칸은 그대로 두고, 끄는 칸만 손가락을 따라 움직여요. 놓은 자리의 칸과 둘이 자리를 바꿔요 (11/04)
const dragFrom = ref(null) // 끄는 칸 번호 (안 끌면 null)
const dropTo = ref(null) // 놓을 칸 번호 (없으면 null)
const dropPlace = ref(null) // 놓는 방법: 'swap' 자리 바꾸기 / 'before'·'after' 고정 칸 앞·뒤에 끼우기
const dragY = ref(0) // 끄는 칸이 손가락을 따라 움직인 거리(px)
let startY = 0
let scale = 1 // 폰 화면을 줄여 보여 줄 때 손가락 거리 → 화면 거리 비율

// 움직임·놓기는 화면 전체(window)에서 받아요 (손가락이 손잡이 밖으로 나가도 계속)
function startDrag(i, e) {
  e.preventDefault()
  const row = e.currentTarget.closest('[data-row]')
  scale = row.getBoundingClientRect().height / row.offsetHeight || 1
  dragFrom.value = i
  dropTo.value = null
  dragY.value = 0
  startY = e.clientY
  window.addEventListener('pointermove', onDrag)
  window.addEventListener('pointerup', endDrag)
  window.addEventListener('pointercancel', endDrag)
}
function stopListening() {
  window.removeEventListener('pointermove', onDrag)
  window.removeEventListener('pointerup', endDrag)
  window.removeEventListener('pointercancel', endDrag)
}
onUnmounted(stopListening)
// 끄는 중에 목록이 바뀌면(점검 결과가 새로 옴) 끌기를 그만둠
watch(() => props.stops, () => {
  if (dragFrom.value !== null) cancelDrag()
})
function cancelDrag() {
  stopListening()
  dragFrom.value = null
  dropTo.value = null
  dropPlace.value = null
  dragY.value = 0
}
// 손가락 아래 칸이 자리를 바꿀 수 있는 칸이면 표시 (끄는 칸은 pointer-events: none이라 아래 칸이 잡힘)
function onDrag(e) {
  if (dragFrom.value === null) return
  dragY.value = (e.clientY - startY) / scale
  const row = document.elementFromPoint(e.clientX, e.clientY)?.closest('[data-row]')
  const target = row ? Number(row.dataset.row) : null
  dropPlace.value = dropPlaceOf(props.stops, dragFrom.value, target)
  dropTo.value = dropPlace.value ? target : null
}
// 놓으면 새 순서를 알림 (점검 화면이 초안 순서를 바꾸고 시간을 다시 계산)
function endDrag() {
  const from = dragFrom.value
  const to = dropTo.value
  const place = dropPlace.value
  cancelDrag()
  if (from === null || to === null) return
  emit('reorder', orderAfterDrop(props.stops.length, from, to, place))
}
// 키보드 위아래 화살표로도 한 칸씩 (고정 칸이면 그 너머로)
function moveBy(i, step) {
  const to = i + step
  if (to < 0 || to >= props.stops.length) return
  const place = dropPlaceOf(props.stops, i, to)
  if (place) emit('reorder', orderAfterDrop(props.stops.length, i, to, place))
}

const openLeg = ref(null) // 이동 수단 고르기를 연 줄 번호

const weather = {
  sun: { icon: 'i-sun', label: '맑음' },
  cloud: { icon: 'i-cloud', label: '흐림' },
  rain: { icon: 'i-rain', label: '비 60%' },
}

// 대중교통·차인데 걷는 시간이 있으면 "대중교통 16분 +도보 6분"으로 나눠 보여 줘요 (11/04)
//   서버 minutes는 걷는 시간까지 합친 값이라, 앞 숫자는 걷는 시간을 뺀 타는 시간
function hasWalkPart(leg) {
  return leg.mode !== 'walk' && leg.walkMin > 0
}
function rideMinutes(leg) {
  return hasWalkPart(leg) ? Math.max(1, leg.min - leg.walkMin) : leg.min
}

function toggleLeg(i) {
  openLeg.value = openLeg.value === i ? null : i
}
// 수단을 고름: 지금 수단이면 닫기만, 다르면 점검 화면에 알림
function pickMode(i, mode) {
  openLeg.value = null
  if (mode !== props.stops[i].leg.mode) emit('change-mode', i, mode)
}

const REVIEW_PART = /^리뷰 \d+개/ // 정규식: "리뷰 + 숫자 + 개"로 시작하는 조각을 찾음 (숫자가 매번 달라서 글자 비교로는 못 찾음)

// 메모에서 리뷰 근거("분위기 좋아요 · 리뷰 10개 중 6개")를 빼고 남은 글. 남는 게 없으면 ''
function memoText(memo) {
  if (!memo) return ''
  const kept = []
  for (const part of memo.split(' · ')) {
    if (REVIEW_PART.test(part)) {
      kept.pop() // 바로 앞 조각이 태그 이름이라 같이 뺌
    } else {
      kept.push(part)
    }
  }
  return kept.join(' · ')
}

// 앞 장소와 같은 건물인지 (메모에 "같은 건물" + 200m 안, 예: 더현대 서울 → 지하 1층 테디뵈르하우스). 이동 줄을 "같은 건물 · 걸어서 2분"으로 (11/08 주형)
const SAME_BUILDING_M = 200
const SAME_BUILDING_WALK_MIN = 2
const METERS_PER_DEGREE = 111000
function isSameBuilding(i) {
  const before = props.stops[i - 1]
  const stop = props.stops[i]
  if (!before || !stop || before.lat == null || stop.lat == null) return false
  if (!(stop.memo || '').includes('같은 건물')) return false // 일정 메모에 "같은 건물"이라고 적힌 곳만
  const meters = Math.hypot(before.lat - stop.lat, (before.lng - stop.lng) * Math.cos((stop.lat * Math.PI) / 180)) * METERS_PER_DEGREE
  return meters < SAME_BUILDING_M
}
</script>

<template>
  <ol class="tl" aria-label="일정" style="list-style: none; margin: 0; padding: 0">
    <template v-for="(stop, i) in stops" :key="stop.id + '-' + i">
      <!-- 앞 장소에서 오는 이동 -->
      <li v-if="stop.leg" class="leg">
        <span></span>
        <!-- 위치를 모르는 칸을 오가는 구간: 가짜 좌표로 잰 시간이라 숫자 대신 안내 -->
        <span v-if="stop.leg.isUnknown"><span v-if="stop.leg.from" class="legfrom">{{ stop.leg.from }}에서</span><span class="legbtn legbtn--text legbtn--unknown"><i class="ic ic--s i-warning" aria-hidden="true"></i>이동 시간 확인 필요</span></span>
        <span v-else-if="isSameBuilding(i)"><span class="legbtn legbtn--text"><i class="ic ic--s i-walk" aria-hidden="true"></i>같은 건물 · 걸어서 {{ SAME_BUILDING_WALK_MIN }}분</span></span>
        <span v-else>
          <span v-if="stop.leg.from" class="legfrom">{{ stop.leg.from }}에서</span>
          <button v-if="modeEditable" type="button" class="legbtn" :class="{ 'is-open': openLeg === i }" :aria-expanded="openLeg === i" @click="toggleLeg(i)">
            <i class="ic ic--s" :class="modes[stop.leg.mode].icon" aria-hidden="true"></i>{{ modes[stop.leg.mode].label }} {{ rideMinutes(stop.leg) }}분<b v-if="hasWalkPart(stop.leg)" class="legwalk">+도보 {{ stop.leg.walkMin }}분</b><i class="ic ic--s i-down" aria-hidden="true"></i>
          </button>
          <span v-else class="legbtn legbtn--text"><i class="ic ic--s" :class="modes[stop.leg.mode].icon" aria-hidden="true"></i>{{ modes[stop.leg.mode].label }} {{ rideMinutes(stop.leg) }}분<b v-if="hasWalkPart(stop.leg)" class="legwalk">+도보 {{ stop.leg.walkMin }}분</b></span>
          <b v-if="stop.leg.rush" class="legwarn"><i class="ic ic--s i-users" aria-hidden="true"></i>퇴근길 혼잡</b>
        </span>
      </li>
      <li v-if="modeEditable && stop.leg && openLeg === i" class="legpick" role="radiogroup" aria-label="이동 수단">
        <button
          v-for="key in MODE_CHOICES"
          :key="key"
          type="button"
          role="radio"
          :aria-checked="key === stop.leg.mode"
          class="chip chip--sm"
          :class="{ 'is-on': key === stop.leg.mode }"
          @click="pickMode(i, key)"
        >
          <i class="ic ic--s" :class="modes[key].icon" aria-hidden="true"></i>{{ modes[key].label }}<template v-if="key === stop.leg.mode"> {{ stop.leg.min }}분</template>
        </button>
      </li>

      <!-- 장소 카드 -->
      <li
        :data-row="i"
        :class="{ 'tl-row': reorderable, 'is-drag': dragFrom === i, 'is-target': dropTo === i && dropPlace === 'swap', 'is-before': dropTo === i && dropPlace === 'before', 'is-after': dropTo === i && dropPlace === 'after' }"
        :style="dragFrom === i ? { transform: 'translateY(' + dragY + 'px)' } : null"
      >
        <button type="button" class="stop" :class="{ 'is-fixed': stop.fixed, 'is-sel': selected === i }" :data-stop="i" @click="emit('select', i)">
          <span class="stop__time">{{ stop.time }}</span>
          <span class="stop__card">
            <StopThumb :src="stop.photo === undefined ? stop.img : stop.photo" :name="stop.name" :category="stop.category" />
            <span class="stop__body">
              <span class="stop__name"><small v-if="stop.isAi" class="stop__ai">AI</small>{{ stop.name }}</span>
              <span class="stop__meta" :class="stop.metaCls"><i v-if="stop.metaIcon" class="ic ic--s" :class="stop.metaIcon" aria-hidden="true"></i>{{ stop.meta }}</span>
              <span v-if="stop.hoursUnknown" class="stop__hours"><i class="ic ic--s i-clock" aria-hidden="true"></i>{{ stop.hoursChecked ? '영업시간 확인함' : '영업시간 확인 전' }}</span>
              <span v-if="memoText(stop.memo)" class="stop__memo">{{ memoText(stop.memo) }}</span>
            </span>
            <span class="stop__side">
              <span v-if="stop.locationUnknown" class="badge badge--warning">위치 확인 필요</span>
              <small v-if="stop.costText" class="stop__cost tn">{{ stop.costText }}</small>
              <span v-if="stop.isAi" class="stop__ai-remove" role="button" tabindex="0" :aria-label="stop.name + ' 빼기'" @click.stop="emit('remove', i)" @keydown.enter.stop.prevent="emit('remove', i)">빼기</span>
              <span v-if="!stop.fixed && stop.imp" class="imp" :class="'imp--' + stop.imp">{{ importance[stop.imp] }}</span>
              <span v-if="stop.wx" class="stop__wx" :class="{ 'is-rain': stop.wx === 'rain' }"><i class="ic ic--s" :class="weather[stop.wx].icon" aria-hidden="true"></i>{{ weather[stop.wx].label }}</span>
            </span>
          </span>
        </button>
        <template v-if="reorderable">
          <button
            v-if="stop.movable"
            type="button"
            class="tl-grip"
            :aria-label="stop.name + ' 순서 바꾸기, 위아래 화살표로도 움직여요'"
            @pointerdown.stop="startDrag(i, $event)"
            @keydown.up.prevent="moveBy(i, -1)"
            @keydown.down.prevent="moveBy(i, 1)"
          >
            <i class="ic i-grip" aria-hidden="true"></i>
          </button>
          <span v-else class="tl-grip" aria-hidden="true"></span>
        </template>
      </li>
      <!-- 영업시간 모르는 칸 안내 (카드 버튼 안에는 버튼을 못 넣어서 카드 바로 아래 줄로) -->
      <li v-if="showHoursHelp && stop.hoursUnknown && !stop.hoursChecked" class="hours-help" :class="{ 'has-grip': reorderable }">
        <b class="hours-help__title">{{ stop.name }} 영업시간을 아직 몰라요</b>
        <span class="hours-help__text">가기 전에 한 번만 확인해 주세요.</span>
        <span class="hours-help__buttons">
          <a v-if="stop.naverUrl" class="btn btn--secondary btn--sm":href="stop.naverUrl" target="_blank" rel="noopener noreferrer" :aria-label="stop.name + ' 네이버에서 확인 (새 창)'">네이버에서 확인</a>
          <button type="button" class="btn btn--primary btn--sm" @click="emit('hours-checked', stop.id)">확인했어요</button>
        </span>
      </li>
    </template>
  </ol>
</template>

<style>
.tl .tl-row{display:flex;align-items:stretch;gap:2px}
.tl .tl-row>.stop{flex:1;min-width:0}
.tl .tl-grip{width:28px;flex:none;border:0;padding:0;background:none;color:var(--ink-subtle);display:flex;align-items:center;justify-content:center;touch-action:none;cursor:grab}
.tl .tl-row.is-drag{position:relative;z-index:5;pointer-events:none}
.stop__ai{display:inline-block;margin-right:4px;padding:0 4px;border-radius:4px;background:var(--navy-soft);color:var(--navy);font-size:10px;line-height:16px;font-weight:700;vertical-align:1px}
.stop__ai-remove{font-size:12px;line-height:16px;color:var(--ink-muted);text-decoration:underline;cursor:pointer}
.stop__side .stop__cost{order:9;margin-top:auto;font-size:11px;line-height:14px;color:var(--ink-muted)}
.tl .tl-row.is-drag .stop__card{border-color:var(--navy);background:#fff;box-shadow:0 8px 20px rgba(24,33,58,.22)}
.tl .tl-row.is-target .stop__card{border:2px dashed var(--navy);background:var(--navy-soft)}
/* 고정 칸 앞·뒤에 끼울 때: 카드 위(앞)·아래(뒤) 테두리를 굵게 → "여기 앞/뒤에 들어가요" */
.tl .tl-row.is-before .stop__card{background:var(--navy-soft);box-shadow:inset 0 4px 0 var(--navy)}
.tl .tl-row.is-after .stop__card{background:var(--navy-soft);box-shadow:inset 0 -4px 0 var(--navy)}
.tl .legwalk{color:var(--success);font-weight:700}
.tl .legfrom{font-size:12px;color:var(--ink-muted);margin-right:6px}
.tl .legbtn--text,.tl .legbtn--text:hover{cursor:default;border-color:transparent;background:none;color:var(--ink-muted)}
.tl .legbtn--text.legbtn--unknown{color:var(--warning)} /* 위 회색 규칙보다 구체적이라 주황이 이겨요 */
/* 영업시간 모르는 칸: 카드 안 회색 한 줄 + 카드 아래 흰 상자(테두리 있음, 버튼이 바탕에 안 묻히게). 경고 아님이라 주황을 안 씀 */
.tl .stop__hours{font-size:12px;line-height:17px;color:var(--ink-muted);display:flex;align-items:center;gap:4px}
.tl .hours-help{margin:6px 0 2px 58px;padding:12px;border:1px solid var(--line);border-radius:var(--radius-md);background:var(--surface-raised);display:flex;flex-direction:column;gap:4px} /* 58px = 시각 칸 48px + 간격 10px → 카드와 왼쪽을 맞춤 */
.tl .hours-help.has-grip{margin-right:30px} /* 순서 손잡이(28px + 2px) 만큼 오른쪽도 카드와 맞춤 */
.tl .hours-help__title{font-size:13px;line-height:18px;font-weight:700;color:var(--ink)}
.tl .hours-help__text{font-size:13px;line-height:18px;color:var(--ink-muted)}
.tl .hours-help__buttons{display:flex;gap:8px;margin-top:6px}
.tl .hours-help__buttons .btn{flex:1;min-width:0}
.tl .stop__memo{font-size:12px;line-height:17px;color:var(--ink-muted);display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;white-space:normal;text-align:left}
</style>
