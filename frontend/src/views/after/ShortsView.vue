<script setup>
// S19-d 추억 쇼츠 (⑤, 11/14): 지난 여행 사진을 쇼츠처럼 자동으로 넘겨 봐요. 옆으로 밀면 앞·뒤 장, 위로 밀면 지난 여행으로
// 데이터는 이미 있는 API만 써요: 여행 목록 GET /api/albums, 사진 GET /api/trips/{id}/photos, 장소 이름·인원 GET /api/trips/{id}/retro
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { get } from '@/api/http'

const FIRST_MS = 3000        // 여행 첫 장(제목)을 보여 주는 시간
const PHOTO_MS = 4000        // 사진 한 장을 보여 주는 시간
const VIDEO_MAX_MS = 15000   // 영상이 길어도 이만큼만 보고 넘어감
const LAST_MS = 5000         // 여행 끝 장. 마지막 3초 동안 "N초 뒤 이어서 재생"
const TICK_MS = 100          // 진행 막대를 다시 그리는 간격
const SWIPE_PX = 60          // 이만큼 넘게 위·아래로 밀면 다른 여행으로
const TAP_PX = 10            // 이만큼 안 움직였으면 누른 것으로 봄
const WHEEL_LOCK_MS = 700    // 마우스 휠 한 번에 여행이 여러 개 넘어가지 않게
const COLLAGE_MAX = 5        // 끝 장에 흩뿌릴 사진 수
const ONE_DAY_MS = 24 * 60 * 60 * 1000
const FILL_MAX_RATIO = 0.8   // 가로÷세로가 이 값 이하(세로 사진)면 칸을 꽉 채움. 넘으면(가로 사진) 잘리지 않게 그대로
const TOAST_MS = 2000        // 아래 알림 글자를 보여 주는 시간

const router = useRouter()
const trips = ref([])            // GET /api/albums (최근 여행부터)
const error = ref('')
const isLoaded = ref(false)      // 여행 목록을 다 불러왔나 (불러오는 중엔 빈 화면)
const isMuted = ref(false)
const isPaused = ref(false)      // 멈춤 버튼을 눌렀나
const fillIds = ref({})          // 꽉 채워도 되는 사진 번호 → true (사진이 다 불러와지면 크기를 보고 정함)
const toast = ref('')            // 아래에 잠깐 뜨는 알림 글자
const videoEl = ref(null)        // 지금 재생 중인 영상 칸 (멈춤·재생에 씀)
const captions = ref({})         // AI 감성 자막: 사진 번호 → 자막 (GET /api/trips/{id}/shorts)
const askedShorts = {}           // 자막을 이미 요청한 여행 번호 → true (두 번 안 부르게)
const tripIndex = ref(0)         // 지금 보는 여행 번호 (0 = 가장 최근)
const slideIndex = ref(0)        // 그 여행 안에서 몇 번째 장
const progress = ref(0)          // 지금 장이 얼마나 지났는지 0 ~ 1
const detail = ref(null)         // 지금 여행의 { photos, stopNames, people }
const collage = ref([])          // 끝 장에 흩뿌린 사진 카드들 (끝 장에 올 때마다 새로 섞음)
const direction = ref('up')      // 여행이 바뀔 때 움직이는 방향 (up = 다음 여행이 아래에서 올라옴)
const detailCache = {}           // 한 번 불러온 여행은 다시 안 불러오게 tripId → detail
let timer = null
let elapsed = 0                  // 지금 장에서 흐른 시간(ms)
let pointerStart = null          // 누르기 시작한 점 { x, y }
let isWheelLocked = false

const trip = computed(() => trips.value[tripIndex.value])

// 다음 여행. 마지막 여행이면 null
const nextTrip = computed(() => {
  if (tripIndex.value + 1 >= trips.value.length) return null
  return trips.value[tripIndex.value + 1]
})

// 이 여행의 장 목록: 첫 장 + 사진마다 한 장 + 끝 장. 예: 사진 12장 → 14장
const slides = computed(() => {
  const list = [{ type: 'first' }]
  if (detail.value !== null) {
    for (const photo of detail.value.photos) list.push({ type: 'photo', photo: photo })
  }
  list.push({ type: 'last' })
  return list
})

const slide = computed(() => slides.value[slideIndex.value])

// 위쪽 진행 막대: 지난 장은 꽉, 지금 장은 진행만큼, 남은 장은 빈 칸
const segments = computed(() => {
  const list = []
  for (let i = 0; i < slides.value.length; i++) {
    let fill = 0
    if (i < slideIndex.value) fill = 100
    if (i === slideIndex.value) fill = progress.value * 100
    list.push(fill)
  }
  return list
})

