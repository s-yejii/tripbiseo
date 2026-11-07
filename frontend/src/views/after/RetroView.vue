<script setup>
// S19-a 지난 여행 다시보기 (⑤): 다녀온 동선을 재생하고, 핀을 누르면 시간·결제 메모를 봐요. 계획과 실제도 비교해요
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { get, post, put } from '@/api/http'
import { toTime, toMinutes, toDuration, toWon } from '@/utils/time'
import { showToast } from '@/utils/toast' // 아래에 잠깐 뜨는 알림
import { goBack } from '@/utils/back' // 알림에서 들어왔으면(?back=) 알림함으로
import BottomSheet from '@/components/common/BottomSheet.vue' // 끌어서 내리고 올리는 아래 시트 (다른 화면과 같은 것)
import PhotoViewer from '@/components/after/PhotoViewer.vue' // 사진 크게 보기 (앨범과 같은 것)
import { toDurationText } from '@/utils/photoUpload' // 영상 길이 12 → "0:12"


const visited = ref([

])
const trackPoints = ref([]) // gps 점 목록 drawMap에서 채워짐
const extraPoints = ref([]) // 계획 없이 머문 곳 핀 {x, y, kind, name, label}. toScreenPoints에서 채워짐

const progress = ref(0) // 재생 위치 (%)
const playing = ref(false)
const speed = ref(1) // 재생 속도(1,2,4배)
const PLAY_STEP_1X = 0.375 // 1배속일 때 90ms마다 나아가는 % 100%까지 약 24초
const infoNo = ref(null)
const selectedDay  = ref(1)
let timer = null

const routePoints = computed(() => trackPoints.value.map((v) => v.x + ',' + v.y).join(' ')) // 다녀온 곳 좌표를 "x,y x,y x,y" 문자열로 만들어서 <polyline>에 넣음
// 열린 정보창의 핀. 계획 장소 핀(번호 1, 2, …)을 먼저 찾고, 없으면 머문 곳 핀('x0', 'x1', …)
const info = computed(() => {
  const place = visited.value.find((v) => v.no === infoNo.value)
  if (place !== undefined) return place
  return extraPoints.value.find((v) => v.no === infoNo.value)
})
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
  // 첫 장소에 오기 전에 머문 곳 (예: 아침 카페)은 맨 앞에
  for (const extra of extrasBetween(0, firstArriveMinutes(stops))) rows.push(toExtraRow(extra))

  let lastVisited  = null // 바로 앞에 실제로 간 장소, 건너 뛴 곳은 지나치고 이걸로 이동 줄을 이음
  for(let i = 0; i < stops.length; i++) {
    // 실제로 간 곳이면: 앞에 간 곳 → 여기 이동 줄을 먼저 넣고, 이제 여기가 "바로 앞에 간 곳"
    if(stops[i].actualArrive !== null) {
      if(lastVisited !== null) {
        pushMoveWithExtras(rows, lastVisited, stops[i]) // 이동 줄. 사이에 머문 곳이 있으면 잘라서 끼워 넣음
      }
      lastVisited = stops[i];
    }
      rows.push({
      type: 'place',
      no : i + 1,
      stopId: stops[i].id, // 사진을 고를 때 씀
      name : stops[i].name,
      timeText : toTimeRange(stops[i]),
      skipped: stops[i].state === 'skipped',
    })
  }

  // 마지막 장소를 떠난 뒤에 머문 곳 (예: 숙소로 보이는 곳)은 맨 뒤에
  if (lastVisited !== null && lastVisited.actualStay !== null) {
    const lastLeave = toMinutes(lastVisited.actualArrive) + lastVisited.actualStay
    for (const extra of extrasBetween(lastLeave, MINUTES_PER_DAY)) rows.push(toExtraRow(extra))
  }
  return rows
})

// ============================== 계획 없이 머문 곳 (11/07, 서버가 GPS로 찾아 extraStops로 줌)
const MINUTES_PER_DAY = 24 * 60 // 하루 = 1440분

// 고른 날의 머문 곳만. 예: 2일차 → [카페 11:15 45분]
const dayExtras = computed(() => {
  const list = []
  if (retro.value === null) return list
  for (const extra of retro.value.extraStops) {
    if (extra.day === selectedDay.value) list.push(extra)
  }
  return list
})

// from ≤ 온 시각 < to 인 머문 곳. 예: (660, 750) = 11:00 ~ 12:30 → [카페 11:15]
function extrasBetween(from, to) {
  const list = []
  for (const extra of dayExtras.value) {
    const arrive = toMinutes(extra.arrive) // "11:15" → 675
    if (from <= arrive && arrive < to) list.push(extra)
  }
  return list
}


