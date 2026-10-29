<script setup>
// S02-b 회원가입 (①): 약관 동의, 아이디·비밀번호 규칙 검사, 이메일 인증, 닉네임
// 아이디 중복·인증번호는 지금 가짜예요. 백엔드가 붙으면 POST /api/auth/signup
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'
import { showToast } from '@/utils/toast'

const router = useRouter()

const agreeTerms = ref(true)
const agreeLocation = ref(true)
const userId = ref('')
const password = ref('')
const password2 = ref('')
const showPassword = ref(false)
const email = ref('')
const codeSent = ref(false)
const code = ref('')
const codeWrong = ref(false)
const verified = ref(false)
const nickname = ref('')

const takenIds = ['haneul', 'trip', 'admin', 'tripmate', 'yeoheng'] // 가짜: 이미 있는 아이디
const agreeAll = computed(() => agreeTerms.value && agreeLocation.value)

// 아이디: 영문·숫자만, 중복 금지
const idCheck = computed(() => {
  if (!userId.value) return { cls: '', icon: 'i-info', msg: '영문과 숫자만 쓸 수 있어요', ok: false }
  if (/[^A-Za-z0-9]/.test(userId.value)) return { cls: 'is-error', icon: 'i-danger', msg: '기호와 한글, 띄어쓰기는 쓸 수 없어요', ok: false }
  if (takenIds.includes(userId.value.toLowerCase())) return { cls: 'is-error', icon: 'i-danger', msg: '이미 쓰고 있는 아이디예요', ok: false }
  return { cls: 'is-success', icon: 'i-success', msg: '쓸 수 있는 아이디예요', ok: true }
})

// 비밀번호 규칙 4가지
const rules = computed(() => [
  { label: '영문 (대·소문자 아무거나)', ok: /[A-Za-z]/.test(password.value) },
  { label: '숫자', ok: /\d/.test(password.value) },
  { label: '기호', ok: /[^A-Za-z0-9\s]/.test(password.value) },
  { label: '8자 이상', ok: password.value.length >= 8 },
])
const passwordOk = computed(() => rules.value.every((r) => r.ok))
function ruleClass(rule) {
  if (!password.value) return 'c-muted'
  return rule.ok ? 'c-success' : 'c-danger'
}

// 비밀번호 확인
const password2Check = computed(() => {
  if (!password2.value) return { cls: '', msg: '', ok: false }
  if (password2.value !== password.value) return { cls: 'is-error', msg: '비밀번호가 맞지 않아요', ok: false }
  if (!passwordOk.value) return { cls: 'is-error', msg: '위 비밀번호 규칙을 먼저 맞춰 주세요', ok: false }
  return { cls: 'is-success', msg: '', ok: true }
})

// 이메일 인증
const emailValid = computed(() => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value))
const emailCheck = computed(() => {
  if (verified.value) return { cls: 'is-success', icon: 'i-success', msg: '이메일 인증을 마쳤어요' }
  if (email.value && !emailValid.value) return { cls: 'is-error', icon: 'i-danger', msg: '이메일 형식이 맞지 않아요' }
  if (codeSent.value) return { cls: '', icon: 'i-info', msg: email.value + ' 로 인증번호를 보냈어요' }
  return { cls: '', icon: '', msg: '' }
})

const canJoin = computed(() => agreeAll.value && idCheck.value.ok && passwordOk.value && password2Check.value.ok && verified.value && nickname.value.trim().length > 0)
const hint = computed(() => {
  if (!agreeAll.value) return '필수 약관에 동의해야 가입할 수 있어요'
  if (canJoin.value || !(userId.value || password.value || email.value)) return ''
  if (!idCheck.value.ok) return '아이디를 확인해 주세요'
  if (!passwordOk.value) return '비밀번호 규칙을 맞춰 주세요'
  if (!password2Check.value.ok) return '비밀번호 확인이 맞지 않아요'
  if (!verified.value) return '이메일 인증을 마쳐 주세요'
  return '닉네임을 적어 주세요'
})

function toggleAll() {
  const next = !agreeAll.value
  agreeTerms.value = next
  agreeLocation.value = next
}
function changeEmail() {
  codeSent.value = false
  code.value = ''
  codeWrong.value = false
}
function sendCode() {
  codeSent.value = true
  code.value = ''
  codeWrong.value = false
  showToast('인증번호를 보냈어요')
}
function typeCode(e) {
  code.value = e.target.value.replace(/\D/g, '').slice(0, 6)
  codeWrong.value = false
}
// 가짜: 000000 이면 틀린 번호
function verify() {
  if (code.value === '000000') codeWrong.value = true
  else verified.value = true
}
</script>