// 끝 장 아래 "N초 뒤 이어서 재생"의 N
const countdown = computed(() => Math.ceil(((1 - progress.value) * LAST_MS) / 1000))

onMounted(loadTrips)
onUnmounted(stopTimer)

// 끝난 여행 목록 불러오기
// 여행 목록을 불러오면 입구 화면 없이 바로 가장 최근 여행부터 재생
async function loadTrips() {
  try {
    trips.value = await get('/albums')
  } catch (e) {
    error.value = e.message
  }
  isLoaded.value = true
  if (trips.value.length === 0) return
  isMuted.value = !hasClickedPage() // 브라우저는 사용자가 한 번도 누르지 않은 페이지에서 소리 나는 영상을 막아요
  openTrip(0)
}

// 이 페이지에서 사용자가 한 번이라도 눌렀나. 앨범의 "쇼츠" 버튼으로 왔으면 true, 주소를 바로 치고 왔으면 false
// navigator.userActivation: 브라우저가 알려 주는 "사용자가 눌렀는지" 정보 (없는 브라우저면 눌렀다고 봄)
function hasClickedPage() {
  if (!navigator.userActivation) return true
  return navigator.userActivation.hasBeenActive
}

// index번 여행을 처음 장부터 보여 주기
async function openTrip(index) {
  stopTimer()
  tripIndex.value = index
  slideIndex.value = 0
  progress.value = 0
  detail.value = null
  loadCaptions(index)     // 기다리지 않음: AI 자막은 늦게 와도 오는 대로 붙어요
  loadCaptions(index + 1) // 다음 여행 자막도 미리
  const loaded = await loadDetail(trips.value[index])
  if (tripIndex.value !== index) return // 불러오는 사이에 다른 여행으로 넘겼으면 버림
  detail.value = loaded
  startSlide()
}

// 여행 하나의 사진 전체 + 장소 이름 + 인원
async function loadDetail(album) {
  if (detailCache[album.tripId]) return detailCache[album.tripId]
  try {
    // Promise.all: 두 요청을 같이 보내고 둘 다 올 때까지 기다려요 (하나씩 기다리면 두 배 느림)
    const results = await Promise.all([get('/trips/' + album.tripId + '/photos'), get('/trips/' + album.tripId + '/retro')])
    const stopNames = {} // 일정 칸 번호 → 장소 이름
    for (const stop of results[1].stops) stopNames[stop.id] = stop.name
    detailCache[album.tripId] = { photos: results[0], stopNames: stopNames, people: results[1].people }
  } catch {
    detailCache[album.tripId] = { photos: album.photos, stopNames: {}, people: null } // 못 불러오면 앨범의 앞 8장만
  }
  return detailCache[album.tripId]
}

// index번 여행의 AI 자막 불러오기. Gemini가 사진마다 답해서 몇 초 걸릴 수 있어요
async function loadCaptions(index) {
  const album = trips.value[index]
  if (!album || askedShorts[album.tripId]) return
  askedShorts[album.tripId] = true
  try {
    const shorts = await get('/trips/' + album.tripId + '/shorts')
    for (const item of shorts.captions) captions.value[item.photoId] = item.caption
  } catch {
    askedShorts[album.tripId] = false // 실패하면 자막 없이 보여 주고, 다음에 다시 시도
  }
}

// 지금 장을 처음부터 재기 시작
function startSlide() {
  stopTimer()
  elapsed = 0
  progress.value = 0
  isPaused.value = false // 다른 장으로 넘어가면 다시 재생
  if (slide.value.type === 'last') makeCollage()
  timer = setInterval(tick, TICK_MS)
}

function stopTimer() {
  clearInterval(timer)
  timer = null
}

// TICK_MS마다 진행을 늘리고, 다 차면 다음 장으로
function tick() {
  if (isPaused.value) return // 멈춤이면 시간을 안 늘림
  elapsed = elapsed + TICK_MS
  progress.value = Math.min(1, elapsed / slideDuration(slide.value))
  if (progress.value >= 1) goNextSlide()
}

// 장마다 보여 주는 시간(ms). 영상은 길이만큼, 너무 길면 VIDEO_MAX_MS까지
function slideDuration(item) {
  if (item.type === 'first') return FIRST_MS
  if (item.type === 'last') return LAST_MS
  if (item.photo.mediaType === 'video' && item.photo.durationSec) {
    return Math.min(VIDEO_MAX_MS, item.photo.durationSec * 1000)
  }
  return PHOTO_MS
}

function goNextSlide() {
  if (slideIndex.value < slides.value.length - 1) {
    slideIndex.value++
    startSlide()
    return
  }
  goNextTrip()
}

function goPrevSlide() {
  if (slideIndex.value === 0) return
  slideIndex.value--
  startSlide()
}

