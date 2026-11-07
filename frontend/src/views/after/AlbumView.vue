<script setup>
// S19-c 앨범 (⑤): 내 지난 여행마다 사진 앞 8장. 한 번에 올리면 찍은 날짜·시각으로 여행·장소에 붙어요
import { onMounted, ref } from 'vue'
import { get } from '@/api/http'
import { uploadPhotos, toDurationText } from '@/utils/photoUpload'
import PhotoViewer from '@/components/after/PhotoViewer.vue'
import TabBar from '@/components/common/TabBar.vue'

const ALBUM_CELL_COUNT = 8 // 여행마다 보여 줄 칸 (2줄 × 4칸)
const DAY_NAMES = ['일', '월', '화', '수', '목', '금', '토']

const albums = ref([])        // GET /api/albums (AlbumResponse 목록)
const error = ref('')
const uploading = ref(false)  // 올리는 중이면 버튼 막기
const snack = ref('')         // 아래에 잠깐 뜨는 알림 글자
const openTripId = ref(null)  // "더보기"로 펼친 여행 번호
const allPhotos = ref([])     // 펼친 여행의 사진 전체 (GET /api/trips/{id}/photos)
const viewer = ref(null)      // 크게 보기 { photos, start }. null이면 닫힘
const fileInput = ref(null)   // 숨겨 둔 파일 고르기 칸
let uploadTripId = null       // 점선 칸에서 고르면 그 여행 번호, 위 버튼이면 null

onMounted(loadAlbums)

// 앨범 불러오기
async function loadAlbums() {
  try {
    albums.value = await get('/albums')
  } catch (e) {
    error.value = e.message
  }
}

// 화면에 보여 줄 사진: 펼친 여행이면 전체, 아니면 앞 8장
function shownPhotos(album) {
  if (openTripId.value === album.tripId) return allPhotos.value
  return album.photos
}

// 이 칸이 "+N 더보기" 칸인가: 안 펼쳤고, 전체가 8개보다 많고, 8번째 칸(i = 7)
function isMoreCell(album, i) {
  const total = album.photoCount + album.videoCount
  return openTripId.value !== album.tripId && total > ALBUM_CELL_COUNT && i === ALBUM_CELL_COUNT - 1
}

// 더보기 칸에 적을 숫자: 전체 9개면 7장만 보이니까 +2
function moreCount(album) {
  return album.photoCount + album.videoCount - (ALBUM_CELL_COUNT - 1)
}

// 칸을 누르면: 더보기 칸이면 전체 펼치기, 아니면 크게 보기
async function onCellClick(album, i) {
  if (isMoreCell(album, i)) {
    allPhotos.value = await get('/trips/' + album.tripId + '/photos')
    openTripId.value = album.tripId
    return
  }
  viewer.value = { photos: shownPhotos(album), start: i }
}

// 파일 고르기 창 열기. tripId가 null이면 날짜로 여행을 찾아요
function pickFiles(tripId) {
  uploadTripId = tripId
  fileInput.value.click()
}

// 파일을 고르면 바로 올리고 앨범을 다시 불러와요
async function onFilesPicked(event) {
  const files = event.target.files
  if (files.length === 0) return
  uploading.value = true
  try {
    const result = await uploadPhotos(files, uploadTripId)
    snack.value = result.savedCount + '개 올렸어요'
    if (result.unmatchedCount > 0) {
      snack.value += ' · ' + result.unmatchedCount + '개는 그날 여행이 없어요'
    }
    openTripId.value = null
    await loadAlbums()
  } catch (e) {
    snack.value = e.message
  }
  uploading.value = false
  event.target.value = '' // 같은 파일을 다시 골라도 또 올라가게 비움
}

// "2026-11-01" → "11/1 (일)"
function toDateLabel(date) {
  const d = new Date(date + 'T00:00')
  return d.getMonth() + 1 + '/' + d.getDate() + ' (' + DAY_NAMES[d.getDay()] + ')'
}

// "3곳 · 사진 8 · 영상 1" / "4곳 · 사진 없음"
function toCountText(album) {
  let text = album.placeCount + '곳'
  if (album.photoCount + album.videoCount === 0) return text + ' · 사진 없음'
  if (album.photoCount > 0) text += ' · 사진 ' + album.photoCount
  if (album.videoCount > 0) text += ' · 영상 ' + album.videoCount
  return text
}
</script>

