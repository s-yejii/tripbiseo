<script setup>
// S02-b 회원가입 (①): 약관 동의, 아이디·비밀번호 규칙 검사, 이메일 인증, 닉네임
// 아이디 중복 GET /api/auth/check-id · 이메일 인증 POST /api/auth/email-code(·/verify) · 가입 POST /api/auth/signup
// 가입은 이메일 인증을 마친 브라우저에서만 돼요 (서버가 세션으로 확인)
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'
import { showToast } from '@/utils/toast'
import { get, post } from '@/api/http'
import { useEmailCode } from '@/utils/useEmailCode'

const router = useRouter()

const agreeTerms = ref(false)
const agreeLocation = ref(false)
const userId = ref('')
const password = ref('')
const password2 = ref('')
const showPassword = ref(false)
const mail = useEmailCode('signup')
const email = mail.email
const verified = mail.isVerified
const nickname = ref('')
const isJoining = ref(false)

const checkedId = ref('') // 중복 확인을 통과한 아이디
const takenId = ref('')   // 이미 있다고 나온 아이디
const agreeAll = computed(() => agreeTerms.value && agreeLocation.value)

// 아이디 규칙 (서버와 같게: 영문·숫자·_ 4~30자)
const idFormatOk = computed(() => /^[A-Za-z0-9_]{4,30}$/.test(userId.value))

// 아이디 칸 아래 안내: 규칙 → 중복 확인 결과
const idCheck = computed(() => {
  if (!userId.value) return { cls: '', icon: 'i-info', msg: '영문·숫자·_ 4~30자', ok: false }
  if (!idFormatOk.value) return { cls: 'is-error', icon: 'i-danger', msg: '영문·숫자·_ 4~30자만 쓸 수 있어요', ok: false }
  if (checkedId.value === userId.value) return { cls: 'is-success', icon: 'i-success', msg: '쓸 수 있는 아이디예요', ok: true }
  if (takenId.value === userId.value) return { cls: 'is-error', icon: 'i-danger', msg: '이미 쓰고 있는 아이디예요', ok: false }
  return { cls: '', icon: 'i-info', msg: '중복 확인을 눌러 주세요', ok: false }
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
const emailCheck = computed(() => {
  if (verified.value) return { cls: 'is-success', icon: 'i-success', msg: '이메일 인증을 마쳤어요' }
  if (email.value && !mail.isEmailValid.value) return { cls: 'is-error', icon: 'i-danger', msg: '이메일 형식이 맞지 않아요' }
  if (mail.error.value && !mail.isSent.value) return { cls: 'is-error', icon: 'i-danger', msg: mail.error.value }
  if (mail.isSent.value) return { cls: '', icon: 'i-info', msg: email.value + ' 로 인증번호를 보냈어요 · 메일이 안 보이면 스팸함도 봐 주세요' }
  return { cls: '', icon: '', msg: '' }
})
const sendLabel = computed(() => {
  if (verified.value) return '인증 완료'
  if (mail.isSending.value) return '보내는 중…'
  if (mail.resendLeft.value > 0) return mail.resendLeft.value + '초 뒤 다시'
  return mail.isSent.value ? '다시 받기' : '인증번호 받기'
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
  mail.reset()
}
async function sendCode() {
  if (await mail.send()) showToast('인증번호를 보냈어요')
}
async function verify() {
  if (await mail.verify()) showToast('이메일 인증을 마쳤어요')
}

// 중복 확인: 서버에 물어보고 결과를 기억
async function checkId() {
  try {
    // encodeURIComponent: 아이디를 주소에 안전하게 넣기
    const result = await get('/auth/check-id?username=' + encodeURIComponent(userId.value))
    if (result.available) checkedId.value = userId.value
    else takenId.value = userId.value
  } catch (e) {
    showToast(e.message)
  }
}

// 가입하기: 서버에 가입 → 같은 아이디로 바로 로그인 → 다음 화면
async function join() {
  if (!canJoin.value || isJoining.value) return
  isJoining.value = true
  const account = { username: userId.value, password: password.value }
  try {
    await post('/auth/signup', { ...account, nickname: nickname.value.trim(), email: email.value.trim() })
    await post('/auth/login', account)
    router.replace('/permission')
  } catch (e) {
    showToast(e.message) // 예: "이미 쓰는 아이디예요." (서버가 보낸 문장)
  } finally {
    isJoining.value = false
  }
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
          <div class="su-inline">
          <div class="field__box"><input id="su-id" v-model="userId" type="text" placeholder="영문·숫자·_ 4~30자" autocomplete="username" autocapitalize="off" aria-describedby="su-id-h" /></div>
          <button type="button" class="btn btn--secondary btn--md" :disabled="!idFormatOk || idCheck.ok" @click="checkId">{{ idCheck.ok ? '확인 완료' : '중복 확인' }}</button>
          </div>
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
            <button type="button" class="btn btn--secondary btn--md" :disabled="!mail.canSend.value" @click="sendCode">{{ sendLabel }}</button>
          </div>
          <p v-if="emailCheck.msg" class="field__help"><i class="ic ic--s" :class="emailCheck.icon" aria-hidden="true"></i>{{ emailCheck.msg }}</p>
        </div>

        <div v-if="mail.isSent.value && !verified" class="field" :class="{ 'is-error': mail.error.value }">
          <label class="field__label" for="su-code">인증번호</label>
          <div class="su-inline">
            <div class="field__box"><input id="su-code" :value="mail.code.value" type="text" inputmode="numeric" maxlength="6" placeholder="6자리 숫자" autocomplete="one-time-code" @input="mail.typeCode($event.target.value)" @keydown.enter.prevent="verify" /><span class="t-cap tn c-danger" style="margin-right: 12px">{{ mail.timeText.value }}</span></div>
            <button type="button" class="btn btn--primary btn--md" :disabled="!mail.canVerify.value" @click="verify">{{ mail.isVerifying.value ? '확인 중' : '확인' }}</button>
          </div>
          <p class="field__help"><i class="ic ic--s" :class="mail.error.value ? 'i-danger' : 'i-info'" aria-hidden="true"></i>{{ mail.error.value || (mail.secondsLeft.value > 0 ? '메일로 받은 6자리를 넣어 주세요' : '시간이 지났어요 · 다시 받아 주세요') }}</p>
        </div>

        <div class="field">
          <label class="field__label" for="su-nick">닉네임</label>
          <div class="field__box"><input id="su-nick" v-model="nickname" type="text" maxlength="10" placeholder="여행에서 보일 이름" /><span class="t-cap tn" style="margin-right: 12px">{{ nickname.length }}/10</span></div>
        </div>
      </form>

      <div class="cta cta--white">
        <button type="button" class="btn btn--primary btn--lg btn--block" :disabled="!canJoin" @click="join">가입하기</button>
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
