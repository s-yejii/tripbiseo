<script setup>
// 일정에 넣기 전에 순서 정하기 (④ 11/13). 구글 지도·카카오맵 "경유지 추가"처럼:
//   새 장소가 남은 일정 목록에 끼워져 강조돼 보이고, 오른쪽 손잡이(≡)를 끌어 원하는 자리로 옮겨요 (위아래 화살표키도 됨)
//   왼쪽 번호는 넣은 뒤의 일정 번호 (지도 핀 번호와 같음: 옮기면 지도 핀 번호도 같이 바뀜)
//   놓을 때마다 POST /live/preview(저장 안 함)로 바뀐 시각을 받고 지도에 전체 경로를 그려요
//   [이 순서로 넣기] → POST /live/insert (afterStopId = 새 장소 바로 앞 칸)
// 일정 순서 바꾸기 끌기는 일정 편집(TripTimeline)과 같은 방식: 손가락 아래 칸의 위쪽 절반이면 그 앞, 아래쪽 절반이면 그 뒤
import { computed, onUnmounted, ref, watch } from 'vue'
import { insertPlace, previewPlan } from '@/api/trips'
import { showToast } from '@/utils/toast'
import '@/assets/css/live-panel.css'

const CAFE_STAY_MIN = 40 // 새 칸 머무는 시간: 서버 일정에 넣기(LiveServiceImpl.getStay)와 같은 규칙
const OTHER_STAY_MIN = 60

const props = defineProps({
  tripId: { type: [Number, String], required: true },
  position: { type: Object, required: true }, // 지금 위치 { lat, lng }
  place: { type: Object, required: true }, // 넣을 장소 { id, name, category, lat, lng }
  stops: { type: Array, required: true }, // 남은 일정 칸 (여행 중 화면 순서) [{ id, name, eta, fixed }]
  fromName: { type: String, default: '지금 위치' }, // 맨 위 출발 줄 (지금 있는 칸 이름 또는 지금 위치)
  firstNo: { type: Number, default: 1 }, // 남은 첫 칸의 일정 번호 (여행 중 화면·지도 핀 번호)
})
// close: 뒤로(장소 카드로), route: 바뀐 순서의 전체 경로선 [[위도, 경도], ...], slot: 새 장소 자리 (지도 핀 번호를 맞춤)
const emit = defineEmits(['close', 'added', 'route', 'slot'])

const slot = ref(0) // 새 장소 자리: 0이면 첫 칸 앞(바로 다음), i면 i번째 칸 뒤
const result = ref(null) // /live/preview 응답 (바뀐 시각·여유·경로선)
const isAdding = ref(false)
const isLoading = ref(false) // 바뀐 시각을 받는 중 (시각을 흐리게)
let requestNo = 0 // 빨리 여러 번 옮기면 마지막 계산만 씀
let previewTimer = null
const PREVIEW_WAIT_MS = 250 // 화살표키로 여러 칸 옮길 때는 멈춘 뒤 한 번만 계산

// 화면에 보일 줄: 남은 칸 사이 slot 자리에 새 장소
const rows = computed(() => {
  const list = props.stops.map((stop, index) => ({ key: stop.id, stop, index }))
  list.splice(slot.value, 0, { key: 'new', isNew: true })
  return list
})
const firstFixedId = computed(() => {
  const fixed = props.stops.find((stop) => stop.fixed)
  return fixed ? fixed.id : null
})
const newEta = computed(() => {
  const added = result.value ? result.value.stops.find((stop) => stop.id == null) : null
  return added ? added.eta : ''
})

// 처음엔 바로 다음 자리(지금 동작과 같음)
watch(() => props.place.id, () => {
  result.value = null
  moveTo(0)
}, { immediate: true })

/** 새 장소를 이 자리로 옮김 (목록은 바로, 시각·지도는 계산이 오면 한 번에) */
function moveTo(index, wait = 0) {
  const next = Math.max(0, Math.min(props.stops.length, index))
  if (next === slot.value && result.value) return
  slot.value = next
  isLoading.value = true
  clearTimeout(previewTimer)
  previewTimer = setTimeout(loadPreview, wait)
}

