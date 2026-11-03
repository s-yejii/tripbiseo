<script setup>
// S02-c 아이디·비밀번호 찾기 (①): 가입한 이메일로 받은 인증번호를 넣어요
//   아이디 찾기   : 이메일 → 인증번호 → 아이디 보여 주기
//   비밀번호 찾기 : 아이디 + 이메일 → 인증번호 → 새 비밀번호 (POST /api/auth/reset-password)
// 인증번호는 POST /api/auth/email-code (purpose: find) · /email-code/verify
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'
import { resetPassword } from '@/api/members'
import { useEmailCode } from '@/utils/useEmailCode'
import { showToast } from '@/utils/toast'

const PASSWORD_MIN = 8
const PASSWORD_MAX = 72

const router = useRouter()
const mail = useEmailCode('find')
const findPassword = ref(false) // false: 아이디 찾기 / true: 비밀번호 찾기
const accountId = ref('')
const newPassword = ref('')
const newPassword2 = ref('')
const isResetting = ref(false)

function switchTab(isPassword) {
  findPassword.value = isPassword
  mail.reset()
  newPassword.value = ''
  newPassword2.value = ''
}

const canSend = computed(() => mail.canSend.value && (!findPassword.value || accountId.value.trim().length > 0))
const sendLabel = computed(() => {
  if (mail.isVerified.value) return '인증 완료'
  if (mail.isSending.value) return '보내는 중…'
  if (mail.resendLeft.value > 0) return mail.resendLeft.value + '초 뒤 다시'
  return mail.isSent.value ? '다시 받기' : '인증번호 받기'
})
const passwordCheck = computed(() => {
  if (!newPassword.value) return { cls: '', msg: `${PASSWORD_MIN}~${PASSWORD_MAX}자`, ok: false }
  if (newPassword.value.length < PASSWORD_MIN || newPassword.value.length > PASSWORD_MAX) return { cls: 'is-error', msg: `${PASSWORD_MIN}~${PASSWORD_MAX}자로 정해 주세요`, ok: false }
  return { cls: 'is-success', msg: '쓸 수 있는 비밀번호예요', ok: true }
})
const password2Check = computed(() => {
  if (!newPassword2.value) return { cls: '', msg: '', ok: false }
  if (newPassword2.value !== newPassword.value) return { cls: 'is-error', msg: '새 비밀번호가 서로 달라요', ok: false }
  return { cls: 'is-success', msg: '맞아요', ok: true }
})

async function sendCode() {
  if (!canSend.value) return
  if (await mail.send(findPassword.value ? accountId.value.trim() : null)) showToast('인증번호를 보냈어요')
}
async function verify() {
  await mail.verify()
}

async function savePassword() {
  if (!passwordCheck.value.ok || !password2Check.value.ok || isResetting.value) return
  isResetting.value = true
  try {
    await resetPassword(mail.email.value.trim(), newPassword.value)
    showToast('비밀번호를 바꿨어요 · 새 비밀번호로 로그인해 주세요')
    router.replace('/login')
  } catch (e) {
    showToast(e.message)
  } finally {
    isResetting.value = false
  }
}
</script>

