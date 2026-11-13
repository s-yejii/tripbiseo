<script setup>
// ⑤ 웹 사진 편집기 (11/10 → 11/11 다시 만듦: 손으로 꾸미는 편집). 여행 메모 > 사진에서 사진을 누르면 떠요
//  - 왼쪽: 이 여행 사진 목록 (눌러서 다른 사진 편집, [+ 사진 추가])
//  - 가운데: 사진 / 오른쪽: 펜·형광펜·지우개·선택, 굵기, 색, 텍스트·스티커·도형·도형 채우기·기타
// 원본은 그대로 두고, [저장]을 누르면 꾸민 사진을 새 사진으로 올려요 (기존 API ⑤ POST /api/photos)
// 찍은 시각을 원본과 같게 보내서, 서버가 원본과 같은 장소로 분류해요
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { upload } from '@/api/http'
import { emptySketch, renderSketch } from './sketchRender'

const props = defineProps({
  photo: { type: Object, required: true },   // { id, url, takenAtFull, placeName, timeLabel, day }
  photos: { type: Array, default: () => [] }, // 왼쪽 목록에 보여 줄 사진 (같은 모양)
  tripId: { type: [Number, String], required: true },
  isCover: { type: Boolean, default: false },      // 이 장소의 대표 사진인지
  isTripCover: { type: Boolean, default: false },  // 여행 대표 사진인지
  canCover: { type: Boolean, default: false },     // 장소에서 찍은 사진인지 (이동 중 사진은 장소 대표가 될 수 없음)
})
const emit = defineEmits(['close', 'saved', 'cover', 'trip-cover', 'switch', 'add-files'])

const PREVIEW_MAX = 1600        // 미리보기 사진 긴 변(px)
const EXPORT_MAX = 3000         // 저장할 사진 긴 변 최대(px)
const HISTORY_MAX = 60          // 되돌리기 몇 번까지
const JPEG_QUALITY = 0.92
const HAND_FONT_URL = 'https://fonts.googleapis.com/css2?family=Gaegu:wght@400;700&display=swap' // 손글씨 글꼴 (Google Fonts)
const TOOLS = [
  { id: 'pen', name: '펜', icon: '✏️' },
  { id: 'marker', name: '형광펜', icon: '🖍️' },
  { id: 'eraser', name: '지우개', icon: '🧽' },
  { id: 'select', name: '선택', icon: '⬚' },
]
const SIZE_PRESETS = [2, 5, 10]  // 굵기 빠른 선택(pt)
const COLORS = ['#111111', '#8A8A8A', '#FFFFFF', '#F03E3E', '#FF8A1E', '#FFD43B', '#40C057', '#1E9BFF', '#3B5BDB', '#BE4BDB', '#F7A8C8']
const STICKERS = ['❤️', '💕', '⭐', '✨', '😊', '🥰', '😆', '🐻', '🌸', '🍁', '☀️', '🌙', '📍', '📸', '☕', '🍜', '🍰', '🎉', '✅', '👍']
const SHAPES = [
  { id: 'rect', name: '네모', icon: '▢' },
  { id: 'circle', name: '동그라미', icon: '◯' },
  { id: 'heart', name: '하트', icon: '♡' },
  { id: 'star', name: '별', icon: '☆' },
  { id: 'cloud', name: '말풍선', icon: '☁' },
  { id: 'arrow', name: '화살표', icon: '→' },
]
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

const tool = ref('pen')
const penSize = ref(5)
const penColor = ref('#F7A8C8')
const isFill = ref(false)
const openSection = ref('')        // 펼친 줄: 'text' · 'sticker' · 'shape' · 'more' · ''
const newText = ref('')
const sketch = ref(emptySketch())
const history = ref([])
const historyIndex = ref(-1)
const selectedId = ref(null)
const selectedBox = ref(null)      // 고른 것의 자리 (점선 상자)
const isLoaded = ref(false)
const isSaving = ref(false)
const message = ref('')
const pendingLeave = ref(null)     // 저장 안 하고 떠날지 묻는 중: { kind: 'close' } 또는 { kind: 'switch', photo }
const canvasBox = ref({ width: 0, height: 0 })
const stageRef = ref(null)
const canvasRef = ref(null)