// 위쪽 진행 막대의 i번째 칸을 누르면 그 장으로 바로
function goToSlide(index) {
  slideIndex.value = index
  startSlide()
}

// 다음(더 지난) 여행. 마지막 여행이면 끝 장에서 멈춤
function goNextTrip() {
  if (nextTrip.value === null) {
    stopTimer()
    progress.value = 1
    return
  }
  direction.value = 'up'
  openTrip(tripIndex.value + 1)
}

function goPrevTrip() {
  if (tripIndex.value === 0) return
  direction.value = 'down'
  openTrip(tripIndex.value - 1)
}

function toggleMute() {
  isMuted.value = !isMuted.value
}

// 멈춤 ↔ 재생. 영상이면 영상도 같이 멈추고 다시 틀어요
function togglePause() {
  isPaused.value = !isPaused.value
  if (videoEl.value === null) return
  if (isPaused.value) videoEl.value.pause()
  else videoEl.value.play()
}

// 공유: 휴대폰이면 공유 창(navigator.share), 안 되는 브라우저면 주소를 복사
async function share() {
  const url = window.location.href
  if (navigator.share) {
    try {
      await navigator.share({ title: trip.value.title + ' 여행 쇼츠', url: url })
    } catch {
      // 공유 창을 그냥 닫으면 여기로 와요. 할 일 없음
    }
    return
  }
  try {
    await navigator.clipboard.writeText(url)
    showToast('링크를 복사했어요')
  } catch {
    showToast('이 브라우저는 공유가 안 돼요')
  }
}

function showToast(text) {
  toast.value = text
  setTimeout(() => (toast.value = ''), TOAST_MS)
}

// 사진·영상이 불러와지면 크기를 보고 꽉 채울지 정해요. 예: 720×1280(0.56) → 채움, 960×640(1.5) → 그대로
function onMediaLoad(photo, width, height) {
  if (!width || !height) return
  fillIds.value[photo.id] = width / height <= FILL_MAX_RATIO
}

function close() {
  stopTimer()
  router.back()
}

function goRetro() {
  stopTimer()
  router.push('/retro?id=' + trip.value.tripId)
}

// 누르기 시작한 점을 기억
function onPointerDown(event) {
  pointerStart = { x: event.clientX, y: event.clientY }
}

// 뗄 때: 옆으로 밀면 앞·뒤 장, 위로 밀면 다음 여행, 아래로 밀면 앞 여행, 그냥 눌렀으면 왼쪽 1/3은 이전 장·나머지는 다음 장
function onPointerUp(event) {
  if (pointerStart === null) return
  const dx = event.clientX - pointerStart.x
  const dy = event.clientY - pointerStart.y
  pointerStart = null
  const isSideways = Math.abs(dx) > Math.abs(dy) // 옆으로 더 많이 움직였나
  if (isSideways && dx < -SWIPE_PX) return goNextSlide() // 왼쪽으로 밀면 다음 장
  if (isSideways && dx > SWIPE_PX) return goPrevSlide()
  if (!isSideways && dy < -SWIPE_PX) return goNextTrip()
  if (!isSideways && dy > SWIPE_PX) return goPrevTrip()
  if (Math.abs(dx) > TAP_PX || Math.abs(dy) > TAP_PX) return
  const box = event.currentTarget.getBoundingClientRect()
  if (event.clientX - box.left < box.width / 3) goPrevSlide()
  else goNextSlide()
}

// PC에서는 마우스 휠로도 여행을 넘겨요
function onWheel(event) {
  if (isWheelLocked || Math.abs(event.deltaY) < 30) return
  isWheelLocked = true
  setTimeout(() => (isWheelLocked = false), WHEEL_LOCK_MS)
  if (event.deltaY > 0) goNextTrip()
  else goPrevTrip()
}

// ---------------------------------------------------------------- 끝 장: 사진 흩뿌리기 (모양이 매번 달라요)

