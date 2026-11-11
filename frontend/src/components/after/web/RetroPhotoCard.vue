<script setup>
// ⑤ 웹 회고 오른쪽 칸 '사진' 카드 (11/11, 따로 있던 메모·사진 페이지에서 옮김)
//  - 날짜·장소별 사진 작게 보기, 사진·영상 올리기, 사진을 누르면 꾸미기 편집기, 대표 사진 고르기
//  - 사진은 앱과 같은 API(⑤ GET·POST /api/photos)라 앱에서 찍은 사진이 그대로 보여요
//  - 대표 사진·편집본 기록은 웹 메모 저장소(utils/webJournal.js, 이 브라우저)에 같이 둬요
import { computed, ref } from 'vue'
import { upload } from '@/api/http'
import { isEditedPhoto, editedOriginalIds } from '@/utils/webJournal'
import PhotoEditor from './PhotoEditor.vue'

const props = defineProps({
  retro: { type: Object, required: true },   // GET /trips/{id}/retro 응답
  photos: { type: Array, required: true },   // GET /trips/{id}/photos 응답 (부모가 읽음)
  journal: { type: Object, required: true }, // 웹 메모 (reactive). covers·tripCover·editedFrom을 여기서 고침
})
const emit = defineEmits(['reload', 'view'])  // reload: 사진을 다시 읽어 달라 / view: 영상 크게 보기

const UPLOAD_GAP_MINUTES = 3     // 장소를 골라 여러 장 올리면 이만큼씩 시각을 띄움
const REENCODE_QUALITY = 0.95    // 촬영 정보를 지우려고 다시 저장할 때 화질

const editingPhoto = ref(null)
const uploadTarget = ref('file')  // 'file' = 파일에 적힌 시각 그대로, 아니면 일정 칸 id
const uploadMessage = ref('')
const isUploading = ref(false)

// ============================== 날짜·장소 묶기
// 여행 첫날 + (day − 1)일 → "2026-11-07"
function dateOfDay(day) {
  const date = new Date(props.retro.startDate + 'T00:00:00')
  date.setDate(date.getDate() + day - 1)
  return date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0') + '-' + String(date.getDate()).padStart(2, '0')
}

// 일정 칸에 그날 번호를 붙인 목록
const stopRows = computed(() => {
  const list = []
  const countOfDay = {}
  for (const stop of props.retro.stops) {
    countOfDay[stop.day] = (countOfDay[stop.day] || 0) + 1
    list.push(Object.assign({}, stop, { no: countOfDay[stop.day], isVisited: stop.actualArrive !== null }))
  }
  return list
})

function findStop(stopId) {
  for (const stop of stopRows.value) {
    if (String(stop.id) === String(stopId)) return stop
  }
  return null
}

// 보여 줄 묶음: 날짜 → 장소(사진 있는 곳만) + 이동 중
const photoGroups = computed(() => {
  const groups = []
  const days = []
  for (const photo of props.photos) {
    if (!days.includes(photo.day)) days.push(photo.day)
  }
  days.sort((a, b) => a - b)
  for (const day of days) {
    for (const stop of stopRows.value) {
      const list = props.photos.filter((photo) => photo.stopId === stop.id)
      if (stop.day === day && list.length > 0) groups.push({ key: 'stop-' + stop.id, name: day + '일차 · ' + stop.no + '. ' + stop.name, photos: list })
    }
    const moving = props.photos.filter((photo) => photo.stopId === null && photo.day === day)
    if (moving.length > 0) groups.push({ key: 'move-' + day, name: day + '일차 · 이동 중', photos: moving })
  }
  return groups
})

const originalIdsWithEdit = computed(() => editedOriginalIds(props.journal))

// ============================== 편집기
// 편집기·대표 사진에 넘길 값
function toEditorPhoto(photo) {
  const stop = photo.stopId ? findStop(photo.stopId) : null
  return {
    id: photo.id,
    url: photo.url,
    stopId: photo.stopId,
    takenAtFull: dateOfDay(photo.day) + 'T' + photo.takenAt,
    placeName: stop ? stop.name : null,
    timeLabel: photo.day + '일차 ' + photo.takenAt,
    day: photo.day,
  }
}

