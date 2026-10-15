import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { auth } from '@/api'

// 로그인 상태 (스프링 시큐리티 세션 쿠키)
export const useAuth = defineStore('auth', () => {
  const user = ref(null) // { username, nickname, admin }
  const checked = ref(false)
  const isAdmin = computed(() => Boolean(user.value?.admin))

  async function load() {
    if (checked.value) return user.value
    try {
      user.value = await auth.me()
    } catch {
      user.value = null
    }
    checked.value = true
    return user.value
  }
  async function login(username, password) {
    user.value = await auth.login(username, password)
  }
  async function signup(username, password, nickname) {
    await auth.signup(username, password, nickname)
    await login(username, password)
  }
  async function logout() {
    await auth.logout().catch(() => {})
    user.value = null
  }
  return { user, checked, isAdmin, load, login, signup, logout }
})