// 카드 자리 모양 3가지. 모두 사진 칸 전체에 퍼지게(덜 겹치게) 놓았어요. isBig = 조금 큰 카드
// left·top은 사진 칸(390 × 430) 안의 위치(px), rotate는 기울기(도)
const LAYOUTS = [
  // 흩뿌리기: 네 귀퉁이 + 가운데
  [{ left: 14, top: 8, rotate: -8 }, { left: 250, top: 18, rotate: 7 }, { left: 20, top: 262, rotate: 6 }, { left: 244, top: 256, rotate: -6 }, { left: 121, top: 122, rotate: -2, isBig: true }],
  // 지그재그: 왼쪽·오른쪽 번갈아 내려감
  [{ left: 16, top: 0, rotate: -6 }, { left: 246, top: 40, rotate: 6 }, { left: 28, top: 150, rotate: 4 }, { left: 240, top: 200, rotate: -5 }, { left: 116, top: 244, rotate: -2, isBig: true }],
  // 원형: 가운데를 비우고 오각형으로 빙 둘러
  [{ left: 133, top: 7, rotate: -3 }, { left: 252, top: 97, rotate: 7 }, { left: 206, top: 252, rotate: 4 }, { left: 60, top: 252, rotate: -5 }, { left: 14, top: 97, rotate: -8 }],
]
const JITTER_PX = 12      // 자리를 이만큼까지 흔듦
const JITTER_DEG = 5      // 기울기를 이만큼까지 흔듦
const COLLAGE_W = 390     // 사진 칸 크기 (가운데에서 출발하는 거리 계산용)
const COLLAGE_H = 430
const CARD_W = 124        // 카드 크기 (CSS .sh-polaroid와 같게)
const CARD_H = 156
const BIG_W = 148
const BIG_H = 186

// min ~ max 사이 아무 수
function randomBetween(min, max) {
  return min + Math.random() * (max - min)
}

// 배열을 섞은 새 배열 (피셔-예이츠: 뒤에서부터 아무 칸과 자리 바꾸기)
function shuffle(list) {
  const copy = list.slice()
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    const temp = copy[i]
    copy[i] = copy[j]
    copy[j] = temp
  }
  return copy
}

// 모양 하나를 고르고, 사진을 섞어서 자리·기울기를 조금씩 흔들어 카드 목록을 만듦
function makeCollage() {
  const layout = LAYOUTS[Math.floor(Math.random() * LAYOUTS.length)]
  const photos = shuffle(detail.value.photos).slice(0, COLLAGE_MAX)
  const start = layout.length - photos.length // 사진이 적으면 큰 카드(뒤쪽 칸)부터 채움
  const cards = []
  for (let i = 0; i < photos.length; i++) {
    const spot = layout[start + i]
    const isBig = spot.isBig === true
    const left = spot.left + randomBetween(-JITTER_PX, JITTER_PX)
    const top = spot.top + randomBetween(-JITTER_PX, JITTER_PX)
    // 흩어지는 움직임: 모든 카드가 가운데에 모여 있다가 자기 자리로 날아가요. from = 가운데 - 내 자리
    const width = isBig ? BIG_W : CARD_W
    const height = isBig ? BIG_H : CARD_H
    cards.push({
      photo: photos[i],
      name: placeName(photos[i]),
      isBig: isBig,
      left: left,
      top: top,
      rotate: spot.rotate + randomBetween(-JITTER_DEG, JITTER_DEG),
      fromX: (COLLAGE_W - width) / 2 - left,
      fromY: (COLLAGE_H - height) / 2 - top,
    })
  }
  collage.value = cards
}

// ---------------------------------------------------------------- 글자 만들기

// 사진을 찍은 장소 이름. 이동 중에 찍었으면 "1일째 13:30"
function placeName(photo) {
  if (photo.stopId && detail.value.stopNames[photo.stopId]) return detail.value.stopNames[photo.stopId]
  return photo.day + '일째 ' + photo.takenAt
}

// 앨범의 첫 사진(영상이 아닌 것). 없으면 null
function firstImage(album) {
  if (!album) return null
  for (const photo of album.photos) {
    if (photo.mediaType !== 'video') return photo
  }
  return null
}

// "2026-10-24", "2026-10-25" → "2026.10.24 ~ 10.25"
function toDateRange(album) {
  const start = album.startDate.replaceAll('-', '.')
  if (album.startDate === album.endDate) return start
  return start + ' ~ ' + album.endDate.slice(5).replace('-', '.')
}

// 여행 기간 글자. 예: 하루면 "당일치기", 이틀이면 "1박 2일"
function toNightsText(album) {
  const days = Math.round((new Date(album.endDate) - new Date(album.startDate)) / ONE_DAY_MS) + 1
  if (days <= 1) return '당일치기'
  return (days - 1) + '박 ' + days + '일'
}

// "2명 · 장소 6곳 · 사진 12장"
function toCountText(album) {
  const parts = []
  if (detail.value && detail.value.people) parts.push(detail.value.people + '명')
  parts.push('장소 ' + album.placeCount + '곳')
  parts.push('사진 ' + (album.photoCount + album.videoCount) + '장')
  return parts.join(' · ')
}
</script>

