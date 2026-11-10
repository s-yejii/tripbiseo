<script setup>
// ⑤ 웹 사진 편집기 (11/10). 여행 메모 > 사진에서 사진을 누르면 떠요
// 원본은 그대로 두고, [새 사진으로 저장]을 누르면 편집한 사진을 새 파일로 올려요 (기존 API ⑤ POST /api/photos)
// 찍은 시각을 원본과 같게 보내서, 서버가 원본과 같은 장소로 분류해요
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { upload } from '@/api/http'
import { FILTER_PRESETS, MAX_STRAIGHTEN, buildOriented, centerCrop, emptyEdit, fitSize, renderEdit } from './photoRender'

const props = defineProps({
  photo: { type: Object, required: true },   // { id, url, takenAtFull, placeName, timeLabel }
  tripId: { type: [Number, String], required: true },
  isCover: { type: Boolean, default: false },      // 이 장소의 대표 사진인지
  isTripCover: { type: Boolean, default: false },  // 여행 대표 사진인지
  canCover: { type: Boolean, default: false },     // 장소에서 찍은 사진인지 (이동 중 사진은 장소 대표가 될 수 없음)
})
const emit = defineEmits(['close', 'saved', 'cover', 'trip-cover'])

const PREVIEW_MAX = 1600        // 미리보기용 사진 긴 변(px). 크면 느려짐
const EXPORT_MAX = 4000         // 저장할 사진 긴 변 최대(px)
const THUMB_SIZE = 120          // 필터 미리보기 그림 크기(px)
const HISTORY_MAX = 60          // 되돌리기 몇 번까지
const MIN_CROP = 0.05           // 자르기 상자 최소 크기(비율)
const JPEG_QUALITY = 0.92

const TOOLS = [
  { id: 'crop', name: '자르기·회전' },
  { id: 'adjust', name: '조정' },
  { id: 'filter', name: '필터' },
  { id: 'text', name: '글자·도장' },
  { id: 'draw', name: '그리기' },
]
const ADJUSTS = [
  { key: 'brightness', name: '밝기', min: -100, max: 100 },
  { key: 'contrast', name: '대비', min: -100, max: 100 },
  { key: 'saturation', name: '채도', min: -100, max: 100 },
  { key: 'warmth', name: '색온도', min: -100, max: 100, hint: '− 차갑게 · + 따뜻하게' },
  { key: 'tint', name: '색조', min: -100, max: 100, hint: '− 초록 · + 자주' },
  { key: 'fade', name: '바랜 느낌', min: 0, max: 100 },
  { key: 'vignette', name: '주변 어둡게', min: 0, max: 100 },
  { key: 'grain', name: '필름 입자', min: 0, max: 100 },
  { key: 'sharpen', name: '선명하게', min: 0, max: 100 },
  { key: 'blur', name: '흐리게', min: 0, max: 20 },
]
const ASPECTS = [
  { id: 'free', name: '자유' },
  { id: 'original', name: '원래 비율' },
  { id: '1:1', name: '1:1', ratio: 1 },
  { id: '4:5', name: '4:5', ratio: 4 / 5 },
  { id: '3:4', name: '3:4', ratio: 3 / 4 },
  { id: '4:3', name: '4:3', ratio: 4 / 3 },
  { id: '16:9', name: '16:9', ratio: 16 / 9 },
  { id: '9:16', name: '9:16', ratio: 9 / 16 },
]
const COLORS = ['#FFFFFF', '#111111', '#FF8A3D', '#E8603F', '#F2C94C', '#4CAF50', '#2F7BFF', '#7B5CD6', '#FF6FA8']
const STICKERS = ['📍', '❤️', '⭐', '☕', '🍜', '🍰', '🚆', '🚕', '📸', '😊', '😆', '🎉', '🌅', '🌙', '🌸', '🍁']

const tool = ref('adjust')
const edit = ref(emptyEdit())
const history = ref([])
const historyIndex = ref(-1)
const isComparing = ref(false)      // [원본 비교]를 누르고 있는 동안 원본을 보여 줌
const isLoaded = ref(false)
const isSaving = ref(false)
const message = ref('')
const isAskingClose = ref(false)    // 저장 안 하고 닫을지 묻는 줄
const selectedTextId = ref(null)
const selectedBox = ref(null)       // 고른 글자의 자리 (테두리 표시용)
const drawColor = ref('#FF8A3D')
const drawSize = ref(8)
const isHighlighter = ref(false)
const filterThumbs = ref({})        // 필터 id → 미리보기 그림 주소
const canvasBox = ref({ width: 0, height: 0 }) // 화면에 보이는 캔버스 크기(px)
const stageRef = ref(null)
const canvasRef = ref(null)

