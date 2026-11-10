<script setup>
// W00 웹사이트 첫 화면 · 로그인 (⑤, 11/10). PC에서 웹으로 바로 들어오는 입구
// 로그인은 앱과 같은 API(① POST /api/auth/login)라서, 로그인하면 앱과 웹이 같은 로그인 상태가 돼요
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { get, post } from '@/api/http'

const route = useRoute()
const router = useRouter()
const username = ref('')
const password = ref('')
const errorMessage = ref('')
const isSending = ref(false)

// 로그인한 뒤 갈 곳: 다른 웹 페이지에서 왔으면 거기로, 아니면 지난 여행 목록
function nextPath() {
  const next = route.query.next
  if (typeof next === 'string' && next.startsWith('/web/')) return next
  return '/web/trips'
}

// 이미 로그인돼 있으면 바로 넘어감
onMounted(async () => {
  try {
    await get('/auth/me')
    router.replace(nextPath())
  } catch (e) {
    // 로그인 안 함 → 이 화면 그대로
  }
})

async function login() {
  if (!username.value || !password.value) {
    errorMessage.value = '아이디와 비밀번호를 넣어 주세요.'
    return
  }
  isSending.value = true
  errorMessage.value = ''
  try {
    await post('/auth/login', { username: username.value, password: password.value })
    router.replace(nextPath())
  } catch (e) {
    errorMessage.value = e.message
  } finally {
    isSending.value = false
  }
}
</script>

<template>
  <div class="wl">
    <section class="wl-hero">
      <p class="wl-badge">착착트립 웹</p>
      <h1>지난 여행을<br />큰 화면으로 다시 걸어 보세요</h1>
      <ul>
        <li>3D 지도 위에서 실제로 걸은 길을 내비처럼 다시 재생</li>
        <li>그날의 일출·일몰에 맞춰 바뀌는 하늘</li>
        <li>찍은 시각과 위치에 맞춰 지도에 놓이는 사진</li>
        <li>여행 메모와 사진 편집</li>
      </ul>
      <p class="wl-note">앱과 같은 계정이에요. 앱에서 기록한 여행이 그대로 보여요.</p>
    </section>

    <form class="wl-card" @submit.prevent="login">
      <h2>로그인</h2>
      <label>아이디<input v-model.trim="username" type="text" autocomplete="username" /></label>
      <label>비밀번호<input v-model="password" type="password" autocomplete="current-password" /></label>
      <p v-if="errorMessage" class="wl-error">{{ errorMessage }}</p>
      <button type="submit" class="wl-submit" :disabled="isSending">{{ isSending ? '로그인 중…' : '로그인' }}</button>
      <p class="wl-sub">계정이 없으면 <a href="/signup" target="_blank" rel="noopener">앱에서 회원가입</a></p>
    </form>
  </div>
</template>

<style scoped>
.wl { display: flex; align-items: center; justify-content: center; gap: 64px; min-height: 100vh; padding: 32px; background: linear-gradient(135deg, #1F3A68 0%, #2B4F8A 55%, #E8603F 140%); color: #fff; font-family: var(--font-sans); flex-wrap: wrap; }
.wl-hero { max-width: 460px; }
.wl-badge { display: inline-block; margin: 0; padding: 4px 10px; border-radius: var(--radius-full); background: rgba(255, 255, 255, 0.16); font-size: 13px; }
.wl-hero h1 { margin: 16px 0; font-size: 36px; line-height: 48px; word-break: keep-all; }
.wl-hero ul { margin: 0; padding-left: 20px; line-height: 30px; opacity: 0.92; }
.wl-note { margin-top: 20px; opacity: 0.75; font-size: 13px; }
.wl-card { display: flex; flex-direction: column; gap: 14px; width: 360px; max-width: 100%; padding: 28px; border-radius: var(--radius-lg); background: #fff; color: var(--ink); box-shadow: 0 16px 48px rgba(0, 0, 0, 0.25); }
.wl-card h2 { margin: 0 0 4px; font-size: 22px; }
.wl-card label { display: flex; flex-direction: column; gap: 6px; font-size: 13px; color: var(--ink-muted); }
.wl-card input { padding: 10px 12px; border: 1px solid var(--line); border-radius: var(--radius-md); font: inherit; font-size: 15px; color: var(--ink); }
.wl-card input:focus { outline: 2px solid var(--navy); outline-offset: -1px; }
.wl-error { margin: 0; color: var(--danger); font-size: 13px; }
.wl-submit { padding: 12px; border: 0; border-radius: var(--radius-md); background: var(--coral); color: #fff; font: inherit; font-size: 16px; font-weight: 700; cursor: pointer; }
.wl-submit:disabled { opacity: 0.6; cursor: default; }
.wl-sub { margin: 0; font-size: 13px; color: var(--ink-muted); text-align: center; }
.wl-sub a { color: var(--navy); }
</style>
