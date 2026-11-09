<script setup>
// S20-a 알림 센터 (①): 내 알림을 종류별로 걸러 보고, 누르면 읽음 표시 후 그 화면으로 가요
//   서버: GET /api/notifications · POST /api/notifications/{id}/read · POST /api/notifications/read-all
//   알림이 쌓이는 때: 여행 중 화면의 출발·지각·영업 끝·비 소식 알림(④), 대안 적용(④), 내일 여행·오늘 여행·돌아보기(①이 알림함을 열 때)
//   눌러서 간 화면에서 뒤로가기를 누르면 알림함으로 돌아오게 주소에 ?back=/notifications 를 붙여요
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'
import PageMessage from '@/components/common/PageMessage.vue'
import { getNotificationList, readAllNotifications, readNotification } from '@/api/notifications'
import { showToast } from '@/utils/toast'
import { isPracticeTrip, withoutPracticeNotices } from '@/utils/practice'
import { getTrashTrips, getTripList } from '@/api/trips'

const FILTERS = [
  { key: 'all', label: '전체' },
  { key: 'trip', label: '여행 중' },
  { key: 'plan', label: '일정' },
  { key: 'info', label: '날씨' },
]
// 알림 단계 → 아이콘과 바탕색
const LOOK = {
  ok: { icon: 'i-success', tile: 'ok' },
  danger: { icon: 'i-warning', tile: 'coral' },
  warn: { icon: 'i-clock', tile: 'warn' },
  info: { icon: 'i-calendar', tile: '' },
}
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

const router = useRouter()
const notifications = ref([])
const filter = ref('all')
const isLoading = ref(true)
const error = ref('')

onMounted(load)

async function load() {
  isLoading.value = true
  error.value = ''
  try {
    const [list, trash] = await Promise.all([getNotificationList(), getTrashTrips().catch(() => [])]) // Promise.all: 두 요청을 같이
    const practiceIds = [...(await getTripList()), ...trash].filter(isPracticeTrip).map((trip) => trip.id)
    notifications.value = withoutPracticeNotices(list.notifications, practiceIds) // 시연 연습 알림은 숨김
  } catch (e) {
    if (e.status === 401) {
      router.replace('/login')
      return
    }
    error.value = e.message
  } finally {
    isLoading.value = false
  }
}

const unreadCount = computed(() => notifications.value.filter((n) => !n.readAt).length)

// 고른 종류만 남기고 날짜별로 묶어요 (서버가 최근 것부터 줌)
const groups = computed(() => {
  const result = []
  for (const n of notifications.value) {
    if (filter.value !== 'all' && n.kind !== filter.value) continue
    const day = dayLabel(n.createdAt)
    const last = result[result.length - 1]
    if (last && last.day === day) last.items.push(n)
    else result.push({ day, items: [n] })
  }
  return result
})

// "2026-11-03T15:42:00" → 오늘 / 어제 / 11월 1일 (토)
function dayLabel(createdAt) {
  const date = createdAt.slice(0, 10)
  const today = new Date()
  const yesterday = new Date(today.getTime() - 24 * 60 * 60 * 1000)
  if (date === toDateText(today)) return '오늘'
  if (date === toDateText(yesterday)) return '어제'
  const [y, m, d] = date.split('-').map(Number)
  return `${m}월 ${d}일 (${WEEKDAYS[new Date(y, m - 1, d).getDay()]})`
}
function toDateText(date) {
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${m}-${d}`
}

function lookOf(n) {
  if (n.kind === 'info') return { icon: 'i-rain', tile: 'warn' } // 지금은 날씨 알림만 info
  return LOOK[n.level] || LOOK.info
}

// 알림 누르기: 읽음 표시 → 그 화면으로 (뒤로가기하면 알림함으로)
async function open(n) {
  if (!n.readAt) {
    n.readAt = 'now' // 화면에서 먼저 점을 지움
    readNotification(n.id).catch(() => {}) // 읽음 저장이 실패해도 이동은 함
  }
  if (!n.link) return
  const mark = n.link.includes('?') ? '&' : '?'
  router.push(n.link + mark + 'back=/notifications')
}

async function readAll() {
  if (!unreadCount.value) return
  try {
    await readAllNotifications()
    for (const n of notifications.value) {
      if (!n.readAt) n.readAt = 'now'
    }
    showToast('모두 읽음으로 표시했어요')
  } catch (e) {
    showToast(e.message)
  }
}
</script>

<template>
  <div class="scr scr-Notifications">
    <div class="app">
      <TopBar title="알림" back="/">
        <button type="button" class="btn btn--ghost btn--sm" style="margin-right: 4px" :disabled="!unreadCount" @click="readAll">모두 읽음</button>
      </TopBar>

      <PageMessage v-if="isLoading" is-loading title="알림을 불러오는 중이에요" />
      <PageMessage v-else-if="error" icon="i-warning" title="알림을 불러오지 못했어요" :text="error" button="다시 시도" @retry="load" />

      <div v-else class="scroll" style="padding-top: 4px; gap: 12px; bottom: 34px">
        <div class="chips" role="tablist" aria-label="알림 종류">
          <button v-for="f in FILTERS" :key="f.key" type="button" role="tab" class="chip" :class="{ 'is-on': filter === f.key }" :aria-selected="filter === f.key" @click="filter = f.key">{{ f.label }}</button>
        </div>

        <PageMessage
          v-if="!groups.length"
          icon="i-bell"
          :title="notifications.length ? '이 종류의 알림은 없어요' : '아직 알림이 없어요'"
          text="여행 전날·여행 날, 여행 중 출발·지각·비 소식이 여기 쌓여요"
        />

        <template v-for="group in groups" :key="group.day">
          <p class="divlabel">{{ group.day }}</p>
          <div class="group">
            <button v-for="n in group.items" :key="n.id" type="button" class="row nrow" :class="{ unread: !n.readAt }" @click="open(n)">
              <span class="ico-tile" :class="lookOf(n).tile ? 'ico-tile--' + lookOf(n).tile : ''"><i class="ic" :class="lookOf(n).icon" aria-hidden="true"></i></span>
              <span class="row__body"><span class="row__title">{{ n.title }}</span><span v-if="n.text" class="row__meta">{{ n.text }}</span></span>
              <span class="t-cap tn">{{ n.createdAt.slice(11, 16) }}</span>
            </button>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<style>
.scr-Notifications .nrow{align-items:flex-start;width:100%;text-align:left;font:inherit;color:inherit;background:none;border:0;cursor:pointer}
.scr-Notifications .nrow .ico-tile{width:40px;height:40px}
.scr-Notifications .nrow.unread::before{content:"";position:absolute;left:6px;top:26px;width:6px;height:6px;border-radius:50%;background:var(--coral)}
.scr-Notifications .ico-tile--coral{background:var(--coral-soft);color:var(--coral-ink)}
</style>