let image = null                    // 원본 사진
let orientedCache = { key: '', canvas: null }
let textBoxes = []                  // 마지막으로 그린 글자 자리들
let renderFrame = null
let drag = null                     // 끄는 중인 것 { kind: 'crop' | 'text' | 'draw', … }
let nextTextId = 1

// ============================== 열기·닫기
onMounted(async () => {
  window.addEventListener('keydown', onKey)
  window.addEventListener('resize', scheduleRender)
  image = new Image()
  image.src = props.photo.url
  try {
    await image.decode() // 사진을 다 받을 때까지 기다림
  } catch (e) {
    message.value = '사진을 불러오지 못했어요.'
    return
  }
  isLoaded.value = true
  pushHistory()
  await nextTick()
  scheduleRender()
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKey)
  window.removeEventListener('resize', scheduleRender)
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
  cancelAnimationFrame(renderFrame)
})

const isChanged = computed(() => historyIndex.value > 0)

function askClose() {
  if (isChanged.value && !isSaving.value) isAskingClose.value = true
  else emit('close')
}

// 단축키: Ctrl+Z 되돌리기, Ctrl+Y·Ctrl+Shift+Z 다시 하기, Esc 닫기 (글자를 쓰는 중이면 그 칸의 되돌리기를 씀)
function onKey(event) {
  const isTyping = event.target.tagName === 'INPUT' || event.target.tagName === 'TEXTAREA'
  if (event.key === 'Escape') askClose()
  if (isTyping || !(event.ctrlKey || event.metaKey)) return
  const key = event.key.toLowerCase()
  if (key === 'z' && !event.shiftKey) undo()
  else if (key === 'y' || (key === 'z' && event.shiftKey)) redo()
  else return
  event.preventDefault()
}

// ============================== 되돌리기
function pushHistory() {
  const snapshot = JSON.stringify(edit.value)
  if (history.value[historyIndex.value] === snapshot) return // 바뀐 게 없으면 기록 안 함 (글자를 눌러 고르기만 한 경우 등)
  const list = history.value.slice(0, historyIndex.value + 1) // 되돌린 뒤 새로 고치면 '다시 하기' 기록은 버림
  list.push(snapshot)
  if (list.length > HISTORY_MAX) list.shift()
  history.value = list
  historyIndex.value = list.length - 1
}

function undo() {
  if (historyIndex.value <= 0) return
  historyIndex.value--
  edit.value = JSON.parse(history.value[historyIndex.value])
}

function redo() {
  if (historyIndex.value >= history.value.length - 1) return
  historyIndex.value++
  edit.value = JSON.parse(history.value[historyIndex.value])
}

function resetAll() {
  edit.value = emptyEdit()
  selectedTextId.value = null
  pushHistory()
}

// ============================== 그리기 (미리보기)
watch([edit, tool, isComparing, selectedTextId], scheduleRender, { deep: true })

// 한 화면 그릴 때 한 번만 (슬라이더를 빨리 움직여도 밀리지 않게)
function scheduleRender() {
  cancelAnimationFrame(renderFrame)
  renderFrame = requestAnimationFrame(render)
}

// 돌리기 값이 같으면 전에 만든 것을 다시 씀 (색만 바꿀 때 빨라짐)
function orientedFor(maxSide) {
  const value = edit.value
  const key = [value.rotate, value.flipX, value.flipY, value.straighten, maxSide].join('/')
  if (orientedCache.key !== key) orientedCache = { key: key, canvas: buildOriented(image, value, maxSide) }
  return orientedCache.canvas
}

function render() {
  const canvas = canvasRef.value
  const stage = stageRef.value
  if (!isLoaded.value || !canvas || !stage) return
  const isCropView = tool.value === 'crop'
  const source = isComparing.value ? image : orientedFor(PREVIEW_MAX)
  const crop = isComparing.value || isCropView ? { x: 0, y: 0, w: 1, h: 1 } : edit.value.crop
  const size = fitSize({ width: source.naturalWidth || source.width, height: source.naturalHeight || source.height }, crop, stage.clientWidth - 48, stage.clientHeight - 48)
  setCanvasSize(canvas, size)
  if (isComparing.value) {
    canvas.getContext('2d').drawImage(image, 0, 0, canvas.width, canvas.height)
    return
  }
  textBoxes = renderEdit(canvas, source, edit.value, { isCropView: isCropView })
  selectedBox.value = findBox(selectedTextId.value)
}

// 캔버스 크기: 보이는 크기 × 화면 배율(레티나 화면에서 선명하게)
function setCanvasSize(canvas, size) {
  const ratio = Math.min(2, window.devicePixelRatio || 1)
  canvas.style.width = size.width + 'px'
  canvas.style.height = size.height + 'px'
  canvas.width = Math.round(size.width * ratio)
  canvas.height = Math.round(size.height * ratio)
  canvasBox.value = { width: size.width, height: size.height }
}