// 편집기 왼쪽 목록: 사진만(영상 빼고)
const editorPhotos = computed(() => {
  const list = []
  for (const photo of props.photos) {
    if (photo.mediaType === 'photo') list.push(toEditorPhoto(photo))
  }
  return list
})

function openPhoto(photo) {
  if (photo.mediaType === 'video') emit('view', photo)
  else editingPhoto.value = toEditorPhoto(photo)
}

/** 부모(사진 크게 보기의 [꾸미기])가 사진 번호로 편집기를 엶 */
function openEditorById(photoId) {
  for (const photo of props.photos) {
    if (photo.id === photoId && photo.mediaType === 'photo') editingPhoto.value = toEditorPhoto(photo)
  }
}
defineExpose({ openEditorById }) // 부모가 ref로 부를 수 있게 내보냄

function isCoverPhoto(photo) {
  return photo.stopId !== null && props.journal.covers[photo.stopId] === photo.id
}

function chooseCover(photoId) {
  const stopId = editingPhoto.value.stopId
  props.journal.covers[stopId] = props.journal.covers[stopId] === photoId ? null : photoId
}

function chooseTripCover(photoId) {
  props.journal.tripCover = props.journal.tripCover === photoId ? null : photoId
}

// 편집본 저장 뒤: 사진을 다시 읽고, 새로 생긴 사진(같은 찍은 시각)을 원본과 이어 둠
async function onEditorSaved(result) {
  const beforeIds = new Set(props.photos.map((photo) => photo.id))
  const fresh = await reloadAndWait()
  const takenTime = result.takenAtFull.slice(11, 16)
  for (const photo of fresh) {
    if (!beforeIds.has(photo.id) && photo.takenAt === takenTime) linkEdited(photo.id, result.originalId)
  }
  editingPhoto.value = null
}

// 편집본 ↔ 원본 기록, 원본이 대표 사진이었으면 편집본으로 바꿈
function linkEdited(editedId, originalId) {
  const journal = props.journal
  journal.editedFrom[editedId] = originalId
  for (const stopId in journal.covers) {
    if (journal.covers[stopId] === originalId) journal.covers[stopId] = editedId
  }
  if (journal.tripCover === originalId) journal.tripCover = editedId
}

// 부모에게 다시 읽어 달라고 하고, 읽은 목록을 돌려받음 (부모가 resolve를 불러 줌)
function reloadAndWait() {
  return new Promise((resolve) => emit('reload', resolve))
}

// 편집기 [+ 사진 추가]: 파일에 적힌 시각으로 올리고, 올린 첫 사진을 바로 편집
async function addFromEditor(files) {
  const beforeIds = new Set(props.photos.map((photo) => photo.id))
  uploadTarget.value = 'file'
  const fresh = await uploadList(files)
  for (const photo of fresh) {
    if (!beforeIds.has(photo.id) && photo.mediaType === 'photo') {
      editingPhoto.value = toEditorPhoto(photo)
      return
    }
  }
}

// ============================== 사진 올리기
const uploadChoices = computed(() => {
  const list = [{ value: 'file', label: '파일에 적힌 찍은 시각 그대로' }]
  for (const stop of stopRows.value) {
    if (stop.isVisited) list.push({ value: String(stop.id), label: stop.day + '일차 ' + stop.actualArrive + ' · ' + stop.name + '에서 찍은 사진으로' })
  }
  return list
})

// 고른 장소의 도착 시각부터 n번째 사진 시각 (머문 시간 안에서만)
function takenAtForStop(stop, index) {
  const [hour, minute] = stop.actualArrive.split(':').map(Number)
  const stay = stop.actualStay !== null ? stop.actualStay : stop.stay
  const minutes = Math.min(hour * 60 + minute + Math.min(stay - 1, 1 + index * UPLOAD_GAP_MINUTES), 24 * 60 - 1)
  return dateOfDay(stop.day) + 'T' + String(Math.floor(minutes / 60)).padStart(2, '0') + ':' + String(minutes % 60).padStart(2, '0')
}

