<script setup>
// S20-f 잠금화면 · 들른 곳 확인 (①): 일정에 없던 곳에 머물면 들렀는지 물어봐요
import { useRouter } from 'vue-router'
import LockNoti from '@/components/member/LockNoti.vue'
import LockScreen from '@/components/member/LockScreen.vue'
import { showToast } from '@/utils/toast'

const router = useRouter()
const trip = { progress: 40, next: '다음 · 성내동 카페', badge: 'success', badgeIcon: 'i-lock', badgeText: '여유 15분' }

function answer(visited) {
  router.push('/live')
  showToast(visited ? '천호 소품 편집숍을 들른 곳으로 남겼어요' : '지도에서 지웠어요')
}
</script>

<template>
  <div class="scr scr-LockStay">
    <LockScreen time="14:31" :trip="trip">
      <LockNoti icon="i-pin" title="천호 소품 편집숍에 들렀나요?" desc="일정에 없던 곳에서 8분 머물고 6,500원 결제가 있었어요" thumb="/img/sopum.jpg" is-new>
        <button type="button" @click="answer(false)">아니요</button>
        <button type="button" class="is-main" @click="answer(true)">네, 들렀어요</button>
      </LockNoti>
    </LockScreen>
  </div>
</template>