function findBox(id) {
  for (const box of textBoxes) {
    if (box.id === id) return box
  }
  return null
}

// ============================== 자르기·회전
function rotateBy(degrees) {
  edit.value.rotate = (edit.value.rotate + degrees + 360) % 360
  applyAspect(edit.value.aspect) // 가로·세로가 바뀌어서 자르기 상자를 다시 맞춤
  pushHistory()
}

function flip(axis) {
  if (axis === 'x') edit.value.flipX = !edit.value.flipX
  else edit.value.flipY = !edit.value.flipY
  pushHistory()
}

function aspectRatio(id) {
  const oriented = orientedFor(PREVIEW_MAX)
  if (id === 'original') return oriented.width / oriented.height
  for (const aspect of ASPECTS) {
    if (aspect.id === id) return aspect.ratio || null
  }
  return null
}

function applyAspect(id) {
  const oriented = orientedFor(PREVIEW_MAX)
  edit.value.aspect = id
  edit.value.crop = centerCrop(aspectRatio(id), oriented.width, oriented.height)
}

function chooseAspect(id) {
  applyAspect(id)
  pushHistory()
}

// 자르기 상자 위치 (화면 % 단위)
const cropStyle = computed(() => {
  const crop = edit.value.crop
  return { left: crop.x * 100 + '%', top: crop.y * 100 + '%', width: crop.w * 100 + '%', height: crop.h * 100 + '%' }
})

function startCropDrag(event, handle) {
  drag = { kind: 'crop', handle: handle, startX: event.clientX, startY: event.clientY, start: Object.assign({}, edit.value.crop) }
  listenDrag(event)
}

// 0~1 안으로
function clamp01(value) {
  return Math.max(0, Math.min(1, value))
}

// 상자 옮기기: 크기는 그대로, 사진 밖으로는 못 나감
function moveCrop(start, dx, dy) {
  return { x: clamp01(Math.min(start.x + dx, 1 - start.w)), y: clamp01(Math.min(start.y + dy, 1 - start.h)), w: start.w, h: start.h }
}

// 모서리 끌기: 반대쪽 모서리는 고정. 비율이 정해져 있으면 가로에 맞춰 세로를 계산
function resizeCrop(start, handle, dx, dy) {
  let left = start.x
  let top = start.y
  let right = start.x + start.w
  let bottom = start.y + start.h
  if (handle.includes('w')) left = clamp01(Math.min(left + dx, right - MIN_CROP))
  if (handle.includes('e')) right = clamp01(Math.max(right + dx, left + MIN_CROP))
  if (handle.includes('n')) top = clamp01(Math.min(top + dy, bottom - MIN_CROP))
  if (handle.includes('s')) bottom = clamp01(Math.max(bottom + dy, top + MIN_CROP))
  const ratio = aspectRatio(edit.value.aspect)
  if (!ratio) return { x: left, y: top, w: right - left, h: bottom - top }
  return keepRatio({ left, top, right, bottom }, handle, ratio)
}

// 비율 고정: 세로 = 가로 × (사진 가로/세로) ÷ 비율. 사진 밖으로 나가면 둘 다 줄임
function keepRatio(box, handle, ratio) {
  const oriented = orientedFor(PREVIEW_MAX)
  const perWidth = oriented.width / oriented.height / ratio // 가로 비율 1당 세로 비율
  let w = box.right - box.left
  let h = w * perWidth
  const roomH = handle.includes('n') ? box.bottom : 1 - box.top
  if (h > roomH) {
    h = roomH
    w = h / perWidth
  }
  const x = handle.includes('w') ? box.right - w : box.left
  const y = handle.includes('n') ? box.bottom - h : box.top
  return { x: x, y: y, w: w, h: h }
}

// ============================== 조정·필터
function changeAdjust(key, value) {
  edit.value.adjust[key] = Number(value)
}

function resetAdjust(key) {
  edit.value.adjust[key] = 0
  pushHistory()
}

// 자동 보정: 조금 밝고, 또렷하고, 선명하게
function autoFix() {
  Object.assign(edit.value.adjust, { brightness: 8, contrast: 14, saturation: 12, sharpen: 20 })
  pushHistory()
}

function chooseFilter(id) {
  edit.value.filter = id
  pushHistory()
}

// 필터 미리보기 그림: 지금 편집에 필터만 바꿔서 작게 그림
function makeFilterThumbs() {
  if (!isLoaded.value) return
  const oriented = orientedFor(PREVIEW_MAX)
  const size = fitSize(oriented, edit.value.crop, THUMB_SIZE, THUMB_SIZE)
  const canvas = document.createElement('canvas')
  canvas.width = size.width
  canvas.height = size.height
  const thumbs = {}
  for (const preset of FILTER_PRESETS) {
    const trial = Object.assign({}, edit.value, { filter: preset.id, texts: [], strokes: [] })
    renderEdit(canvas, oriented, trial, { isCropView: false })
    thumbs[preset.id] = canvas.toDataURL('image/jpeg', 0.8)
  }
  filterThumbs.value = thumbs
}

