import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from '@/stores/auth'

// meta.tab: 하단바에 불이 들어올 탭 / meta.public: 로그인 없이 / meta.admin: 관리자만
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
    { path: '/', component: () => import('@/views/HomeView.vue'), meta: { tab: '/' } },
    { path: '/trips', component: () => import('@/views/TripsView.vue'), meta: { tab: '/trips' } },
    { path: '/retro', component: () => import('@/views/RetroListView.vue'), meta: { tab: '/retro' } },
    { path: '/my', component: () => import('@/views/MyView.vue'), meta: { tab: '/my' } },
    { path: '/admin/keys', component: () => import('@/views/KeysView.vue'), meta: { admin: true } },

    { path: '/plan/ai', component: () => import('@/views/plan/PlanAiView.vue') },
    { path: '/plan/manual', component: () => import('@/views/plan/PlanManualView.vue') },
    { path: '/plan/check', component: () => import('@/views/plan/PlanCheckView.vue') },

    { path: '/trips/:id', component: () => import('@/views/trip/TripView.vue') },
    { path: '/trips/:id/live', component: () => import('@/views/trip/LiveView.vue') },
    { path: '/trips/:id/alternatives', component: () => import('@/views/trip/AlternativesView.vue') },
    { path: '/trips/:id/applied', component: () => import('@/views/trip/AppliedView.vue') },
    { path: '/trips/:id/retro', component: () => import('@/views/trip/RetroView.vue') },
    { path: '/places/:id', component: () => import('@/views/PlaceView.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

// 로그인해야 앱을 쓸 수 있음
router.beforeEach(async (to) => {
  const auth = useAuth()
  await auth.load()
  if (!to.meta.public && !auth.user) return '/login'
  if (to.path === '/login' && auth.user) return '/'
  if (to.meta.admin && !auth.isAdmin) return '/my'
})

export default router