<template>
  <div class="scr scr-Shorts">
    <div class="app">
      <!-- 1 입구: 처음 한 번만 누르면 끝까지 이어서 재생 -->
      <!-- 불러오는 중·여행 없음·오류: 닫기 버튼 + 안내 한 줄 -->
      <div v-if="trips.length === 0" class="sh-none">
        <button type="button" class="sh-icon sh-icon--dark sh-none__close" aria-label="닫기" @click="close"><i class="ic i-close" aria-hidden="true"></i></button>
        <p v-if="error" class="sh-sub" role="alert">{{ error }}</p>
        <p v-else-if="isLoaded" class="sh-sub">아직 끝난 여행이 없어요</p>
      </div>

      <!-- 재생: 누르면 앞·뒤 장, 옆으로 밀면 앞·뒤 장, 위로 밀면 지난 여행 -->
      <div v-else class="sh-player" @pointerdown="onPointerDown" @pointerup="onPointerUp" @wheel.passive="onWheel">
        <Transition :name="'sh-trip-' + direction">
          <div :key="trip.tripId" class="sh-stage">
            <!-- 진행 막대: 칸을 누르면 그 장으로 바로 (막대가 얇아서 버튼 위아래에 누를 자리를 둠) -->
            <div class="sh-segs" :aria-label="slides.length + '장 중 ' + (slideIndex + 1) + '번째'" @pointerdown.stop @pointerup.stop>
              <button v-for="(fill, i) in segments" :key="i" type="button" class="sh-seg" :aria-label="(i + 1) + '번째 장으로'" @click="goToSlide(i)">
                <span><i :style="{ width: fill + '%' }"></i></span>
              </button>
            </div>

            <!-- 2 여행 첫 장: 대표 사진을 화면 가득 + 왼쪽 아래에 제목 -->
            <template v-if="slide.type === 'first'">
              <div class="sh-full">
                <template v-if="firstImage(trip)">
                  <!-- 뒤에 같은 사진을 흐리게 깔아서, 가로 사진이라 남는 위아래 칸을 채워요 -->
                  <img :src="firstImage(trip).url" alt="" class="sh-full__blur" />
                  <img :src="firstImage(trip).url" alt="" class="sh-full__media sh-zoom" :class="{ 'is-fill': fillIds[firstImage(trip).id] }" @load="onMediaLoad(firstImage(trip), $event.target.naturalWidth, $event.target.naturalHeight)" />
                </template>
                <span v-else class="sh-empty sh-empty--center"><i class="ic i-camera" aria-hidden="true"></i>사진이 없는 여행이에요</span>
              </div>
              <div class="sh-shade"></div>
              <div class="sh-text sh-text--first">
                <p class="sh-kicker tn">{{ tripIndex === 0 ? '가장 최근 여행' : '지난 여행' }} · {{ toDateRange(trip) }}</p>
                <h1 class="sh-head__title">{{ trip.title }}</h1>
                <p class="sh-sub">{{ toNightsText(trip) }}의 기록 · {{ toCountText(trip) }}</p>
                <p v-if="nextTrip" class="sh-hint"><i class="ic ic--s i-up" aria-hidden="true"></i>위로 밀면 지난 여행</p>
              </div>
            </template>

            <!-- 3 사진 한 장씩 자동으로: 화면 가득 + 왼쪽 아래에 장소 -->
            <template v-else-if="slide.type === 'photo'">
              <div class="sh-full">
                <img v-if="slide.photo.mediaType !== 'video'" :key="'b' + slide.photo.id" :src="slide.photo.url" alt="" class="sh-full__blur" />
                <!-- 꽉 채워도 되는 사진(세로 사진)만 is-fill. 가로 사진은 잘리지 않게 그대로 -->
                <video v-if="slide.photo.mediaType === 'video'" ref="videoEl" :key="'v' + slide.photo.id" :src="slide.photo.url" :muted="isMuted" class="sh-full__media" :class="{ 'is-fill': fillIds[slide.photo.id] }" autoplay playsinline @loadedmetadata="onMediaLoad(slide.photo, $event.target.videoWidth, $event.target.videoHeight)" @ended="goNextSlide"></video>
                <img v-else :key="'p' + slide.photo.id" :src="slide.photo.url" alt="" class="sh-full__media sh-zoom" :class="{ 'is-fill': fillIds[slide.photo.id] }" @load="onMediaLoad(slide.photo, $event.target.naturalWidth, $event.target.naturalHeight)" />
              </div>
              <div class="sh-shade"></div>
              <div class="sh-text sh-text--photo">
                <p v-if="captions[slide.photo.id]" :key="'c' + slide.photo.id" class="sh-line">{{ captions[slide.photo.id] }}</p>
                <span class="sh-chip"><i class="ic ic--s i-pin" aria-hidden="true"></i>{{ placeName(slide.photo) }}</span>
                <span class="sh-sub tn">{{ slide.photo.day }}일째 {{ slide.photo.takenAt }}</span>
              </div>
            </template>

            <!-- 4 여행 끝: 사진을 흩뿌리고(모양은 매번 랜덤) 다음 여행 예고 -->
            <template v-else>
              <div class="sh-end">
                <h1 class="sh-end__title">이번 여행, 여기까지예요</h1>
                <p class="sh-sub">{{ trip.title }} · 장소 {{ trip.placeCount }}곳 · 사진 {{ trip.photoCount + trip.videoCount }}장</p>
              </div>
              <div class="sh-collage">
                <div v-for="card in collage" :key="card.photo.id" class="sh-polaroid" :class="{ 'sh-polaroid--big': card.isBig }" :style="{ left: card.left + 'px', top: card.top + 'px', '--rot': card.rotate + 'deg', '--from-x': card.fromX + 'px', '--from-y': card.fromY + 'px' }">
                  <video v-if="card.photo.mediaType === 'video'" :src="card.photo.url" muted preload="metadata"></video>
                  <img v-else :src="card.photo.url" alt="" />
                  <p>{{ card.name }}</p>
                </div>
                <p v-if="collage.length === 0" class="sh-empty sh-empty--center">사진이 없는 여행이에요</p>
              </div>
              <div class="sh-end__route" @pointerdown.stop @pointerup.stop>
                <button type="button" class="sh-pill" @click="goRetro"><i class="ic ic--s i-map" aria-hidden="true"></i>이 여행 다녀온 길 보기</button>
              </div>

              <button v-if="nextTrip" type="button" class="sh-next" @pointerdown.stop @pointerup.stop @click="goNextTrip">
                <span class="sh-next__thumb"><img v-if="firstImage(nextTrip)" :src="firstImage(nextTrip).url" alt="" /></span>
                <span class="sh-next__text">
                  <span class="sh-next__kicker">다음 · 지난 여행</span>
                  <b>{{ nextTrip.title }}</b>
                  <span class="sh-sub tn">{{ nextTrip.startDate.replaceAll('-', '.') }} · {{ countdown }}초 뒤 이어서 재생</span>
                </span>
                <i class="ic i-up" aria-hidden="true"></i>
                <span class="sh-next__bar" :style="{ width: progress * 100 + '%' }"></span>
              </button>
              <button v-else type="button" class="sh-next sh-next--again" @pointerdown.stop @pointerup.stop @click="openTrip(0)">
                <span class="sh-next__text"><b>마지막 여행이에요</b><span class="sh-sub">처음 여행부터 다시 보기</span></span>
                <i class="ic i-refresh" aria-hidden="true"></i>
              </button>
            </template>

            <!-- 위: 닫기(왼쪽) + 여행 제목. 사진 위에 떠 있어요 -->
            <div class="sh-bar" @pointerdown.stop @pointerup.stop>
              <button type="button" class="sh-icon sh-icon--dark" aria-label="닫기" @click="close"><i class="ic i-close" aria-hidden="true"></i></button>
              <p class="sh-bar__title">{{ slide.type === 'photo' ? trip.title : '' }}</p>
            </div>
          </div>
        </Transition>

        <!-- 오른쪽 아래 작은 버튼: 멈춤·재생, 소리, 공유 (여행이 바뀌어도 그 자리에 그대로) -->
        <div class="sh-controls" @pointerdown.stop @pointerup.stop>
          <button type="button" class="sh-icon sh-icon--dark sh-icon--small" :aria-label="isPaused ? '재생' : '멈춤'" @click="togglePause">
            <i class="ic ic--s" :class="isPaused ? 'i-play' : 'i-pause'" aria-hidden="true"></i>
          </button>
          <!-- 아이콘 묶음에 스피커가 없어서 선 그림(svg)으로 그림 -->
          <button type="button" class="sh-icon sh-icon--dark sh-icon--small" :aria-label="isMuted ? '소리 켜기' : '소리 끄기'" @click="toggleMute">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M11 5L6 9H2v6h4l5 4V5z"></path>
              <path v-if="isMuted" d="M22 9l-6 6M16 9l6 6"></path>
              <template v-else><path d="M15.5 8.5a5 5 0 0 1 0 7"></path><path d="M19 5a10 10 0 0 1 0 14"></path></template>
            </svg>
          </button>
          <button type="button" class="sh-icon sh-icon--dark sh-icon--small" aria-label="공유" @click="share"><i class="ic ic--s i-share" aria-hidden="true"></i></button>
        </div>

        <p v-if="toast" class="sh-toast" role="status">{{ toast }}</p>
      </div>
    </div>
  </div>
