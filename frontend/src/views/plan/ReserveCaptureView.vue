<script setup>
// S06-d 예약 캡처로 추가 (③): 예약 화면 캡처를 올리면 글자를 읽어 장소·날짜·시간을 채워요
// 사진을 올리면 서버(POST /api/plans/capture)가 Gemini로 장소·날짜·시각을 읽어 채워요 (사진은 저장하지 않아요).
// 읽은 값은 사용자가 고친 뒤 "고정 일정으로 넣기"를 눌러야 들어가요 (AI가 읽은 값을 그대로 믿지 않게)
import { computed, ref } from 'vue'
import { readCapture } from '@/api/trips'
import { useTripStore } from '@/stores/trip'
import { goBack } from '@/utils/back'
import { showToast } from '@/utils/toast'

const store = useTripStore()
const photo = ref('') // 올린 사진 (없으면 올리기 안내)
// 11/12: 예전엔 "잔나비 콘서트 18:30" 가짜 값이 채워져 있어서 사진 없이 눌러도 가짜 일정이 들어갔어요. 이제 빈칸에서 시작해요
const name = ref('')
const time = ref('')
const date = ref('')
const found = ref(null) // 서버가 읽은 결과 (DB 장소를 찾았으면 좌표 있음)
const reading = ref(false)
const readNote = ref('예약 확인 화면을 캡처해서 올려 주세요')
const isFailed = ref(false) // 못 읽었으면 배너를 경고 색으로
const TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/ // HH:mm
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/ // yyyy-MM-dd
// 이름과 HH:mm 시각이 채워져야 [고정 일정으로 넣기]를 누를 수 있어요 (사진을 못 읽었으면 손으로 적어도 돼요)
const canAdd = computed(() => !reading.value && name.value.trim() !== '' && TIME_PATTERN.test(time.value))
const bannerTitle = computed(() => {
  if (reading.value) return '읽는 중'
  if (isFailed.value) return '사진을 읽지 못했어요'
  return found.value ? '글자를 읽어 아래에 채웠어요' : '캡처를 올리면 글자를 읽어 채워요'
})

// 장소를 어떻게 찾았는지 한 줄 (DB 가게 / 주소 위치만 / 못 찾음)
function placeNote(result) {
  if (result.placeId) return '장소를 지도에서 찾았어요 · 시간이 맞는지 확인해 주세요'
  if (result.lat != null) return '우리 DB에 없는 곳이에요 · 위치가 맞는지 확인해 주세요'
  return '지도에서 장소를 못 찾았어요 · 이름을 확인해 주세요'
}

// 읽은 결과에서 비어 있는 칸 이름 (예: "날짜·시각")
function missingFields(result) {
  const missing = []
  if (!result.name) missing.push('장소')
  if (!result.date) missing.push('날짜')
  if (!result.time) missing.push('시각')
  return missing.join('·')
}
const DAY_MS = 24 * 60 * 60 * 1000 // 하루를 밀리초로

// 읽은 날짜(yyyy-MM-dd)가 여행 며칠째인지 (여행 시작일 기준). 날짜를 못 읽었으면 1일차, 여행 날짜 밖이면 0
function dayOfTrip(readDate) {
  const conditions = store.planConditions || {}
  const start = conditions.startDate || store.planStartDate
  const end = conditions.endDate || store.planEndDate || start
  if (!readDate || !start) return 1
  if (readDate < start || readDate > end) return 0
  return Math.round((new Date(readDate) - new Date(start)) / DAY_MS) + 1
}

