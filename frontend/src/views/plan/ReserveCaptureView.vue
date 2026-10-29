<script setup>
// S06-d 예약 캡처로 추가 (③): 예약 화면 캡처를 올리면 글자를 읽어 장소·날짜·시간을 채워요
// 글자 읽기는 지금 가짜예요. 백엔드가 붙으면 Gemini가 사진에서 읽어요
import { ref } from 'vue'
import { goBack } from '@/utils/back'
import { showToast } from '@/utils/toast'

const photo = ref('') // 올린 사진 (없으면 예시 화면)
const name = ref('잔나비 콘서트')
const time = ref('18:30')

// 고른 사진을 화면에 바로 보여 줘요
function onFile(e) {
  const file = e.target.files && e.target.files[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => {
    photo.value = reader.result
  }
  reader.readAsDataURL(file)
}
function add() {
  const placeName = name.value.trim() || '예약한 곳'
  showToast(placeName + ' ' + time.value + ' 고정했어요')
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
          <span v-else class="rc-shot" aria-label="예시 예약 화면">
            <span class="t-label" style="color: var(--success)"><i class="ic ic--s i-success" aria-hidden="true"></i> 예약 확정</span>
            <b style="font-size: 17px">잔나비 콘서트</b>
            <span>잠실실내체육관</span>
            <span class="tn">2026.10.24 (토) 19:00 · 입장 18:00</span>
            <span>R석 2매</span>
            <span class="t-cap" style="margin-top: 6px">예시 화면</span>
          </span>
          <span class="btn btn--secondary btn--sm rc-up"><i class="ic ic--s i-camera" aria-hidden="true"></i>{{ photo ? '다른 사진' : '캡처 올리기' }}</span>
          <input id="rc-file" type="file" accept="image/*" class="rc-file" @change="onFile" />
        </label>

        <div class="banner banner--success">
          <i class="ic i-success" aria-hidden="true"></i>
          <span class="banner__body"><span class="t-strong">글자를 읽어 아래에 채웠어요</span><span class="t-cap">공연 19:00 · 자리 찾기까지 30분 넉넉히 18:30 도착으로 잡았어요</span></span>
        </div>

        <div class="field"><label class="field__label" for="rc-name">장소</label><div class="field__box"><input id="rc-name" v-model="name" type="text" /></div></div>
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px">
          <div class="field"><label class="field__label" for="rc-date">날짜</label><div class="field__box"><input id="rc-date" type="text" value="10/24(토)" readonly /></div></div>
          <div class="field"><label class="field__label" for="rc-time">도착할 시간</label><div class="field__box"><input id="rc-time" v-model="time" type="text" inputmode="numeric" maxlength="5" placeholder="18:30" /></div></div>
        </div>
      </div>

      <div class="cta cta--white">
        <button type="button" class="btn btn--primary btn--lg btn--block" @click="add"><i class="ic i-lock" aria-hidden="true"></i>고정 일정으로 넣기</button>
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
.scr-ReserveCapture .rc-shot{width:200px;padding:18px 16px;border-radius:14px;background:var(--surface-raised);box-shadow:0 6px 20px rgba(24,33,58,.14);display:flex;flex-direction:column;gap:4px;font-size:14px;color:var(--ink)}
</style>