/** 바뀐 시각·경로 받기 → 지도 핀 번호와 경로선을 같이 바꿈 (지도를 한 번만 다시 그리게) */
async function loadPreview() {
  const no = ++requestNo
  const index = slot.value
  try {
    const res = await previewPlan(props.tripId, makePlanStops(index), props.position.lat, props.position.lng)
    if (no !== requestNo) return
    result.value = res
    emit('slot', index)
    emit('route', res.coords || [])
  } catch (e) {
    if (no === requestNo) showToast(e.message)
  } finally {
    if (no === requestNo) isLoading.value = false
  }
}

/** /live/preview에 보낼 칸: 남은 칸은 id만, 새 장소는 index 자리에 */
function makePlanStops(index) {
  const list = props.stops.map((stop) => ({ id: stop.id }))
  list.splice(index, 0, {
    placeId: props.place.id, name: props.place.name, category: props.place.category,
    lat: props.place.lat, lng: props.place.lng, stay: stayFor(props.place.category),
  })
  return list
}

function stayFor(category) {
  return /카페|디저트|베이커리/.test(category || '') ? CAFE_STAY_MIN : OTHER_STAY_MIN
}

/** 이 칸 시각: 계산했으면 바뀐 시각, 아직이면 원래 시각 */
function etaOf(stop) {
  const found = result.value ? result.value.stops.find((item) => item.id === stop.id) : null
  return found ? found.eta : stop.eta
}
/** 첫 예약 칸에 늦는 분 (늦지 않으면 0) */
function lateOf(stop) {
  if (!result.value || stop.id !== firstFixedId.value || result.value.slackMin >= 0) return 0
  return -result.value.slackMin
}

// ------------------------------------------------------------ 끌어서 옮기기
const isDragging = ref(false)
const dragY = ref(0) // 새 장소 줄이 손가락을 따라 움직인 거리(px)
const dropSlot = ref(null) // 놓으면 갈 자리
const dropTop = ref(0) // 놓을 자리 표시 선 위치(px, 목록 안). 줄을 끼워 넣지 않아 목록이 흔들리지 않음
const listBox = ref(null)
let startY = 0
let frame = 0 // 손가락 움직임은 화면 한 장(requestAnimationFrame)에 한 번만 처리
let lastEvent = null
let scale = 1 // 화면이 폰 크기로 줄어 있으면 그 비율 (일정 편집과 같음)

function startDrag(e) {
  e.preventDefault()
  const row = e.currentTarget.closest('li')
  scale = row.getBoundingClientRect().height / row.offsetHeight || 1
  startY = e.clientY
  dragY.value = 0
  dropSlot.value = null
  isDragging.value = true
  window.addEventListener('pointermove', onDrag)
  window.addEventListener('pointerup', endDrag)
  window.addEventListener('pointercancel', endDrag)
}
// 손가락 아래 줄(끄는 줄은 pointer-events: none이라 아래 줄이 잡힘)의 위쪽 절반이면 그 앞, 아래쪽 절반이면 그 뒤
function onDrag(e) {
  lastEvent = e
  if (!frame) frame = requestAnimationFrame(applyDrag)
}
function applyDrag() {
  frame = 0
  const e = lastEvent
  dragY.value = (e.clientY - startY) / scale
  const row = document.elementFromPoint(e.clientX, e.clientY)?.closest('[data-stop-index]')
  if (!row) return
  const index = Number(row.dataset.stopIndex) // 출발 줄은 -1
  const rect = row.getBoundingClientRect()
  const isUpperHalf = e.clientY < rect.top + rect.height / 2
  dropSlot.value = index < 0 ? 0 : isUpperHalf ? index : index + 1
  dropTop.value = index < 0 || !isUpperHalf ? row.offsetTop + row.offsetHeight : row.offsetTop // 그 줄 위 또는 아래 경계
}
function endDrag() {
  stopListening()
  cancelAnimationFrame(frame)
  frame = 0
  const target = dropSlot.value
  isDragging.value = false
  dragY.value = 0
  dropSlot.value = null
  if (target !== null && target !== slot.value) moveTo(target)
}
function stopListening() {
  window.removeEventListener('pointermove', onDrag)
  window.removeEventListener('pointerup', endDrag)
  window.removeEventListener('pointercancel', endDrag)
}
onUnmounted(() => {
  stopListening()
  cancelAnimationFrame(frame)
  clearTimeout(previewTimer)
})

