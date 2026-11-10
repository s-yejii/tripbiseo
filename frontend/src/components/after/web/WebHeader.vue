<script setup>
// ⑤ 웹사이트 공통 머리줄 (11/10). 웹 페이지(/web/…) 위에 붙어요
// 로그인 안 했으면 웹 로그인(/web)으로 보내고, 다 보면 원래 페이지로 돌아오게 next를 붙여요
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { get, post } from '@/api/http'

const route = useRoute()
const router = useRouter()
const nickname = ref('')

onMounted(async () => {
  try {
    const user = await get('/auth/me')
    nickname.value = user.nickname || user.username
  } catch (e) {
    if (e.status === 401) router.replace({ path: '/web', query: { next: route.fullPath } })
  }
})

// 로그아웃하고 웹 로그인으로
async function logout() {
  try {
    await post('/auth/logout')
  } catch (e) {
    // 이미 로그아웃됐어도 로그인 화면으로
  }
  router.replace('/web')
}
</script>

<template>
  <header class="wh">
    <RouterLink to="/web/trips" class="wh-logo"><b>착착트립</b> <span>웹</span></RouterLink>
    <nav class="wh-nav">
      <RouterLink to="/web/trips" class="wh-link" :class="{ 'is-on': route.path === '/web/trips' }">지난 여행</RouterLink>
      <slot />
    </nav>
    <div class="wh-me">
      <span v-if="nickname" class="wh-name">{{ nickname }} 님</span>
      <a href="/" class="wh-link" target="_blank" rel="noopener">앱 열기</a>
      <button type="button" class="wh-link" @click="logout">로그아웃</button>
    </div>
  </header>
</template>

<style scoped>
.wh { display: flex; align-items: center; gap: 24px; height: 56px; padding: 0 24px; background: var(--navy); color: var(--on-navy); flex: none; }
.wh-logo { color: inherit; text-decoration: none; font-size: 18px; }
.wh-logo span { padding: 1px 6px; border-radius: var(--radius-sm); background: var(--coral); color: #fff; font-size: 12px; vertical-align: 2px; }
.wh-nav { display: flex; align-items: center; gap: 4px; flex: 1; }
.wh-me { display: flex; align-items: center; gap: 4px; }
.wh-name { margin-right: 8px; opacity: 0.85; }
.wh-link { padding: 6px 12px; border: 0; border-radius: var(--radius-md); background: none; color: inherit; font: inherit; text-decoration: none; cursor: pointer; opacity: 0.85; }
.wh-link:hover { background: rgba(255, 255, 255, 0.12); opacity: 1; }
.wh-link.is-on { background: rgba(255, 255, 255, 0.18); opacity: 1; font-weight: 700; }
</style>
