<script setup>
// S03-b 위치 권한 거부 (①): 위치 없이 쓰면 무엇이 멈추고 무엇은 그대로인지 보여 줘요
import { useRouter } from 'vue-router'
import PermissionFrame from '@/components/member/PermissionFrame.vue'
import { showToast } from '@/utils/toast'

const router = useRouter()

const items = [
  { icon: 'i-bell', title: '늦을 때 먼저 알림', stop: true },
  { icon: 'i-locate', title: '도착·출발 자동 기록', stop: true, desc: "'도착했어요'를 직접 눌러요" },
  { icon: 'i-route', title: '일정 만들기·점검', stop: false },
]

function allowInSettings() {
  router.push('/')
  showToast('위치를 허용했어요')
}
</script>

<template>
  <div class="scr scr-PermissionDenied">
    <PermissionFrame tight>
      <div style="display: flex; align-items: center; gap: 12px">
        <span class="ico-tile" style="width: 48px; height: 48px"><i class="ic ic--l i-nolocation" aria-hidden="true"></i></span>
        <h1 class="h-title">위치 없이 쓰면<br />여행 중 기능이 멈춰요</h1>
      </div>
      <ul class="perm-list">
        <li v-for="item in items" :key="item.title">
          <span class="ico-tile"><i class="ic ic--l" :class="item.icon" aria-hidden="true"></i></span>
          <span class="perm-list__body">
            <span class="perm-list__title">
              <b class="t-sub">{{ item.title }}</b>
              <span class="badge" :class="item.stop ? 'badge--neutral' : 'badge--navy'">{{ item.stop ? '멈춤' : '그대로' }}</span>
            </span>
            <span v-if="item.desc" class="t-cap">{{ item.desc }}</span>
          </span>
        </li>
      </ul>
      <div style="display: flex; flex-direction: column; gap: 4px">
        <button type="button" class="btn btn--primary btn--lg btn--block" @click="allowInSettings">설정에서 위치 허용하기</button>
        <RouterLink to="/" class="textlink">위치 없이 계속하기</RouterLink>
      </div>
    </PermissionFrame>
  </div>
</template>
