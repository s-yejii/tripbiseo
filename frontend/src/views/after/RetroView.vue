<script setup>
// S19-a 지난 여행 다시보기 (⑤): 다녀온 동선을 재생하고, 핀을 누르면 시간·결제 메모를 봐요. 계획과 실제도 비교해요
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { get } from '@/api/http'
import { toTime } from '@/utils/time'

// 가짜 데이터: 다녀온 곳 (백엔드가 붙으면 GET /api/trips/{id}/retro)
const visited = ref([

])
const trackPoints = ref([]) // gps 점 목록 drawMap에서 채워짐

const progress = ref(68) // 재생 위치 (%)
const playing = ref(false)
const infoNo = ref(null)
let timer = null

const routePoints = computed(() => trackPoints.value.map((v) => v.x + ',' + v.y).join(' ')) // 다녀온 곳 좌표를 "x,y x,y x,y" 문자열로 만들어서 <polyline>에 넣음
const info = computed(() => visited.value.find((v) => v.no === infoNo.value)) 
// 재생 위치에 맞춰 GPS 점 사이에서 지금 점을 계산해요 (12:00 ~ 18:14)
const me = computed(() => {
  const sections = trackPoints.value.length - 1 // sections: GPS 점과 점 사이 구간 수. 예: GPS 점이 5개면 구간은 4개
  if (sections < 1 ) return { x : - 100, y : - 100 } // 선이 없으면 점을 화면 밖에 두게 함
  const part = Math.min(sections - 0.001, (progress.value / 100) * sections) // part: 지금 몇 번째 구간쯤인지. 예: 2.72면 2번 구간의 72% 지점. sections - 0.001: 마지막 점을 넘어가지 않게 막음
  // (progress.value / 100) * sections) : 현재 위치가 전체 구간에 어디에 있는지 계산
  const i = Math.floor(part) // i: 지금 지나는 구간의 시작 GPS 점 번호 (2.72 → 2)
  const rate = part - i
  const a = trackPoints.value[i]
  const b = trackPoints.value[i + 1]
  return { x: Math.round(a.x + (b.x - a.x) * rate), y: Math.round(a.y + (b.y - a.y) * rate) }
})
const nowText = computed(() => toTime(720 + Math.round((progress.value / 100) * 374)) + (playing.value ? ' · 4배속' : ''))

function togglePlay() {
  if (playing.value) return stop()
  if (progress.value >= 100) progress.value = 0
  playing.value = true
  infoNo.value = null
  timer = setInterval(() => {
    progress.value = Math.min(100, progress.value + 1.5)
    if (progress.value >= 100) stop()
  }, 90)
}
function stop() {
  clearInterval(timer)
  playing.value = false
}
onUnmounted(() => clearInterval(timer))
const route = useRoute() // 지금 주소 정보. ?id = 12 값을 읽을 떄 사용
const retro = ref(null)//서버 응답 . 받기 전에는 null
const errorMessage = ref(``) // 회고를 못 불러왔을 때 화면에 보여 줄 문구
const mapBox = ref(null) // 지도를 그릴 칸
const MAP_LEVEL = 5 // 지도 확대 단계, 숫자가 작을수록 가까이
const MAP_PADDING_TOP = 60 // 위쪽 뒤로가기 버튼에 안 가리게
const MAP_PADDING_SIDE = 40 // 핀이 화면 양옆에 잘리지 않게
const MAP_PADDING_BOTTOM = 100 // 재생 막대에 안 가리게
const BAR_FULL_MINUTES = 240 // 막대가 꽉 차는 기준 시간(분) // 240분으로 정한 이유는 한 장소에 4시간 이상 머무는 일은 거의 없을것 같아서 주관임.

// 머문 시간을 %로 해주는 것
function toBarWidth(minutes) {
  return Math.min(100, (minutes / BAR_FULL_MINUTES) * 100)
}

