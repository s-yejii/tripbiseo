<script setup>
// W20 웹 여행 메모하기 (⑤, 11/10). [여행 메모] [사진] 버튼으로 화면을 바꿔요
//  - 여행 메모: 별점·여행 일기·날짜별 메모·장소별 메모 → 이 브라우저에 저장 (utils/webJournal.js, 서버 API는 아직 없음)
//  - 사진: 날짜·장소별 사진, 올리기, 편집(새 사진으로 저장), 대표 사진 고르기
// 일정·사진은 앱과 같은 API(⑤ retro·photos)를 읽어서 앱에서 찍은 사진이 그대로 보여요
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { get, upload } from '@/api/http'
import { toDuration } from '@/utils/time'
import { loadJournal, saveJournal, isEditedPhoto, editedOriginalIds } from '@/utils/webJournal'
import WebHeader from '@/components/after/web/WebHeader.vue'
import PhotoEditor from '@/components/after/web/PhotoEditor.vue'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']
const SAVE_DELAY_MS = 400        // 글자를 다 쓰고 이만큼 지나면 저장 (한 글자마다 저장하지 않게)
const UPLOAD_GAP_MINUTES = 3     // 장소를 골라 여러 장 올리면 이만큼씩 시각을 띄움
const REENCODE_QUALITY = 0.95    // 촬영 정보를 지우려고 다시 저장할 때 화질

const route = useRoute()
const router = useRouter()
const tripId = route.query.id
const retro = ref(null)
const photos = ref([])
const journal = ref(loadJournal(tripId))
const errorMessage = ref('')
const screen = ref(route.query.tab === 'photo' ? 'photo' : 'memo') // 'memo' · 'photo'
const savedText = ref('')
const editingPhoto = ref(null)
const viewingVideo = ref(null)
const uploadTarget = ref('file')  // 'file' = 파일에 적힌 시각 그대로, 아니면 일정 칸 id
const uploadMessage = ref('')
const isUploading = ref(false)
let saveTimer = null

// ============================== 읽기
onMounted(async () => {
  try {
    retro.value = await get('/trips/' + tripId + '/retro')
    document.title = retro.value.title + ' · 여행 메모'
  } catch (e) {
    if (e.status !== 401) errorMessage.value = '여행을 불러오지 못했어요 (' + e.message + ')'
    return
  }
  await loadPhotos()
})

async function loadPhotos() {
  try {
    photos.value = await get('/trips/' + tripId + '/photos')
  } catch (e) {
    photos.value = []
  }
}

// 버튼으로 화면 바꾸기 (주소에도 남겨서 새로 고쳐도 같은 화면)
function changeScreen(name) {
  screen.value = name
  router.replace({ query: Object.assign({}, route.query, { tab: name }) })
}

// ============================== 저장 (쓰고 잠깐 멈추면 저장)
watch(journal, () => {
  clearTimeout(saveTimer)
  savedText.value = '저장 중…'
  saveTimer = setTimeout(() => {
    const isSaved = saveJournal(tripId, journal.value)
    savedText.value = isSaved ? '저장됨 · ' + new Date().toTimeString().slice(0, 5) : '이 브라우저에서는 저장할 수 없어요'
  }, SAVE_DELAY_MS)
}, { deep: true })

function setRating(value) {
  journal.value.rating = journal.value.rating === value ? 0 : value // 같은 별을 다시 누르면 지움
}

// ============================== 날짜·장소 묶기
// 여행 첫날 + (day − 1)일 → "2026-11-07"
function dateOfDay(day) {
  const date = new Date(retro.value.startDate + 'T00:00:00')
  date.setDate(date.getDate() + day - 1)
  return date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0') + '-' + String(date.getDate()).padStart(2, '0')
}

// 2 → "2일차 · 11월 7일 (토)"
function dayTitle(day) {
  const date = new Date(dateOfDay(day) + 'T00:00:00')
  return day + '일차 · ' + (date.getMonth() + 1) + '월 ' + date.getDate() + '일 (' + WEEKDAYS[date.getDay()] + ')'
}

const dayList = computed(() => {
  if (!retro.value) return []
  const start = new Date(retro.value.startDate)
  const end = new Date(retro.value.endDate)
  const count = Math.round((end - start) / 86400000) + 1 // 86400000 = 하루(밀리초)
  const list = []
  for (let day = 1; day <= count; day++) list.push(day)
  return list
})

// 그날 일정 칸 (번호 붙여서)
function stopsOfDay(day) {
  const list = []
  for (const stop of retro.value.stops) {
    if (stop.day === day) list.push(Object.assign({}, stop, { no: list.length + 1, isVisited: stop.actualArrive !== null }))
  }
  return list
}