watch(tool, (value) => {
  if (value === 'filter') makeFilterThumbs()
})

// ============================== 글자·도장·스티커
const selectedText = computed(() => {
  for (const item of edit.value.texts) {
    if (item.id === selectedTextId.value) return item
  }
  return null
})

function addText(values) {
  const item = Object.assign({ id: nextTextId++, text: '글자를 넣어 주세요', x: 0.5, y: 0.5, size: 60, color: '#FFFFFF', bold: true, shadow: true, box: false, font: 'sans' }, values)
  edit.value.texts.push(item)
  selectedTextId.value = item.id
  pushHistory()
}

// "2026-11-07T14:52" → "2026.11.07" (+ " 14:52")
function stampText(withTime) {
  const taken = props.photo.takenAtFull
  const date = taken.slice(0, 4) + '.' + taken.slice(5, 7) + '.' + taken.slice(8, 10)
  return withTime ? date + ' ' + taken.slice(11, 16) : date
}

// 날짜 도장: 필름 카메라처럼 오른쪽 아래 주황 글씨 (시각까지 넣으면 길어서 조금 왼쪽으로)
function addDateStamp(withTime) {
  addText({ text: stampText(withTime), x: withTime ? 0.72 : 0.8, y: 0.92, size: 36, color: '#FF8A3D', font: 'stamp' })
}

// 장소 이름: 위쪽 가운데 (날짜 도장과 안 겹치게)
function addPlaceStamp() {
  addText({ text: '📍 ' + (props.photo.placeName || '이동 중'), x: 0.5, y: 0.08, size: 40, box: true, shadow: false })
}

function addSticker(emoji) {
  addText({ text: emoji, x: 0.5, y: 0.4, size: 110, shadow: false, bold: false })
}

function changeText(key, value) {
  if (!selectedText.value) return
  selectedText.value[key] = value
}

function removeText() {
  edit.value.texts = edit.value.texts.filter((item) => item.id !== selectedTextId.value)
  selectedTextId.value = null
  pushHistory()
}

const selectedBoxStyle = computed(() => {
  const box = selectedBox.value
  if (!box || tool.value !== 'text') return null
  return { left: box.x0 * 100 + '%', top: box.y0 * 100 + '%', width: (box.x1 - box.x0) * 100 + '%', height: (box.y1 - box.y0) * 100 + '%' }
})

// ============================== 캔버스 누르기: 글자 옮기기 · 그림 그리기
// 화면 좌표 → 사진 안 비율(0~1)
function toPhotoPoint(event) {
  const rect = canvasRef.value.getBoundingClientRect()
  return [clamp01((event.clientX - rect.left) / rect.width), clamp01((event.clientY - rect.top) / rect.height)]
}

// 이 자리에 있는 글자 (위에 그린 것부터)
function textAt(point) {
  for (let i = textBoxes.length - 1; i >= 0; i--) {
    const box = textBoxes[i]
    if (point[0] >= box.x0 && point[0] <= box.x1 && point[1] >= box.y0 && point[1] <= box.y1) return box.id
  }
  return null
}

function onCanvasDown(event) {
  const point = toPhotoPoint(event)
  if (tool.value === 'draw') {
    edit.value.strokes.push({ color: drawColor.value, size: drawSize.value, alpha: isHighlighter.value ? 0.4 : 1, points: [point] })
    drag = { kind: 'draw' }
    listenDrag(event)
    return
  }
  if (tool.value !== 'text') return
  selectedTextId.value = textAt(point)
  if (!selectedText.value) return
  drag = { kind: 'text', offsetX: selectedText.value.x - point[0], offsetY: selectedText.value.y - point[1] }
  listenDrag(event)
}

function listenDrag(event) {
  event.preventDefault()
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp)
}

function onPointerMove(event) {
  if (!drag) return
  if (drag.kind === 'crop') {
    const dx = (event.clientX - drag.startX) / canvasBox.value.width
    const dy = (event.clientY - drag.startY) / canvasBox.value.height
    edit.value.crop = drag.handle === 'move' ? moveCrop(drag.start, dx, dy) : resizeCrop(drag.start, drag.handle, dx, dy)
    return
  }
  const point = toPhotoPoint(event)
  if (drag.kind === 'draw') {
    edit.value.strokes[edit.value.strokes.length - 1].points.push(point)
  } else if (drag.kind === 'text' && selectedText.value) {
    selectedText.value.x = clamp01(point[0] + drag.offsetX)
    selectedText.value.y = clamp01(point[1] + drag.offsetY)
  }
}

