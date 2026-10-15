<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '@/stores/auth'

const auth = useAuth()
const router = useRouter()
const mode = ref('login')
const form = ref({ username: '', password: '', nickname: '' })
const busy = ref(false)
const error = ref('')

async function submit() {
  error.value = ''
  busy.value = true
  try {
    const { username, password, nickname } = form.value
    if (mode.value === 'login') await auth.login(username.trim(), password)
    else await auth.signup(username.trim(), password, nickname.trim())
    router.replace('/')
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="page login">
    <h1 class="logo">여행어때</h1>
    <p class="muted">늦어지면 먼저 알려주는 여행 비서</p>

    <div class="switch">
      <button :class="{ on: mode === 'login' }" @click="mode = 'login'">로그인</button>
      <button :class="{ on: mode === 'signup' }" @click="mode = 'signup'">회원가입</button>
    </div>

    <form @submit.prevent="submit">
      <input v-model="form.username" class="field" placeholder="아이디 (영문·숫자 4자 이상)" autocomplete="username" required />
      <input v-model="form.password" class="field" type="password" placeholder="비밀번호 (8자 이상)" required
        :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" />
      <input v-if="mode === 'signup'" v-model="form.nickname" class="field" placeholder="닉네임" maxlength="20" />
      <p v-if="error" class="error">{{ error }}</p>
      <button class="btn primary block" :disabled="busy">{{ busy ? '확인 중…' : mode === 'login' ? '로그인' : '가입하고 시작하기' }}</button>
    </form>
  </main>
</template>

<style scoped>
.login { display: flex; flex-direction: column; justify-content: center; gap: 10px; padding: 0 28px 60px; }
.logo { font-size: 44px; font-weight: 900; color: var(--blue); letter-spacing: -2px; }
.switch { display: flex; margin: 28px 0 8px; border-bottom: 1px solid var(--line); }
.switch button { flex: 1; height: 44px; font-weight: 700; color: var(--sub); border-bottom: 2px solid transparent; }
.switch button.on { color: var(--ink); border-color: var(--ink); }
form { display: flex; flex-direction: column; gap: 10px; }
</style>
