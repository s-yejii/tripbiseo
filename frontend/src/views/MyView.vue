<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '@/stores/auth'

const auth = useAuth()
const router = useRouter()
const notice = ref('')

async function logout() {
  await auth.logout()
  router.replace('/login')
}
// 개발용 숨은 버튼: 관리자만 API 키 화면으로
function secret() {
  if (auth.isAdmin) router.push('/admin/keys')
  else notice.value = '관리자 계정만 들어갈 수 있어요'
}
</script>

<template>
  <main class="page with-tab my">
    <h1 class="title">마이</h1>
    <section class="card profile">
      <span class="avatar">{{ (auth.user?.nickname || auth.user?.username || '?').slice(0, 1) }}</span>
      <div>
        <b>{{ auth.user?.nickname || auth.user?.username }}</b>
        <p class="muted small">@{{ auth.user?.username }}<span v-if="auth.isAdmin"> · 관리자</span></p>
      </div>
    </section>
    <button class="btn ghost block" @click="logout">로그아웃</button>

    <button class="secret" aria-label="개발자 설정" @click="secret">v1.0</button>
    <p v-if="notice" class="muted small center">{{ notice }}</p>
  </main>
</template>

<style scoped>
.my { display: flex; flex-direction: column; gap: 14px; }
.title { font-size: 24px; font-weight: 800; margin: 8px 0; }
.profile { display: flex; align-items: center; gap: 14px; }
.avatar { width: 52px; height: 52px; border-radius: 50%; background: var(--blue-soft); color: var(--blue); display: grid; place-items: center; font-size: 22px; font-weight: 800; }
.secret { margin: auto auto 0; font-size: 11px; color: #c4c9d2; padding: 6px 10px; }
.center { text-align: center; }
</style>