// 계획 vs 실제 막대: 실제로 머문 기록이 있는 장소만 한 줄씩 만듦
const compare = computed(() => {
  const rows = []
  if(retro.value === null)
  return rows

  for(const place of retro.value.stops) {
    if(place.actualStay === null) continue // 머문 기록이 없으면 비교할 수 없어서 건너뜀

    const diff = place.actualStay - place.stay // 실제 - 계획. 예: 80 - 60 = 20분 더 머묾
    rows.push({
      name: place.name,
      plan: toBarWidth(place.stay),
      real: toBarWidth(place.actualStay),
      diff: (diff > 0 ? '+' : '') + diff + '분', // 더 머물렀으면 앞에 + 를 붙임
      cls: diff > 0 ? 'c-warning' : 'muted', // 더 머문 곳만 눈에 띄는 색으로 표시
    })
  }
  return rows
})


// 분류별로 계획과의 시간 차이 합계와 장소 개수를 모음. 예: { 카페: { sum: 36, count: 2 } }
const categoryTotals = computed(() => {
  const totals = {}
  if (retro.value === null) return totals

  for (const place of retro.value.stops) {
    if (place.actualStay ===null) continue // 머문 기록이 없는 곳은 뺌
    if (place.category === null) continue // 분류가 없는 곳은 뺌
    const categoryName = place.category.split(',')[0] // 쉼표 앞 첫 분류만 씀. 예: '카페,디저트' → '카페'
    if (totals[categoryName] === undefined) // 처음 나온 분류면 0부터 시작
      totals[categoryName] = { sum : 0, count: 0 }
    totals[categoryName].count = totals[categoryName].count + 1 // 장소 개수 1 늘림
    totals[categoryName].sum = (place.actualStay - place.stay) + totals[categoryName].sum // 이번 차이를 합계에 더함
  }
  return totals
})

// 계획보다 평균적으로 가장 더 머무른 분류를 찾음
const topCategory = computed(() => {
  let best = null // 지금까지의 1등. 예: { name: '카페', average: 18 }

  // for...in은 객체의 키를 하나씩 꺼내요
  for (const categoryName in categoryTotals.value) { // categoryName: 분류 이름. 예: '카페'
    const total = categoryTotals.value[categoryName] // total: 그 분류의 합계와 개수. 예: { sum: 36, count: 2 }
    const average = Math.round(total.sum / total.count) // average: 한 곳당 계획과의 평균 차이(분). 예: 18

    if (best === null || average > best.average) {
      best = { name : categoryName, average : average }
    }
  }
  return best
})

// 배너 첫 줄 문구. 비교할 기록이 없으면 null
const bannerTitle = computed(() => {
  if (topCategory.value === null) return null

  const name = topCategory.value.name
  const average = topCategory.value.average

  if (average > 0) return name + '에선 평균 ' + average + '분 더 머물렀어요'
  if (average < 0) return name + '에선 평균 ' + Math.abs(average) + '분 덜 머물렀어요'
  return name + '에선 계획대로 머물렀어요'
})


// 배너 두 번째 줄 문구. 기록이 없거나 평균 차이가 0이면 null
const bannerTip = computed(() => {
  if (topCategory.value === null) return null

  const name = topCategory.value.name
  const average = topCategory.value.average

  if (average > 0) return '다음 여행엔 ' + name + ' 시간을 넉넉히 잡아 보세요'
  if (average < 0) return '다음 여행엔 ' + name + ' 시간을 줄여도 괜찮을거 같아요'
  return null
})

