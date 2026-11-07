<script setup>
// S19-a 지난 여행 다시보기 (⑤): 다녀온 동선을 재생하고, 핀을 누르면 시간·결제 메모를 봐요. 계획과 실제도 비교해요
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { get } from '@/api/http'
import { toTime, toMinutes } from '@/utils/time'
import { goBack } from '@/utils/back' // 알림에서 들어왔으면(?back=) 알림함으로
import BottomSheet from '@/components/common/BottomSheet.vue' // 끌어서 내리고 올리는 아래 시트 (다른 화면과 같은 것)
import PhotoViewer from '@/components/after/PhotoViewer.vue' // 사진 크게 보기 (앨범과 같은 것)
import { toDurationText } from '@/utils/photoUpload' // 영상 길이 12 → "0:12"


const visited = ref([

])
const trackPoints = ref([]) // gps 점 목록 drawMap에서 채워짐

const progress = ref(68) // 재생 위치 (%)
const playing = ref(false)
const speed = ref(1) // 재생 속도(1,2,4배)
const PLAY_STEP_1X = 0.375 // 1배속일 때 90ms마다 나아가는 % 100%까지 약 24초
const infoNo = ref(null)
const selectedDay  = ref(1)
let timer = null

const routePoints = computed(() => trackPoints.value.map((v) => v.x + ',' + v.y).join(' ')) // 다녀온 곳 좌표를 "x,y x,y x,y" 문자열로 만들어서 <polyline>에 넣음
const info = computed(() => visited.value.find((v) => v.no === infoNo.value)) 
//재생 위치(%)를 시각으로 바꾸고, 그 시각이 들어 있는 GPS 구간에서 점을 계산해요
const me = computed(() => {
  const points = trackPoints.value
  if (points.length < 2) return { x: -100, y: -100 } // 선이 없으면 점을 화면 밖에 두게 함
  const now = nowMinutes.value // 지금 시각(분). 막대 시각과 같은 계산
  for (let i = 0; i < points.length - 1; i++) {
    const a = points[i]
    const b = points[i + 1]
    const aTime = toMinutes(a.time)
    const bTime = toMinutes(b.time)
    if (now > bTime) continue // 지금이 이 구간 끝보다 늦으면 → 다음 구간으로
    let rate = 0 // 구간 안에서 몇 % 왔는지 (두 점 시각이 같으면 0)
    if (bTime > aTime) rate = (now - aTime) / (bTime - aTime)
    return { x: Math.round(a.x + (b.x - a.x) * rate), y: Math.round(a.y + (b.y - a.y) * rate) }
  }
  const last = points[points.length - 1] // 끝까지 못 찾으면 마지막 점
  return { x: last.x, y: last.y }
})
const nowText = computed(() => toTime(Math.round(nowMinutes.value)) + (playing.value ? ' · ' + speed.value + '배속' : ''))


const movedText = computed(() => {
  if(!retro.value) return '' // 응답이 없다면 빈글자
  const meters = retro.value.movedMeters
  if (meters < 1000) return meters + 'm'
  return (meters / 1000).toFixed(1) + 'km' // toFixed(1) 소수점 첫째 자리까지만 글자로 만듬 
})

const skippedCount = computed(() => {
  if (!retro.value) return 0 //응답이 오기 전이면 0
  let count = 0
  for (const place of retro.value.stops) {
    if (place.state === 'skipped') count++
  }
  return count
})

// 고른 날의 일정 칸만 담기. 예: 2일차 → [명동, 남산, 남대문시장]
const dayStops = computed(() => {
  const list = []
  if (retro.value === null) return list
  for (const place of retro.value.stops ) {
    if(place.day===selectedDay.value)  {
      list.push(place)
    }

  }
      return list

})


// 고른 날의 GPS 점만 담기. 예: 2일차 → 09:40 ~ 13:40, 9점
const dayTrack = computed(() => {
  const gpslist = []
  if (retro.value === null) return gpslist
  for (const gps of retro.value.track) {
    if(gps.day === selectedDay.value ){
      gpslist.push(gps)
    }
  }
  return gpslist
})

// 고른 날에 사진만 담기. 예: 1일차 → 12:20 사진

const dayPhotos = computed(() => {
  const list = []
  for(const photo of photos.value){
    if(photo.day === selectedDay.value){
      list.push(photo)
    }
  }
  return list
})
// 여행 날짜 목록. 예: 일정 칸 day가 1, 1, 2, 2, 2 → [1, 2]

const dayList = computed(() => {
  const list = []
  if (retro.value === null) return list

  let lastDay = 1
  for (const place of retro.value.stops) {
    if (place.day > lastDay) {
      lastDay = place.day
    }
  }

  for (let day = 1; day <= lastDay; day++) {
    list.push(day)
  }
  return list
})

