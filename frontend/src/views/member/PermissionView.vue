<script setup>
// S03 권한 허용 (①): 위치(항상)와 알림 두 가지를 왜 쓰는지 알려 주고 허용을 받아요
import { useRouter } from 'vue-router'
import PermissionFrame from '@/components/member/PermissionFrame.vue'
import { showToast } from '@/utils/toast'

const router = useRouter()

const items = [
  { icon: 'i-pin', title: '위치 · 항상 허용', desc: '여행 중에만 남은 일정을 다시 계산하고, 도착·출발을 기록해요' },
  { icon: 'i-bell', title: '알림', desc: '출발·지연·날씨만 알려요. 광고는 없어요' },
]

function allow() {
  router.push('/')
  showToast('위치·알림을 허용했어요')
}
</script>

<template>
  <div class="scr scr-Permission">
    <PermissionFrame>
      <div style="display: flex; flex-direction: column; gap: 6px">
        <h1 class="h-title">여행 중에 대신 챙기려면<br />두 가지만 허용해 주세요</h1>
      </div>
      <ul class="perm-list">
        <li v-for="item in items" :key="item.title">
          <span class="ico-tile"><i class="ic ic--l" :class="item.icon" aria-hidden="true"></i></span>
          <span class="perm-list__body">
            <span class="perm-list__title"><b class="t-sub">{{ item.title }}</b><span class="badge badge--navy">필수</span></span>
            <span class="t-cap">{{ item.desc }}</span>
          </span>
        </li>
      </ul>
      <div style="display: flex; flex-direction: column; gap: 4px">
        <button type="button" class="btn btn--primary btn--lg btn--block" @click="allow">허용하고 시작하기</button>
        <RouterLink to="/permission/denied" class="textlink">나중에 할게요</RouterLink>
      </div>
    </PermissionFrame>
  </div>
</template>