let image = null                   // 원본 사진
let preview = null                 // 미리보기용으로 줄인 사진
let boxes = []                     // 마지막으로 그린 고를 수 있는 것들의 자리
let renderFrame = null
let drag = null                    // 끄는 중 { kind: 'draw' | 'move' | 'resize', … }
let nextId = 1
let addCount = 0                   // 넣은 횟수 (넣을 자리를 조금씩 옮기는 데 씀)

// ============================== 열기·닫기
onMounted(async () => {
  window.addEventListener('keydown', onKey)
  window.addEventListener('resize', scheduleRender)
  loadHandFont()
  image = new Image()
  image.src = props.photo.url
  try {
    await image.decode() // 사진을 다 받을 때까지 기다림
  } catch (e) {
    message.value = '사진을 불러오지 못했어요.'
    return
  }
  preview = shrink(image, PREVIEW_MAX)
  isLoaded.value = true
  pushHistory()
  await nextTick()
  scheduleRender()
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKey)
  window.removeEventListener('resize', scheduleRender)
  stopDragListen()
  cancelAnimationFrame(renderFrame)
})

// 손글씨 글꼴을 한 번만 붙이고, 다 받으면 다시 그림 (못 받아도 기본 글꼴로 그려짐)
function loadHandFont() {
  if (!document.getElementById('chakchak-hand-font')) {
    const link = document.createElement('link')
    link.id = 'chakchak-hand-font'
    link.rel = 'stylesheet'
    link.href = HAND_FONT_URL
    document.head.appendChild(link)
  }
  document.fonts.load('700 40px Gaegu').then(scheduleRender).catch(() => {})
}

// 긴 변이 maxSide를 넘지 않게 줄인 캔버스
function shrink(source, maxSide) {
  const scale = Math.min(1, maxSide / Math.max(source.naturalWidth, source.naturalHeight))
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(source.naturalWidth * scale)
  canvas.height = Math.round(source.naturalHeight * scale)
  canvas.getContext('2d').drawImage(source, 0, 0, canvas.width, canvas.height)
  return canvas
}

const isChanged = computed(() => historyIndex.value > 0)

// 떠나기 전에 확인 (고친 게 있으면 묻는 줄을 띄움)
function leave(request) {
  if (isChanged.value && !isSaving.value) {
    pendingLeave.value = request
    return
  }
  doLeave(request)
}

function doLeave(request) {
  pendingLeave.value = null
  if (request.kind === 'switch') emit('switch', request.photo)
  else emit('close')
}

// 단축키: Ctrl+Z 되돌리기, Ctrl+Y 다시 하기, Delete 고른 것 지우기, Esc 닫기 (글자를 쓰는 중이면 그 칸이 먼저)
function onKey(event) {
  const isTyping = event.target.tagName === 'INPUT' || event.target.tagName === 'TEXTAREA'
  if (isTyping) return
  if (event.key === 'Escape') leave({ kind: 'close' })
  else if ((event.key === 'Delete' || event.key === 'Backspace') && selectedId.value !== null) removeSelected()
  else if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'z' && !event.shiftKey) undo()
  else if ((event.ctrlKey || event.metaKey) && (event.key.toLowerCase() === 'y' || event.key.toLowerCase() === 'z')) redo()
  else return
  event.preventDefault()
}

// ============================== 되돌리기
function pushHistory() {
  const snapshot = JSON.stringify(sketch.value)
  if (history.value[historyIndex.value] === snapshot) return // 바뀐 게 없으면 기록 안 함
  const list = history.value.slice(0, historyIndex.value + 1) // 되돌린 뒤 새로 고치면 '다시 하기' 기록은 버림
  list.push(snapshot)
  if (list.length > HISTORY_MAX) list.shift()
  history.value = list
  historyIndex.value = list.length - 1
}

function undo() {
  if (historyIndex.value <= 0) return
  historyIndex.value--
  sketch.value = JSON.parse(history.value[historyIndex.value])
  selectedId.value = null
}

function redo() {
  if (historyIndex.value >= history.value.length - 1) return
  historyIndex.value++
  sketch.value = JSON.parse(history.value[historyIndex.value])
  selectedId.value = null
}

// ============================== 화면에 그리기
watch([sketch, selectedId], scheduleRender, { deep: true })

// 한 화면에 한 번만 그림 (펜을 빨리 움직여도 밀리지 않게)
function scheduleRender() {
  cancelAnimationFrame(renderFrame)
  renderFrame = requestAnimationFrame(render)
}

