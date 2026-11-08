<script setup>
// S06-c 숙소 잡기 (③): 1박 이상이면 숙소를 담아요. 여러 박이면 숙소가 여러 곳일 수 있어서 한 번에 하나씩 추가해요
//   찾기 두 가지 → 둘 다 같은 "위치 맞추기" 단계로 가요
//     · 맨 위 검색창: 숙소 이름·주소 (GET /api/places/locations, 카카오)
//     · 예약 확정 화면 캡처: 올리자마자 위치 맞추기 단계로 넘어가고, 서버(POST /api/plans/capture/stay)가 Gemini로
//       숙소 이름·주소·체크인·체크아웃(연월일·시각)을 읽어 채워요. 사진은 저장하지 않아요
//   위치 맞추기: 배민 주소 등록처럼 가운데 핀은 그대로 두고 지도를 움직여 맞춰요 → 멈춘 자리의 주소를 다시 찾아 보여 줘요
//   날짜·시간 확인 후 "숙소로 추가" → store.planStays (여행 정보 화면 목록)
//   ?index=N 으로 들어오면 담아 둔 N번째 숙소를 고치거나 지워요 (여행 정보 화면에서 숙소를 누름)
//   날짜는 여행 기간(store.planStartDate ~ planEndDate) 안에서만: 1박 여행이면 1박까지, 달력도 그 밖은 못 골라요
import { computed, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import BottomSheet from '@/components/common/BottomSheet.vue'
import KakaoMap from '@/components/common/KakaoMap.vue'
import { getLocationHere, readStayCapture, searchStays } from '@/api/trips'
import { useTripStore } from '@/stores/trip'
import { addDays, daysBetween } from '@/utils/dates'
import { toShortDate } from '@/utils/tripView'
import { goBack } from '@/utils/back'
import { showToast } from '@/utils/toast'

const SEARCH_WAIT_MS = 300
const PICK_LEVEL = 3 // 위치 맞출 때 지도 확대 단계 (작을수록 가까이)
const MOVE_MIN_M = 5 // 이보다 적게 움직였으면 주소를 다시 찾지 않음
const DEFAULT_CHECK_IN = '15:00' // 시각을 못 읽었을 때만 넣는 흔한 시각 (화면에 "기본값"으로 표시)
const DEFAULT_CHECK_OUT = '11:00'
const WEEKDAYS = '일월화수목금토'
// 예약 사이트: 각 사이트 첫 화면으로 (네이버는 "지역 + 숙소" 검색 결과로)
const SITES = [
  { name: '야놀자', url: 'https://www.yanolja.com/' },
  { name: '여기어때', url: 'https://www.yeogi.com/' },
  { name: '아고다', url: 'https://www.agoda.com/ko-kr/' },
  { name: '트립닷컴', url: 'https://kr.trip.com/' },
  { name: '네이버', url: null },
]

const store = useTripStore()
const route = useRoute()
const mapRef = ref(null)
// 고치는 숙소 순서 (주소의 ?index=). 없거나 범위 밖이면 새로 담기 (-1)
const editIndex = store.planStays[Number(route.query.index)] && route.query.index !== '' ? Number(route.query.index) : -1
const isEdit = editIndex >= 0
// 담으려는(고치려는) 숙소 { name, address, lat, lng, checkInDate, checkInTime, checkOutDate, checkOutTime }
const candidate = ref(isEdit ? { ...store.planStays[editIndex] } : null)
const isConfirmDelete = ref(false) // 삭제 버튼을 한 번 누름 (한 번 더 누르면 삭제)
const isReading = ref(false) // 캡처 읽는 중 (위치 맞추기 화면에서 기다림)
const isTimeDefault = ref({ in: false, out: false }) // 시각이 기본값인지 (읽은 값이 아님)
const keyword = ref(isEdit ? candidate.value.name : '')
const found = ref([])
const isSearching = ref(false)
const isListOpen = ref(false)
let timer = null
let lookupNo = 0 // 늦게 온 예전 주소 찾기 결과는 버리려고

onUnmounted(() => clearTimeout(timer))

const step = computed(() => (candidate.value ? 'pick' : 'list'))
// 여행 기간: 체크인은 첫날~마지막 전날, 체크아웃은 둘째 날~마지막 날
const tripStart = computed(() => store.planStartDate)
const tripEnd = computed(() => store.planEndDate || store.planStartDate)
const tripNights = computed(() => daysBetween(tripStart.value, tripEnd.value))
const checkOutMin = computed(() => addDays(candidate.value?.checkInDate || tripStart.value, 1))

// 다른 숙소가 아직 안 잡은 첫 밤 (없으면 null) → 이름으로 찾은 숙소의 날짜를 미리 채움
function firstFreeNight() {
  for (let day = tripStart.value; day < tripEnd.value; day = addDays(day, 1)) {
    const isTaken = store.planStays.some((stay, i) => i !== editIndex && stay.checkInDate <= day && day < stay.checkOutDate)
    if (!isTaken) return day
  }
  return null
}
// 지도 핀: 이미 담은 숙소(남색) + 여행 지역(회색, 참고). 위치를 맞추는 숙소는 지도 가운데 고정 핀으로 따로 그려요 (고치는 숙소는 빼요)
const pins = computed(() => {
  const list = []
  store.planStays.forEach((stay, i) => i !== editIndex && list.push({ key: 'stay' + i, lat: stay.lat, lng: stay.lng, icon: 'i-home', cls: 'pin--added', label: stay.name, route: false }))
  if (store.planArea) list.push({ key: 'area', lat: store.planArea.lat, lng: store.planArea.lng, icon: 'i-pin', cls: 'pin--done', label: store.planArea.name + ' (여행 지역)', route: false })
  return list
})

// 지도가 준비되면: 고치는 숙소 자리로 옮김
function onMapReady() {
  if (candidate.value?.lat != null) mapRef.value.moveTo(candidate.value.lat, candidate.value.lng, PICK_LEVEL)
}

function siteUrl(site) {
  if (site.url) return site.url
  const area = store.planArea ? store.planArea.name : '서울'
  return 'https://search.naver.com/search.naver?query=' + encodeURIComponent(area + ' 숙소')
}

// ---------------------------------------------------------------- 맨 위 검색
function search(value) {
  keyword.value = value
  isListOpen.value = true
  clearTimeout(timer)
  if (!value.trim()) {
    found.value = []
    isSearching.value = false
    return
  }
  isSearching.value = true
  timer = setTimeout(async () => {
    try {
      found.value = await searchStays(value.trim())
    } catch (err) {
      found.value = []
      showToast(err.message)
    }
    isSearching.value = false
  }, SEARCH_WAIT_MS)
}

// 검색 결과를 고르면 위치 맞추기로 (캡처로 읽은 날짜가 있으면 그대로 두고 이름·위치만 바꿈)
function pickFound(item) {
  isListOpen.value = false
  keyword.value = item.name
  const before = candidate.value
  const freeNight = firstFreeNight() // 날짜를 모르면 아직 빈 첫 밤 1박으로 채워 둠
  candidate.value = {
    name: item.name, address: item.address, lat: item.lat, lng: item.lng,
    checkInDate: before?.checkInDate || freeNight, checkInTime: before?.checkInTime || DEFAULT_CHECK_IN,
    checkOutDate: before?.checkOutDate || (freeNight && addDays(freeNight, 1)), checkOutTime: before?.checkOutTime || DEFAULT_CHECK_OUT,
  }
  if (!before) isTimeDefault.value = { in: true, out: true }
  mapRef.value?.moveTo(item.lat, item.lng, PICK_LEVEL)
}

// ---------------------------------------------------------------- 캡처: 올리자마자 위치 맞추기로
async function onFile(e) {
  const file = e.target.files && e.target.files[0]
  e.target.value = '' // 같은 사진을 다시 골라도 동작하게
  if (!file) return
  candidate.value = { name: '', address: '', lat: null, lng: null, checkInDate: null, checkInTime: null, checkOutDate: null, checkOutTime: null }
  isReading.value = true
  try {
    const read = await readStayCapture(file)
    isTimeDefault.value = { in: !read.checkInTime, out: !read.checkOutTime }
    candidate.value = { ...read, checkInTime: read.checkInTime || DEFAULT_CHECK_IN, checkOutTime: read.checkOutTime || DEFAULT_CHECK_OUT }
    keyword.value = read.name
    if (read.lat != null) mapRef.value?.moveTo(read.lat, read.lng, PICK_LEVEL)
    else showToast('숙소 위치를 못 찾았어요 · 맨 위에서 숙소 이름으로 찾아 주세요')
  } catch (err) {
    candidate.value = null
    showToast(err.message + ' · 맨 위에서 숙소 이름으로 찾을 수 있어요')
  } finally {
    isReading.value = false
  }
}

// ---------------------------------------------------------------- 위치 맞추기 (배민 주소 등록처럼)
// 지도를 움직이고 멈추면: 가운데 좌표로 바꾸고, 그 자리 주소를 다시 찾아요
async function onCenter(point) {
  const stay = candidate.value
  if (!stay || isReading.value || stay.lat == null) return
  if (distanceM(stay.lat, stay.lng, point.lat, point.lng) < MOVE_MIN_M) return
  stay.lat = point.lat
  stay.lng = point.lng
  const no = ++lookupNo
  try {
    const here = await getLocationHere(point.lat, point.lng)
    if (no === lookupNo && candidate.value === stay) stay.address = here.address
  } catch {
    // 주소를 못 찾아도 좌표는 옮겨 둠
  }
}

// 두 좌표 사이 거리(m) — 짧은 거리라 평면으로 계산
function distanceM(lat1, lng1, lat2, lng2) {
  const dy = (lat2 - lat1) * 111320
  const dx = (lng2 - lng1) * 111320 * Math.cos((lat1 * Math.PI) / 180)
  return Math.hypot(dx, dy)
}

function reset() {
  candidate.value = null
  keyword.value = ''
  found.value = []
}

// '2026-11-14', '15:00' → '2026년 11월 14일 (토) 15:00'
function fullDate(date, time) {
  if (!date) return '날짜 없음'
  const d = new Date(date + 'T00:00:00')
  return `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일 (${WEEKDAYS[d.getDay()]}) ${time || ''}`.trim()
}
const nights = computed(() => {
  const stay = candidate.value
  if (!stay?.checkInDate || !stay?.checkOutDate) return 0
  return daysBetween(stay.checkInDate, stay.checkOutDate)
})

const problem = computed(() => {
  const stay = candidate.value
  if (!stay || isReading.value) return ''
  if (tripNights.value < 1) return '당일치기 여행이에요 · 여행 정보 달력에서 1박 이상으로 골라 주세요'
  if (!stay.name || !stay.name.trim()) return '숙소 이름을 적어 주세요'
  if (stay.lat == null) return '맨 위에서 숙소를 찾아 위치를 정해 주세요'
  if (!stay.checkInDate || !stay.checkOutDate) return '체크인·체크아웃 날짜를 넣어 주세요'
  if (stay.checkOutDate <= stay.checkInDate) return '체크아웃은 체크인 다음 날 이후여야 해요'
  if (stay.checkInDate < tripStart.value || stay.checkOutDate > tripEnd.value) {
    return `여행 기간(${toShortDate(tripStart.value)}~${toShortDate(tripEnd.value)}, ${tripNights.value}박) 안으로 맞춰 주세요`
  }
  const overlap = store.planStays.find((other, i) => i !== editIndex && stay.checkInDate < other.checkOutDate && other.checkInDate < stay.checkOutDate)
  if (overlap) return overlap.name + '과(와) 날짜가 겹쳐요'
  return ''
})

// 담기 (고치는 중이면 그 자리를 바꿈). 체크인 순으로 다시 줄 세움
function add() {
  if (problem.value) return showToast(problem.value)
  const stay = { ...candidate.value, name: candidate.value.name.trim() }
  const others = store.planStays.filter((_, i) => i !== editIndex)
  store.planStays = [...others, stay].sort((a, b) => a.checkInDate.localeCompare(b.checkInDate))
  showToast(stay.name + (isEdit ? ' 숙소를 고쳤어요' : '을(를) 숙소로 담았어요'))
  goBack('/plan/info')
}

// 삭제: 한 번 누르면 확인 문구로 바뀌고, 한 번 더 누르면 지움
function remove() {
  if (!isConfirmDelete.value) {
    isConfirmDelete.value = true
    return
  }
  const name = store.planStays[editIndex].name
  store.planStays = store.planStays.filter((_, i) => i !== editIndex)
  showToast(name + '을(를) 숙소에서 뺐어요')
  goBack('/plan/info')
}
</script>

<template>
  <div class="scr scr-StayPin">
    <div class="app">
      <!-- 지도: 위치를 맞출 때는 시트 위 칸만 쓰고, 가운데에 핀을 고정해요 -->
      <div class="sp-mapbox">
        <KakaoMap ref="mapRef" :pins="pins" :show-route="false" pick-center :padding="{ top: 120, right: 48, bottom: 48, left: 48 }" @center="onCenter" @ready="onMapReady" />
        <template v-if="step === 'pick' && candidate.lat != null">
          <span class="sp-center" aria-hidden="true"><i class="ic i-home"></i></span>
          <span class="sp-shadow" aria-hidden="true"></span>
        </template>
      </div>

      <!-- 맨 위: 뒤로 + 숙소 검색 -->
      <header class="topbar topbar--float sp-top">
        <button type="button" class="iconbtn iconbtn--float" aria-label="뒤로가기" @click="goBack('/plan/info')"><i class="ic ic--l i-back" aria-hidden="true"></i></button>
        <div class="field__box sp-search">
          <i class="ic i-search subtle" aria-hidden="true"></i>
          <label class="sr" for="sp-q">숙소 이름 또는 주소</label>
          <input id="sp-q" :value="keyword" type="text" placeholder="숙소 이름·주소로 찾기" autocomplete="off" @input="search($event.target.value)" @focus="isListOpen = !!keyword" />
          <button v-if="keyword" type="button" class="iconbtn" aria-label="검색어 지우기" @click="search('')"><i class="ic i-close subtle" aria-hidden="true"></i></button>
        </div>
      </header>
      <div v-if="isListOpen && keyword" class="card sp-results" role="listbox" aria-label="숙소 검색 결과">
        <p v-if="isSearching" class="t-cap sp-msg">'{{ keyword }}' 찾는 중…</p>
        <p v-else-if="!found.length" class="t-cap sp-msg">'{{ keyword }}' 결과가 없어요. 숙소 이름이나 도로명 주소로 찾아보세요</p>
        <template v-else>
          <button v-for="item in found.slice(0, 6)" :key="item.name + item.lat" type="button" role="option" class="row sp-hit" @click="pickFound(item)">
            <span class="row__body"><span class="row__title">{{ item.name }}</span><span class="row__meta">{{ item.category ? item.category + ' · ' : '' }}{{ item.address }}</span></span>
          </button>
        </template>
      </div>

      <BottomSheet class="sheet--peek stay-sheet" :class="'is-' + step">
        <!-- 담기 전: 예약 사이트 → 캡처 올리기 -->
        <template v-if="step === 'list'">
          <div data-drag>
            <h1 class="h-title">숙소 추가</h1>
            <p class="t-cap tn" style="margin: 2px 0 0">여행 {{ toShortDate(tripStart) }}~{{ toShortDate(tripEnd) }} · {{ tripNights }}박{{ store.planStays.length ? ` 중 ${store.planStays.length}곳 담았어요` : ' · 예약하고 확정 화면을 캡처해서 올려 주세요' }}</p>
          </div>
          <section class="sp-step" aria-labelledby="sp-book">
            <h2 id="sp-book" class="sp-step__title"><span class="sp-no">1</span>예약하기</h2>
            <div class="sp-sites">
              <a v-for="site in SITES" :key="site.name" :href="siteUrl(site)" target="_blank" rel="noopener noreferrer" class="chip chip--sm sp-site">{{ site.name }}<i class="ic ic--s i-next" aria-hidden="true"></i></a>
            </div>
          </section>
          <section class="sp-step" aria-labelledby="sp-cap">
            <h2 id="sp-cap" class="sp-step__title"><span class="sp-no">2</span>예약 확정 화면 올리기</h2>
            <label class="btn btn--primary btn--lg btn--block sp-upload">
              <i class="ic i-camera" aria-hidden="true"></i>캡처 사진 고르기
              <input type="file" accept="image/png,image/jpeg,image/webp" hidden @change="onFile" />
            </label>
            <p class="t-cap">숙소 이름·주소·체크인·체크아웃만 읽어요. 예약 번호·결제 정보는 읽지 않고, 사진은 저장하지 않아요. 캡처가 없으면 맨 위에서 숙소를 찾아요.</p>
          </section>
        </template>

        <!-- 위치 맞추기 + 날짜 확인 -->
        <template v-else>
          <div v-if="isReading" class="sp-reading" role="status">
            <span class="sp-spinner" aria-hidden="true"></span>
            <b>캡처에서 숙소 정보를 읽는 중이에요</b>
            <span class="t-cap">숙소 이름·주소·체크인·체크아웃을 찾고 있어요</span>
          </div>
          <template v-else>
            <div data-drag class="sp-head">
              <div style="flex: 1; min-width: 0">
                <input v-model="candidate.name" class="sp-name" type="text" aria-label="숙소 이름" placeholder="숙소 이름" />
                <p class="t-cap" style="margin: 2px 0 0">{{ candidate.address || '주소 없음' }}</p>
              </div>
              <span v-if="isEdit" class="badge badge--navy">수정</span>
              <button type="button" class="btn btn--ghost btn--sm" @click="reset">다시 고르기</button>
            </div>
            <p v-if="candidate.lat != null" class="sp-tip"><i class="ic ic--s i-info" aria-hidden="true"></i>지도를 움직여 핀을 숙소 입구에 맞춰 주세요</p>

            <div class="sp-dates">
              <div class="field">
                <label class="field__label" for="sp-in">체크인</label>
                <div class="field__box"><input id="sp-in" v-model="candidate.checkInDate" type="date" :min="tripStart" :max="addDays(tripEnd, -1)" /></div>
                <div class="field__box"><input v-model="candidate.checkInTime" type="time" aria-label="체크인 시각" @input="isTimeDefault.in = false" /></div>
                <p v-if="isTimeDefault.in" class="field__help">기본값 · 예약 화면에서 확인</p>
              </div>
              <div class="field">
                <label class="field__label" for="sp-out">체크아웃</label>
                <div class="field__box"><input id="sp-out" v-model="candidate.checkOutDate" type="date" :min="checkOutMin" :max="tripEnd" /></div>
                <div class="field__box"><input v-model="candidate.checkOutTime" type="time" aria-label="체크아웃 시각" @input="isTimeDefault.out = false" /></div>
                <p v-if="isTimeDefault.out" class="field__help">기본값 · 예약 화면에서 확인</p>
              </div>
            </div>
            <p v-if="nights > 0" class="t-cap tn sp-sum">{{ fullDate(candidate.checkInDate, candidate.checkInTime) }} → {{ fullDate(candidate.checkOutDate, candidate.checkOutTime) }} · {{ nights }}박</p>
            <p v-if="problem" class="t-cap" style="display: flex; gap: 4px; align-items: center"><i class="ic ic--s i-info c-warning" aria-hidden="true"></i>{{ problem }}</p>
            <div v-if="isEdit" class="cta__row">
              <button type="button" class="btn btn--lg" :class="isConfirmDelete ? 'btn--danger-solid' : 'btn--danger'" @click="remove">{{ isConfirmDelete ? '한 번 더 누르면 삭제' : '숙소 삭제' }}</button>
              <button type="button" class="btn btn--primary btn--lg" :disabled="!!problem" @click="add">수정 완료</button>
            </div>
            <button v-else type="button" class="btn btn--primary btn--lg btn--block" :disabled="!!problem" @click="add">숙소로 추가</button>
          </template>
        </template>
      </BottomSheet>
    </div>
  </div>
</template>

<style>
.scr-StayPin .sp-mapbox{position:absolute;left:0;right:0;top:0;bottom:390px} /* 크기를 바꾸면 카카오 지도가 빈 칸을 남겨서 두 단계 모두 같은 크기 */
.scr-StayPin .sp-mapbox .map{inset:0}
/* 가운데 고정 핀: 돌린 물방울 끝(가운데에서 9px 아래)이 정확히 지도 가운데를 가리키게 위로 9px 더 올림 */
.scr-StayPin .sp-center{position:absolute;left:50%;top:50%;z-index:2;width:44px;height:44px;margin:-53px 0 0 -22px;border-radius:50% 50% 50% 0;transform:rotate(-45deg);background:var(--coral);display:flex;align-items:center;justify-content:center;box-shadow:0 4px 10px rgba(24,33,58,.35);pointer-events:none}
.scr-StayPin .sp-center .ic{transform:rotate(45deg);color:#fff}
.scr-StayPin .sp-shadow{position:absolute;left:50%;top:50%;z-index:1;width:14px;height:6px;margin:-3px 0 0 -7px;border-radius:50%;background:rgba(24,33,58,.35);pointer-events:none}
.scr-StayPin .sp-top{z-index:4}
.scr-StayPin .sp-search{flex:1;min-width:0;height:44px;background:var(--surface-raised);box-shadow:0 2px 8px rgba(24,33,58,.16);border-radius:var(--radius-full)}
.scr-StayPin .sp-results{position:absolute;top:107px;left:16px;right:16px;z-index:5;max-height:330px;overflow-y:auto;padding:6px;display:flex;flex-direction:column;gap:2px}
.scr-StayPin .sp-msg{padding:12px;text-align:center}
.scr-StayPin .sp-hit{width:100%;text-align:left;font:inherit;color:inherit;background:none;border:0;border-radius:var(--radius-lg);cursor:pointer}
.scr-StayPin .sp-hit:hover{background:var(--surface)}
.scr-StayPin .sheet.stay-sheet{top:330px!important;overflow-y:auto;gap:18px}
.scr-StayPin .sheet.stay-sheet.is-pick{top:440px!important;gap:10px}
.scr-StayPin .sp-step{display:flex;flex-direction:column;gap:8px}
.scr-StayPin .sp-step__title{margin:0;display:flex;align-items:center;gap:8px;font-size:15px;font-weight:700}
.scr-StayPin .sp-no{width:20px;height:20px;border-radius:50%;background:var(--navy);color:#fff;font-size:12px;display:inline-flex;align-items:center;justify-content:center}
.scr-StayPin .sp-sites{display:flex;flex-wrap:wrap;gap:8px}
.scr-StayPin .sp-site{text-decoration:none;gap:2px}
.scr-StayPin .sp-upload{cursor:pointer}
.scr-StayPin .sp-head{display:flex;align-items:flex-start;gap:8px}
.scr-StayPin .sp-name{width:100%;border:0;padding:0;font:inherit;font-size:18px;font-weight:700;color:var(--ink);background:none}
.scr-StayPin .sp-name:focus{outline:none;border-bottom:1.5px solid var(--navy)}
.scr-StayPin .sp-tip{margin:0;display:flex;align-items:center;gap:6px;padding:8px 10px;border-radius:var(--radius-md);background:var(--navy-soft);color:var(--navy);font-size:13px}
.scr-StayPin .sp-dates{display:grid;grid-template-columns:1fr 1fr;gap:10px}
.scr-StayPin .sp-dates .field__box{height:44px}
.scr-StayPin .sp-dates input{font-variant-numeric:tabular-nums;width:100%}
.scr-StayPin .sp-sum{margin:0}
.scr-StayPin .sp-reading{display:flex;flex-direction:column;align-items:center;gap:8px;padding:32px 0;text-align:center}
.scr-StayPin .sp-spinner{width:28px;height:28px;border-radius:50%;border:3px solid var(--navy-soft);border-top-color:var(--navy);animation:sp-spin .8s linear infinite}
@keyframes sp-spin{to{transform:rotate(360deg)}}
</style>
