<script setup>
// 알림 버튼 (홈·여행 중 화면 오른쪽 위): 안 읽은 알림이 있으면 빨간 점
// 서버: GET /api/notifications 의 unreadCount (로그인 전이면 점 없이 버튼만)
import { onMounted, ref } from 'vue'
import { getNotificationList } from '@/api/notifications'

defineProps({
  float: { type: Boolean, default: false }, // 지도 위에 떠 있는 버튼 모양
})

const unreadCount = ref(0)

onMounted(async () => {
  try {
    unreadCount.value = (await getNotificationList()).unreadCount
  } catch {
    unreadCount.value = 0 // 알림을 못 읽어도 버튼은 보여 줌
  }
})
</script>

<template>
  <RouterLink to="/notifications" class="iconbtn noti-bell" :class="{ 'iconbtn--float': float }" :aria-label="unreadCount ? '알림 (안 읽은 알림 ' + unreadCount + '개)' : '알림'">
    <i class="ic ic--l i-bell" aria-hidden="true"></i>
    <span v-if="unreadCount" class="noti-bell__dot" aria-hidden="true"></span>
  </RouterLink>
</template>

<style>
.noti-bell { position: relative; }
.noti-bell__dot { position: absolute; top: 8px; right: 8px; width: 8px; height: 8px; border-radius: 50%; background: var(--coral); box-shadow: 0 0 0 2px var(--bg, #fff); }
</style>
