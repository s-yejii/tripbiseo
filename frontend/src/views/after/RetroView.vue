<script setup>
// S19-a 지난 여행 다시보기 (⑤): 다녀온 동선을 재생하고, 핀을 누르면 시간·결제 메모를 봐요. 계획과 실제도 비교해요
import { computed, onUnmounted, ref } from 'vue'
import { toTime } from '@/utils/time'

// 가짜 데이터: 다녀온 곳 (백엔드가 붙으면 GET /api/trips/{id}/retro)
const visited = [
  { no: 1, x: 90, y: 250, name: '천호 골목 산책', time: '12:00–12:40', pay: '' },
  { no: 2, x: 140, y: 170, name: '천호 디저트 카페', time: '12:46–13:51', pay: '18,000원\n신예지: 9,000원\n이찬기: 9,000원' },
  { no: 3, x: 215, y: 205, name: '성내동 카페', time: '14:00–15:42', pay: '12,000원\n이찬기: 12,000원' },
  { no: 4, x: 290, y: 140, name: '천호 작은 갤러리숍', time: '16:05–16:40', pay: '' },
  { no: 5, x: 300, y: 250, name: '천호 소품 편집숍', time: '16:50–17:25', pay: '24,500원\n신예지: 24,500원' },
]
// 계획 vs 실제 (막대 길이 %)
const compare = [
  { name: '성내동 카페', plan: 55, real: 75, diff: '+22분', cls: 'c-warning' },
  { name: '디저트 카페', plan: 40, real: 54, diff: '+15분', cls: 'c-warning' },
  { name: '갤러리숍', plan: 30, real: 28, diff: '−2분', cls: 'muted' },
  { name: '소품 편집숍', plan: 30, real: 25, diff: '−5분', cls: 'muted' },
]

const progress = ref(68) // 재생 위치 (%)
const playing = ref(false)
const infoNo = ref(null)
let timer = null

const routePoints = visited.map((v) => v.x + ',' + v.y).join(' ')
const info = computed(() => visited.find((v) => v.no === infoNo.value))
// 재생 위치에 맞춰 핀 사이에서 지금 점을 계산해요 (12:00 ~ 18:14)
const me = computed(() => {
  const part = Math.min(3.999, (progress.value / 100) * 4)
  const i = Math.floor(part)
  const rate = part - i
  const a = visited[i]
  const b = visited[i + 1]
  return { x: Math.round(a.x + (b.x - a.x) * rate), y: Math.round(a.y + (b.y - a.y) * rate) }
})
const nowText = computed(() => toTime(720 + Math.round((progress.value / 100) * 374)) + (playing.value ? ' · 4배속' : ''))

function togglePlay() {
  if (playing.value) return stop()
  if (progress.value >= 100) progress.value = 0
  playing.value = true
  infoNo.value = null
  timer = setInterval(() => {
    progress.value = Math.min(100, progress.value + 1.5)
    if (progress.value >= 100) stop()
  }, 90)
}
function stop() {
  clearInterval(timer)
  playing.value = false
}
onUnmounted(() => clearInterval(timer))

function toggleInfo(no) {
  infoNo.value = infoNo.value === no ? null : no
}
</script>