function render() {
  const canvas = canvasRef.value
  const stage = stageRef.value
  if (!isLoaded.value || !canvas || !stage) return
  const scale = Math.min((stage.clientWidth - 40) / preview.width, (stage.clientHeight - 40) / preview.height)
  const width = Math.max(1, Math.round(preview.width * scale))
  const height = Math.max(1, Math.round(preview.height * scale))
  const ratio = Math.min(2, window.devicePixelRatio || 1) // 레티나 화면에서 선명하게
  canvas.style.width = width + 'px'
  canvas.style.height = height + 'px'
  canvas.width = Math.round(width * ratio)
  canvas.height = Math.round(height * ratio)
  canvasBox.value = { width: width, height: height }
  boxes = renderSketch(canvas, preview, sketch.value)
  selectedBox.value = findBox(selectedId.value)
}

function findBox(id) {
  for (const box of boxes) {
    if (box.id === id) return box
  }
  return null
}

const selectedBoxStyle = computed(() => {
  const box = selectedBox.value
  if (!box || tool.value !== 'select') return null
  return { left: box.x0 * 100 + '%', top: box.y0 * 100 + '%', width: (box.x1 - box.x0) * 100 + '%', height: (box.y1 - box.y0) * 100 + '%' }
})

// ============================== 넣기 (글자·스티커·도형·메모지) → 넣은 것을 골라 둠
function addItem(values) {
  const shift = (addCount % 5) * 0.07 - 0.14 // 새로 넣는 것끼리 안 겹치게 −0.14 ~ +0.14 사이를 돌아가며 옮김
  addCount++
  const item = Object.assign({ id: nextId++, x: 0.5 + shift, y: 0.45 + shift }, values)
  sketch.value.items.push(item)
  tool.value = 'select'
  selectedId.value = item.id
  pushHistory()
}

function addText() {
  const text = newText.value.trim()
  if (text === '') return
  addItem({ type: 'text', text: text, size: 55, color: penColor.value, font: 'hand' })
  newText.value = ''
}

function addSticker(emoji) {
  addItem({ type: 'sticker', emoji: emoji, size: 90 })
}

function addShape(shape) {
  const isLong = shape === 'arrow' || shape === 'cloud'
  addItem({ type: 'shape', shape: shape, w: isLong ? 0.32 : 0.2, h: shape === 'arrow' ? 0.06 : 0.2, color: penColor.value, size: penSize.value, fill: isFill.value })
}

// 날짜 메모지: "2026.10.31 (토)" + "경복궁 · 1일차 ♥"
function addNote() {
  const taken = props.photo.takenAtFull
  const date = new Date(taken.slice(0, 10) + 'T00:00:00')
  const first = taken.slice(0, 4) + '.' + taken.slice(5, 7) + '.' + taken.slice(8, 10) + ' (' + WEEKDAYS[date.getDay()] + ')'
  const second = (props.photo.placeName || '여행') + ' · ' + props.photo.day + '일차 ♥'
  addItem({ type: 'note', text: first + '\n' + second, x: 0.27, y: 0.82, w: 0.3, color: '#3A3A3A' })
}

// ============================== 고른 것 바꾸기 (색·굵기·채우기를 누르면 고른 것에도)
const selectedItem = computed(() => {
  for (const item of sketch.value.items) {
    if (item.id === selectedId.value) return item
  }
  return null
})

function chooseColor(color) {
  penColor.value = color
  if (tool.value === 'eraser') tool.value = 'pen'
  const item = selectedItem.value
  if (item && item.color !== undefined && item.type !== 'note') {
    item.color = color
    pushHistory()
  }
}

function chooseSize(size) {
  penSize.value = size
  const item = selectedItem.value
  if (item && item.type === 'shape') {
    item.size = size
    pushHistory()
  }
}

function toggleFill() {
  isFill.value = !isFill.value
  const item = selectedItem.value
  if (item && item.type === 'shape') {
    item.fill = isFill.value
    pushHistory()
  }
}

function changeSelectedText(value) {
  if (!selectedItem.value) return
  selectedItem.value.text = value
}

function removeSelected() {
  sketch.value.items = sketch.value.items.filter((item) => item.id !== selectedId.value)
  selectedId.value = null
  pushHistory()
}

function clearAll() {
  sketch.value = emptySketch()
  selectedId.value = null
  pushHistory()
}

function chooseTool(id) {
  tool.value = id
  if (id !== 'select') selectedId.value = null
}

function toggleSection(name) {
  openSection.value = openSection.value === name ? '' : name
}