function changeDay(day) {
  stop()                       // 재생 중이면 멈춤
  selectedDay.value = day   // 누른 날
  progress.value = 0      // 막대 처음으로
  infoNo.value = null        // 열려 있던 핀 정보창 닫기
  floatPhoto.value = null    // 떠 있던 사진 카드 내리기
  if (selectedmap === null) return  // 지도가 아직 없으면 맞출 게 없음
  const sheet = document.querySelector('.retro-sheet')
 fitRoute(sheet.classList.contains('is-collapsed'))             // 시트가 내려가 있으면 true
}

// 재생 위치에 따라 현재 시각을 계산(시작 시간 + 진행률 + 도착 시간 - 시작 시간 + 진행률)
const startMinutes = computed(() => { // 첫 점의 시각
  if (trackPoints.value.length === 0) return 0
  return toMinutes(trackPoints.value[0].time)
})

const endMinutes = computed(() => {
  if (trackPoints.value.length === 0) return 0
  return toMinutes(trackPoints.value[ trackPoints.value.length - 1].time)
})

// 재생 위치(%)를 지금 시각 (분)으로 바꿈 예 50% -> 770 + 0.5 *270분 = 905분
const nowMinutes = computed(() => {
  return startMinutes.value + (progress.value / 100) * (endMinutes.value - startMinutes.value) // 지금 시각(분). 막대 시각과 같은 계산
})
// 재생 중 지금 머물고 있는 장소 번호. 예: 800분(13:20) → 경복궁(780~860) 안이라 1. 이동 중이면 null

const currentNo = computed(() => {
  if (retro.value === null) return null

  const stops = dayStops.value
  for (let i = 0; i<stops.length; i++) {
    if (stops[i].actualArrive === null || stops[i].actualStay === null) continue

    const arrive = toMinutes(stops[i].actualArrive)
    const leave = (arrive + stops[i].actualStay)

    if(arrive <= nowMinutes.value && nowMinutes.value < leave) return i + 1
  }
  return null
})


function togglePlay() {
  if (playing.value) return stop()
  if (progress.value >= 100) progress.value = 0
  playing.value = true
  infoNo.value = null
  floatPhoto.value = null
  timer = setInterval(() => {
    const before = nowMinutes.value // 한 칸 나아가기 전 시각
    progress.value = Math.min(100, progress.value + PLAY_STEP_1X * speed.value)
    checkPhotoPopup(before, nowMinutes.value) // 그 사이에 찍은 사진이 있으면 지도 위에 띄움
    if (progress.value >= 100) stop()
  }, 90)
}
function stop() {
  clearInterval(timer)
  playing.value = false
}

// 재생 속도를 바꾸는 함수

function changeSpeed() {
  if(speed.value === 1) {
    speed.value = 2
  }
  else if(speed.value === 2) {
    speed.value = 4
  }
  else {

    speed.value = 1
  }
}

onUnmounted(() => clearInterval(timer))
onUnmounted(() => { if(sheetWatcher !== null ) sheetWatcher.disconnect()}) // 화면을 나가면 감시도 RRma
const route = useRoute() // 지금 주소 정보. ?id = 12 값을 읽을 떄 사용
const retro = ref(null)//서버 응답 . 받기 전에는 null
const errorMessage = ref(``) // 회고를 못 불러왔을 때 화면에 보여 줄 문구
const mapBox = ref(null) // 지도를 그릴 칸
let selectedmap = null // 선택 한 지도로 변환
const MAP_LEVEL = 5 // 지도 확대 단계, 숫자가 작을수록 가까이
const MAP_PADDING_TOP = 120 // 위쪽 버튼 줄(47 + 56 = 103)과 핀 반지름(14)만큼 비워서 핀이 버튼에 안 가리게
const MAP_PADDING_SIDE = 40 // 핀이 화면 양옆에 잘리지 않게
const MAP_PADDING_BOTTOM_OPEN = 600 // 시트 펼침: 재생 막대(위쪽 262) 위만 보임 → 844 − 262 + 18
const MAP_PADDING_BOTTOM_CLOSED = 232 // 시트 내림: 재생 막대가 약 630까지 내려감 → 844 − 630 + 18
const MAP_HEIGHT = 844 // 지도 칸 높이 = 화면 전체 (정보창 위치 계산에 씀)
// 다녀온 길 : 자옷를 순서대로 한 줄 씩 담음 : {  예: { no: 1, name: '경복궁', timeText: '13:00 ~ 14:20', skipped: false }
const timeline = computed(() => {
  const rows = []
  if (retro.value === null) return rows

  const stops = dayStops.value
  for (let i = 0; i < stops.length; i++) { // i : 몇번째 장소인지 (다음 단계에서 '다음 장소를 볼때 i +1')
    rows.push({
      type: 'place',
      no : i + 1,
      stopId: stops[i].id, // 사진을 고를 때 씀
      name : stops[i].name,
      timeText : toTimeRange(stops[i]),
      skipped: stops[i].state === 'skipped',
    })
    if (i + 1 < stops.length) { // 마지막 장소 뒤에는 이동이 없음
      const move = toMoveRow(stops[i], stops[i + 1])
      if (move !== null) rows.push(move) // 기록이 없어 계산 못 하면 안 넣음
    }
  }

  return rows
})