<template>
  <div class="scr scr-Retro">
    <div class="app">
      <div class="retro-map" aria-hidden="true">
        <div class="map"><img class="base" src="/img/mapA.svg" alt="" style="top: -186px" /></div>
        <svg class="route" viewBox="0 0 390 330" style="position: absolute; inset: 0">
          <polyline :points="routePoints" fill="none" stroke="#1F3A68" stroke-width="5" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </div>
      <div class="retro-map">
        <button v-for="v in visited" :key="v.no" type="button" class="pin pin--done" :class="{ 'pin--sel': infoNo === v.no }" :style="{ left: v.x + 'px', top: v.y + 'px' }" :aria-label="v.no + ' ' + v.name" @click="toggleInfo(v.no)">
          {{ v.no }}<span v-if="v.pay" class="paydot" aria-hidden="true"><i class="ic i-won"></i></span>
        </button>
        <span class="me" :style="{ left: me.x + 'px', top: me.y + 'px' }" aria-hidden="true"></span>
        <div
          v-if="info"
          class="payinfo"
          :style="{ left: Math.max(12, Math.min(150, info.x - 30)) + 'px', ...(info.y > 160 ? { bottom: 330 - info.y + 24 + 'px' } : { top: info.y + 26 + 'px' }) }"
          role="dialog"
          aria-label="다녀온 곳 정보"
        >
          <b>{{ info.name }}</b>
          <span class="t-cap tn">{{ info.time }}</span>
          <template v-if="info.pay">
            <span class="t-cap tn retro-pay"><i class="ic ic--s i-won" aria-hidden="true"></i> 결제 메모</span>
            <pre>{{ info.pay }}</pre>
          </template>
          <span v-else class="t-cap">결제 내역 없음</span>
        </div>
      </div>

      <header class="topbar topbar--float">
        <RouterLink to="/trips" class="iconbtn iconbtn--float" aria-label="뒤로가기"><i class="ic ic--l i-back" aria-hidden="true"></i></RouterLink>
      </header>

      <div class="player" style="position: absolute; left: 16px; right: 16px; top: 262px">
        <button type="button" class="iconbtn retro-play" :aria-label="playing ? '멈추기' : '이동 경로 재생'" @click="togglePlay"><i class="ic" :class="playing ? 'i-pause' : 'i-play'" aria-hidden="true"></i></button>
        <span style="flex: 1; display: flex; flex-direction: column; gap: 4px">
          <span class="bar"><i :style="{ width: progress + '%' }"></i></span>
          <span style="display: flex; justify-content: space-between" class="t-cap tn"><span>12:00</span><b class="c-navy">{{ nowText }}</b><span>18:14</span></span>
        </span>
      </div>

      <div class="scroll" style="top: 340px; padding-top: 4px; gap: 14px; bottom: 102px">
        <div><h1 class="h-title">천호 카페 투어</h1><p class="t-cap">10월 11일 토요일 · 2명</p></div>

        <div class="stats">
          <div class="stat"><b>5/5</b><span class="t-cap">다녀온 곳</span></div>
          <div class="stat"><b>6.8km</b><span class="t-cap">걸은 거리</span></div>
          <div class="stat"><b class="c-success">16분</b><span class="t-cap">예약 여유</span></div>
        </div>

        <section class="card card--pad retro-cmp" aria-labelledby="cmp-h">
          <div class="sec-h" style="min-height: 28px">
            <h2 id="cmp-h" class="t-sub">계획 vs 실제</h2>
            <span class="t-cap" style="display: inline-flex; gap: 10px"><span><i class="retro-key" style="background: var(--line-strong)"></i> 계획</span><span><i class="retro-key" style="background: var(--navy)"></i> 실제</span></span>
          </div>
          <div v-for="row in compare" :key="row.name" class="cmp">
            <span class="t-strong" style="font-size: 14px">{{ row.name }}</span>
            <span class="cmp__bars"><i :style="{ width: row.plan + '%', background: 'var(--line-strong)' }"></i><i :style="{ width: row.real + '%', background: 'var(--navy)' }"></i></span>
            <b class="tn" :class="row.cls" style="font-size: 13px; text-align: right">{{ row.diff }}</b>
          </div>
          <div class="banner banner--info" style="margin-top: 6px">
            <i class="ic i-info" aria-hidden="true"></i>
            <span class="banner__body"><span class="t-strong">카페에선 평균 18분 더 머물렀어요</span><span class="t-cap">다음 일정엔 카페 시간을 넉넉히 잡을게요</span></span>
          </div>
        </section>
      </div>

      <div class="cta">
        <div class="cta__row">
          <RouterLink to="/album" class="btn btn--secondary btn--lg"><i class="ic i-camera" aria-hidden="true"></i>사진 담기</RouterLink>
          <RouterLink to="/theme?id=alley" class="btn btn--primary btn--lg"><i class="ic i-refresh" aria-hidden="true"></i>이 코스로 다시</RouterLink>
        </div>
      </div>
    </div>
  </div>
</template>

<style>
.scr-Retro .retro-map{position:absolute;left:0;right:0;top:0;height:330px;overflow:hidden}
.scr-Retro .retro-play{background:var(--navy);color:#fff;border-radius:50%;width:44px;height:44px}
.scr-Retro .retro-pay{color:var(--coral-ink);font-weight:600}
.scr-Retro .retro-cmp{display:flex;flex-direction:column;gap:6px}
.scr-Retro .retro-key{display:inline-block;width:10px;height:8px;border-radius:4px}
.scr-Retro .stats{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}
.scr-Retro .stat{padding:12px;border-radius:var(--radius-lg);background:var(--surface-raised);border:1px solid var(--line);display:flex;flex-direction:column;gap:2px}
.scr-Retro .stat b{font-size:20px;line-height:28px;font-weight:700;font-variant-numeric:tabular-nums}
.scr-Retro .cmp{display:grid;grid-template-columns:88px 1fr 44px;column-gap:10px;align-items:center;min-height:36px}
.scr-Retro .cmp__bars{display:flex;flex-direction:column;gap:3px}
.scr-Retro .cmp__bars i{display:block;height:8px;border-radius:999px}
.scr-Retro .player{display:flex;align-items:center;gap:12px;padding:10px 12px;border-radius:var(--radius-full);background:var(--surface-raised);box-shadow:var(--shadow-card)}
</style>
