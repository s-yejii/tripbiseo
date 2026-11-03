// 이메일 인증번호 받기·확인 (①, 회원가입·계정 찾기가 같이 씀). "use…" 함수: 여러 화면이 같은 상태·동작을 쓰게 묶은 함수
//   send()   → POST /api/auth/email-code       (유효 시간·다시 받기 대기 시간을 1초씩 줄여 보여 줌)
//   verify() → POST /api/auth/email-code/verify (계정 찾기면 아이디도 받음)
import { computed, onUnmounted, ref } from 'vue'
import { sendEmailCode, verifyEmailCode } from '@/api/members'

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const CODE_LENGTH = 6

export function useEmailCode(purpose) {
  const email = ref('')
  const code = ref('')
  const isSent = ref(false)
  const isVerified = ref(false)
  const isSending = ref(false)
  const isVerifying = ref(false)
  const error = ref('') // 서버가 보낸 이유 (인증번호가 맞지 않아요 등)
  const secondsLeft = ref(0) // 인증번호 남은 시간
  const resendLeft = ref(0) // 다시 받기까지 남은 시간
  const username = ref('') // 계정 찾기에서 확인된 아이디
  let timer = null

  const isEmailValid = computed(() => EMAIL_PATTERN.test(email.value.trim()))
  const canVerify = computed(() => isSent.value && !isVerified.value && code.value.length === CODE_LENGTH && secondsLeft.value > 0 && !isVerifying.value)
  const canSend = computed(() => isEmailValid.value && !isVerified.value && !isSending.value && resendLeft.value === 0)
  // 120 → "2:00"
  const timeText = computed(() => Math.floor(secondsLeft.value / 60) + ':' + String(secondsLeft.value % 60).padStart(2, '0'))

  function startTimer(expiresIn, resendAfter) {
    clearInterval(timer)
    secondsLeft.value = expiresIn
    resendLeft.value = resendAfter
    timer = setInterval(() => {
      if (secondsLeft.value > 0) secondsLeft.value--
      if (resendLeft.value > 0) resendLeft.value--
      if (secondsLeft.value === 0 && resendLeft.value === 0) clearInterval(timer)
    }, 1000)
  }

  async function send(accountId = null) {
    if (!canSend.value) return false
    isSending.value = true
    error.value = ''
    try {
      const res = await sendEmailCode(email.value.trim(), purpose, accountId)
      isSent.value = true
      code.value = ''
      startTimer(res.expiresInSec, res.resendAfterSec)
      return true
    } catch (e) {
      error.value = e.message
      return false
    } finally {
      isSending.value = false
    }
  }

  async function verify() {
    if (!canVerify.value) return false
    isVerifying.value = true
    error.value = ''
    try {
      const res = await verifyEmailCode(email.value.trim(), code.value, purpose)
      isVerified.value = true
      username.value = res.username || ''
      clearInterval(timer)
      secondsLeft.value = 0
      resendLeft.value = 0
      return true
    } catch (e) {
      error.value = e.message
      return false
    } finally {
      isVerifying.value = false
    }
  }

  // 이메일을 고치면 처음부터
  function reset() {
    clearInterval(timer)
    isSent.value = false
    isVerified.value = false
    code.value = ''
    error.value = ''
    secondsLeft.value = 0
    resendLeft.value = 0
    username.value = ''
  }

  // 숫자만 6자리까지
  function typeCode(value) {
    code.value = value.replace(/\D/g, '').slice(0, CODE_LENGTH)
    error.value = ''
  }

  onUnmounted(() => clearInterval(timer))

  return { email, code, isSent, isVerified, isSending, isVerifying, error, secondsLeft, resendLeft, username, isEmailValid, canSend, canVerify, timeText, send, verify, reset, typeCode }
}
