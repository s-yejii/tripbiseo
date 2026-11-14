<script setup>
// S08-c 한 곳 빼고 다시 만들기 (③)
// 11/13: 예전엔 가짜 장소(천호 쭈꾸미골목 등)를 보여 줬어요. 이제 진짜 "조건 바꿔 다시 만들기"로 보내요:
//   여행 정보 화면(/plan/info)으로 돌아가요. 고른 값은 그대로 남아 있어서 고쳐서 다시 만들면 돼요
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from '@/utils/toast'

const router = useRouter()

onMounted(() => {
  showToast('조건을 고쳐서 다시 만들어 보세요')
  router.replace('/plan/info')
})
</script>

<template>
  <div class="scr scr-GenTrim"><div class="app"></div></div>
</template>