function photosOfStop(stopId) {
  return photos.value.filter((photo) => photo.stopId === stopId)
}

function movingPhotosOfDay(day) {
  return photos.value.filter((photo) => photo.stopId === null && photo.day === day)
}

// 사진 화면에 보여 줄 묶음: 날짜 → 장소(사진 있는 곳만) + 이동 중
const photoGroups = computed(() => {
  if (!retro.value) return []
  const groups = []
  for (const day of dayList.value) {
    const places = []
    for (const stop of stopsOfDay(day)) {
      const list = photosOfStop(stop.id)
      if (list.length > 0) places.push({ key: 'stop-' + stop.id, stop: stop, name: stop.no + '. ' + stop.name, photos: list })
    }
    const moving = movingPhotosOfDay(day)
    if (moving.length > 0) places.push({ key: 'move-' + day, stop: null, name: '이동 중', photos: moving })
    if (places.length > 0) groups.push({ day: day, places: places })
  }
  return groups
})

function stopName(stopId) {
  for (const stop of retro.value.stops) {
    if (stop.id === stopId) return stop.name
  }
  return null
}

function photoUrl(photo) {
  return photo.url
}

// 편집기·대표 사진에 넘길 값
function toEditorPhoto(photo) {
  return {
    id: photo.id,
    url: photo.url,
    stopId: photo.stopId,
    takenAtFull: dateOfDay(photo.day) + 'T' + photo.takenAt,
    placeName: photo.stopId ? stopName(photo.stopId) : null,
    timeLabel: photo.day + '일차 ' + photo.takenAt,
  }
}

// ============================== 사진 누르기 · 대표 사진
function openPhoto(photo) {
  if (photo.mediaType === 'video') viewingVideo.value = photo
  else editingPhoto.value = toEditorPhoto(photo)
}

function isCoverPhoto(photo) {
  return photo.stopId !== null && journal.value.covers[photo.stopId] === photo.id
}

function chooseCover(photoId) {
  const stopId = editingPhoto.value.stopId
  journal.value.covers[stopId] = journal.value.covers[stopId] === photoId ? null : photoId
}

function chooseTripCover(photoId) {
  journal.value.tripCover = journal.value.tripCover === photoId ? null : photoId
}

function coverUrlOfStop(stopId) {
  const coverId = journal.value.covers[stopId]
  if (coverId) return '/api/photos/' + coverId + '/file'
  const list = photosOfStop(stopId)
  for (const photo of list) {
    if (photo.mediaType === 'photo') return photo.url
  }
  return null
}

const originalIdsWithEdit = computed(() => editedOriginalIds(journal.value))

// ============================== 편집본 저장 뒤: 새로 생긴 사진을 원본과 이어 두기
async function onEditorSaved(result) {
  const beforeIds = new Set(photos.value.map((photo) => photo.id))
  await loadPhotos()
  const takenTime = result.takenAtFull.slice(11, 16)
  for (const photo of photos.value) {
    if (!beforeIds.has(photo.id) && photo.takenAt === takenTime) linkEdited(photo.id, result.originalId)
  }
  editingPhoto.value = null
}

// 편집본 ↔ 원본 기록, 원본이 대표 사진이었으면 편집본으로 바꿈
function linkEdited(editedId, originalId) {
  journal.value.editedFrom[editedId] = originalId
  for (const stopId in journal.value.covers) {
    if (journal.value.covers[stopId] === originalId) journal.value.covers[stopId] = editedId
  }
  if (journal.value.tripCover === originalId) journal.value.tripCover = editedId
}

// ============================== 사진 올리기
const uploadChoices = computed(() => {
  const list = [{ value: 'file', label: '파일에 적힌 찍은 시각 그대로' }]
  if (!retro.value) return list
  for (const day of dayList.value) {
    for (const stop of stopsOfDay(day)) {
      if (stop.isVisited) list.push({ value: String(stop.id), label: day + '일차 ' + stop.actualArrive + ' · ' + stop.name + '에서 찍은 사진으로' })
    }
  }
  return list
})

function findStop(stopId) {
  for (const stop of retro.value.stops) {
    if (String(stop.id) === String(stopId)) return stop
  }
  return null
}