// ============================== 캔버스 누르기: 그리기 · 고르기 · 옮기기 · 크기 바꾸기
// 화면 좌표 → 사진 안 비율(0~1)
function toPhotoPoint(event) {
  const rect = canvasRef.value.getBoundingClientRect()
  return [Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width)), Math.max(0, Math.min(1, (event.clientY - rect.top) / rect.height))]
}

// 이 자리에 있는 것 (위에 그린 것부터)
function itemAt(point) {
  for (let i = boxes.length - 1; i >= 0; i--) {
    const box = boxes[i]
    if (point[0] >= box.x0 && point[0] <= box.x1 && point[1] >= box.y0 && point[1] <= box.y1) return box.id
  }
  return null
}

function onCanvasDown(event) {
  const point = toPhotoPoint(event)
  if (tool.value !== 'select') {
    sketch.value.items.push({ id: nextId++, type: 'stroke', mode: tool.value, color: penColor.value, size: penSize.value, points: [point] })
    drag = { kind: 'draw' }
    startDragListen(event)
    return
  }
  selectedId.value = itemAt(point)
  if (!selectedItem.value) return
  drag = { kind: 'move', offsetX: selectedItem.value.x - point[0], offsetY: selectedItem.value.y - point[1] }
  startDragListen(event)
}

// 오른쪽 아래 손잡이: 가운데에서 멀어질수록 크게
function onResizeDown(event) {
  const item = selectedItem.value
  if (!item) return
  const point = toPhotoPoint(event)
  drag = { kind: 'resize', startDistance: Math.max(0.01, Math.hypot(point[0] - item.x, point[1] - item.y)), start: JSON.parse(JSON.stringify(item)) }
  startDragListen(event)
}

function startDragListen(event) {
  event.preventDefault()
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp)
}

function stopDragListen() {
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
}

function onPointerMove(event) {
  if (!drag) return
  const point = toPhotoPoint(event)
  const items = sketch.value.items
  if (drag.kind === 'draw') {
    items[items.length - 1].points.push(point)
  } else if (drag.kind === 'move' && selectedItem.value) {
    selectedItem.value.x = Math.max(0, Math.min(1, point[0] + drag.offsetX))
    selectedItem.value.y = Math.max(0, Math.min(1, point[1] + drag.offsetY))
  } else if (drag.kind === 'resize' && selectedItem.value) {
    resizeItem(selectedItem.value, drag.start, Math.hypot(point[0] - drag.start.x, point[1] - drag.start.y) / drag.startDistance)
  }
}

// 크기 배율만큼 키우기 (너무 작아지지 않게 0.2배까지)
function resizeItem(item, start, ratio) {
  const scale = Math.max(0.2, ratio)
  if (item.type === 'shape') {
    item.w = start.w * scale
    item.h = start.h * scale
  } else if (item.type === 'note') {
    item.w = start.w * scale
  } else {
    item.size = start.size * scale
  }
}

function onPointerUp() {
  stopDragListen()
  if (drag) pushHistory()
  drag = null
}