function toTimeRange(place) { // 실제 시각 ~ 떠난 시각 글자 : 예 : 13:00에 와서 80분 -> 13:00 ~ 14:20
  if(place.actualArrive === null) return '기록 없음' // 안 간 곳
  if(place.actualStay === null) return place.actualArrive + ' 떠난 시간이 없어요'
  const leave = toMinutes(place.actualArrive) + place.actualStay

  return place.actualArrive + ' ~ ' + toTime(leave) // 도착  + 머문시간
}

// 두 장소 사이 이동 줄. 예: 14:20에 나와서 14:50에 도착 → { type: 'move', text: '이동 30분', timeText: '14:20 ~ 14:50' }
function toMoveRow(from, to) {
if(from.actualArrive === null || from.actualStay === null ) return null
if(to.actualArrive === null) return null
const leave = toMinutes(from.actualArrive) + from.actualStay
const arrive = toMinutes(to.actualArrive)
return { type : 'move', text : '이동 ' + (arrive - leave) + '분', timeText: toTime(leave) + ' ~ ' + to.actualArrive,
  day: to.day, leave: leave, arrive: arrive } // day·leave·arrive: 이동 중 사진을 고를 때 씀
}

//화면이 열리면 회고 데이터를 가져옴
onMounted(async () => {
  watchSheet() // 시트가 내려가고 올라가는 걸 지켜보기 시작

  try{
    retro.value = await get('/trips/' + route.query.id +'/retro')
    await loadMap()
  }catch  (error){
    errorMessage.value = error.message


  }
  loadPhotos()
})

// ============================== 사진 (11/06)
const photos = ref([])        // 이 여행의 사진·영상 전체, 찍은 순 (GET /api/trips/{id}/photos)
const viewerStart = ref(null) // 크게 보기로 연 칸 번호. null이면 닫힘

// 사진 불러오기. 실패해도 회고는 그대로 보이게 따로 try
async function loadPhotos() {
  try {
    photos.value = await get('/trips/' + route.query.id + '/photos')
  } catch {
    photos.value = []
  }
}

// 이 일정 칸(stopId)에서 찍은 사진만 골라 담기. 예: 경복궁 칸(4884) → [13:30 사진]
function photosOf(stopId) {
  const list = []
 for (const picture of photos.value) {
    
    if(stopId === picture.stopId){
      list.push(picture)
    }
 }
  return list
}

// 이동 중에 찍은 사진: 일정 칸이 없고(null), 같은 날, 이 이동 시간(leave ~ arrive) 안에 찍은 것
function photosOfMove(row) {
  const list = []
  for (const picture of photos.value) {
    const minutes = toMinutes(picture.takenAt)
    if (picture.stopId === null && picture.day === row.day && minutes >= row.leave && minutes < row.arrive) {
      list.push(picture)
    }
  }
  return list
}

// 사진을 누르면 크게 보기. 여행 사진 전체에서 몇 번째인지로 열어요 (◁ ▷로 다른 장소 사진도 넘겨 봄)
function openPhoto(photo) {
  viewerStart.value = photos.value.indexOf(photo)
}

// ============================== 재생 중 사진 띄우기 + 막대 눈금
const PHOTO_SHOW_MINUTES = 20 // 사진 카드가 떠 있는 시간 (재생 시각 기준 20분)
const floatPhoto = ref(null)  // 지금 지도 위에 떠 있는 사진 { photo, x, y, until }. 없으면 null

// 재생이 before → after 시각으로 나아갔을 때, 그 사이에 찍은 사진이 있으면 지금 점(me) 위치에 띄움
// 예: 12:18 → 12:21로 나아가면 12:20 사진이 뜸
function checkPhotoPopup(before, after) {
  for (const photo of dayPhotos.value) {
    const minutes = toMinutes(photo.takenAt)
    if (minutes > before && minutes <= after) {
      floatPhoto.value = { photo: photo, x: me.value.x, y: me.value.y, until: after + PHOTO_SHOW_MINUTES }
    }
  }
  if (floatPhoto.value !== null && after > floatPhoto.value.until) {
    floatPhoto.value = null // 20분이 지나면 내림
  }
}

// 사진 카드를 누르면: 재생을 멈추고 크게 보기
function openFloatPhoto() {
  stop()
  openPhoto(floatPhoto.value.photo)
}

// 사진을 찍은 장소 이름. 이동 중이면 '이동 중'
function placeNameOf(photo) {
  if (retro.value === null || photo.stopId === null) return '이동 중'
  for (const stop of retro.value.stops) {
    if (stop.id === photo.stopId) return stop.name
  }
  return '이동 중'
}

// 막대 눈금: 사진 찍은 시각이 막대의 몇 % 자리인지. 예: 12:00~18:00 막대에서 15:00 사진 → 50%
const photoTicks = computed(() => {
  const ticks = []
  const range = endMinutes.value - startMinutes.value
  if (range <= 0) return ticks
  for (const photo of dayPhotos.value) {
    const percent = ((toMinutes(photo.takenAt) - startMinutes.value) / range) * 100
    if (percent >= 0 && percent <= 100) ticks.push({ id: photo.id, percent: percent })
  }
  return ticks
})