<template>
  <div class="scr scr-Signup">
    <div class="app app--white">
      <TopBar title="회원가입" back="/login" />

      <form class="scroll" style="padding: 4px 24px 24px; gap: 18px; bottom: 102px" @submit.prevent>
        <div class="group">
          <button type="button" class="row" style="min-height: 52px" role="checkbox" :aria-checked="agreeAll" @click="toggleAll">
            <span class="check" :class="{ 'is-on': agreeAll }"><i class="ic ic--s i-check" aria-hidden="true"></i></span><span class="t-strong" style="flex: 1">전체 동의</span>
          </button>
          <button type="button" class="row" style="min-height: 48px" role="checkbox" :aria-checked="agreeTerms" @click="agreeTerms = !agreeTerms">
            <span class="check" :class="{ 'is-on': agreeTerms }"><i class="ic ic--s i-check" aria-hidden="true"></i></span><span class="t-body" style="flex: 1">[필수] 이용약관</span>
          </button>
          <button type="button" class="row" style="min-height: 48px" role="checkbox" :aria-checked="agreeLocation" @click="agreeLocation = !agreeLocation">
            <span class="check" :class="{ 'is-on': agreeLocation }"><i class="ic ic--s i-check" aria-hidden="true"></i></span><span class="t-body" style="flex: 1">[필수] 개인정보·위치정보 이용</span>
          </button>
        </div>

        <div class="field" :class="idCheck.cls">
          <label class="field__label" for="su-id">아이디</label>
          <div class="field__box"><input id="su-id" v-model="userId" type="text" placeholder="영문, 숫자만" autocomplete="username" autocapitalize="off" aria-describedby="su-id-h" /></div>
          <p id="su-id-h" class="field__help"><i class="ic ic--s" :class="idCheck.icon" aria-hidden="true"></i>{{ idCheck.msg }}</p>
        </div>

        <div class="field" :class="!password ? '' : passwordOk ? 'is-success' : 'is-error'">
          <label class="field__label" for="su-pw">비밀번호</label>
          <div class="field__box">
            <input id="su-pw" v-model="password" :type="showPassword ? 'text' : 'password'" autocomplete="new-password" aria-describedby="su-pw-h" />
            <button type="button" class="iconbtn" :aria-label="showPassword ? '비밀번호 숨기기' : '비밀번호 보기'" @click="showPassword = !showPassword"><i class="ic i-eye" :class="showPassword ? 'c-navy' : 'subtle'" aria-hidden="true"></i></button>
          </div>
          <p id="su-pw-h" class="field__help su-rules">
            <span v-for="rule in rules" :key="rule.label" :class="ruleClass(rule)"><i class="ic ic--s" :class="password && !rule.ok ? 'i-close' : 'i-check'" aria-hidden="true"></i>{{ rule.label }}</span>
          </p>
        </div>

        <div class="field" :class="password2Check.cls">
          <label class="field__label" for="su-pw2">비밀번호 확인</label>
          <div class="field__box">
            <input id="su-pw2" v-model="password2" type="password" autocomplete="new-password" aria-describedby="su-pw2-h" />
            <i v-if="password2Check.ok" class="ic i-check c-success" aria-hidden="true" style="margin-right: 12px"></i>
          </div>
          <p v-if="password2Check.msg" id="su-pw2-h" class="field__help"><i class="ic ic--s i-danger" aria-hidden="true"></i>{{ password2Check.msg }}</p>
        </div>

        <div class="field" :class="emailCheck.cls">
          <label class="field__label" for="su-mail">이메일</label>
          <div class="su-inline">
            <div class="field__box"><input id="su-mail" v-model="email" type="text" inputmode="email" autocapitalize="off" placeholder="trip@example.com" autocomplete="email" :readonly="verified" @input="changeEmail" /></div>
            <button type="button" class="btn btn--secondary btn--md" :disabled="!emailValid || verified" @click="sendCode">{{ verified ? '인증 완료' : codeSent ? '다시 받기' : '인증번호 받기' }}</button>
          </div>
          <p v-if="emailCheck.msg" class="field__help"><i class="ic ic--s" :class="emailCheck.icon" aria-hidden="true"></i>{{ emailCheck.msg }}</p>
        </div>

        <div v-if="codeSent && !verified" class="field" :class="{ 'is-error': codeWrong }">
          <label class="field__label" for="su-code">인증번호</label>
          <div class="su-inline">
            <div class="field__box"><input id="su-code" :value="code" type="text" inputmode="numeric" maxlength="6" placeholder="6자리 숫자" autocomplete="one-time-code" @input="typeCode" /><span class="t-cap tn c-danger" style="margin-right: 12px">4:59</span></div>
            <button type="button" class="btn btn--primary btn--md" :disabled="code.length !== 6" @click="verify">확인</button>
          </div>
          <p class="field__help"><i class="ic ic--s" :class="codeWrong ? 'i-danger' : 'i-info'" aria-hidden="true"></i>{{ codeWrong ? '인증번호가 맞지 않아요' : '메일로 받은 6자리를 넣어 주세요' }}</p>
        </div>

        <div class="field">
          <label class="field__label" for="su-nick">닉네임</label>
          <div class="field__box"><input id="su-nick" v-model="nickname" type="text" maxlength="10" placeholder="여행에서 보일 이름" /><span class="t-cap tn" style="margin-right: 12px">{{ nickname.length }}/10</span></div>
        </div>
      </form>

      <div class="cta cta--white">
        <button type="button" class="btn btn--primary btn--lg btn--block" :disabled="!canJoin" @click="router.push('/permission')">가입하기</button>
        <p v-if="hint" class="cta__hint">{{ hint }}</p>
      </div>
    </div>
  </div>
</template>

<style>
.scr-Signup .su-rules{flex-wrap:wrap;gap:4px 12px!important}
.scr-Signup .su-rules span{display:inline-flex;align-items:center;gap:2px}
.scr-Signup .field .su-rules .c-success{color:var(--success)}
.scr-Signup .field .su-rules .c-danger{color:var(--danger)}
.scr-Signup .field .su-rules .c-muted{color:var(--ink-muted)}
.scr-Signup .su-inline{display:flex;gap:8px;align-items:stretch}
.scr-Signup .su-inline .field__box{flex:1;min-width:0}
.scr-Signup .su-inline .btn{flex:none;height:auto;min-height:48px}
</style>