// ============================== 저장
// 꾸민 사진을 원래 크기(최대 EXPORT_MAX)로 다시 그려서 JPG 파일로
async function exportBlob() {
  const canvas = shrink(image, EXPORT_MAX) // 크기만 맞춘 캔버스 (사진은 아래에서 다시 그림)
  renderSketch(canvas, image, sketch.value)
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

function pickFiles(event) {
  const files = Array.from(event.target.files)
  event.target.value = '' // 같은 파일을 다시 골라도 올라가게
  if (files.length > 0) emit('add-files', files)
}

// 굵기 미리보기 물결 선 굵기(px)
function presetStroke(size) {
  return Math.max(1.5, size * 0.55)
}
</script>

<template>
  <div class="pe" role="dialog" aria-modal="true" aria-label="사진 편집">
    <!-- 위 줄: 앨범으로 · 제목 · 되돌리기 · 저장 -->
    <header class="pe-top">
      <button type="button" class="pe-back" @click="leave({ kind: 'close' })">‹ 여행 사진 앨범</button>
      <div class="pe-title">
        <b>사진 편집</b>
        <span>{{ photo.placeName || '이동 중' }} · {{ photo.timeLabel }}</span>
      </div>
      <div class="pe-actions">
        <button type="button" class="pe-round" :disabled="historyIndex <= 0" title="되돌리기 (Ctrl+Z)" aria-label="되돌리기" @click="undo">↶</button>
        <button type="button" class="pe-round" :disabled="historyIndex >= history.length - 1" title="다시 하기 (Ctrl+Y)" aria-label="다시 하기" @click="redo">↷</button>
        <button type="button" class="pe-save" :disabled="!isChanged || isSaving" @click="saveAsNew">저장</button>
      </div>
    </header>

    <div v-if="pendingLeave" class="pe-ask">
      꾸민 내용이 저장되지 않았어요. 저장 안 하고 {{ pendingLeave.kind === 'switch' ? '다른 사진으로 갈까요?' : '나갈까요?' }}
      <button type="button" class="pe-chip" @click="pendingLeave = null">계속 편집</button>
      <button type="button" class="pe-chip pe-chip--danger" @click="doLeave(pendingLeave)">저장 안 하고 {{ pendingLeave.kind === 'switch' ? '넘어가기' : '나가기' }}</button>
    </div>
    <p v-if="message" class="pe-message">{{ message }}</p>

    <div class="pe-body">
      <!-- 왼쪽: 이 여행 사진 -->
      <nav class="pe-strip" aria-label="여행 사진">
        <button type="button" class="pe-strip__grid" title="앨범으로" aria-label="앨범으로" @click="leave({ kind: 'close' })">▦</button>
        <div class="pe-strip__list">
          <button v-for="item in photos" :key="item.id" type="button" class="pe-strip__thumb" :class="{ 'is-on': item.id === photo.id }" :aria-label="(item.placeName || '이동 중') + ' ' + item.timeLabel" @click="item.id !== photo.id && leave({ kind: 'switch', photo: item })">
            <img :src="item.url" alt="" loading="lazy" />
          </button>
        </div>
        <label class="pe-strip__add">
          <input type="file" accept="image/*" multiple @change="pickFiles" />
          ＋ 사진 추가
        </label>
      </nav>

      <!-- 가운데: 사진 -->
      <div ref="stageRef" class="pe-stage">
        <div class="pe-canvas-wrap" :style="{ width: canvasBox.width + 'px', height: canvasBox.height + 'px' }">
          <canvas ref="canvasRef" :class="'pe-canvas pe-canvas--' + tool" @pointerdown="onCanvasDown"></canvas>
          <div v-if="selectedBoxStyle" class="pe-select" :style="selectedBoxStyle">
            <span class="pe-select__handle" title="끌어서 크기 바꾸기" @pointerdown.stop="onResizeDown"></span>
          </div>
        </div>
        <p v-if="!isLoaded && !message" class="pe-loading">사진을 불러오는 중…</p>
      </div>

      <!-- 오른쪽: 도구 -->
      <aside class="pe-panel">
        <div class="pe-tools">
          <button v-for="item in TOOLS" :key="item.id" type="button" :class="{ 'is-on': tool === item.id }" :aria-pressed="tool === item.id" @click="chooseTool(item.id)">
            <span class="pe-tools__icon">{{ item.icon }}</span>{{ item.name }}
          </button>
        </div>

        <!-- 고른 것 (선택 도구) -->
        <div v-if="tool === 'select'" class="pe-picked">
          <template v-if="selectedItem">
            <textarea v-if="selectedItem.type === 'text' || selectedItem.type === 'note'" :value="selectedItem.text" rows="2" aria-label="글자 고치기" @input="changeSelectedText($event.target.value)" @change="pushHistory"></textarea>
            <div class="pe-picked__row">
              <span>모서리 손잡이를 끌면 크기가 바뀌어요</span>
              <button type="button" class="pe-chip pe-chip--danger" @click="removeSelected">지우기</button>
            </div>
          </template>
          <p v-else class="pe-hint">사진 위 글자·스티커·도형을 눌러 고르고, 끌어서 옮겨요.</p>
        </div>

        <h3>펜 굵기 <span class="pe-value">{{ penSize }} pt</span></h3>
        <input :value="penSize" type="range" min="1" max="30" class="pe-range" aria-label="펜 굵기" @input="chooseSize(Number($event.target.value))" />
        <div class="pe-presets">
          <button v-for="size in SIZE_PRESETS" :key="size" type="button" :class="{ 'is-on': penSize === size }" :aria-label="size + 'pt'" @click="chooseSize(size)">
            <svg viewBox="0 0 60 20" aria-hidden="true"><path d="M4 12 C 14 2, 22 2, 30 10 S 46 18, 56 8" fill="none" stroke="currentColor" :stroke-width="presetStroke(size)" stroke-linecap="round" /></svg>
          </button>
        </div>

        <h3>펜 색상</h3>
        <div class="pe-colors">
          <button v-for="color in COLORS" :key="color" type="button" :style="{ background: color }" :class="{ 'is-on': penColor === color }" :aria-label="'색 ' + color" @click="chooseColor(color)"></button>
          <label class="pe-colors__any" :class="{ 'is-on': !COLORS.includes(penColor) }" title="다른 색 고르기">
            <input type="color" :value="penColor" aria-label="다른 색" @change="chooseColor($event.target.value)" />
          </label>
        </div>

        <div class="pe-rows">
          <button type="button" class="pe-row" :aria-expanded="openSection === 'text'" @click="toggleSection('text')"><span class="pe-row__icon">T</span>텍스트 추가<span class="pe-row__arrow">{{ openSection === 'text' ? '⌄' : '›' }}</span></button>
          <form v-if="openSection === 'text'" class="pe-open" @submit.prevent="addText">
            <textarea v-model="newText" rows="2" placeholder="예: 노을이 너무 예뻤다!" aria-label="넣을 글자"></textarea>
            <button type="submit" class="pe-chip pe-chip--main" :disabled="newText.trim() === ''">사진에 넣기</button>
          </form>

          <button type="button" class="pe-row" :aria-expanded="openSection === 'sticker'" @click="toggleSection('sticker')"><span class="pe-row__icon">☺</span>스티커<span class="pe-row__arrow">{{ openSection === 'sticker' ? '⌄' : '›' }}</span></button>
          <div v-if="openSection === 'sticker'" class="pe-open pe-stickers">
            <button v-for="emoji in STICKERS" :key="emoji" type="button" @click="addSticker(emoji)">{{ emoji }}</button>
          </div>

          <button type="button" class="pe-row" :aria-expanded="openSection === 'shape'" @click="toggleSection('shape')"><span class="pe-row__icon">▢</span>도형<span class="pe-row__arrow">{{ openSection === 'shape' ? '⌄' : '›' }}</span></button>
          <div v-if="openSection === 'shape'" class="pe-open pe-shapes">
            <button v-for="shape in SHAPES" :key="shape.id" type="button" @click="addShape(shape.id)"><b>{{ shape.icon }}</b>{{ shape.name }}</button>
          </div>

          <div class="pe-row pe-row--toggle">
            <span class="pe-row__icon">◆</span>도형 채우기
            <button type="button" class="pe-switch" :class="{ 'is-on': isFill }" role="switch" :aria-checked="isFill" aria-label="도형 채우기" @click="toggleFill"><i></i></button>
          </div>

          <button type="button" class="pe-row" :aria-expanded="openSection === 'more'" @click="toggleSection('more')"><span class="pe-row__icon">⋯</span>기타<span class="pe-row__arrow">{{ openSection === 'more' ? '⌄' : '›' }}</span></button>
          <div v-if="openSection === 'more'" class="pe-open pe-more">
            <button type="button" class="pe-chip" @click="addNote">🗒 날짜 메모지 붙이기</button>
            <button v-if="canCover" type="button" class="pe-chip" :class="{ 'is-on': isCover }" @click="emit('cover', photo.id)">{{ isCover ? '★ 장소 대표 사진' : '☆ 장소 대표로' }}</button>
            <button type="button" class="pe-chip" :class="{ 'is-on': isTripCover }" @click="emit('trip-cover', photo.id)">{{ isTripCover ? '★ 여행 대표 사진' : '☆ 여행 대표로' }}</button>
            <button type="button" class="pe-chip" :disabled="!isLoaded" @click="download">PC에 내려받기</button>
            <button type="button" class="pe-chip pe-chip--danger" :disabled="sketch.items.length === 0" @click="clearAll">꾸민 것 모두 지우기</button>
          </div>
        </div>
        <p class="pe-hint">[저장]하면 원본은 그대로 두고 꾸민 사진이 새 사진으로 앨범에 들어가요.</p>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.pe { position: fixed; inset: 0; z-index: 50; display: flex; flex-direction: column; background: #F1F3F8; color: var(--ink); font-family: var(--font-sans); font-size: 14px; }
.pe-top { display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; gap: 16px; padding: 14px 24px; }
.pe-back { justify-self: start; padding: 6px 4px; border: 0; background: none; color: var(--navy); font: inherit; font-size: 18px; font-weight: 700; cursor: pointer; }
.pe-title { display: flex; flex-direction: column; align-items: center; }
.pe-title b { font-size: 20px; }
.pe-title span { font-size: 12px; color: var(--ink-muted); }
.pe-actions { display: flex; align-items: center; justify-content: flex-end; gap: 10px; }
.pe-round { width: 42px; height: 42px; border: 0; border-radius: 50%; background: #fff; color: var(--ink); font-size: 20px; box-shadow: 0 2px 8px rgba(24, 33, 58, 0.08); cursor: pointer; }
.pe-round:disabled { opacity: 0.35; cursor: default; }
.pe-save { padding: 10px 26px; border: 0; border-radius: var(--radius-full); background: #2F7BFF; color: #fff; font: inherit; font-size: 16px; font-weight: 700; box-shadow: 0 4px 12px rgba(47, 123, 255, 0.35); cursor: pointer; }
.pe-save:disabled { opacity: 0.45; box-shadow: none; cursor: default; }
.pe-ask { display: flex; align-items: center; gap: 8px; margin: 0 24px 8px; padding: 8px 14px; border-radius: var(--radius-md); background: #FFF1E6; }
.pe-message { margin: 0 24px 8px; padding: 6px 14px; border-radius: var(--radius-md); background: #E8F0FF; font-size: 13px; }

.pe-body { display: flex; gap: 16px; flex: 1; min-height: 0; padding: 0 24px 20px; }
.pe-strip { display: flex; flex-direction: column; align-items: center; gap: 10px; width: 112px; flex: none; }
.pe-strip__grid { width: 100%; height: 48px; border: 0; border-radius: 0 var(--radius-lg) var(--radius-lg) 0; background: #fff; font-size: 22px; color: var(--ink); box-shadow: 0 2px 8px rgba(24, 33, 58, 0.06); cursor: pointer; }
.pe-strip__list { display: flex; flex-direction: column; gap: 10px; width: 100%; overflow-y: auto; flex: 1; padding: 2px; box-sizing: border-box; }
.pe-strip__thumb { width: 100%; aspect-ratio: 1; padding: 0; border: 3px solid transparent; border-radius: var(--radius-md); background: #fff; overflow: hidden; cursor: pointer; flex: none; }
.pe-strip__thumb img { width: 100%; height: 100%; object-fit: cover; display: block; }
.pe-strip__thumb.is-on { border-color: #2F7BFF; box-shadow: 0 0 0 2px rgba(47, 123, 255, 0.25); }
.pe-strip__add { position: relative; width: 100%; padding: 10px 0; border-radius: var(--radius-lg); background: #fff; color: var(--navy); font-size: 13px; font-weight: 700; text-align: center; box-shadow: 0 2px 8px rgba(24, 33, 58, 0.06); cursor: pointer; }
.pe-strip__add input { position: absolute; inset: 0; opacity: 0; cursor: pointer; }

.pe-stage { position: relative; display: grid; place-items: center; flex: 1; min-width: 0; border-radius: 24px; background: #fff; box-shadow: 0 4px 18px rgba(24, 33, 58, 0.06); }
.pe-canvas-wrap { position: relative; }
.pe-canvas { display: block; border-radius: 18px; touch-action: none; cursor: crosshair; }
.pe-canvas--select { cursor: default; }
.pe-canvas--eraser { cursor: cell; }
.pe-loading { position: absolute; color: var(--ink-muted); }
.pe-select { position: absolute; outline: 2px dashed #2F7BFF; pointer-events: none; }
.pe-select__handle { position: absolute; right: -9px; bottom: -9px; width: 16px; height: 16px; border: 2px solid #2F7BFF; border-radius: 50%; background: #fff; pointer-events: auto; cursor: nwse-resize; touch-action: none; }

.pe-panel { width: 330px; flex: none; overflow-y: auto; padding: 14px 16px 20px; border-radius: 24px; background: #fff; box-shadow: 0 4px 18px rgba(24, 33, 58, 0.06); box-sizing: border-box; }
.pe-panel h3 { display: flex; justify-content: space-between; align-items: center; margin: 18px 0 8px; font-size: 15px; }
.pe-value { font-size: 14px; font-weight: 400; }
.pe-tools { display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px; }
.pe-tools button { display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 10px 0; border: 0; border-radius: var(--radius-lg); background: none; color: var(--ink); font: inherit; font-size: 13px; cursor: pointer; }
.pe-tools button.is-on { background: #E8F0FF; color: #1F5FD6; font-weight: 700; }
.pe-tools__icon { font-size: 22px; line-height: 1; }
.pe-range { width: 100%; accent-color: #2F7BFF; }
.pe-presets { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-top: 8px; }
.pe-presets button { height: 40px; padding: 4px 10px; border: 1px solid var(--line); border-radius: var(--radius-md); background: #fff; color: var(--ink); cursor: pointer; }
.pe-presets button.is-on { border-color: #2F7BFF; background: #F2F6FF; }
.pe-presets svg { width: 100%; height: 100%; }
.pe-colors { display: grid; grid-template-columns: repeat(6, 1fr); gap: 10px; }
.pe-colors button, .pe-colors__any { width: 34px; height: 34px; border: 1px solid #D9DDE6; border-radius: 50%; cursor: pointer; box-sizing: border-box; }
.pe-colors button.is-on, .pe-colors__any.is-on { box-shadow: 0 0 0 3px #fff, 0 0 0 5px #2F7BFF; }
.pe-colors__any { position: relative; background: conic-gradient(#F03E3E, #FFD43B, #40C057, #1E9BFF, #BE4BDB, #F03E3E); }
.pe-colors__any input { position: absolute; inset: 0; opacity: 0; cursor: pointer; }

.pe-rows { display: flex; flex-direction: column; gap: 8px; margin-top: 18px; }
.pe-row { display: flex; align-items: center; gap: 12px; width: 100%; padding: 14px 14px; border: 0; border-radius: var(--radius-lg); background: #F6F7FA; color: var(--ink); font: inherit; font-size: 15px; font-weight: 700; text-align: left; cursor: pointer; box-sizing: border-box; }
.pe-row--toggle { cursor: default; }
.pe-row__icon { width: 24px; font-size: 18px; text-align: center; }
.pe-row__arrow { margin-left: auto; color: var(--ink-muted); }
.pe-switch { position: relative; width: 48px; height: 28px; margin-left: auto; padding: 0; border: 0; border-radius: var(--radius-full); background: #D5D9E2; cursor: pointer; }
.pe-switch i { position: absolute; top: 3px; left: 3px; width: 22px; height: 22px; border-radius: 50%; background: #fff; transition: left 0.15s; }
.pe-switch.is-on { background: #2F7BFF; }
.pe-switch.is-on i { left: 23px; }
.pe-open { display: flex; flex-direction: column; gap: 8px; padding: 4px 4px 8px; }
.pe-open textarea, .pe-picked textarea { width: 100%; padding: 8px 10px; border: 1px solid var(--line); border-radius: var(--radius-md); font: inherit; resize: vertical; box-sizing: border-box; }
.pe-stickers { display: grid; grid-template-columns: repeat(5, 1fr); gap: 6px; }
.pe-stickers button { padding: 6px 0; border: 0; border-radius: var(--radius-md); background: #F6F7FA; font-size: 24px; cursor: pointer; }
.pe-stickers button:hover, .pe-shapes button:hover { background: #E8F0FF; }
.pe-shapes { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; }
.pe-shapes button { display: flex; flex-direction: column; align-items: center; gap: 2px; padding: 8px 0; border: 0; border-radius: var(--radius-md); background: #F6F7FA; font: inherit; font-size: 12px; cursor: pointer; }
.pe-shapes b { font-size: 22px; font-weight: 400; }
.pe-more { flex-direction: row; flex-wrap: wrap; }
.pe-chip { padding: 7px 12px; border: 1px solid var(--line); border-radius: var(--radius-full); background: #fff; color: var(--ink); font: inherit; font-size: 13px; cursor: pointer; white-space: nowrap; }
.pe-chip:disabled { opacity: 0.4; cursor: default; }
.pe-chip.is-on { border-color: #F2A33A; color: #B86E00; font-weight: 700; }
.pe-chip--main { align-self: flex-end; border-color: #2F7BFF; background: #2F7BFF; color: #fff; font-weight: 700; }
.pe-chip--danger { border-color: #F3B4B4; color: var(--danger); }
.pe-picked { margin-top: 12px; padding: 10px; border-radius: var(--radius-md); background: #F2F6FF; }
.pe-picked__row { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: 6px; font-size: 12px; color: var(--ink-muted); }
.pe-hint { margin: 14px 0 0; font-size: 12px; color: var(--ink-muted); line-height: 18px; }
.pe-picked .pe-hint { margin: 0; }
</style>