// kakao map 소환

async function loadMap() {
  const config = await get('/config/public')
  if(config.kakaoJsKey === undefined) { // 키가 없으면 서버가 빈 객체 {}를 줌
    errorMessage.value = '지도를 불러오지 못했습니다'
    return

  }

// <script> 태그를 코드로 만든후 키를 불러와 카카오 맵 불러옴
const script = document.createElement('script')
script.src = 'https://dapi.kakao.com/v2/maps/sdk.js?autoload=false&appkey=' + config.kakaoJsKey
script.onload = () => {
    // load: autoload=false로 미뤄 둔 준비를 시작하고, 끝나면 안의 함수를 실행함

  kakao.maps.load(()=> {
    drawMap()
  })
}
document.head.appendChild(script) // 페이지에 붙이는 순간 다운로드가 시작돼요
}
// 첫 번째 장소를 가운데로 해서 지도를 그림
function drawMap() {
  if (dayStops.value.length === 0) return 
  const firstStop = dayStops.value[0]
  const center = new kakao.maps.LatLng(firstStop.lat , firstStop.lng)
  const map = new kakao.maps.Map(mapBox.value, { center : center, level: MAP_LEVEL })
  selectedmap = map
  fitRoute(false) // 처음엔 펄쳐져 있음
}
function fitRoute(isCollapsed) {// 시트가 펼쳐졌는지)false 내려갔는지(true)에 따라 아래 여백을 바꿔서 경로를 보이는 칸 가운데 맞춤
  const map = selectedmap
  let paddingBottom =  MAP_PADDING_BOTTOM_OPEN 
  if (isCollapsed === true) 
  { paddingBottom = MAP_PADDING_BOTTOM_CLOSED}

  const bounds = new kakao.maps.LatLngBounds() // LatLngBounds: 넣은 좌표가 모두 들어가는 네모 범위
  for (const place of dayStops.value) // place 장소 들른 곳의 목록 중 꺼낸거
  bounds.extend(new kakao.maps.LatLng(place.lat , place.lng))
  map.setBounds(bounds,MAP_PADDING_TOP , MAP_PADDING_SIDE ,  paddingBottom , MAP_PADDING_SIDE) // 지도에 맞게 확대/축소 순서 : 위 , 왼쪽, 아래 , 오른쪽

  const points = [] // 장소별 핀 정보를 담을 목록
  for (const place of dayStops.value){
    const latLng = new kakao.maps.LatLng(place.lat , place.lng)
    const point = map.getProjection().containerPointFromCoords(latLng) // 방금 받은 지도좌표를 X,Y 화면 좌표로 바꿔줌
    points.push({ no : points.length + 1, x : point.x , y : point.y, name : place.name , time : place.time, pay : '' , skipped : place.state === 'skipped' }) // point.length : 핀 번호에 번호붙이기
  }
  visited.value=points // 화면에 보여줄 핀 좌표를 visited에 넣음 vistied : 화면에 실제로 방문한 잠소를 담는 ref
  const trackList = [] // gps 점마다 {x,y, time}을 담을 목록
  for (const gps of dayTrack.value) { // gps: 실제로 이동한 기록
    const latLng = new kakao.maps.LatLng(gps.lat , gps.lng) // gps 좌표를 실제 좌표로 바꾸고 카카오 맵에 넣어줌
    const point = map.getProjection().containerPointFromCoords(latLng) // 화면 좌푤 ㅗ마바꿔줌
    trackList.push({ x: point.x , y : point.y , time : gps.time }) 
  }
trackPoints.value = trackList // 화면에 보여줄 gps ㅈ점 좌표를 넣음 포인트에
}


// 시트에 'is-collasped(내려감) 표시가 붙거나 떨어지는 순간 경로를 닷 ㅣ맞춤(끌든 누루든 항상 알 수 있음)
let sheetWatcher = null // 시트를 지켜보는 감시자. 화면을 나갈 때 시트를 끄려고 기억해둠
function watchSheet() {
  const sheet = document.querySelector('.retro-sheet') // 정보 시트 (바텀 시트가 그린 세션
  if (sheet === null) return
    // MutationObserver: 요소가 바뀌는지 지켜보다가 바뀌면 안의 함수를 불러 줌
    sheetWatcher = new MutationObserver(() => {
      if(selectedmap === null) return // 지도가 없다면 맞추게 없고
      const isCollapsed = sheet.classList.contains('is-collapsed')
      fitRoute(isCollapsed)
    })
    sheetWatcher.observe(sheet, { attributes: true, attributeFilter: ['class'] }) // class가 바뀔 때만 알려 달라고 함
}




function toggleInfo(no) {
  infoNo.value = infoNo.value === no ? null : no
}