</template>

<style>
.scr-Shorts .app{background:#0A0E1C;color:#fff}
.scr-Shorts .sh-icon{width:44px;height:44px;flex:none;border:0;border-radius:999px;background:transparent;color:#fff;display:flex;align-items:center;justify-content:center;cursor:pointer}
.scr-Shorts .sh-sub{margin:0;font-size:13px;line-height:18px;color:rgba(255,255,255,.72)}
.scr-Shorts .sh-hint{margin:0;font-size:12px;line-height:16px;color:rgba(255,255,255,.6);text-align:center}
.scr-Shorts .sh-empty{display:flex;flex-direction:column;align-items:center;gap:8px;font-size:13px;line-height:18px;color:#535D73}
.scr-Shorts .sh-empty--center{position:absolute;inset:0;justify-content:center;color:rgba(255,255,255,.6)}
.scr-Shorts img,.scr-Shorts video{display:block;width:100%;height:100%;object-fit:cover}

/* 여행 없음 */
.scr-Shorts .sh-none{position:absolute;inset:0;display:flex;align-items:center;justify-content:center}
.scr-Shorts .sh-none__close{position:absolute;top:62px;left:16px}

/* 재생 화면 공통 */
.scr-Shorts .sh-player{position:absolute;inset:0;overflow:hidden;background:#000;touch-action:none;user-select:none}
.scr-Shorts .sh-stage{position:absolute;inset:0;overflow:hidden;background:#0A0E1C}
.scr-Shorts .sh-segs{position:absolute;top:41px;left:12px;right:12px;display:flex;gap:4px;z-index:2}
.scr-Shorts .sh-seg{flex:1;min-width:0;padding:10px 0;border:0;background:transparent;cursor:pointer}
.scr-Shorts .sh-seg span{display:block;height:3px;border-radius:999px;background:rgba(255,255,255,.35);overflow:hidden}
.scr-Shorts .sh-seg i{display:block;height:3px;background:#fff}
.scr-Shorts .sh-bar{position:absolute;top:62px;left:0;right:0;z-index:2;display:flex;align-items:center;gap:10px;padding:0 16px}
.scr-Shorts .sh-bar__title{flex:1;min-width:0;margin:0;font-size:14px;line-height:20px;font-weight:600;text-shadow:0 1px 4px rgba(0,0,0,.5);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.scr-Shorts .sh-icon--dark{width:40px;height:40px;background:rgba(0,0,0,.4)}
.scr-Shorts .sh-icon--small{width:36px;height:36px}
.scr-Shorts .sh-controls{position:absolute;right:16px;bottom:24px;z-index:3;display:flex;gap:8px}
.scr-Shorts .sh-toast{position:absolute;left:50%;bottom:76px;z-index:3;transform:translateX(-50%);margin:0;padding:8px 14px;border-radius:999px;background:rgba(0,0,0,.75);font-size:13px;line-height:18px;white-space:nowrap}

/* 2·3 사진을 화면 가득: 뒤에 흐린 사진, 앞에 원래 사진. 위아래를 살짝 어둡게 해서 글자가 잘 보이게 */
.scr-Shorts .sh-full{position:absolute;inset:0;overflow:hidden;background:#000}
.scr-Shorts .sh-full__blur{position:absolute;inset:0;transform:scale(1.15);filter:blur(24px) brightness(.55)}
.scr-Shorts .sh-full__media{position:relative;object-fit:contain}
.scr-Shorts .sh-full__media.is-fill{object-fit:cover}
.scr-Shorts .sh-zoom{animation:sh-zoom 6s ease-out forwards}
@keyframes sh-zoom{from{transform:scale(1)}to{transform:scale(1.08)}}
.scr-Shorts .sh-shade{position:absolute;inset:0;pointer-events:none;background:linear-gradient(to bottom,rgba(0,0,0,.45),rgba(0,0,0,0) 20%,rgba(0,0,0,0) 55%,rgba(0,0,0,.75))}
.scr-Shorts .sh-text{position:absolute;left:20px;z-index:2;display:flex;flex-direction:column;align-items:flex-start;gap:6px;text-shadow:0 1px 4px rgba(0,0,0,.4)}
.scr-Shorts .sh-text--first{right:20px;bottom:84px;gap:8px}
.scr-Shorts .sh-text--first .sh-sub{font-size:15px;line-height:22px;color:rgba(255,255,255,.85)}
.scr-Shorts .sh-text--photo{right:150px;bottom:24px}
.scr-Shorts .sh-text .sh-hint{display:flex;align-items:center;gap:4px;margin-top:6px}
.scr-Shorts .sh-kicker{margin:0;font-size:13px;line-height:18px;font-weight:600;color:#B5DDF5}
.scr-Shorts .sh-head__title{margin:0;font-size:32px;line-height:40px;font-weight:700;letter-spacing:-.02em}
.scr-Shorts .sh-line{margin:0 0 2px;font-size:20px;line-height:28px;font-weight:700;letter-spacing:-.01em;animation:sh-fade .6s ease-out both}
@keyframes sh-fade{from{opacity:0;transform:translateY(6px)}to{opacity:1;transform:none}}
.scr-Shorts .sh-chip{max-width:100%;box-sizing:border-box;display:inline-flex;align-items:center;gap:4px;height:28px;padding:0 10px;border-radius:999px;background:rgba(0,0,0,.4);font-size:13px;line-height:18px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}

/* 4 끝 장 */
.scr-Shorts .sh-end{position:absolute;top:118px;left:20px;right:20px;display:flex;flex-direction:column;gap:4px;align-items:center;text-align:center}
.scr-Shorts .sh-end__title{margin:0;font-size:24px;line-height:32px;font-weight:700}
.scr-Shorts .sh-collage{position:absolute;top:186px;left:0;right:0;height:430px}
.scr-Shorts .sh-polaroid{position:absolute;width:124px;height:156px;box-sizing:border-box;padding:5px 5px 24px;border-radius:10px;background:#fff;box-shadow:0 2px 16px rgba(0,0,0,.35);transform:rotate(var(--rot));animation:sh-scatter .9s cubic-bezier(.2,.8,.2,1) both}
.scr-Shorts .sh-polaroid img,.scr-Shorts .sh-polaroid video{border-radius:6px}
.scr-Shorts .sh-polaroid p{position:absolute;left:8px;right:8px;bottom:5px;margin:0;font-size:11px;line-height:14px;font-weight:600;color:#18213A;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.scr-Shorts .sh-polaroid--big{width:148px;height:186px;padding-bottom:28px;box-shadow:0 2px 16px rgba(0,0,0,.45)}
.scr-Shorts .sh-polaroid:nth-child(2){animation-delay:.12s}
.scr-Shorts .sh-polaroid:nth-child(3){animation-delay:.24s}
.scr-Shorts .sh-polaroid:nth-child(4){animation-delay:.36s}
.scr-Shorts .sh-polaroid:nth-child(5){animation-delay:.48s}
/* 흩어지기: 가운데에 작게 겹쳐 있다가(--from-x·y만큼 떨어진 곳) 기울어지며 제자리로 */
@keyframes sh-scatter{from{opacity:0;transform:translate(var(--from-x),var(--from-y)) rotate(0deg) scale(.6)}25%{opacity:1}to{opacity:1;transform:translate(0,0) rotate(var(--rot)) scale(1)}}
.scr-Shorts .sh-end__route{position:absolute;top:614px;left:0;right:0;display:flex;justify-content:center}
.scr-Shorts .sh-pill{height:36px;padding:0 14px;border:0;border-radius:999px;background:rgba(255,255,255,.14);color:#fff;font-size:14px;font-weight:600;display:inline-flex;align-items:center;gap:6px;cursor:pointer}
.scr-Shorts .sh-next{position:absolute;left:16px;right:16px;bottom:76px;height:100px;box-sizing:border-box;padding:14px 12px 14px 14px;border:0;border-radius:16px;background:rgba(255,255,255,.1);color:#fff;display:flex;align-items:center;gap:12px;overflow:hidden;text-align:left;cursor:pointer}
.scr-Shorts .sh-next__thumb{width:64px;height:64px;flex:none;border-radius:12px;overflow:hidden;background:#D6EEFB}
.scr-Shorts .sh-next__text{flex:1;min-width:0;display:flex;flex-direction:column;gap:2px}
.scr-Shorts .sh-next__text b{font-size:16px;line-height:24px;font-weight:600}
.scr-Shorts .sh-next__kicker{font-size:12px;line-height:16px;font-weight:600;color:#B5DDF5}
.scr-Shorts .sh-next__bar{position:absolute;left:0;bottom:0;height:3px;background:#fff}
.scr-Shorts .sh-next--again{height:72px}

/* 5 여행 넘기기: 다음 여행이 아래에서 둥근 모서리로 올라옴 */
.scr-Shorts .sh-trip-up-enter-active,.scr-Shorts .sh-trip-up-leave-active,.scr-Shorts .sh-trip-down-enter-active,.scr-Shorts .sh-trip-down-leave-active{transition:transform .45s ease,opacity .45s ease,border-radius .45s ease}
.scr-Shorts .sh-trip-up-enter-active,.scr-Shorts .sh-trip-down-enter-active{z-index:2}
.scr-Shorts .sh-trip-up-enter-from{transform:translateY(100%);border-radius:20px 20px 0 0;box-shadow:0 -8px 24px rgba(0,0,0,.5)}
.scr-Shorts .sh-trip-up-leave-to{transform:translateY(-30%);opacity:.4}
.scr-Shorts .sh-trip-down-enter-from{transform:translateY(-100%);border-radius:0 0 20px 20px}
.scr-Shorts .sh-trip-down-leave-to{transform:translateY(30%);opacity:.4}
</style>