// 고른 장소의 도착 시각부터 n번째 사진 시각 (머문 시간 안에서만)
function takenAtForStop(stop, index) {
  const [hour, minute] = stop.actualArrive.split(':').map(Number)
  const stay = stop.actualStay !== null ? stop.actualStay : stop.stay
  const minutes = hour * 60 + minute + Math.min(stay - 1, 1 + index * UPLOAD_GAP_MINUTES)
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
  if (files.length === 0) return
  isUploading.value = true
  uploadMessage.value = files.length + '개 올리는 중…'
  try {
    const result = await upload('/photos', await toUploadForm(files))
    uploadMessage.value = result.savedCount + '개를 올렸어요' + (result.unmatchedCount > 0 ? ' (여행 날짜 밖 ' + result.unmatchedCount + '개는 빠짐)' : '')
    await loadPhotos()
  } catch (e) {
    uploadMessage.value = '올리지 못했어요 (' + e.message + ')'
  }
  isUploading.value = false
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
  form.append('tripId', tripId)
  return form
}
</script>

<template>
  <div class="wj">
    <WebHeader>
      <RouterLink :to="'/web/retro?id=' + tripId" class="wj-headlink">3D 회고</RouterLink>
      <span class="wj-headlink is-on">여행 메모하기</span>
    </WebHeader>

    <p v-if="errorMessage" class="wj-state">{{ errorMessage }}</p>
    <p v-else-if="!retro" class="wj-state">불러오는 중…</p>

    <main v-else class="wj-main">
      <div class="wj-head">
        <div>
          <h1>{{ retro.title }}</h1>
          <p>{{ retro.startDate }} ~ {{ retro.endDate }} · {{ retro.people }}명 · 사진 {{ photos.length }}장</p>
        </div>
        <div class="wj-switch" role="tablist">
          <button type="button" role="tab" :aria-selected="screen === 'memo'" :class="{ 'is-on': screen === 'memo' }" @click="changeScreen('memo')">📝 여행 메모</button>
          <button type="button" role="tab" :aria-selected="screen === 'photo'" :class="{ 'is-on': screen === 'photo' }" @click="changeScreen('photo')">📷 사진</button>
        </div>
      </div>

      <!-- ============ 여행 메모 ============ -->
      <section v-if="screen === 'memo'">
        <div class="wj-card wj-diary">
          <div class="wj-diary__top">
            <h2>여행 일기</h2>
            <div class="wj-stars" aria-label="여행 별점">
              <button v-for="value in 5" :key="value" type="button" :class="{ 'is-on': value <= journal.rating }" :aria-label="value + '점'" @click="setRating(value)">★</button>
            </div>
            <span class="wj-saved">{{ savedText || '이 브라우저에 저장돼요' }}</span>
          </div>
          <textarea v-model="journal.diary" rows="5" placeholder="이번 여행은 어땠나요? 첫 줄은 지난 여행 목록 카드에 보여요."></textarea>
        </div>

        <div v-for="day in dayList" :key="day" class="wj-card">
          <h2>{{ dayTitle(day) }}</h2>
          <textarea v-model="journal.dayNotes[day]" rows="2" class="wj-daynote" placeholder="이날 한 줄 메모"></textarea>
          <div v-for="stop in stopsOfDay(day)" :key="stop.id" class="wj-stop" :class="{ 'is-skip': !stop.isVisited }">
            <div class="wj-stop__thumb" :style="coverUrlOfStop(stop.id) ? { backgroundImage: 'url(' + coverUrlOfStop(stop.id) + ')' } : {}">
              <span v-if="!coverUrlOfStop(stop.id)">{{ stop.no }}</span>
            </div>
            <div class="wj-stop__body">
              <div class="wj-stop__title">
                <b>{{ stop.no }}. {{ stop.name }}</b>
                <small>{{ stop.category }}</small>
                <span v-if="stop.isVisited" class="wj-stop__time">{{ stop.actualArrive }}<template v-if="stop.actualStay !== null"> · {{ toDuration(stop.actualStay) }}</template></span>
                <span v-else class="wj-stop__time">안 감</span>
                <span v-if="photosOfStop(stop.id).length > 0" class="wj-stop__count">📷 {{ photosOfStop(stop.id).length }}</span>
              </div>
              <textarea v-model="journal.stopNotes[stop.id]" rows="2" :placeholder="stop.isVisited ? '여기서 있었던 일, 맛, 기분…' : '못 간 이유, 다음에 가 볼 곳…'"></textarea>
            </div>
          </div>
        </div>
        <p class="wj-note">메모는 지금 이 브라우저에만 저장돼요. 웹 회고 지도에서 장소를 누르면 같이 보여요.</p>
      </section>

      <!-- ============ 사진 ============ -->
      <section v-else>
        <div class="wj-card wj-upload">
          <label class="wj-upload__btn" :class="{ 'is-busy': isUploading }">
            <input type="file" accept="image/*,video/*" multiple :disabled="isUploading" @change="uploadFiles" />
            ＋ 사진·영상 올리기
          </label>
          <select v-model="uploadTarget" :disabled="isUploading" aria-label="찍은 시각">
            <option v-for="choice in uploadChoices" :key="choice.value" :value="choice.value">{{ choice.label }}</option>
          </select>
          <span class="wj-upload__msg">{{ uploadMessage }}</span>
          <p class="wj-upload__hint">찍은 시각으로 장소가 정해져요. 장소를 고르면 그 장소에 머문 시각으로 넣어요 (사진 안의 촬영 정보는 지우고 올려요).</p>
        </div>

        <p v-if="photoGroups.length === 0" class="wj-state">아직 사진이 없어요. 위에서 올리면 날짜·장소별로 정리돼요.</p>

        <div v-for="group in photoGroups" :key="group.day" class="wj-card">
          <h2>{{ dayTitle(group.day) }}</h2>
          <div v-for="place in group.places" :key="place.key" class="wj-place">
            <h3>{{ place.name }} <small>{{ place.photos.length }}장</small></h3>
            <div class="wj-grid">
              <button v-for="photo in place.photos" :key="photo.id" type="button" class="wj-photo" @click="openPhoto(photo)">
                <video v-if="photo.mediaType === 'video'" :src="photoUrl(photo)" muted preload="metadata"></video>
                <img v-else :src="photoUrl(photo)" alt="" loading="lazy" />
                <span class="wj-photo__time">{{ photo.takenAt }}</span>
                <span class="wj-photo__badges">
                  <i v-if="photo.mediaType === 'video'">▶ 영상</i>
                  <i v-if="isCoverPhoto(photo)" class="is-cover">★ 대표</i>
                  <i v-if="journal.tripCover === photo.id" class="is-cover">★ 여행 대표</i>
                  <i v-if="isEditedPhoto(journal, photo.id)" class="is-edit">편집본</i>
                  <i v-if="originalIdsWithEdit.includes(photo.id)">원본</i>
                </span>
              </button>
            </div>
          </div>
        </div>
      </section>
    </main>

    <PhotoEditor
      v-if="editingPhoto"
      :key="editingPhoto.id"
      :photo="editingPhoto"
      :trip-id="tripId"
      :can-cover="editingPhoto.stopId !== null"
      :is-cover="editingPhoto.stopId !== null && journal.covers[editingPhoto.stopId] === editingPhoto.id"
      :is-trip-cover="journal.tripCover === editingPhoto.id"
      @close="editingPhoto = null"
      @saved="onEditorSaved"
      @cover="chooseCover"
      @trip-cover="chooseTripCover"
    />

    <div v-if="viewingVideo" class="wj-video" @click.self="viewingVideo = null">
      <video :src="photoUrl(viewingVideo)" controls autoplay></video>
      <button type="button" class="wj-video__close" aria-label="닫기" @click="viewingVideo = null">✕</button>
    </div>
  </div>