// 타임라인 줄을 누르면 재생 위치를 그 장소 도착 시각으로 옮김. 예: 13:00 도착 → (780 − 770) / 260 × 100 ≈ 3.8%
function moveToPlace(no) {
  const place = dayStops.value[no - 1] // 줄 번호는 1부터, 목록 번호는 0부터라 1을 뺌
  if (place.actualArrive === null) return // 안 간 곳은 옮길 시각이 없음
  if (endMinutes.value === startMinutes.value) return // GPS가 없거나 한 점이면 옮길 수 없음
  stop() // 재생 중이면 멈추고 옮김
  progress.value = (toMinutes(place.actualArrive) - startMinutes.value) / (endMinutes.value  - startMinutes.value) * 100
}

// 지도 핀을 누르면 타임라인의 그 장소 줄이 보이게 스크롤함. 예: ③ 핀 → id가 'tl-row-3'인 줄
function scrollToRow(no) {
  const rowBox = document.getElementById('tl-row-' +  no ) // 줄마다 붙인 id로 찾음
  if (rowBox === null) return // 그 줄을 못 찾으면 아무것도 안 함
  rowBox.scrollIntoView({ behavior: 'smooth', block: 'center' }) // smooth: 부드럽게, center: 줄을 칸 가운데로
}

const isHybrid = ref(false)

// 버튼을 누를 때마다 일반 ↔ 하이브리드 지도로 바꿈
function toggleMapType() {
  if (selectedmap === null) return // 지도가 아직 안 그려졌으면 아무것도 안 함
  isHybrid.value = !isHybrid.value  // 메모지 고쳐 적기: false → true, true → false
  if (isHybrid.value) {
    selectedmap.setMapTypeId(kakao.maps.MapTypeId.HYBRID)  // 하이브리드 지도
  } else {
    selectedmap.setMapTypeId(kakao.maps.MapTypeId.ROADMAP) // 일반 지도
  }
}


</script>

