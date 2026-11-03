<script setup>
// S06-c 숙소 잡기 (③): 1박 이상이면 숙소를 담아요. 여러 박이면 숙소가 여러 곳일 수 있어서 한 번에 하나씩 추가해요
//   1) 예약 사이트(야놀자·여기어때·아고다·트립닷컴·네이버)에서 직접 예약 → 새 탭으로 열어요
//   2) 예약 확정 화면을 캡처해서 올리면 서버(POST /api/plans/capture/stay)가 Gemini로 숙소 이름·주소·체크인·체크아웃을 읽고
//      주소로 카카오에서 위치를 찾아 지도에 핀을 띄워요 (사진은 저장하지 않아요)
//   3) 캡처가 없으면 숙소 이름·주소로 찾기 (GET /api/places/locations)
//   위치가 다르면 지도를 눌러 핀을 옮기고, 날짜·시간을 확인한 뒤 "숙소로 추가" → store.planStays (여행 정보 화면 목록)
import { computed, ref } from 'vue'
import BottomSheet from '@/components/common/BottomSheet.vue'
import KakaoMap from '@/components/common/KakaoMap.vue'
import { readStayCapture, searchLocations } from '@/api/trips'
import { useTripStore } from '@/stores/trip'
import { goBack } from '@/utils/back'
import { showToast } from '@/utils/toast'

const SEARCH_WAIT_MS = 300
const DEFAULT_CHECK_IN = '15:00' // 캡처에 시간이 안 보이면 흔한 체크인·체크아웃 시각
const DEFAULT_CHECK_OUT = '11:00'
const MAP_PADDING = { top: 100, right: 48, bottom: 520, left: 48 } // 아래 시트에 핀이 가리지 않게
// 예약 사이트: 각 사이트 첫 화면으로 (네이버는 "지역 + 숙소" 검색 결과로)
const SITES = [
  { name: '야놀자', url: 'https://www.yanolja.com/' },
  { name: '여기어때', url: 'https://www.yeogi.com/' },
  { name: '아고다', url: 'https://www.agoda.com/ko-kr/' },
  { name: '트립닷컴', url: 'https://kr.trip.com/' },
  { name: '네이버', url: null },
]

const store = useTripStore()
const candidate = ref(null) // 지금 담으려는 숙소 { name, address, lat, lng, checkInDate, checkInTime, checkOutDate, checkOutTime }
const isReading = ref(false)
const readNote = ref('')
const keyword = ref('')
const found = ref([])
const isSearching = ref(false)
let timer = null

// 지도 핀: 이미 담은 숙소(남색) + 지금 고른 숙소(코랄, 크게) + 여행 지역(회색, 참고)
const pins = computed(() => {
  const list = []
  store.planStays.forEach((stay, i) => list.push({ key: 'stay' + i, lat: stay.lat, lng: stay.lng, icon: 'i-home', cls: 'pin--added', label: stay.name, route: false }))
  if (store.planArea) list.push({ key: 'area', lat: store.planArea.lat, lng: store.planArea.lng, icon: 'i-pin', cls: 'pin--done', label: store.planArea.name + ' (여행 지역)', route: false, fit: !candidate.value })
  if (candidate.value?.lat != null) list.push({ key: 'new', lat: candidate.value.lat, lng: candidate.value.lng, icon: 'i-home', cls: 'pin--sel pin--stay', label: candidate.value.name, route: false })
  return list
})
// 숙소를 새로 고를 때만 화면을 다시 맞춤 (지도를 눌러 핀을 옮길 때는 화면을 그대로 둠)
const fitKey = computed(() => (candidate.value ? 'pick:' + candidate.value.name : 'list' + store.planStays.length))

function siteUrl(site) {
  if (site.url) return site.url
  const area = store.planArea ? store.planArea.name : '서울'
  return 'https://search.naver.com/search.naver?query=' + encodeURIComponent(area + ' 숙소')
}

// 1) 캡처 올리기 → 읽은 값으로 숙소 후보
async function onFile(e) {
  const file = e.target.files && e.target.files[0]
  e.target.value = '' // 같은 사진을 다시 골라도 동작하게
  if (!file) return
  isReading.value = true
  readNote.value = ''
  try {
    const read = await readStayCapture(file)
    candidate.value = {
      ...read,
      checkInTime: read.checkInTime || DEFAULT_CHECK_IN,
      checkOutTime: read.checkOutTime || DEFAULT_CHECK_OUT,
    }
    readNote.value = read.lat != null ? '캡처에서 읽었어요 · 위치와 날짜가 맞는지 확인해 주세요' : '위치를 못 찾았어요 · 지도를 눌러 숙소 자리를 찍어 주세요'
  } catch (err) {
    showToast(err.message)
  } finally {
    isReading.value = false
  }
}