// 파일 수정 시각 → "2026-11-01T10:20" (서버가 파일 안의 찍은 시각을 못 읽을 때만 써요)
function fileTakenAt(file) {
  const date = new Date(file.lastModified)
  const pad = (n) => String(n).padStart(2, '0')
  return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate()) + 'T' + pad(date.getHours()) + ':' + pad(date.getMinutes())
}

// 사진을 한 번 다시 그려서 저장: 파일 안의 찍은 시각(EXIF)이 지워져서 서버가 우리가 보낸 시각을 써요
// 브라우저가 못 여는 사진(HEIC 등)이나 영상은 그대로 올려요
async function withoutTakenInfo(file) {
  if (!file.type.startsWith('image/') || file.type === 'image/heic') return file
  try {
    const bitmap = await createImageBitmap(file) // 파일을 그림으로 읽기
    const canvas = document.createElement('canvas')
    canvas.width = bitmap.width
    canvas.height = bitmap.height
    canvas.getContext('2d').drawImage(bitmap, 0, 0)
    const blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', REENCODE_QUALITY))
    return new File([blob], file.name.replace(/\.\w+$/, '') + '.jpg', { type: 'image/jpeg' })
  } catch (e) {
    return file
  }
}

async function uploadFiles(event) {
  const files = Array.from(event.target.files)
  event.target.value = '' // 같은 파일을 다시 골라도 올라가게
  if (files.length > 0) await uploadList(files)
}

// 올리고 다시 읽은 사진 목록을 돌려줌
async function uploadList(files) {
  isUploading.value = true
  uploadMessage.value = files.length + '개 올리는 중…'
  let fresh = props.photos
  try {
    const result = await upload('/photos', await toUploadForm(files))
    uploadMessage.value = result.savedCount + '개를 올렸어요' + (result.unmatchedCount > 0 ? ' (여행 날짜 밖 ' + result.unmatchedCount + '개는 빠짐)' : '')
    fresh = await reloadAndWait()
  } catch (e) {
    uploadMessage.value = '올리지 못했어요 (' + e.message + ')'
  }
  isUploading.value = false
  return fresh
}

// 올릴 상자: 파일 + 찍은 시각 + 영상 길이(사진은 0) + 이 여행 번호
async function toUploadForm(files) {
  const stop = uploadTarget.value === 'file' ? null : findStop(uploadTarget.value)
  const form = new FormData()
  for (let i = 0; i < files.length; i++) {
    form.append('files', stop ? await withoutTakenInfo(files[i]) : files[i])
    form.append('takenAts', stop ? takenAtForStop(stop, i) : fileTakenAt(files[i]))
    form.append('durations', 0)
  }
  form.append('tripId', props.retro.id)
  return form
}
</script>

