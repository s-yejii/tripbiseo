<script setup>
// S09-b 인스타 릴스에서 담기 (③): 인스타에서 "공유 → 착착트립"으로 들어오는 주소
// 11/13: 예전엔 가짜 여행·가짜 장소(천호 소품샵·잔나비 공연)를 보여 줬어요. 이제 진짜 "링크 담기" 단계(/plan/info 1단계)로 보내고,
//   공유로 받은 링크(?url=)가 있으면 그 칸에 채워 둬요 (장소 찾기는 사용자가 눌러요)
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTripStore } from '@/stores/trip'

const route = useRoute()
const router = useRouter()
const store = useTripStore()

onMounted(() => {
  store.planStep = 0 // 링크 담기 단계
  const link = typeof route.query.url === 'string' ? route.query.url : ''
  router.replace(link ? { path: '/plan/info', query: { link } } : '/plan/info')
})
</script>

<template>
  <div class="scr scr-ShareAdd"><div class="app"></div></div>
</template>
