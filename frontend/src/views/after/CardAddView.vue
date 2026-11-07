<script setup>
// S21-d 카드사 연결 (⑤, 11/07 바꿈): 카드사에 로그인해서 쓴 내역(승인내역)을 가져오려고 연결해요 (CODEF)
// 예전 "카드 번호·유효기간 등록"은 돈을 내는(PG) 방식이라 다른 가게에서 쓴 결제를 못 봐서 바꿨어요
// 카드사 아이디·비밀번호는 저장하지 않아요. 서버가 암호화해서 CODEF로만 보내고, 받은 연결 번호(connectedId)만 card 테이블에 남겨요
// TODO(⑤): 서버 POST /api/cards (CODEF 계정 연결) 만들면 connect()에서 부르기. 지금은 화면만
import { computed, ref } from 'vue'
import { showToast } from '@/utils/toast'

// 카드사 목록. code는 CODEF 기관코드 (TODO: 개발가이드 "기관코드" 표와 한 번 더 맞춰 보기)
// needsCard: 아이디로 로그인할 때 카드 번호·카드 비밀번호도 필요한 카드사 (CODEF 승인내역 문서)
const COMPANIES = [
  { code: '0301', name: 'KB국민', needsCard: true, cardPasswordLength: 2 },
  { code: '0306', name: '신한', needsCard: false },
  { code: '0303', name: '삼성', needsCard: false },
  { code: '0302', name: '현대', needsCard: true, cardPasswordLength: 4 },
  { code: '0311', name: '롯데', needsCard: false },
  { code: '0313', name: '하나', needsCard: true, cardPasswordLength: 2 },
  { code: '0315', name: '우리', needsCard: false },
  { code: '0304', name: 'NH농협', needsCard: false },
  { code: '0305', name: 'BC', needsCard: false },
]

const companyCode = ref('')   // 고른 카드사 코드
const loginId = ref('')       // 카드사 홈페이지 아이디
const password = ref('')      // 카드사 비밀번호 (화면에만, 저장 안 함)
const cardNo = ref('')        // 현대·KB·하나만: 카드 번호 (숫자만)
const cardPassword = ref('')  // 현대 4자리, KB·하나 앞 2자리
const agreed = ref(false)

// 고른 카드사 { code, name, needsCard, ... }. 안 골랐으면 null
const company = computed(() => {
  for (const item of COMPANIES) {
    if (item.code === companyCode.value) return item
  }
  return null
})

// 연결하기 버튼을 누를 수 있나 + 못 누르면 이유
const hint = computed(() => {
  if (company.value === null) return '카드사를 골라 주세요'
  if (!loginId.value.trim() || !password.value) return '카드사 아이디와 비밀번호를 넣어 주세요'
  if (company.value.needsCard && cardNo.value.length < 15) return company.value.name + '카드는 카드 번호가 필요해요'
  if (company.value.needsCard && cardPassword.value.length !== company.value.cardPasswordLength) return '카드 비밀번호 ' + (company.value.cardPasswordLength === 4 ? '4자리' : '앞 2자리') + '를 넣어 주세요'
  if (!agreed.value) return '필수 동의에 체크해 주세요'
  return ''
})

// 숫자만 남기기
function typeCardNo(e) {
  cardNo.value = e.target.value.replace(/\D/g, '').slice(0, 16)
  e.target.value = cardNo.value
}
function typeCardPassword(e) {
  cardPassword.value = e.target.value.replace(/\D/g, '').slice(0, company.value ? company.value.cardPasswordLength : 4)
  e.target.value = cardPassword.value
}

// 연결하기: 아직 서버가 없어서 안내만. 비밀번호는 어디에도 보내지 않고 바로 지움
function connect() {
  password.value = ''
  cardPassword.value = ''
  showToast('카드사 연결은 준비 중이에요. 지금은 연습용 카드 내역으로 볼 수 있어요')
}
</script>

