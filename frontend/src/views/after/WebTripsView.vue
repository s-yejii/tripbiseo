<script setup>
// W18 웹 지난 여행 목록 (⑤, 11/10). 끝난 여행만 카드로, 누르면 3D 회고로 가요 (11/11 메모·사진 버튼은 뺌)
// 여행 목록은 앱과 같은 API(② GET /api/trips)라서 앱에서 마친 여행이 그대로 보여요
import { computed, onMounted, ref } from 'vue'
import { get } from '@/api/http'
import { withoutPractice } from '@/utils/practice'
import { loadJournal } from '@/utils/webJournal'
import WebHeader from '@/components/after/web/WebHeader.vue'

const trips = ref([])
const isLoading = ref(true)
const errorMessage = ref('')

onMounted(async () => {
  document.title = '지난 여행 · 착착트립 웹'
  try {
    const list = await get('/trips')
    trips.value = withoutPractice(list)
  } catch (e) {
    if (e.status !== 401) errorMessage.value = '여행 목록을 불러오지 못했어요 (' + e.message + ')' // 401이면 머리줄이 로그인으로 보냄
  }
  isLoading.value = false
})

// 끝난 여행, 최근 것부터 (지운 여행은 빼고)
const doneTrips = computed(() => {
  const list = []
  for (const trip of trips.value) {
    if (trip.status === 'done' && !trip.deletedAt) list.push(toCard(trip))
  }
  list.sort((a, b) => (a.startDate < b.startDate ? 1 : -1))
  return list
})

// 카드에 보여 줄 값: 여행 정보 + 이 브라우저에 저장한 메모(여행 메모 첫 줄·대표 사진)
function toCard(trip) {
  const journal = loadJournal(trip.id)
  const firstLine = journal.diary.split('\n')[0]
  return {
    id: trip.id,
    title: trip.title,
    startDate: trip.startDate,
    endDate: trip.endDate,
    people: trip.people,
    stopCount: trip.stopCount,
    diaryLine: firstLine,
    coverUrl: journal.tripCover ? '/api/photos/' + journal.tripCover + '/file' : null,
  }
}

// "2026-11-06" ~ "2026-11-07" → "11월 6일 ~ 7일"
function periodText(card) {
  const start = card.startDate.split('-')
  const end = card.endDate.split('-')
  const startText = Number(start[1]) + '월 ' + Number(start[2]) + '일'
  if (card.startDate === card.endDate) return startText
  if (start[1] === end[1]) return startText + ' ~ ' + Number(end[2]) + '일'
  return startText + ' ~ ' + Number(end[1]) + '월 ' + Number(end[2]) + '일'
}
</script>

<template>
  <div class="wt">
    <WebHeader />
    <main class="wt-main">
      <h1>지난 여행 <small v-if="!isLoading">{{ doneTrips.length }}개</small></h1>

      <p v-if="isLoading" class="wt-state">불러오는 중…</p>
      <p v-else-if="errorMessage" class="wt-state">{{ errorMessage }}</p>
      <p v-else-if="doneTrips.length === 0" class="wt-state">아직 마친 여행이 없어요. 앱에서 여행을 마치면 여기에 보여요.</p>

      <div class="wt-grid">
        <article v-for="card in doneTrips" :key="card.id" class="wt-card">
          <div class="wt-cover" :style="card.coverUrl ? { backgroundImage: 'url(' + card.coverUrl + ')' } : {}">
            <span v-if="!card.coverUrl" class="wt-cover__empty">대표 사진이 아직 없어요</span>
          </div>
          <div class="wt-body">
            <h2>{{ card.title }}</h2>
            <p class="wt-meta">{{ periodText(card) }} · {{ card.people }}명 · 장소 {{ card.stopCount }}곳</p>
            <p v-if="card.diaryLine" class="wt-diary">“{{ card.diaryLine }}”</p>
            <div class="wt-actions">
              <RouterLink :to="'/web/retro?id=' + card.id" class="wt-btn wt-btn--main">3D 회고 보기</RouterLink>
            </div>
          </div>
        </article>
      </div>
    </main>
  </div>
</template>

<style scoped>
.wt { display: flex; flex-direction: column; min-height: 100vh; background: var(--surface); color: var(--ink); font-family: var(--font-sans); }
.wt-main { width: 100%; max-width: 1200px; margin: 0 auto; padding: 32px 24px 48px; }
.wt-main h1 { margin: 0; font-size: 28px; }
.wt-main h1 small { margin-left: 8px; font-size: 16px; font-weight: 400; color: var(--ink-muted); }
.wt-state { padding: 40px; border-radius: var(--radius-lg); background: #fff; text-align: center; color: var(--ink-muted); }
.wt-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 20px; }
.wt-card { overflow: hidden; border-radius: var(--radius-lg); background: #fff; box-shadow: var(--shadow-card); }
.wt-cover { display: grid; place-items: center; height: 180px; background: linear-gradient(135deg, #2B4F8A, #E8603F) center / cover no-repeat; }
.wt-cover__empty { padding: 4px 10px; border-radius: var(--radius-full); background: rgba(255, 255, 255, 0.2); color: #fff; font-size: 12px; }
.wt-body { padding: 16px 18px 18px; }
.wt-body h2 { margin: 0; font-size: 18px; line-height: 26px; }
.wt-meta { margin: 4px 0 0; color: var(--ink-muted); font-size: 13px; }
.wt-diary { margin: 6px 0 0; color: var(--ink-muted); font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.wt-actions { display: flex; gap: 8px; margin-top: 14px; }
.wt-btn { flex: 1; padding: 9px 0; border: 1px solid var(--line); border-radius: var(--radius-md); color: var(--ink); text-align: center; text-decoration: none; font-weight: 700; }
.wt-btn:hover { background: var(--surface); }
.wt-btn--main { border-color: var(--navy); background: var(--navy); color: var(--on-navy); }
.wt-btn--main:hover { background: var(--navy); opacity: 0.92; }
</style>