<template>
  <div class="scr scr-FindAccount">
    <div class="app app--white">
      <TopBar title="계정 찾기" back="/login" />

      <div class="scroll" style="padding: 8px 24px 0; gap: 20px">
        <div class="seg" role="tablist" aria-label="찾을 항목">
          <button type="button" role="tab" :aria-selected="!findPassword" :class="{ 'is-on': !findPassword }" @click="switchTab(false)">아이디 찾기</button>
          <button type="button" role="tab" :aria-selected="findPassword" :class="{ 'is-on': findPassword }" @click="switchTab(true)">비밀번호 찾기</button>
        </div>

        <!-- 아이디 찾기 결과 -->
        <section v-if="!findPassword && mail.isVerified.value" class="fa-result" aria-live="polite">
          <span class="ico-tile" style="width: 48px; height: 48px"><i class="ic ic--l i-user" aria-hidden="true"></i></span>
          <p class="t-cap" style="margin: 0">{{ mail.email.value }}로 가입한 아이디예요</p>
          <b class="t-big">{{ mail.username.value }}</b>
        </section>

        <template v-else-if="!(findPassword && mail.isVerified.value)">
          <div v-if="findPassword" class="field">
            <label class="field__label" for="fa-id">아이디</label>
            <div class="field__box"><input id="fa-id" v-model="accountId" type="text" placeholder="가입한 아이디" autocapitalize="off" autocomplete="username" :readonly="mail.isSent.value" /></div>
          </div>
          <div class="field" :class="{ 'is-error': mail.error.value && !mail.isSent.value }">
            <label class="field__label" for="fa-mail">가입한 이메일</label>
            <div class="fa-inline">
              <div class="field__box"><input id="fa-mail" v-model="mail.email.value" type="email" inputmode="email" autocapitalize="off" placeholder="trip@example.com" autocomplete="email" @input="mail.reset()" /></div>
              <button type="button" class="btn btn--secondary btn--md" :disabled="!canSend" @click="sendCode">{{ sendLabel }}</button>
            </div>
            <p v-if="mail.error.value && !mail.isSent.value" class="field__help"><i class="ic ic--s i-danger" aria-hidden="true"></i>{{ mail.error.value }}</p>
            <p v-else-if="mail.isSent.value" class="field__help"><i class="ic ic--s i-info" aria-hidden="true"></i>인증번호를 보냈어요 · 메일이 안 보이면 스팸함도 봐 주세요</p>
          </div>
          <div v-if="mail.isSent.value" class="field" :class="{ 'is-error': mail.error.value }">
            <label class="field__label" for="fa-code">인증번호</label>
            <div class="fa-inline">
              <div class="field__box"><input id="fa-code" :value="mail.code.value" type="text" inputmode="numeric" maxlength="6" placeholder="6자리 숫자" autocomplete="one-time-code" @input="mail.typeCode($event.target.value)" @keydown.enter.prevent="verify" /><span class="t-cap tn c-danger" style="margin-right: 12px">{{ mail.timeText.value }}</span></div>
              <button type="button" class="btn btn--primary btn--md" :disabled="!mail.canVerify.value" @click="verify">{{ mail.isVerifying.value ? '확인 중' : '확인' }}</button>
            </div>
            <p class="field__help"><i class="ic ic--s" :class="mail.error.value ? 'i-danger' : 'i-info'" aria-hidden="true"></i>{{ mail.error.value || (mail.secondsLeft.value > 0 ? '메일로 받은 6자리를 넣어 주세요' : '시간이 지났어요 · 다시 받아 주세요') }}</p>
          </div>
        </template>

        <!-- 비밀번호 찾기: 인증 뒤 새 비밀번호 -->
        <template v-else>
          <p class="t-cap" style="margin: 0"><i class="ic ic--s i-success c-success" aria-hidden="true"></i> {{ mail.username.value }} 계정의 이메일 인증을 마쳤어요. 새 비밀번호를 정해 주세요.</p>
          <input type="text" :value="mail.username.value" autocomplete="username" hidden />
          <div class="field" :class="passwordCheck.cls">
            <label class="field__label" for="fa-new">새 비밀번호</label>
            <div class="field__box"><input id="fa-new" v-model="newPassword" type="password" autocomplete="new-password" /></div>
            <p class="field__help">{{ passwordCheck.msg }}</p>
          </div>
          <div class="field" :class="password2Check.cls">
            <label class="field__label" for="fa-new2">새 비밀번호 확인</label>
            <div class="field__box"><input id="fa-new2" v-model="newPassword2" type="password" autocomplete="new-password" @keydown.enter="savePassword" /></div>
            <p v-if="password2Check.msg" class="field__help">{{ password2Check.msg }}</p>
          </div>
        </template>
      </div>

      <div class="cta cta--white">
        <button v-if="!findPassword && mail.isVerified.value" type="button" class="btn btn--primary btn--lg btn--block" @click="router.replace('/login')">로그인하러 가기</button>
        <button v-else-if="findPassword && mail.isVerified.value" type="button" class="btn btn--primary btn--lg btn--block" :disabled="!passwordCheck.ok || !password2Check.ok || isResetting" @click="savePassword">
          {{ isResetting ? '바꾸는 중…' : '새 비밀번호로 바꾸기' }}
        </button>
        <p v-else class="cta__hint">가입할 때 인증한 이메일로 6자리 인증번호를 보내 드려요</p>
      </div>
    </div>
  </div>
</template>

<style>
.scr-FindAccount .fa-inline { display: flex; gap: 8px; }
.scr-FindAccount .fa-inline .field__box { flex: 1; min-width: 0; }
.scr-FindAccount .fa-inline .btn { flex: none; }
.scr-FindAccount .fa-result { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 32px 0; text-align: center; }
</style>