<template>
  <div class="scr scr-CardAdd">
    <div class="app app--white">
      <header class="topbar">
        <RouterLink to="/my" class="iconbtn" aria-label="뒤로가기"><i class="ic ic--l i-back" aria-hidden="true"></i></RouterLink>
        <h1 class="topbar__title">카드사 연결</h1>
      </header>

      <div class="scroll" style="padding-top: 8px; gap: 18px; bottom: 102px">
        <p class="t-cap ca-note">
          <i class="ic ic--s i-info c-navy" aria-hidden="true"></i>카드사에 연결하면 여행 기간에 쓴 카드 결제를 가져와 그 장소 핀에 붙여요. 결제는 하지 않아요.
        </p>

        <!-- 카드사 고르기 -->
        <div class="field">
          <span class="field__label">카드사</span>
          <div class="ca-companies" role="radiogroup" aria-label="카드사">
            <button v-for="item in COMPANIES" :key="item.code" type="button" class="chip" role="radio" :aria-checked="companyCode === item.code" :class="{ 'is-on': companyCode === item.code }" @click="companyCode = item.code">{{ item.name }}</button>
          </div>
        </div>

        <div class="field">
          <label class="field__label" for="ca-id">카드사 아이디</label>
          <div class="field__box"><input id="ca-id" v-model="loginId" type="text" placeholder="카드사 홈페이지 아이디" autocomplete="off" /></div>
        </div>
        <div class="field">
          <label class="field__label" for="ca-pw">비밀번호</label>
          <div class="field__box"><input id="ca-pw" v-model="password" type="password" placeholder="카드사 홈페이지 비밀번호" autocomplete="off" /></div>
          <p class="field__help ca-lock"><i class="ic ic--s i-lock" aria-hidden="true"></i>저장하지 않아요. 암호화해서 카드사 조회에만 써요</p>
        </div>

        <!-- 현대·KB·하나만: 카드 번호 + 카드 비밀번호 -->
        <div v-if="company && company.needsCard" class="ca-row">
          <div class="field">
            <label class="field__label" for="ca-no">카드 번호</label>
            <div class="field__box"><input id="ca-no" :value="cardNo" type="text" inputmode="numeric" placeholder="숫자만" autocomplete="off" @input="typeCardNo" /></div>
          </div>
          <div class="field">
            <label class="field__label" for="ca-cpw">카드 비밀번호 {{ company.cardPasswordLength === 4 ? '4자리' : '앞 2자리' }}</label>
            <div class="field__box"><input id="ca-cpw" :value="cardPassword" type="password" inputmode="numeric" :placeholder="company.cardPasswordLength === 4 ? '••••' : '••'" autocomplete="off" @input="typeCardPassword" /></div>
          </div>
        </div>

        <p class="t-cap">카드사 아이디를 모르면 카드사 앱·홈페이지의 "아이디 찾기"에서 찾을 수 있어요.</p>

        <button type="button" class="ca-agree" role="checkbox" :aria-checked="agreed" @click="agreed = !agreed">
          <span class="check" :class="{ 'is-on': agreed }"><i class="ic ic--s i-check" aria-hidden="true"></i></span>
          <span style="display: flex; flex-direction: column; gap: 2px">
            <span class="t-strong" style="font-size: 14px">[필수] 카드 승인내역을 여행 기록에 쓰는 데 동의</span>
            <span class="t-cap">여행 기간의 결제 시각·가맹점·금액만 가져와요</span>
          </span>
        </button>
      </div>

      <div class="cta cta--white">
        <button type="button" class="btn btn--primary btn--lg btn--block" :disabled="hint !== ''" @click="connect">연결하기</button>
        <p v-if="hint" class="cta__hint">{{ hint }}</p>
      </div>
    </div>
  </div>
</template>

<style>
.scr-CardAdd .ca-note{display:flex;gap:6px;align-items:flex-start;margin:0}
.scr-CardAdd .ca-note .ic{margin-top:2px;flex:none}
.scr-CardAdd .ca-companies{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}
.scr-CardAdd .ca-companies .chip{justify-content:center}
.scr-CardAdd .ca-lock{color:var(--ink-subtle)}
.scr-CardAdd .ca-row{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:12px}
.scr-CardAdd .ca-row>.field{min-width:0}
.scr-CardAdd .ca-row input{min-width:0;width:100%}
.scr-CardAdd .ca-agree{display:flex;align-items:flex-start;gap:10px;padding:12px 14px;border-radius:var(--radius-md);background:var(--surface);border:1px solid var(--line);cursor:pointer;text-align:left;font:inherit;color:var(--ink);width:100%}
.scr-CardAdd .ca-agree:hover{border-color:var(--navy)}
</style>