<template>
  <div class="scr scr-Album">
    <div class="app">
      <header class="topbar">
        <h1 class="topbar__title" style="padding-left: 8px">앨범</h1>
        <button type="button" class="btn btn--secondary btn--sm" style="margin-right: 8px" :disabled="uploading" @click="pickFiles(null)">
          <i class="ic ic--s i-plus" aria-hidden="true"></i>{{ uploading ? '올리는 중…' : '사진 불러오기' }}
        </button>
      </header>

      <!-- 숨겨 둔 파일 고르기 칸 (버튼을 누르면 코드가 대신 눌러 줘요). multiple: 여러 개, accept: 사진·영상만 -->
      <input ref="fileInput" type="file" multiple accept="image/*,video/*" hidden @change="onFilesPicked" />

      <div class="scroll" style="padding-top: 4px; gap: 24px">
        <p v-if="error" class="t-cap" role="alert">{{ error }}</p>
        <p v-else-if="albums.length === 0" class="t-cap">끝난 여행이 생기면 여기에 앨범이 만들어져요.</p>

        <!-- ① 여행 하나 -->
        <section v-for="(album, n) in albums" :key="album.tripId" class="album" :aria-labelledby="'album-' + album.tripId">
          <div class="album__head">
            <span class="album__no tn" aria-hidden="true">{{ n + 1 }}</span>
            <div class="album__title">
              <h2 :id="'album-' + album.tripId" class="t-sub">{{ album.title }}</h2>
              <p class="t-cap tn">{{ toDateLabel(album.startDate) }} · {{ toCountText(album) }}</p>
            </div>
            <RouterLink :to="'/retro?id=' + album.tripId " class="album__retro">회고<i class="ic ic--s i-next" aria-hidden="true"></i></RouterLink>
          </div>

          <!-- ② 사진이 없으면 점선 칸 -->
          <button v-if="album.photos.length === 0" type="button" class="album__empty" :disabled="uploading" @click="pickFiles(album.tripId)">
            <i class="ic i-camera" aria-hidden="true"></i>이날 사진 불러오기
          </button>

          <!-- ③ 사진이 있으면 4칸 격자 -->
          <div v-else class="album__grid">
            <button v-for="(photo, i) in shownPhotos(album)" :key="photo.id" type="button" class="album__cell" :aria-label="photo.takenAt + ' ' + (photo.mediaType === 'video' ? '영상' : '사진')" @click="onCellClick(album, i)">
              <!-- 영상은 첫 장면을 그림처럼 보여 줘요 (#t=0.1: 0.1초 장면) -->
              <video v-if="photo.mediaType === 'video'" :src="photo.url + '#t=0.1'" preload="metadata" muted playsinline></video>
              <img v-else :src="photo.url" alt="" loading="lazy" />
              <span v-if="photo.mediaType === 'video' && !isMoreCell(album, i)" class="album__dur tn"><i class="ic ic--s i-play" aria-hidden="true"></i>{{ toDurationText(photo.durationSec) }}</span>
              <span v-if="isMoreCell(album, i)" class="album__more"><b class="tn">+{{ moreCount(album) }}</b>더보기</span>
            </button>
          </div>
        </section>
      </div>

      <div v-if="snack" class="snack album-snack" role="status">
        <i class="ic i-success" aria-hidden="true"></i><span>{{ snack }}</span><button type="button" @click="snack = ''">닫기</button>
      </div>

      <TabBar active="trips" />

      <PhotoViewer v-if="viewer" :photos="viewer.photos" :start="viewer.start" @close="viewer = null" />
    </div>
  </div>
</template>

<style>
.scr-Album .album{display:flex;flex-direction:column;gap:10px}
.scr-Album .album__head{display:flex;align-items:center;gap:10px}
.scr-Album .album__no{flex:none;width:28px;height:28px;border-radius:50%;background:var(--navy);color:#fff;font-size:14px;font-weight:700;display:flex;align-items:center;justify-content:center}
.scr-Album .album__title{flex:1;min-width:0}
.scr-Album .album__title .t-sub{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.scr-Album .album__retro{flex:none;display:flex;align-items:center;gap:2px;min-height:44px;font-size:14px;font-weight:600;color:var(--ink);text-decoration:none}
.scr-Album .album__grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:3px;border-radius:14px;overflow:hidden}
.scr-Album .album__cell{position:relative;aspect-ratio:1;padding:0;border:0;background:var(--line);cursor:pointer;overflow:hidden}
.scr-Album .album__cell img,.scr-Album .album__cell video{width:100%;height:100%;object-fit:cover;display:block;pointer-events:none}
.scr-Album .album__dur{position:absolute;right:6px;bottom:6px;display:flex;align-items:center;gap:2px;padding:1px 6px;border-radius:999px;background:rgba(0,0,0,.55);color:#fff;font-size:12px;font-weight:600}
.scr-Album .album__more{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;background:rgba(10,14,28,.55);color:#fff;font-size:13px;font-weight:600}
.scr-Album .album__more b{font-size:20px;line-height:24px}
.scr-Album .album__empty{height:96px;border:1.5px dashed var(--line-strong);border-radius:14px;background:transparent;display:flex;align-items:center;justify-content:center;gap:8px;color:var(--ink-muted);font:inherit;font-size:15px;font-weight:600;cursor:pointer}
.scr-Album .album-snack{position:absolute;left:16px;right:16px;bottom:100px;z-index:5}
</style>