</template>

<style scoped>
.wj { display: flex; flex-direction: column; min-height: 100vh; background: var(--surface); color: var(--ink); font-family: var(--font-sans); }
.wj-headlink { padding: 6px 12px; border-radius: var(--radius-md); color: inherit; text-decoration: none; opacity: 0.85; }
.wj-headlink:hover { background: rgba(255, 255, 255, 0.12); }
.wj-headlink.is-on { background: rgba(255, 255, 255, 0.18); opacity: 1; font-weight: 700; }
.wj-state { margin: 24px auto; padding: 32px; max-width: 900px; border-radius: var(--radius-lg); background: #fff; color: var(--ink-muted); text-align: center; }
.wj-main { width: 100%; max-width: 980px; margin: 0 auto; padding: 28px 24px 48px; box-sizing: border-box; }
.wj-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; flex-wrap: wrap; margin-bottom: 16px; }
.wj-head h1 { margin: 0; font-size: 26px; }
.wj-head p { margin: 4px 0 0; color: var(--ink-muted); }
.wj-switch { display: flex; padding: 4px; border-radius: var(--radius-lg); background: #fff; box-shadow: var(--shadow-card); }
.wj-switch button { padding: 10px 20px; border: 0; border-radius: var(--radius-md); background: none; color: var(--ink-muted); font: inherit; font-size: 15px; font-weight: 700; cursor: pointer; }
.wj-switch button.is-on { background: var(--navy); color: var(--on-navy); }

.wj-card { margin-top: 14px; padding: 18px 20px; border-radius: var(--radius-lg); background: #fff; box-shadow: var(--shadow-card); }
.wj-card h2 { margin: 0 0 10px; font-size: 17px; }
.wj-card textarea { width: 100%; padding: 10px 12px; border: 1px solid var(--line); border-radius: var(--radius-md); font: inherit; line-height: 22px; resize: vertical; box-sizing: border-box; color: var(--ink); }
.wj-card textarea:focus { outline: 2px solid var(--navy); outline-offset: -1px; }
.wj-diary__top { display: flex; align-items: center; gap: 12px; margin-bottom: 10px; }
.wj-diary__top h2 { margin: 0; }
.wj-stars button { padding: 0 2px; border: 0; background: none; color: var(--line); font-size: 24px; cursor: pointer; }
.wj-stars button.is-on { color: #F2A33A; }
.wj-saved { margin-left: auto; font-size: 12px; color: var(--ink-muted); }
.wj-daynote { margin-bottom: 6px; background: var(--surface); }
.wj-stop { display: flex; gap: 12px; padding: 12px 0; border-top: 1px solid var(--line); }
.wj-stop.is-skip { opacity: 0.6; }
.wj-stop__thumb { display: grid; place-items: center; width: 64px; height: 64px; flex: none; border-radius: var(--radius-md); background: var(--navy-soft) center / cover no-repeat; color: var(--navy); font-weight: 700; }
.wj-stop__body { flex: 1; min-width: 0; }
.wj-stop__title { display: flex; align-items: baseline; gap: 8px; flex-wrap: wrap; margin-bottom: 6px; }
.wj-stop__title small { color: var(--ink-muted); }
.wj-stop__time { color: var(--navy); font-size: 13px; font-weight: 700; }
.wj-stop__count { font-size: 12px; color: var(--ink-muted); }
.wj-note { margin: 16px 0 0; font-size: 12px; color: var(--ink-muted); text-align: center; }

.wj-upload { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.wj-upload__btn { position: relative; padding: 10px 18px; border-radius: var(--radius-md); background: var(--coral); color: #fff; font-weight: 700; cursor: pointer; }
.wj-upload__btn input { position: absolute; inset: 0; opacity: 0; cursor: pointer; }
.wj-upload__btn.is-busy { opacity: 0.6; }
.wj-upload select { flex: 1; min-width: 240px; padding: 9px 10px; border: 1px solid var(--line); border-radius: var(--radius-md); font: inherit; background: #fff; }
.wj-upload__msg { font-size: 13px; color: var(--navy); font-weight: 700; }
.wj-upload__hint { width: 100%; margin: 0; font-size: 12px; color: var(--ink-muted); }
.wj-place { margin-top: 12px; }
.wj-place h3 { margin: 0 0 8px; font-size: 14px; }
.wj-place h3 small { margin-left: 4px; font-weight: 400; color: var(--ink-muted); }
.wj-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 8px; }
.wj-photo { position: relative; aspect-ratio: 1; padding: 0; overflow: hidden; border: 0; border-radius: var(--radius-md); background: var(--surface-sunken); cursor: pointer; }
.wj-photo img, .wj-photo video { width: 100%; height: 100%; object-fit: cover; display: block; transition: transform 0.2s; }
.wj-photo:hover img, .wj-photo:hover video { transform: scale(1.04); }
.wj-photo__time { position: absolute; left: 6px; bottom: 6px; padding: 1px 6px; border-radius: var(--radius-sm); background: rgba(0, 0, 0, 0.55); color: #fff; font-size: 12px; }
.wj-photo__badges { position: absolute; left: 6px; top: 6px; display: flex; gap: 4px; flex-wrap: wrap; }
.wj-photo__badges i { padding: 1px 6px; border-radius: var(--radius-sm); background: rgba(0, 0, 0, 0.55); color: #fff; font-size: 11px; font-style: normal; }
.wj-photo__badges .is-cover { background: #F2A33A; color: #2A1A00; }
.wj-photo__badges .is-edit { background: var(--coral); }
.wj-video { position: fixed; inset: 0; z-index: 50; display: grid; place-items: center; background: rgba(0, 0, 0, 0.85); }
.wj-video video { max-width: 90vw; max-height: 85vh; }
.wj-video__close { position: absolute; top: 16px; right: 16px; width: 40px; height: 40px; border: 0; border-radius: 50%; background: rgba(255, 255, 255, 0.15); color: #fff; font-size: 18px; cursor: pointer; }
</style>