function onPointerUp() {
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
  if (drag) pushHistory()
  drag = null
}

function removeLastStroke() {
  edit.value.strokes.pop()
  pushHistory()
}

function clearStrokes() {
  edit.value.strokes = []
  pushHistory()
}

// ============================== 저장
// 편집한 사진을 원래 크기로 다시 그려서 JPG 파일로
async function exportBlob() {
  const oriented = buildOriented(image, edit.value, EXPORT_MAX)
  const crop = edit.value.crop
  const canvas = document.createElement('canvas')
  canvas.width = Math.max(1, Math.round(oriented.width * crop.w))
  canvas.height = Math.max(1, Math.round(oriented.height * crop.h))
  renderEdit(canvas, oriented, edit.value, { isCropView: false })
  // toBlob: 캔버스 그림을 파일 내용으로 바꿈. 다 되면 부르는 함수(콜백)라서 Promise로 감싸서 기다림
  return new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', JPEG_QUALITY))
}

// 새 사진으로 올리기 (원본은 그대로). 이름은 edited_원본번호.jpg, 서버가 다시 새 이름으로 저장
async function saveAsNew() {
  isSaving.value = true
  message.value = '저장하는 중…'
  try {
    const blob = await exportBlob()
    const form = new FormData() // 파일을 담는 상자
    form.append('files', new File([blob], 'edited_' + props.photo.id + '.jpg', { type: 'image/jpeg' }))
    form.append('takenAts', props.photo.takenAtFull)
    form.append('durations', 0)
    form.append('tripId', props.tripId)
    await upload('/photos', form)
    emit('saved', { originalId: props.photo.id, takenAtFull: props.photo.takenAtFull })
  } catch (e) {
    message.value = '저장하지 못했어요 (' + e.message + ')'
    isSaving.value = false
  }
}

// PC에 내려받기
async function download() {
  const blob = await exportBlob()
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob) // 내 PC에 저장할 파일 주소를 잠깐 만듦
  link.download = 'chakchak_' + props.photo.takenAtFull.replace(/[-:T]/g, '') + '.jpg'
  link.click()
  setTimeout(() => URL.revokeObjectURL(link.href), 1000)
}
</script>