<template>
  <div class="scr scr-Retro">
    <div class="app">
      <div class="retro-map" aria-hidden="true">
        <div class="map"><div ref = "mapBox" style ="width: 100%; height: 100%"></div></div>
        <svg class="route" style="position: absolute; inset: 0; width: 100%; height: 100%">
          <!-- 흰 테두리: 굵은 흰 선을 먼저 깔고, 그 위에 남색 선을 겹쳐 그림 (SVG는 나중에 쓴 게 위에 그려짐) -->
          <polyline :points="routePoints" fill="none" stroke="#FFFFFF" stroke-width="9" stroke-linecap="round" stroke-linejoin="round" />
          <polyline :points="routePoints" fill="none" stroke="#1F3A68" stroke-width="5" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </div>
      <div class="retro-map">
        <button v-for="v in visited" :key="v.no" type="button" class="pin pin--visited" :class="{ 'pin--sel': infoNo === v.no, 'pin--skipped' : v.skipped }" :style="{ left: v.x + 'px', top: v.y + 'px' }" :aria-label="v.no + ' ' + v.name" @click="toggleInfo(v.no); scrollToRow(v.no)">
          {{ v.no }}<span v-if="v.pay" class="paydot" aria-hidden="true"><i class="ic i-won"></i></span>
        </button>
        <span class="me" :style="{ left: me.x + 'px', top: me.y + 'px' }" aria-hidden="true"></span>
        <!-- 재생 중 사진 카드: 사진 찍은 시각을 지나면 점 위에 떠요. 누르면 크게 보기 -->
        <button
          v-if="floatPhoto"
          :key="floatPhoto.photo.id"
          type="button"
          class="retro-float"
          :style="{ left: Math.max(66, Math.min(324, floatPhoto.x)) + 'px', top: Math.max(150, floatPhoto.y - 18) + 'px' }"
          :aria-label="placeNameOf(floatPhoto.photo) + ' ' + floatPhoto.photo.takenAt + ' 사진 크게 보기'"
          @click="openFloatPhoto"
        >
          <span class="retro-float__img">
            <video v-if="floatPhoto.photo.mediaType === 'video'" :src="floatPhoto.photo.url + '#t=0.1'" preload="metadata" muted playsinline></video>
            <img v-else :src="floatPhoto.photo.url" alt="" />
          </span>
          <span class="retro-float__cap tn">{{ placeNameOf(floatPhoto.photo) }} · {{ floatPhoto.photo.takenAt }}</span>
        </button>
        <div
          v-if="info"
          class="payinfo"
          :style="{ left: Math.max(12, Math.min(150, info.x - 30)) + 'px', ...(info.y > 160 ? { bottom: MAP_HEIGHT - info.y + 24 + 'px' } : { top: info.y + 26 + 'px' }) }"
          role="dialog"
          aria-label="다녀온 곳 정보"
        >
          <b>{{ info.name }}</b>
          <span class="t-cap tn">{{ info.time }}</span>
          <template v-if="info.pay">
            <span class="t-cap tn retro-pay"><i class="ic ic--s i-won" aria-hidden="true"></i> 결제 메모</span>
            <pre>{{ info.pay }}</pre>
          </template>
          <span v-else class="t-cap">결제 내역 없음</span>
        </div>
      </div>

      <header class="topbar topbar--float">
        <button type="button" class="iconbtn iconbtn--float" aria-label="뒤로가기" @click="goBack('/trips')"><i class="ic ic--l i-back" aria-hidden="true"></i></button>
        <button type="button" class="iconbtn iconbtn--float" style="margin-left: auto; width: auto; padding: 0 12px" @click="toggleMapType">
          {{ isHybrid ? '일반 지도' : '하이브리드 지도' }}
        </button>
      </header>

      <!-- data-sheet-follow: 시트를 내리면 재생 막대도 같이 내려감 (BottomSheet 기능) -->
      
      <div class="player" data-sheet-follow style="position: absolute; left: 16px; right: 16px; top: 262px">
        <button type="button" class="iconbtn retro-play" :aria-label="playing ? '멈추기' : '이동 경로 재생'" @click="togglePlay"><i class="ic" :class="playing ? 'i-pause' : 'i-play'" aria-hidden="true"></i></button>
        <!-- 재생 속도: 누를 때마다 1배 → 2배 → 4배 → 1배 -->
        <button type="button" class="retro-speed" :aria-label="'재생 속도 ' + speed + '배'" @click="changeSpeed()">{{ speed }}배</button>
        <span style="flex: 1; display: flex; flex-direction: column; gap: 4px">
          <!-- 막대 + 사진 눈금(코랄색): 사진 찍은 시각 자리 -->
          <span class="retro-barwrap">
            <span class="bar"><i :style="{ width: progress + '%' }"></i></span>
            <span v-for="tick in photoTicks" :key="tick.id" class="retro-tick" :style="{ left: tick.percent + '%' }" aria-hidden="true"></span>
          </span>
          <span style="display: flex; justify-content: space-between" class="t-cap tn"><span>{{ toTime(startMinutes) }}</span><b class="c-navy">{{ nowText }}</b><span>{{ toTime(endMinutes) }}</span></span>
        </span>
      </div>

      <!-- 정보 시트: 손잡이나 제목 부분(data-drag)을 끌면 내려가고 올라옴 -->
      <BottomSheet class="sheet--peek retro-sheet" aria-labelledby="retro-title" >
        <!-- 여행 제목과 날짜를 API 값으로 보여 줌 -->
        <div class="retro-sheet__head" data-drag>
          <h1 v-if="retro" id="retro-title" class="h-title">{{ retro.title }}</h1>
          <!-- 날짜 줄: 왼쪽 기간, 오른쪽 날짜 버튼(여러 날 여행일 때만). 누르면 그날 장소·GPS·사진만 보여 줌 -->
          <div v-if="retro" class="retro-dayrow">
            <p class="t-cap">{{ retro.startDate }} ~ {{ retro.endDate }}</p>
            <span v-if="dayList.length > 1" class="retro-days" role="group" aria-label="날짜 고르기">
              <button v-for="day in dayList" :key="day" type="button" class="chip chip--sm" :aria-pressed="selectedDay === day" @click="changeDay(day)">{{ day }}일차</button>
            </span>
          </div>
          <p v-if="errorMessage" class="t-cap">{{ errorMessage }}</p>
        </div>
        <div class="retro-scroll">
        <div class="stats">
       <!-- 다녀온 곳 수 / 전체 장소 수 -->
       <div v-if="retro" class="stat"><b>{{ retro.visited }}/{{ retro.stops.length }}</b><span class="t-cap">다녀온 곳</span></div>
          <div class="stat"><b>{{ movedText }}</b><span class="t-cap">이동 거리</span></div>


          <div class="stat"><b>{{  skippedCount }}</b><span class="t-cap">건너 뛴 곳</span></div>
        </div>

        <!-- 다녀온 길: 장소 줄과 이동 줄을 시간 순서대로 (timeline) -->
        <section v-if="timeline.length > 0" class="card card--pad" aria-labelledby="tl-h">
          <h2 id="tl-h" class="t-sub">다녀온 길</h2>
          <div class="retro-tl">
            <!-- template: 화면에 안 그려지는 묶음. 한 번 돌 때 장소 줄 또는 이동 줄 중 하나를 고르려고 씀 -->
            <template v-for="(row, index) in timeline" :key="index">
              <!-- 장소 줄 -->
              <div v-if="row.type === 'place'" :id="'tl-row-' + row.no" class="retro-tl__place" :class="{ 'is-skipped': row.skipped, 'is-sel': infoNo === row.no, 'is-now': currentNo === row.no }"