// 2) 이름·주소로 찾기
function search(value) {
  keyword.value = value
  clearTimeout(timer)
  if (!value.trim()) {
    found.value = []
    isSearching.value = false
    return
  }
  isSearching.value = true
  timer = setTimeout(async () => {
    try {
      found.value = await searchLocations(value.trim())
    } catch (err) {
      found.value = []
      showToast(err.message)
    }
    isSearching.value = false
  }, SEARCH_WAIT_MS)
}
function pickFound(item) {
  candidate.value = { name: item.name, address: item.address, lat: item.lat, lng: item.lng, checkInDate: null, checkInTime: DEFAULT_CHECK_IN, checkOutDate: null, checkOutTime: DEFAULT_CHECK_OUT }
  readNote.value = '이름으로 찾았어요 · 예약한 날짜를 넣어 주세요'
}

// 지도를 누르면 고른 숙소 핀을 그 자리로
function moveCandidate(point) {
  if (!candidate.value) return
  candidate.value = { ...candidate.value, lat: point.lat, lng: point.lng }
  readNote.value = '지도에서 누른 자리로 옮겼어요'
}

function reset() {
  candidate.value = null
  readNote.value = ''
}

const problem = computed(() => {
  const stay = candidate.value
  if (!stay) return ''
  if (!stay.name || !stay.name.trim()) return '숙소 이름을 적어 주세요'
  if (stay.lat == null) return '지도를 눌러 숙소 위치를 찍어 주세요'
  if (!stay.checkInDate || !stay.checkOutDate) return '체크인·체크아웃 날짜를 넣어 주세요'
  if (stay.checkOutDate <= stay.checkInDate) return '체크아웃은 체크인 다음 날 이후여야 해요'
  const overlap = store.planStays.find((other) => stay.checkInDate < other.checkOutDate && other.checkInDate < stay.checkOutDate)
  if (overlap) return overlap.name + '과(와) 날짜가 겹쳐요'
  return ''
})

function add() {
  if (problem.value) return showToast(problem.value)
  const stay = { ...candidate.value, name: candidate.value.name.trim() }
  store.planStays = [...store.planStays, stay].sort((a, b) => a.checkInDate.localeCompare(b.checkInDate))
  showToast(stay.name + '을(를) 숙소로 담았어요')
  goBack('/plan/info')
}
</script>