// 고른 사진을 화면에 바로 보여 줘요
function onFile(e) {
  const file = e.target.files && e.target.files[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => {
    photo.value = reader.result
  }
  reader.readAsDataURL(file)
  readFromServer(file)
}

async function readFromServer(file) {
  reading.value = true
  isFailed.value = false
  readNote.value = '글자를 읽는 중이에요…'
  // 앞 사진에서 읽은 값이 남지 않게 비우고 시작
  found.value = null
  name.value = ''
  time.value = ''
  date.value = ''
  try {
    found.value = await readCapture(file)
    name.value = found.value.name
    time.value = found.value.time || ''
    date.value = found.value.date || ''
    readNote.value = placeNote(found.value)
    const missing = missingFields(found.value)
    if (missing) readNote.value = missing + '을(를) 못 읽었어요 · 아래 칸에 직접 적어 주세요'
  } catch (e) {
    isFailed.value = true
    readNote.value = e.message
  } finally {
    reading.value = false
  }
}

function add() {
  const placeName = name.value.trim() || '예약한 곳'
  if (!TIME_PATTERN.test(time.value)) return showToast('시간을 HH:mm으로 적어 주세요')
  if (date.value && !DATE_PATTERN.test(date.value)) return showToast('날짜를 2026-11-15처럼 적어 주세요')
  // 예전엔 날짜를 버려서 2일차 예약도 1일차에 들어갔어요 (11/11 주형). 손으로 고친 날짜도 그대로 써요
  const day = dayOfTrip(date.value)
  if (day === 0) return showToast('예약 날짜가 여행 날짜 밖이에요 · 여행 날짜를 확인해 주세요')
  if (found.value && found.value.lat != null) {
    store.addDraftPlace({ id: found.value.placeId, name: placeName, address: found.value.address, lat: found.value.lat, lng: found.value.lng }, true, time.value, day)
  }
  // 여행 정보 화면에서 먼저 왔으면 조건이 아직 없어서 새로 만듦 (여행 정보의 다음 버튼이 이 값을 그대로 이어 씀)
  const conditions = store.planConditions || {}
  const fixed = conditions.fixed || []
  store.planConditions = { ...conditions, fixed: [...fixed, { name: placeName, time: time.value, day }] }
  showToast(day + '일차 ' + placeName + ' ' + time.value + ' 고정했어요')
  goBack('/plan/info')
}
</script>

<template>
  <div class="scr scr-ReserveCapture">
    <div class="app app--white">
      <header class="topbar">
        <button type="button" class="iconbtn" aria-label="뒤로가기" @click="goBack('/plan/info')"><i class="ic ic--l i-back" aria-hidden="true"></i></button>
        <h1 class="topbar__title">예약 캡처로 추가</h1>
      </header>

      <div class="scroll" style="padding-top: 4px; gap: 16px; bottom: 102px">
        <label class="rc-drop" for="rc-file">
          <img v-if="photo" class="rc-img" :src="photo" alt="올린 예약 캡처" />
          <span v-else class="rc-shot" aria-label="캡처 올리기 안내">
            <i class="ic i-camera" aria-hidden="true"></i>
            <b>예약 확인 화면을 올려 주세요</b>
            <span class="t-cap">예약 문자 · 앱 예약 내역 캡처</span>
            <span class="t-cap">장소 · 날짜 · 시각을 읽어요</span>
            <span class="t-cap">예약이 여러 개면 첫 번째만 읽어요 · 나머지는 다시 올려 주세요</span>
          </span>
          <span class="btn btn--secondary btn--sm rc-up"><i class="ic ic--s i-camera" aria-hidden="true"></i>{{ photo ? '다른 사진' : '캡처 올리기' }}</span>
          <input id="rc-file" type="file" accept="image/*" class="rc-file" @change="onFile" />
        </label>

        <div class="banner" :class="isFailed ? 'banner--warning' : found ? 'banner--success' : 'banner--info'">
          <i class="ic" :class="isFailed ? 'i-warning' : found ? 'i-success' : 'i-info'" aria-hidden="true"></i>
          <span class="banner__body"><span class="t-strong">{{ bannerTitle }}</span><span class="t-cap">{{ readNote }}</span></span>
        </div>

        <p v-if="found" class="t-cap rc-multi">예약이 여러 개면 첫 번째만 읽어요 · 나머지는 다시 올려 주세요</p>

        <div class="field"><label class="field__label" for="rc-name">장소</label><div class="field__box"><input id="rc-name" v-model="name" type="text" /></div></div>
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px">
          <div class="field"><label class="field__label" for="rc-date">날짜</label><div class="field__box"><input id="rc-date" v-model="date" type="text" inputmode="numeric" maxlength="10" placeholder="2026-11-15" /></div></div>
          <div class="field"><label class="field__label" for="rc-time">도착할 시간</label><div class="field__box"><input id="rc-time" v-model="time" type="text" inputmode="numeric" maxlength="5" placeholder="19:00" /></div></div>
        </div>
      </div>

      <div class="cta cta--white">
        <button type="button" class="btn btn--primary btn--lg btn--block" :disabled="!canAdd" @click="add"><i class="ic i-lock" aria-hidden="true"></i>고정 일정으로 넣기</button>
      </div>
    </div>
  </div>
</template>

<style>
.scr-ReserveCapture .rc-up{position:absolute;right:10px;bottom:10px}
.scr-ReserveCapture .rc-file{position:absolute;width:1px;height:1px;opacity:0}
.scr-ReserveCapture .field{min-width:0}
.scr-ReserveCapture .rc-drop{position:relative;display:flex;align-items:center;justify-content:center;height:260px;border-radius:var(--radius-lg);background:var(--surface-sunken);border:1.5px dashed var(--line-strong);cursor:pointer;overflow:hidden;transition:border-color .15s}
.scr-ReserveCapture .rc-drop:hover{border-color:var(--navy)}
.scr-ReserveCapture .rc-img{max-width:100%;max-height:100%;object-fit:contain}
.scr-ReserveCapture .rc-multi{margin-top:-8px}
.scr-ReserveCapture .rc-shot{display:flex;flex-direction:column;align-items:center;gap:4px;font-size:15px;color:var(--ink);text-align:center}
</style>