@click="toggleInfo(row.no); moveToPlace(row.no)">
                <span class="retro-tl__num">{{ row.no }}</span>
                <span class="retro-tl__body">
                  <b>{{ row.name }}</b>
                  <span class="t-cap tn">{{ row.timeText }}</span>
                  <!-- 이 장소에서 찍은 사진 (건너뛴 곳은 안 보여 줌). @click.stop: 줄 클릭(지도 이동)까지 같이 눌리지 않게 -->
                  <span v-if="!row.skipped" class="retro-tl__photos">
                    <button v-for="photo in photosOf(row.stopId)" :key="photo.id" type="button" class="retro-tl__thumb" :aria-label="photo.takenAt + ' 사진 크게 보기'" @click.stop="openPhoto(photo)">
                      <video v-if="photo.mediaType === 'video'" :src="photo.url + '#t=0.1'" preload="metadata" muted playsinline></video>
                      <img v-else :src="photo.url" alt="" loading="lazy" />
                      <span v-if="photo.mediaType === 'video'" class="retro-tl__dur tn">{{ toDurationText(photo.durationSec) }}</span>
                    </button>
                    <span v-if="photosOf(row.stopId).length === 0" class="retro-tl__nophoto">사진 없음</span>
                  </span>
                </span>
                <span v-if="row.skipped" class="retro-tl__tag">건너뜀</span>
              </div>
              <!-- 이동 줄 -->
              <div v-else class="retro-tl__move">
                <span class="retro-tl__icon"><span><i class="ic i-walk" aria-hidden="true"></i></span></span>
                <span class="retro-tl__body">
                  <span class="t-cap">{{ row.text  }} · <span class="tn">{{ row.timeText }}</span></span>
                  <!-- 이동 중에 찍은 사진 (있을 때만) -->
                  <span v-if="photosOfMove(row).length > 0" class="retro-tl__photos">
                    <button v-for="photo in photosOfMove(row)" :key="photo.id" type="button" class="retro-tl__thumb" :aria-label="photo.takenAt + ' 사진 크게 보기'" @click="openPhoto(photo)">
                      <video v-if="photo.mediaType === 'video'" :src="photo.url + '#t=0.1'" preload="metadata" muted playsinline></video>
                      <img v-else :src="photo.url" alt="" loading="lazy" />
                      <span v-if="photo.mediaType === 'video'" class="retro-tl__dur tn">{{ toDurationText(photo.durationSec) }}</span>
                    </button>
                  </span>
                </span>
              </div>
            </template>
          </div>
        </section>
        </div>
      </BottomSheet>

      <div class="cta">
        <div class="cta__row">
          <RouterLink to="/album" class="btn btn--secondary btn--lg"><i class="ic i-camera" aria-hidden="true"></i>사진 담기</RouterLink>
          <RouterLink to="/theme?id=alley" class="btn btn--primary btn--lg"><i class="ic i-refresh" aria-hidden="true"></i>이 코스로 다시</RouterLink>
        </div>
      </div>

      <!-- 사진 크게 보기 (◁ ▷로 이 여행 사진 전체를 넘겨 봄) -->
      <PhotoViewer v-if="viewerStart !== null" :photos="photos" :start="viewerStart" @close="viewerStart = null" />
    </div>
  </div>
</template>