// 그날 처음 실제로 도착한 시각(분). 간 곳이 없으면 하루 끝(1440) → 머문 곳이 전부 맨 앞에 나옴
function firstArriveMinutes(stops) {
  for (const stop of stops) {
    if (stop.actualArrive !== null) return toMinutes(stop.actualArrive)
  }
  return MINUTES_PER_DAY
}

// 머문 곳 줄. 예: { type: 'extra', kind: 'visit', timeText: '11:15 ~ 12:00', stayText: '45분' }
// day·leave·arrive는 이동 줄과 같은 이름이라 photosOfMove로 그 사이 사진을 그대로 고를 수 있음
function toExtraRow(extra) {
  const arrive = toMinutes(extra.arrive)
  const leave = arrive + extra.stay
  return { type: 'extra', no: 'x' + dayExtras.value.indexOf(extra), // 핀 번호와 같음 ('x0', 'x1', …)
    kind: extra.kind, name: extra.name, lat: extra.lat, lng: extra.lng, day: extra.day, arrive: arrive, leave: leave,
    timeText: extra.arrive + ' ~ ' + toLeaveText(leave), stayText: toDuration(extra.stay) }
}

// 떠난 시각 글자. 자정을 넘기면 "다음 날 09:30"
function toLeaveText(minutes) {
  if (minutes >= MINUTES_PER_DAY) return '다음 날 ' + toTime(minutes - MINUTES_PER_DAY)
  return toTime(minutes)
}

// 이동 줄 한 토막. 예: (660, 675) → { type: 'move', text: '이동 15분', timeText: '11:00 ~ 11:15' }
function toMoveSegment(day, leave, arrive) {
  return { type: 'move', text: '이동 ' + (arrive - leave) + '분', timeText: toTime(leave) + ' ~ ' + toTime(arrive),
    day: day, leave: leave, arrive: arrive }
}

// 두 장소 사이 이동 줄을 넣음. 사이에 머문 곳이 있으면 이동 줄을 잘라 끼워 넣음
// 예: 명동(11:00 떠남) → 남대문(12:30 도착), 카페 11:15~12:00 → [이동 15분] [카페] [이동 30분]
function pushMoveWithExtras(rows, from, to) {
  const move = toMoveRow(from, to)
  if (move === null) return // 기록이 없어 계산 못 하면 안 넣음
  let leave = move.leave // 지금 토막이 시작하는 시각
  for (const extra of extrasBetween(move.leave, move.arrive)) {
    const extraRow = toExtraRow(extra)
    rows.push(toMoveSegment(move.day, leave, extraRow.arrive)) // 머문 곳 앞 이동 토막: 지금 토막 시작 → 머문 곳 도착
    rows.push(extraRow)                                           // 머문 곳 줄
    leave = extraRow.leave // 다음 토막은 머문 곳을 떠난 시각부터
  }
  rows.push(toMoveSegment(move.day, leave, move.arrive)) // 마지막 토막 (머문 곳이 없으면 이게 원래 이동 줄 하나)
}

