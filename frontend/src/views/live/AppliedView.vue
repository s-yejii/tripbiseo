<script setup>
// S17 바뀐 일정 (④): 대안을 적용한 뒤 무엇이 바뀌었는지 색으로 보여 줘요. 5분 안에는 되돌릴 수 있어요
import { useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'
import { modes } from '@/data/places'
import { showToast } from '@/utils/toast'

const router = useRouter()

// 가짜 데이터: 적용한 뒤 남은 일정 (change: 바뀐 내용 표시)
//   tag: 오른쪽 배지 / was·now: 바뀌기 전·후 / leg: 앞 장소에서 오는 이동
const stops = [
  { time: '16:36', name: '성내동 소품샵', img: '/img/sopum.jpg', changed: true, was: '60분', now: '30분', tag: '줄임', tagCls: 'badge--danger', leg: { mode: 'walk', min: 15 } },
  { time: '17:14', name: '천호 로데오거리', img: '/img/understand.jpg', changed: true, was: '17:44', now: '17:14', after: ' · 40분', tag: '시간 이동', tagCls: 'badge--danger', leg: { mode: 'walk', min: 8 } },
  { time: '18:30', name: '잔나비 콘서트', img: '/img/concert.svg', fixed: true, meta: '18:14 도착 · 16분 여유', tag: '예약 그대로', tagCls: 'badge--navy', leg: { mode: 'subway', min: 20 } },
]

function undo() {
  router.push('/live/delay')
  showToast('원래 일정으로 되돌렸어요')
}
</script>

<template>
  <div class="scr scr-Applied">
    <div class="app">
      <TopBar title="바뀐 일정" icon="close" to="/live">
        <button type="button" class="btn btn--sm btn--secondary" style="margin-right: 8px" @click="undo"><i class="ic ic--s i-undo" aria-hidden="true"></i>되돌리기</button>
      </TopBar>

      <div class="scroll" style="padding-top: 4px; gap: 14px; bottom: 102px">
        <div class="banner banner--success">
          <i class="ic ic--l i-success" aria-hidden="true"></i>
          <span class="banner__body">
            <span class="t-strong tn">공연장 18:14 도착 · 예약 여유 16분</span>
            <span class="t-cap">성내동 소품샵 30분 줄임 · 공연 입장을 지킬 수 있어요 · 지금 출발, 성내동 소품샵까지 도보 15분</span>
          </span>
        </div>

        <div class="legend" aria-label="색 안내">
          <span><i class="sw" style="background: var(--danger)"></i>바뀜</span>
          <span><i class="sw" style="background: var(--surface-raised); border: 1px solid var(--ink-subtle)"></i>그대로</span>
          <span><i class="sw" style="background: var(--surface-sunken); border: 1px dashed var(--line-strong)"></i>뺌</span>
          <span><i class="sw" style="background: var(--ink-subtle)"></i>다녀옴</span>
        </div>

        <ol class="tl" style="list-style: none; margin: 0; padding: 0">
          <li class="stop is-done">
            <span class="stop__time">13:00</span>
            <span class="stop__card"><span class="stop__body">
              <span class="stop__name"><i class="ic ic--s i-check c-success" aria-hidden="true"></i>천호 쭈꾸미골목 · 강풀만화거리 산책</span>
              <span class="stop__meta">다녀옴</span>
            </span></span>
          </li>
          <li class="leg"><span></span><span></span></li>
          <li class="stop">
            <span class="stop__time">16:21</span>
            <span class="stop__card">
              <img class="thumb" src="/img/daelim.jpg" alt="" />
              <span class="stop__body"><span class="stop__name">성내동 카페</span><span class="stop__meta">지금 · 여기서 출발</span></span>
              <span class="me" aria-label="지금 위치" style="position: static; margin: 0 8px 0 0; flex: none"></span>
            </span>
          </li>

          <template v-for="stop in stops" :key="stop.name">
            <li class="leg"><span></span><span><i class="ic ic--s" :class="modes[stop.leg.mode].icon" aria-hidden="true"></i>{{ modes[stop.leg.mode].label }} {{ stop.leg.min }}분</span></li>
            <li class="stop" :class="{ 'is-changed': stop.changed, 'is-fixed': stop.fixed }">
              <span class="stop__time">{{ stop.time }}</span>
              <span class="stop__card">
                <img class="thumb" :src="stop.img" alt="" />
                <span class="stop__body">
                  <span class="stop__name"><i v-if="stop.fixed" class="ic ic--s i-lock c-navy" aria-hidden="true"></i>{{ stop.name }}</span>
                  <span v-if="stop.changed" class="stop__meta tn"><span class="was">{{ stop.was }}</span><b class="c-danger">{{ stop.now }}</b>{{ stop.after }}</span>
                  <span v-else class="stop__meta tn">{{ stop.meta }}</span>
                </span>
                <span class="badge" :class="stop.tagCls">{{ stop.tag }}</span>
              </span>
            </li>
          </template>
        </ol>

        <div class="banner banner--info">
          <i class="ic i-bell" aria-hidden="true"></i>
          <span class="banner__body"><span class="t-strong tn">16:56에 다음 출발 알림이 와요</span><span class="t-cap">성내동 소품샵 → 천호 로데오거리</span></span>
        </div>
        <p class="t-cap" style="text-align: center">5분 안에는 위쪽 되돌리기로 원래 일정으로 돌아가요</p>
      </div>

      <div class="cta">
        <RouterLink to="/live" class="btn btn--primary btn--lg btn--block"><i class="ic i-trip" aria-hidden="true"></i>지도로 돌아가기</RouterLink>
      </div>
    </div>
  </div>
</template>

<style>
.scr-Applied .legend{display:flex;gap:14px;align-items:center}
.scr-Applied .legend span{display:inline-flex;align-items:center;gap:6px;font-size:13px;line-height:18px;color:var(--ink-muted)}
.scr-Applied .legend i.sw{width:12px;height:12px;border-radius:4px;display:inline-block}
.scr-Applied .topbar .btn{flex:none}
.scr-Applied .stop.is-gone .stop__card{background:var(--surface-sunken);border:1px dashed var(--line-strong);opacity:.8}
.scr-Applied .was{font-size:13px;color:var(--ink-subtle);text-decoration:line-through}
</style>