const isDropShown = computed(() => dropSlot.value !== null && dropSlot.value !== slot.value) // 자리가 그대로면 선 안 그림

async function add() {
  if (isAdding.value) return
  isAdding.value = true
  const afterStopId = slot.value === 0 ? null : props.stops[slot.value - 1].id
  try {
    const res = await insertPlace(props.tripId, props.place.id, props.position.lat, props.position.lng, null, afterStopId)
    emit('added', res.message || props.place.name + '을(를) 넣었어요')
  } catch (e) {
    showToast(e.message)
  } finally {
    isAdding.value = false
  }
}
</script>

<template>
  <div class="addbar">
    <section class="addcard lp-panel" aria-label="넣을 순서 정하기">
      <!-- 같은 패널의 다음 단계처럼: 뒤로 가면 장소 카드로 -->
      <header class="lp-head">
        <button type="button" class="iconbtn" aria-label="장소 카드로 돌아가기" @click="emit('close')"><i class="ic i-back" aria-hidden="true"></i></button>
        <span class="lp-head__title">일정에 넣기<small class="lp-head__sub">끌어서 순서를 바꿀 수 있어요</small></span>
      </header>

      <ol ref="listBox" class="lp-order" :class="{ 'is-dragging': isDragging, 'is-loading': isLoading }">
        <li v-if="isDropShown" class="lp-order__drop" :style="{ top: dropTop - 1 + 'px' }" aria-hidden="true"></li>
        <li class="lp-order__row lp-order__row--from" data-stop-index="-1">
          <span class="lp-order__name">{{ fromName }}</span>
        </li>
        <template v-for="row in rows" :key="row.key">
          <li
            v-if="row.isNew"
            class="lp-order__row lp-order__row--new"
            :class="{ 'is-drag': isDragging }"
            :style="isDragging ? { transform: 'translateY(' + dragY + 'px)' } : null"
          >
            <span class="lp-order__no tn" aria-hidden="true">{{ firstNo + slot }}</span>
            <span class="lp-order__name"><b>{{ place.name }}</b><small class="lp-order__tag">새로</small></span>
            <span class="lp-order__time tn">{{ isLoading ? '…' : newEta }}</span>
            <button
              type="button"
              class="grip"
              :aria-label="place.name + ' 순서 바꾸기, 위아래 화살표로도 움직여요'"
              @pointerdown="startDrag"
              @keydown.up.prevent="moveTo(slot - 1, PREVIEW_WAIT_MS)"
              @keydown.down.prevent="moveTo(slot + 1, PREVIEW_WAIT_MS)"
            >
              <i class="ic i-grip" aria-hidden="true"></i>
            </button>
          </li>
          <li v-else class="lp-order__row" :class="{ 'is-fixed': row.stop.fixed }" :data-stop-index="row.index">
            <span class="lp-order__no tn" aria-hidden="true">{{ firstNo + row.index + (row.index >= slot ? 1 : 0) }}</span>
            <span class="lp-order__name">{{ row.stop.name }}<i v-if="row.stop.fixed" class="ic ic--s i-lock" aria-label="예약"></i></span>
            <span v-if="lateOf(row.stop)" class="lp-order__time lp-order__time--late tn">{{ etaOf(row.stop) }} · {{ lateOf(row.stop) }}분 늦어요</span>
            <span v-else class="lp-order__time tn">{{ etaOf(row.stop) }}</span>
          </li>
        </template>
      </ol>

      <button type="button" class="btn btn--primary btn--lg" :disabled="isAdding" @click="add">이 순서로 넣기</button>
    </section>
  </div>
</template>