function toTimeRange(place) { // 실제 시각 ~ 떠난 시각 글자 : 예 : 13:00에 와서 80분 -> 13:00 ~ 14:20
  if(place.actualArrive === null) return '기록 없음' // 안 간 곳
  if(place.actualStay === null) return place.actualArrive + ' 떠난 시간이 없어요'
  const leave = toMinutes(place.actualArrive) + place.actualStay

  return place.actualArrive + ' ~ ' + toLeaveText(leave) // 도착 + 머문 시간. 숙소처럼 자정을 넘기면 "다음 날 09:30"
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
  loadPayments()
  loadCompanions()
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

// ============================== 결제 내역 (11/07, GET·POST /api/trips/{id}/payments + 카드 승인내역 목업)
const payments = ref([])       // 이 여행 결제 전부, 결제 순
const cardList = ref(null)     // "카드 내역 가져오기" 창의 목록. null이면 창 닫힘
const checkedCards = ref([])   // 가져오려고 체크한 카드 내역 번호 (목록 순서)
const importing = ref(false)   // 가져오는 중이면 버튼 막기

// 결제 내역 불러오기. 실패해도 회고는 그대로 보이게 따로 try
async function loadPayments() {
  try {
    payments.value = await get('/trips/' + route.query.id + '/payments')
  } catch {
    payments.value = []
  }
}

// 고른 날 결제만
const dayPayments = computed(() => {
  const list = []
  for (const payment of payments.value) {
    if (payment.day === selectedDay.value) list.push(payment)
  }
  return list
})

// 고른 날 결제 합계
const dayPaymentTotal = computed(() => {
  let total = 0
  for (const payment of dayPayments.value) total += payment.amount
  return total
})

// 이 결제가 이 핀의 것인가. 계획 장소 핀은 일정 칸 번호(stopId)로, 머문 곳 핀은 그 시간 안의 (일정 칸 없는) 결제로
function isPaymentOfPin(payment, pin) {
  if (pin.stopId !== undefined) return payment.stopId === pin.stopId
  if (payment.stopId !== null) return false
  const minutes = toMinutes(payment.paidAt)
  return pin.arriveMinutes <= minutes && minutes < pin.leaveMinutes
}

// 핀 정보창에 쓸 결제 글자. 예: "10:20 명동 45,000원". 없으면 빈 글자
function payTextOf(pin) {
  const lines = []
  for (const payment of dayPayments.value) {
    if (isPaymentOfPin(payment, pin)) lines.push(payment.paidAt + ' ' + payment.storeName + ' ' + toWon(payment.amount))
  }
  return lines.join('\n') // 줄바꿈으로 이어 붙임
}

// 결제 줄에 붙일 장소 이름: 일정 칸 → 그 장소, 머문 곳 시간 안 → 머문 곳, 아니면 이동 중
function placeOfPayment(payment) {
  for (const pin of visited.value) {
    if (isPaymentOfPin(payment, pin)) return pin.name
  }
  for (const pin of extraPoints.value) {
    if (isPaymentOfPin(payment, pin)) return pin.name
  }
  return '이동 중'
}

// "카드 내역 가져오기" 창 열기. 아직 안 가져온 건 미리 체크
async function openCardList() {
  try {
    const list = await get('/trips/' + route.query.id + '/card-transactions')
    checkedCards.value = []
    for (let i = 0; i < list.length; i++) {
      if (!list[i].imported) checkedCards.value.push(i)
    }
    cardList.value = list
  } catch (e) {
    showToast(e.message)
  }
}

// 체크 칸을 누르면 넣었다 뺐다
function toggleCard(i) {
  const at = checkedCards.value.indexOf(i)
  if (at === -1) checkedCards.value.push(i)
  else checkedCards.value.splice(at, 1) // splice: 그 자리에서 1개 지우기
}

// 체크한 카드 내역을 결제 내역으로 저장 (하나씩 POST, source: card)
async function importCards() {
  importing.value = true
  let count = 0
  try {
    for (const i of checkedCards.value) {
      const card = cardList.value[i]
      await post('/trips/' + route.query.id + '/payments', { paidAt: card.paidAt, storeName: card.storeName, amount: card.amount, source: 'card' })
      count++
    }
    showToast(count + '건 가져왔어요')
    cardList.value = null
  } catch (e) {
    showToast(e.message)
  }
  await loadPayments()
  importing.value = false
}

// ============================== 정산 (11/07): 결제마다 낸 사람 → 여행 전체를 인원으로 나눠 누가 누구에게 얼마
// 같이 간 사람 (GET /api/trips/{id}/companions). 처음 열면 서버가 여행 인원만큼 '나', '동행 1' … 을 만들어 줌
const companions = ref([])        // [{ id, name, friendMemberId, sortNo }] sortNo 0이 '나'
const companionEdit = ref(null)   // 이름 칸을 연 상태 { id, name }. id가 null이면 새로 추가. null이면 닫힘
const savingCompanion = ref(false)

async function loadCompanions() {
  try {
    companions.value = await get('/trips/' + route.query.id + '/companions')
  } catch {
    companions.value = []
  }
}

// 낸 사람 이름 목록. 예: ['나', '지은', '엄마']. 못 불러왔으면 ['나']
const payerNames = computed(() => {
  const names = []
  for (const companion of companions.value) names.push(companion.name)
  if (names.length === 0) names.push('나')
  return names
})

// [+ 추가] → 빈 이름 칸 열기
function startAddCompanion() {
  companionEdit.value = { id: null, name: '' }
}

// 사람 칩 누르기 → 그 이름으로 칸 열기 ('나'는 못 바꿈)
function startRenameCompanion(companion) {
  if (companion.sortNo === 0) return
  companionEdit.value = { id: companion.id, name: companion.name }
}

// 저장: 새로면 POST, 있던 사람이면 PUT. 이름이 바뀌면 그 사람이 낸 결제 이름도 서버가 같이 바꿔서 결제를 다시 불러옴
async function saveCompanion() {
  const edit = companionEdit.value
  savingCompanion.value = true
  try {
    if (edit.id === null) {
      companions.value = await post('/trips/' + route.query.id + '/companions', { name: edit.name })
    } else {
      companions.value = await put('/trips/' + route.query.id + '/companions/' + edit.id, { name: edit.name })
      await loadPayments()
    }
    companionEdit.value = null
  } catch (e) {
    showToast(e.message)
  }
  savingCompanion.value = false
}

// 낸 사람 칩을 누르면 다음 사람으로 (나 → 동행 1 → 동행 2 → 나 …) 바꾸고 서버에 저장
async function cyclePayer(payment) {
  const names = payerNames.value
  const next = names[(names.indexOf(payment.payer) + 1) % names.length] // %: 끝을 넘으면 처음으로
  try {
    const changed = await put('/trips/' + route.query.id + '/payments/' + payment.id + '/payer', { payer: next })
    payment.payer = changed.payer
  } catch (e) {
    showToast(e.message)
  }
}

// 정산 결과. 인원이 1명이거나 결제가 없으면 null
// 예: 2명, 합계 54,800원, 나 45,000 / 동행 1 9,800 → 1인당 27,400 → 동행 1이 나에게 17,600원
const settlement = computed(() => {
  const names = payerNames.value
  if (names.length < 2 || payments.value.length === 0) return null
  let total = 0
  const paid = {} // 이름 → 낸 돈
  for (const name of names) paid[name] = 0
  for (const payment of payments.value) {
    total += payment.amount
    if (paid[payment.payer] !== undefined) paid[payment.payer] += payment.amount
  }
  const share = Math.floor(total / names.length / 10) * 10 // 1인당 (10원 단위로 내림)
  const people = [] // { name, paid, balance }. balance = 낸 돈 − 낼 돈 (+면 받을 사람, −면 보낼 사람)
  for (let i = 0; i < names.length; i++) {
    const should = i === 0 ? total - share * (names.length - 1) : share // 10원 단위로 남는 돈은 '나'가 냄
    people.push({ name: names[i], paid: paid[names[i]], balance: paid[names[i]] - should })
  }
  return { total: total, share: share, people: people, transfers: toTransfers(people) }
})

// 보낼 사람(balance < 0)이 받을 사람(balance > 0)에게 차례로 보내기. 예: [{from:'동행 1', to:'나', amount:17600}]
function toTransfers(people) {
  const receivers = [] // { name, left: 아직 받을 돈 }
  for (const person of people) {
    if (person.balance > 0) receivers.push({ name: person.name, left: person.balance })
  }
  const transfers = []
  for (const person of people) {
    let owe = -person.balance // 보낼 돈 (받을 사람이면 0 이하라 아래 for를 바로 나감)
    for (const receiver of receivers) {
      if (owe <= 0) break
      if (receiver.left <= 0) continue
      const amount = Math.min(owe, receiver.left)
      transfers.push({ from: person.name, to: receiver.name, amount: amount })
      owe -= amount
      receiver.left -= amount
    }
  }
  return transfers
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
  kakao.maps.event.addListener(map, 'bounds_changed', toScreenPoints) // addListener: 지도에 일이 생기면(bounds_changed = 끌기·확대로 보이는 범위가 바뀜) 뒤의 함수를 불러 줌

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

toScreenPoints() 
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

// 핀·GPS 점의 화면 좌표(x, y)를 지금 지도 기준으로 다시 계산. 지도를 끌거나 확대할 때마다 불림
function toScreenPoints() {
  const map = selectedmap
    const points = [] // 장소별 핀 정보를 담을 목록
  for (const place of dayStops.value){
    const latLng = new kakao.maps.LatLng(place.lat , place.lng)
    const point = map.getProjection().containerPointFromCoords(latLng) // 방금 받은 지도좌표를 X,Y 화면 좌표로 바꿔줌
    points.push({ no : points.length + 1, x : point.x , y : point.y, name : place.name , time : toTimeRange(place), stopId: place.id, skipped : place.state === 'skipped' }) // point.length : 핀 번호에 번호붙이기
  }
  visited.value=points // 화면에 보여줄 핀 좌표를 visited에 넣음 vistied : 화면에 실제로 방문한 잠소를 담는 ref
  const trackList = [] // gps 점마다 {x,y, time}을 담을 목록
  for (const gps of dayTrack.value) { // gps: 실제로 이동한 기록
    const latLng = new kakao.maps.LatLng(gps.lat , gps.lng) // gps 좌표를 실제 좌표로 바꾸고 카카오 맵에 넣어줌
    const point = map.getProjection().containerPointFromCoords(latLng) // 화면 좌푤 ㅗ마바꿔줌
    trackList.push({ x: point.x , y : point.y , time : gps.time }) 
    
  }
  trackPoints.value = trackList // 화면에 보여 줄 GPS 점 좌표

  // 계획 없이 머문 곳 핀 좌표. 지도를 끌 때마다 같이 다시 계산됨
  const extraList = []
  for (const extra of dayExtras.value) { // 고른 날의 머문 곳
    const latLng = new kakao.maps.LatLng(extra.lat, extra.lng)
    const point = map.getProjection().containerPointFromCoords(latLng)
    const baseName = extra.kind === 'lodging' ? '숙소로 보이는 곳' : '계획 없이 머문 곳'
    extraList.push({ no: 'x' + extraList.length, x: point.x, y: point.y, kind: extra.kind,
      placeName: extra.name,                 // 핀 옆 작은 이름 (못 찾았으면 null)
      name: extra.name || baseName,          // 정보창 제목
      time: extra.arrive + ' ~ ' + toLeaveText(toMinutes(extra.arrive) + extra.stay), // 정보창 시각
      arriveMinutes: toMinutes(extra.arrive), leaveMinutes: toMinutes(extra.arrive) + extra.stay, // 이 시간 안의 결제를 붙임
      label: baseName + ' ' + extra.arrive })
  }
  extraPoints.value = extraList // 화면에 보여 줄 머문 곳 핀
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

// 재생 위치를 그 시각(분)으로 옮김. 예: 11:15(675) → (675 − 545) / (820 − 545) × 100 ≈ 47%
function moveToMinutes(minutes) {
  if (endMinutes.value === startMinutes.value) return
  stop()
  const percent = (minutes - startMinutes.value) / (endMinutes.value - startMinutes.value) * 100
  progress.value = Math.max(0, Math.min(100, percent)) // 0 ~ 100 밖으로 안 나가게
}

// ============================== 줄을 누르면 지도를 그 마커로 확대해 가운데로 (11/07)
const FOCUS_LEVEL = 3              // 이 단계까지 확대 (숫자가 작을수록 가까이). 이미 더 가까우면 그대로
const VISIBLE_CENTER_Y_OPEN = 190  // 시트 펼침: 지도가 보이는 칸(위 버튼 120 ~ 재생 막대 262)의 가운데
const VISIBLE_CENTER_Y_CLOSED = 375 // 시트 내림: 보이는 칸(120 ~ 630)의 가운데

// 장소 줄 번호 → 그 장소로 지도 맞추기
function focusPlace(no) {
  const place = dayStops.value[no - 1]
  focusOn(place.lat, place.lng)
}

// 좌표를 지도의 "보이는 칸" 가운데로. 지도 진짜 가운데(844 / 2 = 422)는 시트에 가려서, 마커가 보이는 칸 가운데에 오게 가운데 점을 아래로 내림
function focusOn(lat, lng) {
  if (selectedmap === null) return
  const map = selectedmap
  if (map.getLevel() > FOCUS_LEVEL) map.setLevel(FOCUS_LEVEL) // setLevel: 확대 단계 바꾸기
  let targetY = VISIBLE_CENTER_Y_OPEN
  const sheet = document.querySelector('.retro-sheet')
  if (sheet !== null && sheet.classList.contains('is-collapsed')) targetY = VISIBLE_CENTER_Y_CLOSED
  const projection = map.getProjection()
  const marker = projection.containerPointFromCoords(new kakao.maps.LatLng(lat, lng)) // 마커의 화면 x, y
  const center = new kakao.maps.Point(marker.x, marker.y + (MAP_HEIGHT / 2 - targetY)) // 가운데로 삼을 화면 점
  map.panTo(projection.coordsFromContainerPoint(center)) // panTo: 부드럽게 그 좌표로 옮김
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
      <!-- 핀·점·사진 카드 판: 판 자체는 누름을 지도로 흘려보내고(끌기·확대), 핀·카드만 눌림 -->
      <div class="retro-map retro-layer">
        <button v-for="v in visited" :key="v.no" type="button" class="pin pin--visited" :class="{ 'pin--sel': infoNo === v.no, 'pin--skipped' : v.skipped }" :style="{ left: v.x + 'px', top: v.y + 'px' }" :aria-label="v.no + ' ' + v.name" @click="toggleInfo(v.no); scrollToRow(v.no)">
          {{ v.no }}<span v-if="payTextOf(v)" class="paydot" aria-hidden="true"><i class="ic i-won"></i></span>
        </button>
        <!-- 계획 없이 머문 곳 핀 (코랄): 숙소로 보이는 곳은 집, 나머지는 핀 모양. 이름이 있으면 핀 옆에 작게 -->
        <button v-for="p in extraPoints" :key="p.no" type="button" class="retro-xpin" :class="{ 'is-sel': infoNo === p.no }" :style="{ left: p.x + 'px', top: p.y + 'px' }" :aria-label="p.label" @click="toggleInfo(p.no); scrollToRow(p.no)">
          <i class="ic" :class="p.kind === 'lodging' ? 'i-home' : 'i-pin'" aria-hidden="true"></i>
          <span v-if="p.placeName" class="retro-xpin__name">{{ p.placeName }}</span>
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
          <template v-if="payTextOf(info)">
            <span class="t-cap tn retro-pay"><i class="ic ic--s i-won" aria-hidden="true"></i> 결제</span>
            <pre>{{ payTextOf(info) }}</pre>
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
@click="toggleInfo(row.no); moveToPlace(row.no); focusPlace(row.no)">
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
              <!-- 계획 없이 머문 곳 줄 (GPS로 찾음). 숙소로 보이는 곳이면 집 모양 -->
              <div v-else-if="row.type === 'extra'" :id="'tl-row-' + row.no" class="retro-tl__extra" :class="{ 'is-sel': infoNo === row.no }" @click="toggleInfo(row.no); moveToMinutes(row.arrive); focusOn(row.lat, row.lng)">
                <span class="retro-tl__icon"><span><i class="ic" :class="row.kind === 'lodging' ? 'i-home' : 'i-pin'" aria-hidden="true"></i></span></span>
                <span class="retro-tl__body">
                  <!-- 이름을 찾았으면 이름, 못 찾았으면 "계획 없이 머문 곳" -->
                  <b>{{ row.name || (row.kind === 'lodging' ? '숙소로 보이는 곳' : '계획 없이 머문 곳') }}</b>
                  <span class="t-cap tn">{{ row.timeText }} · {{ row.stayText }}</span>
                  <!-- 머무는 동안 찍은 사진 (이동 줄과 같은 방식: 날짜·시간으로 고름) -->
                  <span v-if="photosOfMove(row).length > 0" class="retro-tl__photos">
                    <button v-for="photo in photosOfMove(row)" :key="photo.id" type="button" class="retro-tl__thumb" :aria-label="photo.takenAt + ' 사진 크게 보기'" @click="openPhoto(photo)">
                      <video v-if="photo.mediaType === 'video'" :src="photo.url + '#t=0.1'" preload="metadata" muted playsinline></video>
                      <img v-else :src="photo.url" alt="" loading="lazy" />
                      <span v-if="photo.mediaType === 'video'" class="retro-tl__dur tn">{{ toDurationText(photo.durationSec) }}</span>
                    </button>
                  </span>
                </span>
                <span class="retro-tl__tag retro-tl__tag--extra">{{ row.kind === 'lodging' ? '숙소?' : '계획 밖' }}</span>
              </div>
              <!-- 이동 줄 -->
              <div v-else-if="row.type === 'move'" class="retro-tl__move">
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

        <!-- 결제 내역: 고른 날 결제 + 합계. 카드 내역(지금은 목업)에서 가져오기 -->
        <section class="card card--pad" aria-labelledby="pay-h">
          <div class="retro-payhead">
            <h2 id="pay-h" class="t-sub">결제 내역</h2>
            <button type="button" class="btn btn--secondary btn--sm" @click="openCardList"><i class="ic ic--s i-receipt" aria-hidden="true"></i>카드 내역 가져오기</button>
          </div>
          <p v-if="dayPayments.length === 0" class="t-cap">이 날 결제 내역이 없어요</p>
          <ul v-else class="retro-paylist">
            <li v-for="payment in dayPayments" :key="payment.id">
              <span class="tn retro-paylist__time">{{ payment.paidAt }}</span>
              <span class="retro-paylist__body"><b>{{ payment.storeName }}</b><span class="t-cap">{{ placeOfPayment(payment) }}{{ payment.source === 'card' ? ' · 카드' : '' }}</span></span>
              <b class="tn">{{ toWon(payment.amount) }}</b>
              <!-- 낸 사람: 누를 때마다 다음 사람 (인원이 2명 이상일 때만) -->
              <button v-if="payerNames.length > 1" type="button" class="chip chip--sm retro-payer" :aria-label="'낸 사람 ' + payment.payer + ', 눌러서 바꾸기'" @click="cyclePayer(payment)">{{ payment.payer }}</button>
            </li>
          </ul>
          <p v-if="dayPayments.length > 0" class="retro-paytotal"><span>이 날 합계</span><b class="tn">{{ toWon(dayPaymentTotal) }}</b></p>

          <!-- 같이 간 사람: 칩을 누르면 이름 바꾸기('나' 빼고), [+ 추가]. 친구 기능(①)이 생기면 친구에서 고르기 -->
          <div class="retro-people">
            <p class="retro-people__title"><b>같이 간 사람</b><span class="t-cap">{{ payerNames.length }}명</span></p>
            <div class="retro-people__chips">
              <button v-for="companion in companions" :key="companion.id" type="button" class="chip chip--sm" :class="{ 'is-on': companion.sortNo === 0 }" :aria-label="companion.sortNo === 0 ? '나' : companion.name + ' 이름 바꾸기'" @click="startRenameCompanion(companion)">
                {{ companion.name }}<i v-if="companion.sortNo !== 0" class="ic ic--s i-edit" aria-hidden="true"></i>
              </button>
              <button type="button" class="chip chip--sm" @click="startAddCompanion"><i class="ic ic--s i-plus" aria-hidden="true"></i>추가</button>
              <button type="button" class="chip chip--sm" disabled title="친구 기능이 생기면 열려요"><i class="ic ic--s i-users" aria-hidden="true"></i>친구에서 고르기</button>
            </div>
            <!-- 이름 칸 (추가·바꾸기 같이 씀) -->
            <form v-if="companionEdit" class="retro-people__edit" @submit.prevent="saveCompanion">
              <input v-model="companionEdit.name" type="text" maxlength="20" :placeholder="companionEdit.id === null ? '같이 간 사람 이름' : '바꿀 이름'" aria-label="이름" />
              <button type="submit" class="btn btn--primary btn--sm" :disabled="savingCompanion || !companionEdit.name.trim()">{{ companionEdit.id === null ? '추가' : '바꾸기' }}</button>
              <button type="button" class="btn btn--secondary btn--sm" @click="companionEdit = null">취소</button>
            </form>
          </div>

          <!-- 정산: 여행 전체 (날짜 버튼과 상관없이) -->
          <div v-if="settlement" class="retro-settle">
            <p class="retro-settle__title"><b>여행 전체 정산</b><span class="t-cap tn">{{ payerNames.length }}명 · 합계 {{ toWon(settlement.total) }} · 1인당 {{ toWon(settlement.share) }}</span></p>
            <ul class="retro-settle__people">
              <li v-for="person in settlement.people" :key="person.name"><span>{{ person.name }}</span><span class="tn">낸 돈 {{ toWon(person.paid) }}</span></li>
            </ul>
            <p v-for="(transfer, n) in settlement.transfers" :key="n" class="retro-settle__send tn">
              <i class="ic ic--s i-send" aria-hidden="true"></i>{{ transfer.from }} → {{ transfer.to }} <b>{{ toWon(transfer.amount) }}</b>
            </p>
            <p v-if="settlement.transfers.length === 0" class="retro-settle__send">모두 똑같이 냈어요</p>
            <p class="t-cap">결제 줄의 사람 칩을 누르면 낸 사람이 바뀌어요 (나 → 지은 → … 순서)</p>
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

      <!-- 카드 내역 가져오기 (지금은 목업 → 나중에 CODEF). 이미 가져온 건 회색으로 막음 -->
      <div v-if="cardList !== null" class="retro-cards" role="dialog" aria-modal="true" aria-label="카드 내역 가져오기" @click.self="cardList = null">
        <div class="retro-cards__box">
          <div class="retro-cards__head">
            <b>카드 내역 가져오기</b>
            <button type="button" class="iconbtn" aria-label="닫기" @click="cardList = null"><i class="ic i-close" aria-hidden="true"></i></button>
          </div>
          <p class="t-cap">연습용 카드 내역이에요. 카드사 연결은 준비 중이에요</p>
          <p v-if="cardList.length === 0" class="t-cap">이 여행 기간의 카드 내역이 없어요</p>
          <ul class="retro-cards__list">
            <li v-for="(card, i) in cardList" :key="i">
              <label :class="{ 'is-done': card.imported }">
                <input type="checkbox" :checked="card.imported || checkedCards.includes(i)" :disabled="card.imported" @change="toggleCard(i)" />
                <span class="tn retro-cards__time">{{ card.day }}일차 {{ card.paidAt.substring(11) }}</span>
                <span class="retro-cards__name">{{ card.storeName }}<small v-if="card.imported"> · 가져옴</small></span>
                <b class="tn">{{ toWon(card.amount) }}</b>
              </label>
            </li>
          </ul>
          <button type="button" class="btn btn--primary btn--lg" :disabled="importing || checkedCards.length === 0" @click="importCards">
            {{ importing ? '가져오는 중…' : checkedCards.length + '건 가져오기' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style>
.scr-Retro .retro-map{position:absolute;left:0;right:0;top:0;bottom:0;overflow:hidden}
/* 핀 판이 지도를 덮어도 끌기·확대가 지도로 가게: 판은 안 눌리고, 안의 핀·정보창·사진 카드만 눌림 */
.scr-Retro .retro-layer{pointer-events:none}
.scr-Retro .retro-layer .pin,.scr-Retro .retro-layer .payinfo,.scr-Retro .retro-layer .retro-float{pointer-events:auto}
/* 계획 없이 머문 곳 핀: 계획 장소 핀(남색 숫자)과 구별되게 코랄 동그라미 + 아이콘 */
.scr-Retro .retro-xpin{position:absolute;width:24px;height:24px;margin:-12px 0 0 -12px;border-radius:50%;background:var(--coral);border:2px solid #fff;box-shadow:0 2px 6px rgba(24,33,58,.35);display:flex;align-items:center;justify-content:center;color:#fff}
.scr-Retro .retro-xpin .ic{width:13px;height:13px}
.scr-Retro button.retro-xpin{padding:0;cursor:pointer;font:inherit;pointer-events:auto}
.scr-Retro .retro-xpin.is-sel{transform:scale(1.25);box-shadow:0 0 0 4px rgba(232,93,63,.25),0 2px 6px rgba(24,33,58,.35)}
.scr-Retro .retro-tl__extra{cursor:pointer;margin:0 -8px;padding:0 8px;border-radius:10px}
.scr-Retro .retro-tl__extra.is-sel{background:var(--coral-soft,#FDECE8)}
.scr-Retro .retro-xpin__name{position:absolute;left:26px;top:50%;transform:translateY(-50%);white-space:nowrap;font-size:11px;font-weight:700;color:var(--ink);background:rgba(255,255,255,.92);padding:1px 6px;border-radius:6px}
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
/* 결제 내역 카드 */
.scr-Retro .retro-payhead{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-bottom:8px}
.scr-Retro .retro-paylist{list-style:none;margin:0;padding:0;display:flex;flex-direction:column}
.scr-Retro .retro-paylist li{display:flex;align-items:center;gap:10px;min-height:48px;border-bottom:1px solid var(--line)}
.scr-Retro .retro-paylist__time{flex:none;width:40px;font-size:13px;color:var(--ink-subtle)}
.scr-Retro .retro-paylist__body{flex:1;min-width:0;display:flex;flex-direction:column}
.scr-Retro .retro-paylist__body b{font-size:14px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.scr-Retro .retro-paytotal{display:flex;justify-content:space-between;margin:10px 0 0;font-size:15px}
.scr-Retro .retro-payer{flex:none;min-width:52px;justify-content:center}
/* 같이 간 사람 */
.scr-Retro .retro-people{margin-top:14px;display:flex;flex-direction:column;gap:8px}
.scr-Retro .retro-people__title{display:flex;align-items:baseline;gap:8px;margin:0}
.scr-Retro .retro-people__chips{display:flex;flex-wrap:wrap;gap:6px}
.scr-Retro .retro-people__chips .ic{width:14px;height:14px}
.scr-Retro .retro-people__chips button:disabled{opacity:.45;cursor:default}
.scr-Retro .retro-people__edit{display:flex;gap:6px;align-items:center}
.scr-Retro .retro-people__edit input{flex:1;min-width:0;height:36px;border:1px solid var(--line-strong);border-radius:10px;padding:0 10px;font:inherit;font-size:14px}
/* 정산 */
.scr-Retro .retro-settle{margin-top:14px;padding:12px;border-radius:12px;background:var(--navy-soft);display:flex;flex-direction:column;gap:6px}
.scr-Retro .retro-settle__title{display:flex;flex-direction:column;margin:0}
.scr-Retro .retro-settle__people{list-style:none;margin:0;padding:0;display:flex;flex-direction:column;gap:2px;font-size:14px}
.scr-Retro .retro-settle__people li{display:flex;justify-content:space-between}
.scr-Retro .retro-settle__send{display:flex;align-items:center;gap:6px;margin:0;font-size:15px;color:var(--navy)}
.scr-Retro .retro-settle__send b{margin-left:auto}
/* 카드 내역 가져오기 창 (화면 전체를 덮고 아래에서 올라온 상자) */
.scr-Retro .retro-cards{position:absolute;inset:0;z-index:40;background:rgba(10,14,28,.45);display:flex;align-items:flex-end}
.scr-Retro .retro-cards__box{width:100%;max-height:80%;background:var(--surface);border-radius:20px 20px 0 0;padding:16px 16px 28px;display:flex;flex-direction:column;gap:10px}
.scr-Retro .retro-cards__head{display:flex;align-items:center;justify-content:space-between}
.scr-Retro .retro-cards__head b{font-size:18px}
.scr-Retro .retro-cards__list{list-style:none;margin:0;padding:0;overflow-y:auto;flex:1;min-height:0}
.scr-Retro .retro-cards__list label{display:flex;align-items:center;gap:10px;min-height:52px;border-bottom:1px solid var(--line);cursor:pointer}
.scr-Retro .retro-cards__list input{width:20px;height:20px;accent-color:var(--navy);flex:none}
.scr-Retro .retro-cards__time{flex:none;width:86px;font-size:13px;color:var(--ink-subtle)}
.scr-Retro .retro-cards__name{flex:1;min-width:0;font-size:14px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.scr-Retro .retro-cards__list .is-done{opacity:.5;cursor:default}
/* 계획 없이 머문 곳 줄: 장소 줄보다 작고, 코랄 아이콘 + "계획 밖" 표시 */
.scr-Retro .retro-tl__extra{display:flex;align-items:center;gap:12px;min-height:48px}
.scr-Retro .retro-tl__extra .retro-tl__icon>span{background:var(--coral-soft,#FDECE8);border-color:var(--coral);color:var(--coral)}
.scr-Retro .retro-tl__extra b{font-size:14px}
.scr-Retro .retro-tl__tag--extra{color:var(--coral);background:var(--coral-soft,#FDECE8)}
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
