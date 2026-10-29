<script setup>
// S20-e 잠금화면 · 결제 알림 (①): 카드 결제가 오면 누가 냈는지 바로 적게 해요
import { useRouter } from 'vue-router'
import LockNoti from '@/components/member/LockNoti.vue'
import LockScreen from '@/components/member/LockScreen.vue'
import { showToast } from '@/utils/toast'

const router = useRouter()

function paidAlone() {
  router.push('/live')
  showToast('쭈꾸미 핀에 32,000원을 남겼어요')
}
</script>

<template>
  <div class="scr scr-LockPay">
    <LockScreen time="13:55">
      <LockNoti to="/live/pay" icon="i-won" title="천호 쭈꾸미골목에서 32,000원 결제" desc="신한카드 4821 · 누가 냈는지 적어 둘까요?" thumb="/img/gamjatang.jpg" is-new>
        <button type="button" @click="paidAlone">내가 다 냈어요</button>
        <RouterLink to="/live/pay" class="is-main">나눠서 적기</RouterLink>
      </LockNoti>
      <LockNoti to="/live" when="5분 전" icon="i-walk" title="다음 장소까지 도보 10분" desc="14:10 강풀만화거리 산책 · 14:00에 출발하면 돼요" old />
    </LockScreen>
  </div>
</template>