<style>
.scr-Retro .retro-map{position:absolute;left:0;right:0;top:0;bottom:0;overflow:hidden}
/* 정보 시트: 처음엔 지도 아래(340px)부터. 안쪽만 스크롤 (TripPage의 trip-sheet와 같은 모양) */
.scr-Retro .retro-sheet{top:340px;padding:8px 0 0;gap:10px}
.scr-Retro .retro-sheet__head{padding:0 16px}
.scr-Retro .retro-scroll{position:relative;flex:1;min-height:0;overflow-y:auto;padding:2px 16px 112px;display:flex;flex-direction:column;gap:14px}
.scr-Retro .retro-scroll>*{flex-shrink:0}
.scr-Retro .map{z-index: 0 }
/* 날짜 줄: 기간 글자 + 날짜 버튼 (1일차 2일차 …). 날이 많으면 버튼만 옆으로 밀어서 봄 */
.scr-Retro .retro-dayrow{display:flex;align-items:center;justify-content:space-between;gap:8px;min-height:32px}
.scr-Retro .retro-dayrow .t-cap{flex:none;margin:0}
.scr-Retro .retro-days{display:flex;gap:6px;min-width:0;overflow-x:auto;scrollbar-width:none}
.scr-Retro .retro-days .chip{flex:none}
.scr-Retro .retro-play{background:var(--navy);color:#fff;border-radius:50%;width:44px;height:44px}
.scr-Retro .retro-speed{flex:none;min-width:40px;height:28px;padding:0 8px;border-radius:999px;border:1px solid var(--line);background:var(--surface);color:var(--navy);font:inherit;font-size:13px;font-weight:700;font-variant-numeric:tabular-nums;cursor:pointer}
.scr-Retro .retro-pay{color:var(--coral-ink);font-weight:600}
/* 다녀온 길: 왼쪽 세로 점선 위에 번호 원과 이동 아이콘을 올림 */
/* 지도 핀: 건너뛴 곳은 타임라인 번호 원과 같은 회색 점선 */
.scr-Retro .pin.pin--skipped{background:#fff;color:var(--ink-subtle);border:1.5px dashed var(--line-strong)}
.scr-Retro .retro-tl{position:relative;display:flex;flex-direction:column;margin-top:6px}
.scr-Retro .retro-tl::before{content:"";position:absolute;left:13px;top:16px;bottom:16px;border-left:2px dashed var(--line-strong);opacity:.5}
.scr-Retro .retro-tl__place{display:flex;align-items:center;gap:12px;min-height:52px;margin:0 -8px;padding:0 8px;border-radius:10px;cursor:pointer;transition:background-color .2s}
.scr-Retro .retro-tl__place.is-sel{background:var(--navy-soft)}
/* 재생 중 지금 머물고 있는 장소: 번호 원을 주황으로, 바깥에 옅은 테두리 */
.scr-Retro .retro-tl__place.is-now .retro-tl__num{background:var(--coral);box-shadow:0 0 0 4px var(--coral-soft)}
.scr-Retro .retro-tl__place.is-now b{color:var(--coral-ink)}
.scr-Retro .retro-tl__num{position:relative;z-index:1;width:28px;height:28px;flex:none;border-radius:50%;background:var(--navy);color:#fff;font-size:13px;font-weight:700;display:flex;align-items:center;justify-content:center;border:2.5px solid #fff;box-shadow:0 1px 4px rgba(24,33,58,.25)}
.scr-Retro .retro-tl__body{flex:1;display:flex;flex-direction:column}
.scr-Retro .retro-tl__tag{font-size:12px;font-weight:600;color:var(--ink-subtle);background:var(--surface-sunken);padding:2px 8px;border-radius:999px}
.scr-Retro .retro-tl__place.is-skipped .retro-tl__num{background:#fff;color:var(--ink-subtle);border:1.5px dashed var(--line-strong);box-shadow:none}
.scr-Retro .retro-tl__place.is-skipped b{color:var(--ink-subtle)}
.scr-Retro .retro-tl__move{display:flex;align-items:center;gap:12px;min-height:32px}
.scr-Retro .retro-barwrap{position:relative;display:block}
.scr-Retro .retro-barwrap .bar{display:block}
.scr-Retro .retro-tick{position:absolute;top:-4px;width:3px;height:16px;margin-left:-1.5px;border-radius:2px;background:var(--coral);pointer-events:none}
.scr-Retro .retro-float{position:absolute;z-index:5;width:126px;padding:4px 4px 6px;border:0;border-radius:12px;background:#fff;box-shadow:0 8px 20px rgba(24,33,58,.3);transform:translate(-50%,-100%);display:flex;flex-direction:column;gap:4px;align-items:center;cursor:pointer;font:inherit;animation:retro-pop .25s ease}
.scr-Retro .retro-float::after{content:"";position:absolute;bottom:-5px;left:50%;margin-left:-5px;width:10px;height:10px;background:#fff;transform:rotate(45deg)}
.scr-Retro .retro-float__img{width:118px;height:84px;border-radius:9px;overflow:hidden;background:var(--line)}
.scr-Retro .retro-float__img img,.scr-Retro .retro-float__img video{width:100%;height:100%;object-fit:cover;display:block;pointer-events:none}
.scr-Retro .retro-float__cap{font-size:11.5px;line-height:15px;color:var(--ink-muted);font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:118px}
@keyframes retro-pop{from{opacity:0;transform:translate(-50%,-90%) scale(.9)}to{opacity:1;transform:translate(-50%,-100%) scale(1)}}
.scr-Retro .retro-tl__photos{display:flex;flex-wrap:wrap;gap:4px;margin:4px 0 6px}
.scr-Retro .retro-tl__thumb{position:relative;width:48px;height:48px;padding:0;border:0;border-radius:8px;overflow:hidden;background:var(--line);cursor:pointer;flex:none}
.scr-Retro .retro-tl__thumb img,.scr-Retro .retro-tl__thumb video{width:100%;height:100%;object-fit:cover;display:block;pointer-events:none}
.scr-Retro .retro-tl__dur{position:absolute;right:2px;bottom:2px;padding:0 4px;border-radius:999px;background:rgba(0,0,0,.6);color:#fff;font-size:10px;line-height:14px}
.scr-Retro .retro-tl__nophoto{font-size:12px;color:var(--ink-subtle);border:1px dashed var(--line-strong);border-radius:8px;padding:2px 8px}
.scr-Retro .retro-tl__icon{position:relative;z-index:1;width:28px;flex:none;display:flex;justify-content:center}
.scr-Retro .retro-tl__icon>span{width:22px;height:22px;border-radius:50%;background:var(--surface-raised);border:1px solid var(--line);display:flex;align-items:center;justify-content:center;color:var(--ink-subtle)}
.scr-Retro .retro-tl__icon .ic{width:14px;height:14px}
.scr-Retro .stats{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}
.scr-Retro .stat{padding:12px;border-radius:var(--radius-lg);background:var(--surface-raised);border:1px solid var(--line);display:flex;flex-direction:column;gap:2px}
.scr-Retro .stat b{font-size:20px;line-height:28px;font-weight:700;font-variant-numeric:tabular-nums}
.scr-Retro .player{display:flex;align-items:center;gap:12px;padding:10px 12px;border-radius:var(--radius-full);background:var(--surface-raised);box-shadow:var(--shadow-card)}
</style>
