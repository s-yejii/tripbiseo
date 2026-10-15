<script setup>
// 장소 상세 (화면 8): 사진·이름·평점·영업 상태, 위치/리뷰 탭, 영업시간
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { places } from '@/api'
import { placeIcon } from '@/utils/format'
import MapView from '@/components/map/MapView.vue'

const route = useRoute()
const router = useRouter()
const p = ref(null)
const tab = ref('loc')
const error = ref('')
onMounted(async () => {
  try {
    p.value = await places.detail(route.params.id)
  } catch (e) {
    error.value = e.message
  }
})
// 영업 상태 글: 닫혀 있을 때 '아직 안 열었는지 / 오늘 끝났는지 / 오늘 휴무인지'를 구분
const closedText = computed(() => {
  const t = (p.value?.hours ?? []).find((h) => h.today)?.text ?? ''
  if (/휴무/.test(t)) return '오늘 휴무'
  const m = t.match(/(\d{1,2}):(\d{2})\s*[-~]/)
  if (!m) return '영업 종료'
  const now = new Date(), open = Number(m[1]) * 60 + Number(m[2])
  return now.getHours() * 60 + now.getMinutes() < open ? `영업 전 · ${m[1].padStart(2, '0')}:${m[2]} 오픈` : '영업 종료'
})
const marker = computed(() => (p.value ? [{ id: 1, lat: p.value.lat, lng: p.value.lng, color: 'red', category: p.value.category }] : []))
</script>

<template>
  <div class="page place">
    <div class="hero" :style="p?.imageUrl ? { backgroundImage: `url(${p.imageUrl})` } : {}">
      <span v-if="!p?.imageUrl" class="big">{{ placeIcon(p?.category) }}</span>
      <button class="back" aria-label="뒤로" @click="router.back()">‹</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <template v-if="p">
      <h1>{{ p.name }}</h1>
      <p class="muted">{{ p.category || '장소' }}</p>
      <p class="rate">
        <template v-if="p.rating">★ <b>{{ p.rating }}</b> <span class="muted">({{ p.reviewCount }})</span> · </template>
        <span v-if="p.openNow === true" class="open">영업 중</span>
        <span v-else-if="p.openNow === false" class="closed">{{ closedText }}</span>
        <span v-else class="muted">영업 정보 없음</span>
      </p>

      <div class="tabs">
        <button :class="{ on: tab === 'loc' }" @click="tab = 'loc'">위치</button>
        <button :class="{ on: tab === 'rev' }" @click="tab = 'rev'">리뷰 {{ p.reviews.length || '' }}</button>
      </div>

      <template v-if="tab === 'loc'">
        <h3>위치</h3>
        <p class="addr">📍 {{ p.address || '주소 정보 없음' }}</p>
        <div class="map"><MapView :markers="marker" fit-key="1" /></div>
        <a class="naver" :href="p.naverUrl" target="_blank" rel="noopener">네이버 지도에서 보기 ↗</a>
        <template v-if="p.hours.length || p.restDate">
          <h3>영업시간</h3>
          <ul class="hours">
            <li v-for="h in p.hours" :key="h.day" :class="{ today: h.today }"><span>{{ h.day }}요일{{ h.today ? ' (오늘)' : '' }}</span><span>{{ h.text }}</span></li>
          </ul>
          <p v-if="p.restDate" class="muted small">휴무: {{ p.restDate }}</p>
        </template>
        <template v-if="p.amenities?.length">
          <h3>편의</h3>
          <p class="amen"><span v-for="a in p.amenities" :key="a" class="chip">{{ a }}</span></p>
        </template>
      </template>

      <ul v-else class="reviews">
        <li v-for="(r, i) in p.reviews" :key="i">{{ r }}</li>
        <li v-if="!p.reviews.length" class="empty">저장된 리뷰가 없어요</li>
      </ul>
    </template>
  </div>
</template>

<style scoped>
.place { padding: 0 0 30px; }
.place > *:not(.hero):not(.map) { margin-left: 18px; margin-right: 18px; }
.hero { position: relative; height: 230px; background: linear-gradient(135deg, #dbe4ff, #eef2ff) center / cover; display: grid; place-items: center; margin-bottom: 16px; }
.big { font-size: 72px; }
.back { position: absolute; top: 12px; left: 12px; width: 38px; height: 38px; border-radius: 50%; background: rgba(0, 0, 0, .35); color: #fff; font-size: 22px; }
h1 { font-size: 26px; font-weight: 800; }
.rate { margin-top: 6px; }
.open { color: var(--green); font-weight: 700; } .closed { color: var(--red); font-weight: 700; }
.tabs { display: flex; gap: 20px; border-bottom: 1px solid var(--line); margin-top: 18px !important; }
.tabs button { padding: 10px 0; font-weight: 700; color: var(--sub); border-bottom: 2px solid transparent; }
.tabs button.on { color: var(--ink); border-color: var(--ink); }
h3 { font-size: 17px; margin: 20px 0 8px; }
.addr { font-size: 14px; }
.map { height: 180px; margin: 10px 18px 0; border-radius: 14px; overflow: hidden; }
.naver { display: inline-block; margin-top: 8px; color: #03c75a; font-weight: 700; font-size: 14px; text-decoration: none; }
.hours { list-style: none; padding: 0; margin: 0; }
.hours li { display: flex; justify-content: space-between; padding: 8px 0; font-size: 14px; border-bottom: 1px solid var(--line); }
.hours li.today { font-weight: 800; }
.amen { display: flex; flex-wrap: wrap; gap: 6px; }
.reviews { list-style: none; padding: 0; }
.reviews li { padding: 14px 0; border-bottom: 1px solid var(--line); font-size: 14px; line-height: 1.6; }
</style>