<template>
  <section class="rp">
    <h2>사진 <small>{{ photos.length }}장 · 누르면 꾸미기</small></h2>
    <div class="rp-upload">
      <label class="rp-upload__btn" :class="{ 'is-busy': isUploading }">
        <input type="file" accept="image/*,video/*" multiple :disabled="isUploading" @change="uploadFiles" />
        ＋ 사진·영상 올리기
      </label>
      <select v-model="uploadTarget" :disabled="isUploading" aria-label="찍은 시각">
        <option v-for="choice in uploadChoices" :key="choice.value" :value="choice.value">{{ choice.label }}</option>
      </select>
    </div>
    <p v-if="uploadMessage" class="rp-msg" role="status">{{ uploadMessage }}</p>
    <p class="rp-hint">찍은 시각으로 장소가 정해져요. 장소를 고르면 그 장소에 머문 시각으로 넣어요.</p>

    <p v-if="photoGroups.length === 0" class="rp-empty">아직 사진이 없어요. 올리면 날짜·장소별로 정리돼요.</p>
    <div v-for="group in photoGroups" :key="group.key" class="rp-group">
      <h3>{{ group.name }} <small>{{ group.photos.length }}장</small></h3>
      <div class="rp-grid">
        <button v-for="photo in group.photos" :key="photo.id" type="button" class="rp-photo" :aria-label="group.name + ' ' + photo.takenAt + (photo.mediaType === 'video' ? ' 영상' : ' 사진 꾸미기')" @click="openPhoto(photo)">
          <video v-if="photo.mediaType === 'video'" :src="photo.url" muted preload="metadata"></video>
          <img v-else :src="photo.url" alt="" loading="lazy" />
          <span class="rp-photo__badges">
            <i v-if="photo.mediaType === 'video'">▶</i>
            <i v-if="isCoverPhoto(photo) || journal.tripCover === photo.id" class="is-cover">★</i>
            <i v-if="isEditedPhoto(journal, photo.id)" class="is-edit">편집</i>
            <i v-if="originalIdsWithEdit.includes(photo.id)">원본</i>
          </span>
        </button>
      </div>
    </div>

    <PhotoEditor
      v-if="editingPhoto"
      :key="editingPhoto.id"
      :photo="editingPhoto"
      :photos="editorPhotos"
      :trip-id="retro.id"
      :can-cover="editingPhoto.stopId !== null"
      :is-cover="editingPhoto.stopId !== null && journal.covers[editingPhoto.stopId] === editingPhoto.id"
      :is-trip-cover="journal.tripCover === editingPhoto.id"
      @close="editingPhoto = null"
      @saved="onEditorSaved"
      @cover="chooseCover"
      @trip-cover="chooseTripCover"
      @switch="editingPhoto = $event"
      @add-files="addFromEditor"
    />
  </section>
</template>

<style scoped>
.rp h2 { margin: 0 0 10px; font-size: 16px; }
.rp h2 small { margin-left: 6px; font-size: 12px; font-weight: 400; color: var(--ink-muted); }
.rp-upload { display: flex; gap: 8px; }
.rp-upload__btn { position: relative; flex: none; padding: 7px 12px; border-radius: var(--radius-md); background: var(--coral); color: #fff; font-size: 13px; font-weight: 700; cursor: pointer; }
.rp-upload__btn input { position: absolute; inset: 0; opacity: 0; cursor: pointer; }
.rp-upload__btn.is-busy { opacity: 0.6; }
.rp-upload select { flex: 1; min-width: 0; padding: 6px 8px; border: 1px solid var(--line); border-radius: var(--radius-md); background: #fff; font: inherit; font-size: 13px; }
.rp-msg { margin: 6px 0 0; color: var(--navy); font-size: 12px; font-weight: 700; }
.rp-hint { margin: 6px 0 0; color: var(--ink-muted); font-size: 12px; }
.rp-empty { margin: 10px 0 0; color: var(--ink-muted); }
.rp-group { margin-top: 12px; }
.rp-group h3 { margin: 0 0 6px; font-size: 13px; }
.rp-group h3 small { margin-left: 4px; font-weight: 400; color: var(--ink-muted); }
.rp-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(84px, 1fr)); gap: 6px; }
.rp-photo { position: relative; aspect-ratio: 1; padding: 0; overflow: hidden; border: 0; border-radius: var(--radius-md); background: var(--surface-sunken); cursor: pointer; }
.rp-photo img, .rp-photo video { width: 100%; height: 100%; object-fit: cover; display: block; transition: transform 0.2s; }
.rp-photo:hover img, .rp-photo:hover video { transform: scale(1.05); }
.rp-photo__badges { position: absolute; left: 4px; top: 4px; display: flex; gap: 3px; }
.rp-photo__badges i { padding: 0 5px; border-radius: var(--radius-sm); background: rgba(0, 0, 0, 0.55); color: #fff; font-size: 10px; font-style: normal; line-height: 16px; }
.rp-photo__badges .is-cover { background: #F2A33A; color: #2A1A00; }
.rp-photo__badges .is-edit { background: var(--coral); }
</style>