<template>
  <div class="scr scr-StayPin">
    <div class="app">
      <KakaoMap :pins="pins" :show-route="false" :fit-key="fitKey" :padding="MAP_PADDING" @map-click="moveCandidate" />

      <header class="topbar topbar--float">
        <button type="button" class="iconbtn iconbtn--float" aria-label="뒤로가기" @click="goBack('/plan/info')"><i class="ic ic--l i-back" aria-hidden="true"></i></button>
        <span class="card sp-help">
          <i class="ic ic--s i-info c-navy" aria-hidden="true"></i>
          <span class="t-cap" style="color: var(--ink)">{{ candidate ? '위치가 다르면 지도를 눌러 옮겨요' : '예약한 숙소를 캡처로 담아요' }}</span>
        </span>
      </header>

      <BottomSheet class="sheet--peek stay-sheet" :class="{ 'is-picked': candidate }">
        <!-- 담기 전: 예약 → 캡처 올리기 / 이름으로 찾기 -->
        <template v-if="!candidate">
          <div data-drag>
            <h1 class="h-title">숙소 추가</h1>
            <p class="t-cap" style="margin: 2px 0 0">{{ store.planStays.length ? `지금 ${store.planStays.length}곳 담았어요 · 다른 날 묵을 숙소를 더 담아요` : '예약 사이트에서 예약하고, 확정 화면을 캡처해서 올려 주세요' }}</p>
          </div>

          <section class="sp-step" aria-labelledby="sp-book">
            <h2 id="sp-book" class="sp-step__title"><span class="sp-no">1</span>예약하기</h2>
            <div class="sp-sites">
              <a v-for="site in SITES" :key="site.name" :href="siteUrl(site)" target="_blank" rel="noopener noreferrer" class="chip chip--sm sp-site">{{ site.name }}<i class="ic ic--s i-next" aria-hidden="true"></i></a>
            </div>
          </section>

          <section class="sp-step" aria-labelledby="sp-cap">
            <h2 id="sp-cap" class="sp-step__title"><span class="sp-no">2</span>예약 확정 화면 올리기</h2>
            <label class="btn btn--primary btn--lg btn--block sp-upload" :class="{ 'is-busy': isReading }">
              <i class="ic i-camera" aria-hidden="true"></i>{{ isReading ? '숙소 정보를 읽는 중…' : '캡처 사진 고르기' }}
              <input type="file" accept="image/png,image/jpeg,image/webp" hidden :disabled="isReading" @change="onFile" />
            </label>
            <p class="t-cap">숙소 이름·주소·체크인·체크아웃만 읽어요. 예약 번호·결제 정보는 읽지 않고, 사진은 저장하지 않아요.</p>
          </section>

          <section class="sp-step" aria-labelledby="sp-find">
            <h2 id="sp-find" class="sp-step__title">캡처가 없으면 이름·주소로 찾기</h2>
            <div class="field__box">
              <i class="ic i-search subtle" aria-hidden="true"></i>
              <label class="sr" for="sp-q">숙소 이름 또는 주소</label>
              <input id="sp-q" :value="keyword" type="text" placeholder="예: 신라스테이 광화문" autocomplete="off" @input="search($event.target.value)" />
            </div>
            <p v-if="isSearching" class="t-cap">찾는 중…</p>
            <button v-for="item in found.slice(0, 5)" :key="item.name + item.lat" type="button" class="row sp-hit" @click="pickFound(item)">
              <span class="row__body"><span class="row__title">{{ item.name }}</span><span class="row__meta">{{ item.category ? item.category + ' · ' : '' }}{{ item.address }}</span></span>
              <i class="ic i-plus c-navy" aria-hidden="true"></i>
            </button>
          </section>
        </template>

        <!-- 고른 숙소 확인 -->
        <template v-else>
          <div data-drag style="display: flex; align-items: center; gap: 12px">
            <span class="ico-tile"><i class="ic ic--l i-home" aria-hidden="true"></i></span>
            <div style="flex: 1; min-width: 0">
              <p class="t-cap" style="margin: 0">{{ readNote }}</p>
            </div>
            <button type="button" class="btn btn--ghost btn--sm" @click="reset">다시 고르기</button>
          </div>
          <div class="field">
            <label class="field__label" for="sp-name">숙소 이름</label>
            <div class="field__box"><input id="sp-name" v-model="candidate.name" type="text" /></div>
            <p v-if="candidate.address" class="field__help">{{ candidate.address }}</p>
          </div>
          <div class="sp-dates">
            <div class="field">
              <label class="field__label" for="sp-in">체크인</label>
              <div class="field__box"><input id="sp-in" v-model="candidate.checkInDate" type="date" /></div>
              <div class="field__box" style="margin-top: 6px"><input v-model="candidate.checkInTime" type="time" aria-label="체크인 시각" /></div>
            </div>
            <div class="field">
              <label class="field__label" for="sp-out">체크아웃</label>
              <div class="field__box"><input id="sp-out" v-model="candidate.checkOutDate" type="date" /></div>
              <div class="field__box" style="margin-top: 6px"><input v-model="candidate.checkOutTime" type="time" aria-label="체크아웃 시각" /></div>
            </div>
          </div>
          <p v-if="problem" class="t-cap" style="display: flex; gap: 4px; align-items: center"><i class="ic ic--s i-info c-warning" aria-hidden="true"></i>{{ problem }}</p>
          <button type="button" class="btn btn--primary btn--lg btn--block" :disabled="!!problem" @click="add">숙소로 추가</button>
        </template>
      </BottomSheet>
    </div>
  </div>
</template>

<style>
.scr-StayPin .sp-help{flex:1;min-width:0;height:44px;display:flex;align-items:center;gap:8px;padding:0 14px;border-radius:var(--radius-full)}
.scr-StayPin .sheet.stay-sheet{top:300px!important;overflow-y:auto;gap:18px}
.scr-StayPin .sheet.stay-sheet.is-picked{top:360px!important;gap:12px}
.scr-StayPin .sp-step{display:flex;flex-direction:column;gap:8px}
.scr-StayPin .sp-step__title{margin:0;display:flex;align-items:center;gap:8px;font-size:15px;font-weight:700}
.scr-StayPin .sp-no{width:20px;height:20px;border-radius:50%;background:var(--navy);color:#fff;font-size:12px;display:inline-flex;align-items:center;justify-content:center}
.scr-StayPin .sp-sites{display:flex;flex-wrap:wrap;gap:8px}
.scr-StayPin .sp-site{text-decoration:none;gap:2px}
.scr-StayPin .sp-upload{cursor:pointer}
.scr-StayPin .sp-upload.is-busy{opacity:.7;pointer-events:none}
.scr-StayPin .sp-hit{width:100%;text-align:left;font:inherit;color:inherit;background:var(--surface);border:0;border-radius:var(--radius-lg);cursor:pointer}
.scr-StayPin .sp-dates{display:grid;grid-template-columns:1fr 1fr;gap:10px}
.scr-StayPin .sp-dates input{font-variant-numeric:tabular-nums;width:100%}
.scr-StayPin .pin--stay{background:var(--coral)}
</style>
