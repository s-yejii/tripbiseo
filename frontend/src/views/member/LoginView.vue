<script setup>
// S02-a 로그인 (①): 아이디·비밀번호 로그인, 간편 로그인(카카오·네이버·구글), 둘러보기
// 지금은 화면 검사만 해요. 백엔드가 붙으면 POST /api/auth/login
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const userId = ref('haneul_trip')
const password = ref('trip2024!')
const showPassword = ref(false)
const error = ref('')

function login() {
  if (!userId.value) return (error.value = '아이디를 넣어 주세요')
  if (password.value.length < 8) return (error.value = '비밀번호가 맞지 않아요 · 남은 시도 4번')
  router.push('/permission')
}
</script>

<template>
  <div class="scr scr-Login">
    <div class="app app--white">
      <header class="topbar">
        <RouterLink to="/onboarding" class="iconbtn" aria-label="뒤로가기"><i class="ic ic--l i-back" aria-hidden="true"></i></RouterLink>
      </header>

      <div class="scroll" style="padding: 8px 24px 0; gap: 24px">
        <h1 class="h-display">로그인하고<br />여행 중엔 맡겨 두세요</h1>

        <form style="display: flex; flex-direction: column; gap: 16px; margin: 0" @submit.prevent="login">
          <div class="field" :class="{ 'is-error': error && !userId }">
            <label class="field__label" for="uid">아이디</label>
            <div class="field__box">
              <input id="uid" v-model="userId" type="text" autocomplete="username" @input="error = ''" />
              <button v-if="userId" type="button" class="iconbtn" aria-label="아이디 지우기" @click="userId = ''"><i class="ic i-close subtle" aria-hidden="true"></i></button>
            </div>
          </div>
          <div class="field" :class="{ 'is-error': error }">
            <label class="field__label" for="pw">비밀번호</label>
            <div class="field__box">
              <input id="pw" v-model="password" :type="showPassword ? 'text' : 'password'" autocomplete="current-password" aria-describedby="pw-err" @input="error = ''" />
              <button type="button" class="iconbtn" :aria-label="showPassword ? '비밀번호 숨기기' : '비밀번호 보기'" @click="showPassword = !showPassword">
                <i class="ic i-eye" :class="showPassword ? 'c-navy' : 'subtle'" aria-hidden="true"></i>
              </button>
            </div>
            <p v-if="error" id="pw-err" class="field__help"><i class="ic ic--s i-danger" aria-hidden="true"></i>{{ error }}</p>
          </div>
          <button type="submit" class="btn btn--primary btn--lg btn--block">로그인</button>
        </form>

        <div class="login-or"><span></span><span class="t-cap">또는 간편 로그인</span><span></span></div>

        <div style="display: flex; flex-direction: column; gap: 8px">
          <RouterLink to="/permission" class="btn btn--kakao btn--lg btn--block">
            <svg class="sns-svg" viewBox="0 0 24 24" aria-hidden="true"><path fill="#000" d="M12 3C6.48 3 2 6.58 2 11c0 2.83 1.86 5.32 4.66 6.73l-.95 3.48c-.08.3.26.54.52.37l4.14-2.75c.53.06 1.07.1 1.63.1 5.52 0 10-3.58 10-8S17.52 3 12 3z" /></svg>카카오 로그인
          </RouterLink>
          <RouterLink to="/permission" class="btn btn--naver btn--lg btn--block">
            <svg class="sns-svg" viewBox="0 0 20 20" aria-hidden="true"><path fill="#fff" d="M13.56 10.7 6.17 0H0v20h6.44V9.3L13.83 20H20V0h-6.44z" transform="translate(3 3) scale(.7)" /></svg>네이버 로그인
          </RouterLink>
          <RouterLink to="/permission" class="btn btn--google btn--lg btn--block">
            <svg class="sns-svg" viewBox="0 0 48 48" aria-hidden="true">
              <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z" />
              <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z" />
              <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z" />
              <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z" />
            </svg>Google로 로그인
          </RouterLink>
        </div>

        <nav aria-label="계정 도움" class="login-help">
          <RouterLink to="/find-account" class="textlink">아이디·비밀번호 찾기</RouterLink>
          <span aria-hidden="true" class="login-help__bar"></span>
          <RouterLink to="/signup" class="textlink" style="color: var(--navy); font-weight: 700">가입하기</RouterLink>
        </nav>
      </div>

      <div class="cta cta--white" style="box-shadow: none; align-items: center">
        <RouterLink to="/guest" class="textlink">로그인 없이 둘러보기<i class="ic ic--s i-next" aria-hidden="true"></i></RouterLink>
      </div>
    </div>
  </div>
</template>

<style>
.scr-Login .login-or { display: flex; align-items: center; gap: 12px; }
.scr-Login .login-or span:first-child, .scr-Login .login-or span:last-child { flex: 1; height: 1px; background: var(--line); }
.scr-Login .login-help { display: flex; justify-content: center; align-items: center; margin-top: -12px; }
.scr-Login .login-help__bar { width: 1px; height: 12px; background: var(--line-strong); }
</style>