<template>
  <div class="pe" role="dialog" aria-modal="true" aria-label="사진 편집">
    <!-- 위 줄: 되돌리기·비교·대표·저장 -->
    <header class="pe-top">
      <div class="pe-title">
        <b>사진 편집</b>
        <span>{{ photo.placeName || '이동 중' }} · {{ photo.timeLabel }}</span>
      </div>
      <div class="pe-actions">
        <button type="button" class="pe-btn" :disabled="historyIndex <= 0" title="Ctrl+Z" @click="undo">↶ 되돌리기</button>
        <button type="button" class="pe-btn" :disabled="historyIndex >= history.length - 1" title="Ctrl+Y" @click="redo">↷ 다시 하기</button>
        <button type="button" class="pe-btn" :disabled="!isLoaded" @pointerdown="isComparing = true" @pointerup="isComparing = false" @pointerleave="isComparing = false">원본 비교 (누르고 있기)</button>
        <button type="button" class="pe-btn" :disabled="!isChanged" @click="resetAll">처음 상태로</button>
        <span class="pe-gap"></span>
        <button v-if="canCover" type="button" class="pe-btn" :class="{ 'is-on': isCover }" @click="emit('cover', photo.id)">{{ isCover ? '★ 장소 대표 사진' : '☆ 장소 대표로' }}</button>
        <button type="button" class="pe-btn" :class="{ 'is-on': isTripCover }" @click="emit('trip-cover', photo.id)">{{ isTripCover ? '★ 여행 대표 사진' : '☆ 여행 대표로' }}</button>
        <button type="button" class="pe-btn" :disabled="!isLoaded" @click="download">PC에 내려받기</button>
        <button type="button" class="pe-btn pe-btn--main" :disabled="!isChanged || isSaving" @click="saveAsNew">새 사진으로 저장</button>
        <button type="button" class="pe-btn" aria-label="닫기" @click="askClose">✕</button>
      </div>
    </header>

    <div v-if="isAskingClose" class="pe-ask">
      고친 내용이 저장되지 않았어요. 닫을까요?
      <button type="button" class="pe-btn" @click="isAskingClose = false">계속 편집</button>
      <button type="button" class="pe-btn pe-btn--danger" @click="emit('close')">저장 안 하고 닫기</button>
    </div>
    <p v-if="message" class="pe-message">{{ message }}</p>

    <div class="pe-body">
      <!-- 사진 -->
      <div ref="stageRef" class="pe-stage">
        <div class="pe-canvas-wrap" :style="{ width: canvasBox.width + 'px', height: canvasBox.height + 'px' }">
          <canvas ref="canvasRef" :class="'pe-canvas pe-canvas--' + tool" @pointerdown="onCanvasDown"></canvas>
          <div v-if="tool === 'crop' && !isComparing" class="pe-crop" :style="cropStyle" @pointerdown="startCropDrag($event, 'move')">
            <i class="pe-crop__grid"></i>
            <span v-for="handle in ['nw', 'ne', 'sw', 'se']" :key="handle" :class="'pe-crop__handle pe-crop__handle--' + handle" @pointerdown.stop="startCropDrag($event, handle)"></span>
          </div>
          <div v-if="selectedBoxStyle" class="pe-select" :style="selectedBoxStyle"></div>
        </div>
        <p v-if="!isLoaded && !message" class="pe-loading">사진을 불러오는 중…</p>
      </div>

      <!-- 도구 -->
      <aside class="pe-panel">
        <div class="pe-tools">
          <button v-for="item in TOOLS" :key="item.id" type="button" :class="{ 'is-on': tool === item.id }" @click="tool = item.id">{{ item.name }}</button>
        </div>

        <section v-if="tool === 'crop'" class="pe-section">
          <h3>회전·뒤집기</h3>
          <div class="pe-row">
            <button type="button" class="pe-btn" @click="rotateBy(-90)">⟲ 왼쪽 90°</button>
            <button type="button" class="pe-btn" @click="rotateBy(90)">⟳ 오른쪽 90°</button>
            <button type="button" class="pe-btn" @click="flip('x')">⇋ 좌우</button>
            <button type="button" class="pe-btn" @click="flip('y')">⇵ 위아래</button>
          </div>
          <h3>수평 맞추기 <small>{{ edit.straighten }}°</small></h3>
          <input type="range" :min="-MAX_STRAIGHTEN" :max="MAX_STRAIGHTEN" step="0.5" :value="edit.straighten" @input="edit.straighten = Number($event.target.value)" @change="pushHistory" />
          <h3>자르기 비율</h3>
          <div class="pe-chips">
            <button v-for="aspect in ASPECTS" :key="aspect.id" type="button" :class="{ 'is-on': edit.aspect === aspect.id }" @click="chooseAspect(aspect.id)">{{ aspect.name }}</button>
          </div>
          <p class="pe-hint">사진 위 상자를 끌어 옮기고, 모서리를 끌어 크기를 바꿔요. 다른 도구를 누르면 자른 모습이 보여요.</p>
        </section>

        <section v-if="tool === 'adjust'" class="pe-section">
          <button type="button" class="pe-btn pe-btn--wide" @click="autoFix">✨ 자동 보정</button>
          <div v-for="item in ADJUSTS" :key="item.key" class="pe-slider">
            <div class="pe-slider__label">
              <span>{{ item.name }} <small v-if="item.hint">{{ item.hint }}</small></span>
              <button type="button" :disabled="edit.adjust[item.key] === 0" @click="resetAdjust(item.key)">{{ edit.adjust[item.key] }}</button>
            </div>
            <input type="range" :min="item.min" :max="item.max" :value="edit.adjust[item.key]" @input="changeAdjust(item.key, $event.target.value)" @change="pushHistory" />
          </div>
        </section>

        <section v-if="tool === 'filter'" class="pe-section">
          <div class="pe-filters">
            <button v-for="preset in FILTER_PRESETS" :key="preset.id" type="button" :class="{ 'is-on': edit.filter === preset.id }" @click="chooseFilter(preset.id)">
              <img v-if="filterThumbs[preset.id]" :src="filterThumbs[preset.id]" alt="" />
              <span>{{ preset.name }}</span>
            </button>
          </div>
          <p class="pe-hint">필터 위에 조정 값을 더 얹을 수 있어요.</p>
        </section>

        <section v-if="tool === 'text'" class="pe-section">
          <div class="pe-row">
            <button type="button" class="pe-btn" @click="addText({})">＋ 글자</button>
            <button type="button" class="pe-btn" @click="addDateStamp(false)">날짜 도장</button>
            <button type="button" class="pe-btn" @click="addDateStamp(true)">날짜·시각</button>
            <button type="button" class="pe-btn" @click="addPlaceStamp">장소 이름</button>
          </div>
          <h3>스티커</h3>
          <div class="pe-stickers">
            <button v-for="emoji in STICKERS" :key="emoji" type="button" @click="addSticker(emoji)">{{ emoji }}</button>
          </div>
          <template v-if="selectedText">
            <h3>고른 글자</h3>
            <textarea :value="selectedText.text" rows="2" @input="changeText('text', $event.target.value)" @change="pushHistory"></textarea>
            <div class="pe-slider">
              <div class="pe-slider__label"><span>크기</span><span>{{ selectedText.size }}</span></div>
              <input type="range" min="15" max="220" :value="selectedText.size" @input="changeText('size', Number($event.target.value))" @change="pushHistory" />
            </div>
            <div class="pe-colors">
              <button v-for="color in COLORS" :key="color" type="button" :style="{ background: color }" :class="{ 'is-on': selectedText.color === color }" :aria-label="'색 ' + color" @click="changeText('color', color); pushHistory()"></button>
            </div>
            <div class="pe-row">
              <button type="button" class="pe-btn" :class="{ 'is-on': selectedText.bold }" @click="changeText('bold', !selectedText.bold); pushHistory()">굵게</button>
              <button type="button" class="pe-btn" :class="{ 'is-on': selectedText.shadow }" @click="changeText('shadow', !selectedText.shadow); pushHistory()">그림자</button>
              <button type="button" class="pe-btn" :class="{ 'is-on': selectedText.box }" @click="changeText('box', !selectedText.box); pushHistory()">배경</button>
              <button type="button" class="pe-btn" :class="{ 'is-on': selectedText.font === 'stamp' }" @click="changeText('font', selectedText.font === 'stamp' ? 'sans' : 'stamp'); pushHistory()">도장 글꼴</button>
              <button type="button" class="pe-btn pe-btn--danger" @click="removeText">지우기</button>
            </div>
          </template>
          <p class="pe-hint">사진 위 글자를 눌러 고르고, 끌어서 옮겨요.</p>
        </section>

        <section v-if="tool === 'draw'" class="pe-section">
          <h3>펜 색</h3>
          <div class="pe-colors">
            <button v-for="color in COLORS" :key="color" type="button" :style="{ background: color }" :class="{ 'is-on': drawColor === color }" :aria-label="'펜 색 ' + color" @click="drawColor = color"></button>
          </div>
          <div class="pe-slider">
            <div class="pe-slider__label"><span>굵기</span><span>{{ drawSize }}</span></div>
            <input v-model.number="drawSize" type="range" min="2" max="60" />
          </div>
          <div class="pe-row">
            <button type="button" class="pe-btn" :class="{ 'is-on': !isHighlighter }" @click="isHighlighter = false">펜</button>
            <button type="button" class="pe-btn" :class="{ 'is-on': isHighlighter }" @click="isHighlighter = true">형광펜</button>
            <button type="button" class="pe-btn" :disabled="edit.strokes.length === 0" @click="removeLastStroke">마지막 선 지우기</button>
            <button type="button" class="pe-btn pe-btn--danger" :disabled="edit.strokes.length === 0" @click="clearStrokes">모두 지우기</button>
          </div>
          <p class="pe-hint">사진 위를 끌어서 그려요.</p>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.pe { position: fixed; inset: 0; z-index: 50; display: flex; flex-direction: column; background: #14181F; color: #E9EDF5; font-family: var(--font-sans); font-size: 14px; }
.pe-top { display: flex; align-items: center; gap: 16px; padding: 10px 16px; border-bottom: 1px solid #2A3140; flex-wrap: wrap; }
.pe-title { display: flex; flex-direction: column; }
.pe-title b { font-size: 16px; }
.pe-title span { font-size: 12px; opacity: 0.7; }
.pe-actions { display: flex; align-items: center; gap: 6px; flex: 1; flex-wrap: wrap; }
.pe-gap { flex: 1; }
.pe-btn { padding: 7px 12px; border: 1px solid #39425A; border-radius: var(--radius-md); background: #1E2430; color: inherit; font: inherit; cursor: pointer; white-space: nowrap; user-select: none; }
.pe-btn:hover:not(:disabled) { background: #283044; }
.pe-btn:disabled { opacity: 0.4; cursor: default; }
.pe-btn.is-on { border-color: #FF8A3D; color: #FFB27A; }
.pe-btn--main { border-color: var(--coral); background: var(--coral); color: #fff; font-weight: 700; }
.pe-btn--main:hover:not(:disabled) { background: var(--coral); opacity: 0.9; }
.pe-btn--danger { border-color: #7A3434; color: #FF9C9C; }
.pe-btn--wide { width: 100%; margin-bottom: 8px; }
.pe-ask { display: flex; align-items: center; gap: 8px; padding: 8px 16px; background: #3A2A1E; }
.pe-message { margin: 0; padding: 6px 16px; background: #1E2A3A; font-size: 13px; }

.pe-body { display: flex; flex: 1; min-height: 0; }
.pe-stage { position: relative; display: grid; place-items: center; flex: 1; min-width: 0; background: repeating-conic-gradient(#1A1F28 0% 25%, #161A22 0% 50%) 0 0 / 24px 24px; }
.pe-canvas-wrap { position: relative; }
.pe-canvas { display: block; box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5); touch-action: none; }
.pe-canvas--text { cursor: move; }
.pe-canvas--draw { cursor: crosshair; }
.pe-loading { position: absolute; }
.pe-crop { position: absolute; box-shadow: 0 0 0 9999px rgba(0, 0, 0, 0.55); outline: 2px solid #fff; cursor: move; touch-action: none; }
.pe-crop__grid { position: absolute; inset: 0; background:
  linear-gradient(to right, transparent 33.2%, rgba(255, 255, 255, 0.5) 33.3%, transparent 33.4%, transparent 66.6%, rgba(255, 255, 255, 0.5) 66.7%, transparent 66.8%),
  linear-gradient(to bottom, transparent 33.2%, rgba(255, 255, 255, 0.5) 33.3%, transparent 33.4%, transparent 66.6%, rgba(255, 255, 255, 0.5) 66.7%, transparent 66.8%); pointer-events: none; }
.pe-crop__handle { position: absolute; width: 18px; height: 18px; border: 3px solid #fff; background: rgba(0, 0, 0, 0.3); }
.pe-crop__handle--nw { left: -9px; top: -9px; cursor: nwse-resize; }
.pe-crop__handle--ne { right: -9px; top: -9px; cursor: nesw-resize; }
.pe-crop__handle--sw { left: -9px; bottom: -9px; cursor: nesw-resize; }
.pe-crop__handle--se { right: -9px; bottom: -9px; cursor: nwse-resize; }
.pe-select { position: absolute; outline: 2px dashed #FFB27A; pointer-events: none; }

.pe-panel { width: 340px; flex: none; overflow-y: auto; border-left: 1px solid #2A3140; background: #181D26; }
.pe-tools { position: sticky; top: 0; display: grid; grid-template-columns: repeat(5, 1fr); background: #181D26; border-bottom: 1px solid #2A3140; }
.pe-tools button { padding: 12px 2px; border: 0; border-bottom: 3px solid transparent; background: none; color: inherit; font: inherit; font-size: 13px; cursor: pointer; opacity: 0.7; }
.pe-tools button.is-on { border-bottom-color: #FF8A3D; opacity: 1; font-weight: 700; }
.pe-section { padding: 14px 16px 24px; }
.pe-section h3 { margin: 16px 0 8px; font-size: 13px; opacity: 0.8; }
.pe-section h3:first-child { margin-top: 0; }
.pe-section h3 small { margin-left: 4px; opacity: 0.7; }
.pe-section input[type='range'] { width: 100%; accent-color: #FF8A3D; }
.pe-section textarea { width: 100%; padding: 8px; border: 1px solid #39425A; border-radius: var(--radius-md); background: #10141B; color: inherit; font: inherit; resize: vertical; box-sizing: border-box; }
.pe-row { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.pe-chips { display: flex; flex-wrap: wrap; gap: 6px; }
.pe-chips button { padding: 6px 10px; border: 1px solid #39425A; border-radius: var(--radius-full); background: none; color: inherit; font: inherit; cursor: pointer; }
.pe-chips button.is-on { border-color: #FF8A3D; background: #FF8A3D; color: #14181F; font-weight: 700; }
.pe-hint { margin: 14px 0 0; font-size: 12px; opacity: 0.6; line-height: 18px; }
.pe-slider { margin-top: 10px; }
.pe-slider__label { display: flex; justify-content: space-between; align-items: center; font-size: 13px; }
.pe-slider__label small { opacity: 0.55; font-size: 11px; }
.pe-slider__label button { min-width: 40px; padding: 1px 6px; border: 1px solid #39425A; border-radius: var(--radius-sm); background: none; color: inherit; font: inherit; font-size: 12px; cursor: pointer; }
.pe-slider__label button:disabled { opacity: 0.5; cursor: default; }
.pe-filters { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.pe-filters button { display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 4px; border: 2px solid transparent; border-radius: var(--radius-md); background: #10141B; color: inherit; font: inherit; font-size: 12px; cursor: pointer; }
.pe-filters button.is-on { border-color: #FF8A3D; }
.pe-filters img { width: 100%; aspect-ratio: 1; object-fit: cover; border-radius: var(--radius-sm); }
.pe-stickers { display: grid; grid-template-columns: repeat(8, 1fr); gap: 4px; }
.pe-stickers button { padding: 4px 0; border: 0; border-radius: var(--radius-sm); background: #10141B; font-size: 22px; cursor: pointer; }
.pe-stickers button:hover { background: #283044; }
.pe-colors { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.pe-colors button { width: 28px; height: 28px; border: 2px solid #39425A; border-radius: 50%; cursor: pointer; }
.pe-colors button.is-on { border-color: #fff; box-shadow: 0 0 0 2px #FF8A3D; }
</style>