//화면이 열리면 회고 데이터를 가져옴
onMounted(async () => {
  try{
    retro.value = await get('/trips/' + route.query.id +'/retro')
    await loadMap() 
  }catch  (error){
    errorMessage.value = error.message


  }
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
  if (retro.value.stops.length === 0) return 
  const firstStop = retro.value.stops[0]
  const center = new kakao.maps.LatLng(firstStop.lat , firstStop.lng)
  const map = new kakao.maps.Map(mapBox.value, { center : center, level: MAP_LEVEL })
  const bounds = new kakao.maps.LatLngBounds() // LatLngBounds: 넣은 좌표가 모두 들어가는 네모 범위
  for (const place of retro.value.stops) // place 장소 들른 곳의 목록 중 꺼낸거
  bounds.extend(new kakao.maps.LatLng(place.lat , place.lng))
  map.setBounds(bounds,MAP_PADDING_TOP , MAP_PADDING_SIDE ,  MAP_PADDING_BOTTOM , MAP_PADDING_SIDE) // 지도에 맞게 확대/축소 순서 : 위 , 왼쪽, 아래 , 오른쪽

  const points = [] // 장소별 핀 정보를 담을 목록
  for (const place of retro.value.stops){
    const latLng = new kakao.maps.LatLng(place.lat , place.lng)
    const point = map.getProjection().containerPointFromCoords(latLng) // 방금 받은 지도좌표를 X,Y 화면 좌표로 바꿔줌
    points.push({ no : points.length + 1, x : point.x , y : point.y, name : place.name , time : place.time, pay : '' }) // point.length : 핀 번호에 번호붙이기()
  }
  visited.value=points // 화면에 보여줄 핀 좌표를 visited에 넣음 vistied : 화면에 실제로 방문한 잠소를 담는 ref
  const trackList = [] // gps 점마다 {x,y, time}을 담을 목록
  for (const gps of retro.value.track) { // gps: 실제로 이동한 기록
    const latLng = new kakao.maps.LatLng(gps.lat , gps.lng) // gps 좌표를 실제 좌표로 바꾸고 카카오 맵에 넣어줌
    const point = map.getProjection().containerPointFromCoords(latLng) // 화면 좌푤 ㅗ마바꿔줌
    trackList.push({ x: point.x , y : point.y , time : gps.time }) 
  }
trackPoints.value = trackList // 화면에 보여줄 gps ㅈ점 좌표를 넣음 포인트에
}



function toggleInfo(no) {
  infoNo.value = infoNo.value === no ? null : no
}

</script>

<template>
  <div class="scr scr-Retro">
    <div class="app">
      <div class="retro-map" aria-hidden="true">
        <div class="map"><div ref = "mapBox" style ="width: 100%; height: 100%"></div></div>
        <svg class="route" viewBox="0 0 390 330" style="position: absolute; inset: 0">
          <!-- 흰 테두리: 굵은 흰 선을 먼저 깔고, 그 위에 남색 선을 겹쳐 그림 (SVG는 나중에 쓴 게 위에 그려짐) -->
          <polyline :points="routePoints" fill="none" stroke="#FFFFFF" stroke-width="9" stroke-linecap="round" stroke-linejoin="round" />
          <polyline :points="routePoints" fill="none" stroke="#1F3A68" stroke-width="5" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </div>
      <div class="retro-map">
        <button v-for="v in visited" :key="v.no" type="button" class="pin pin--visited" :class="{ 'pin--sel': infoNo === v.no }" :style="{ left: v.x + 'px', top: v.y + 'px' }" :aria-label="v.no + ' ' + v.name" @click="toggleInfo(v.no)">
          {{ v.no }}<span v-if="v.pay" class="paydot" aria-hidden="true"><i class="ic i-won"></i></span>
        </button>
        <span class="me" :style="{ left: me.x + 'px', top: me.y + 'px' }" aria-hidden="true"></span>
        <div
          v-if="info"
          class="payinfo"
          :style="{ left: Math.max(12, Math.min(150, info.x - 30)) + 'px', ...(info.y > 160 ? { bottom: 330 - info.y + 24 + 'px' } : { top: info.y + 26 + 'px' }) }"
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
        <RouterLink to="/trips" class="iconbtn iconbtn--float" aria-label="뒤로가기"><i class="ic ic--l i-back" aria-hidden="true"></i></RouterLink>
      </header>

      <div class="player" style="position: absolute; left: 16px; right: 16px; top: 262px">
        <button type="button" class="iconbtn retro-play" :aria-label="playing ? '멈추기' : '이동 경로 재생'" @click="togglePlay"><i class="ic" :class="playing ? 'i-pause' : 'i-play'" aria-hidden="true"></i></button>
        <span style="flex: 1; display: flex; flex-direction: column; gap: 4px">
          <span class="bar"><i :style="{ width: progress + '%' }"></i></span>
          <span style="display: flex; justify-content: space-between" class="t-cap tn"><span>12:00</span><b class="c-navy">{{ nowText }}</b><span>18:14</span></span>
        </span>
      </div>

      <div class="scroll" style="top: 340px; padding-top: 4px; gap: 14px; bottom: 102px">
<!-- 여행 제목과 날짜를 API 값으로 보여 줌 -->
<div><h1 v-if="retro" class="h-title">{{ retro.title }}</h1><p v-if="retro" class="t-cap">{{ retro.startDate }} ~ {{ retro.endDate }}</p></div>
<p v-if="errorMessage" class="t-cap">{{ errorMessage }}</p>
        <div class="stats">
       <!-- 다녀온 곳 수 / 전체 장소 수 -->
       <div v-if="retro" class="stat"><b>{{ retro.visited }}/{{ retro.stops.length }}</b><span class="t-cap">다녀온 곳</span></div>
          <div class="stat"><b>6.8km</b><span class="t-cap">걸은 거리</span></div>


          <div class="stat"><b class="c-success">16분</b><span class="t-cap">예약 여유</span></div>
        </div>

        <section class="card card--pad retro-cmp" aria-labelledby="cmp-h">
          <div class="sec-h" style="min-height: 28px">
            <h2 id="cmp-h" class="t-sub">계획 vs 실제</h2>
            <span class="t-cap" style="display: inline-flex; gap: 10px"><span><i class="retro-key" style="background: var(--line-strong)"></i> 계획</span><span><i class="retro-key" style="background: var(--navy)"></i> 실제</span></span>
          </div>
          <div v-for="row in compare" :key="row.name" class="cmp">
            <span class="t-strong" style="font-size: 14px">{{ row.name }}</span>
            <span class="cmp__bars"><i :style="{ width: row.plan + '%', background: 'var(--line-strong)' }"></i><i :style="{ width: row.real + '%', background: 'var(--navy)' }"></i></span>
            <b class="tn" :class="row.cls" style="font-size: 13px; text-align: right">{{ row.diff }}</b>
          </div>
          <div v-if="bannerTitle" class="banner banner--info" style="margin-top: 6px">
            <i class="ic i-info" aria-hidden="true"></i>
          <span class="banner__body"><span class="t-strong">{{ bannerTitle }}</span><span v-if="bannerTip" class="t-cap">{{ bannerTip }}</span></span></div>

        </section>
      </div>

      <div class="cta">
        <div class="cta__row">
          <RouterLink to="/album" class="btn btn--secondary btn--lg"><i class="ic i-camera" aria-hidden="true"></i>사진 담기</RouterLink>
          <RouterLink to="/theme?id=alley" class="btn btn--primary btn--lg"><i class="ic i-refresh" aria-hidden="true"></i>이 코스로 다시</RouterLink>
        </div>
      </div>
    </div>
  </div>
</template>

<style>
.scr-Retro .retro-map{position:absolute;left:0;right:0;top:0;height:330px;overflow:hidden}
.scr-Retro .map{z-index: 0 }
.scr-Retro .retro-play{background:var(--navy);color:#fff;border-radius:50%;width:44px;height:44px}
.scr-Retro .retro-pay{color:var(--coral-ink);font-weight:600}
.scr-Retro .retro-cmp{display:flex;flex-direction:column;gap:6px}
.scr-Retro .retro-key{display:inline-block;width:10px;height:8px;border-radius:4px}
.scr-Retro .stats{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}
.scr-Retro .stat{padding:12px;border-radius:var(--radius-lg);background:var(--surface-raised);border:1px solid var(--line);display:flex;flex-direction:column;gap:2px}
.scr-Retro .stat b{font-size:20px;line-height:28px;font-weight:700;font-variant-numeric:tabular-nums}
.scr-Retro .cmp{display:grid;grid-template-columns:88px 1fr 44px;column-gap:10px;align-items:center;min-height:36px}
.scr-Retro .cmp__bars{display:flex;flex-direction:column;gap:3px}
.scr-Retro .cmp__bars i{display:block;height:8px;border-radius:999px}
.scr-Retro .player{display:flex;align-items:center;gap:12px;padding:10px 12px;border-radius:var(--radius-full);background:var(--surface-raised);box-shadow:var(--shadow-card)}
</style>
