<script setup>
// S16-a 대안 · 시간 줄이기 (④): 남은 장소의 머무는 시간을 줄여서 예약에 맞춰요
// 장소를 누르면 S16-a2 슬라이더에서 몇 분 머물지 정해요
import { useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'
import AltTabs from '@/components/live/AltTabs.vue'
import { importance } from '@/data/places'

const router = useRouter()

// 가짜 데이터: 남은 일정과 줄일 수 있는지 (백엔드가 붙으면 대안 계산 API 결과)
const lateMinutes = 14
const remain = [
  { id: 'shoes', name: '성내동 소품샵', img: '/img/sopum.jpg', imp: 'maybe', stay: 60, recommendCut: 30 },
  { id: 'under', name: '천호 로데오거리', img: '/img/understand.jpg', imp: 'must', stay: 40 },
  { id: 'gogi', name: '잔나비 콘서트', img: '/img/concert.svg', fixed: '18:30' },
]

function apply() {
  router.push('/live/applied')
}
</script>

<template>
  <div class="scr scr-Alternatives">
    <div class="app">
      <TopBar title="대안 고르기" back="/live/delay">
        <span class="badge badge--warning tn" style="margin-right: 8px"><i class="ic ic--s i-warning" aria-hidden="true"></i>{{ lateMinutes }}분 늦음</span>
      </TopBar>

      <div class="scroll" style="padding-top: 4px; gap: 12px; bottom: 102px">
        <AltTabs active="trim" />

        <section class="tsum" aria-label="줄인 시간 합계" aria-live="polite">
          <div class="tsum__top">
            <span class="tsum__lbl">줄인 시간 합계</span>
            <span class="tsum__need">늦지 않으려면 <b>{{ lateMinutes }}분 이상</b></span>
          </div>
          <div class="tsum__mid">
            <span class="tsum__big">0분</span>
            <div class="tsum__bar" role="progressbar" aria-label="필요한 만큼 줄였는지" aria-valuenow="0" aria-valuemin="0" aria-valuemax="100"><i style="width: 0%"></i></div>
          </div>
          <p class="tsum__res muted"><i class="ic ic--s i-info" aria-hidden="true"></i><span>아직 줄인 곳이 없어요 · 추천은 성내동 소품샵 30분</span></p>
        </section>

        <div class="now-block">
          <span class="me" aria-hidden="true"></span>
          <img class="thumb" src="/img/daelim.jpg" alt="" style="width: 40px; height: 40px" />
          <span style="flex: 1"><span class="t-strong" style="display: block">지금 · 성내동 카페</span><span class="t-cap tn">16:21 · 꼭 갈 곳은 안 줄여요</span></span>
        </div>

        <p class="tr-hint"><i class="ic ic--s i-info c-navy" aria-hidden="true" style="margin-top: 2px"></i>장소를 눌러 머무는 시간을 줄여요. 여러 곳을 조금씩 줄여도 돼요.</p>

        <div style="display: flex; flex-direction: column; gap: 8px">
          <template v-for="stop in remain" :key="stop.id">
            <!-- 고정 일정: 못 줄여요 -->
            <div v-if="stop.fixed" class="opt is-locked">
              <img class="thumb" :src="stop.img" alt="" />
              <span class="opt__body"><b class="t-sub">{{ stop.name }}</b><span class="t-cap tn">{{ stop.fixed }} 고정 · 줄일 수 없어요</span></span>
              <i class="ic i-lock subtle" aria-hidden="true"></i>
            </div>
            <!-- 꼭 갈 곳: 안 줄여요 -->
            <div v-else-if="stop.imp === 'must'" class="opt is-locked">
              <img class="thumb" :src="stop.img" alt="" />
              <span class="opt__body">
                <span class="tr-name"><b class="t-sub">{{ stop.name }}</b><span class="imp imp--must">꼭</span></span>
                <span class="t-cap tn">{{ stop.stay }}분 · 꼭 갈 곳이라 줄이지 않아요</span>
              </span>
              <i class="ic i-lock subtle" aria-hidden="true"></i>
            </div>
            <!-- 줄일 수 있는 곳 -->
            <RouterLink v-else :to="'/live/alt/trim?id=' + stop.id" class="opt tr-row">
              <img class="thumb" :src="stop.img" alt="" />
              <span class="opt__body">
                <span class="tr-name"><b class="t-sub">{{ stop.name }}</b><span class="imp" :class="'imp--' + stop.imp">{{ importance[stop.imp] }}</span></span>
                <span class="tr-meta">{{ stop.stay }}분 머묾</span>
              </span>
              <span v-if="stop.recommendCut" class="badge badge--coral">추천 −{{ stop.recommendCut }}분</span>
              <i class="ic i-next subtle" aria-hidden="true"></i>
            </RouterLink>
          </template>
        </div>
      </div>

      <div class="cta">
        <button type="button" class="btn btn--primary btn--lg btn--block" @click="apply">추천대로 적용 · 성내동 소품샵 30분 줄이기</button>
      </div>
    </div>
  </div>
</template>

<style>
.scr-Alternatives .tsum{position:sticky;top:0;z-index:3;display:flex;flex-direction:column;gap:4px;padding:8px 12px;border-radius:var(--radius-lg);background:var(--surface-raised);border:1px solid var(--line)}
.scr-Alternatives .tsum__top{display:flex;align-items:center;justify-content:space-between;gap:8px;font-size:12px;line-height:16px;color:var(--ink-muted)}
.scr-Alternatives .tsum__need{font-variant-numeric:tabular-nums}
.scr-Alternatives .tsum__need b{color:var(--ink);font-weight:700}
.scr-Alternatives .tsum__mid{display:flex;align-items:center;gap:10px}
.scr-Alternatives .tsum__big{flex:none;font-size:20px;line-height:26px;font-weight:700;font-variant-numeric:tabular-nums}
.scr-Alternatives .tsum__bar{flex:1;position:relative;height:6px;border-radius:999px;background:var(--surface-sunken);overflow:hidden}
.scr-Alternatives .tsum__bar i{position:absolute;left:0;top:0;bottom:0;border-radius:999px;background:var(--warning);transition:width .2s}
.scr-Alternatives .tsum__res{display:flex;align-items:center;gap:4px;margin:0;font-size:12px;line-height:16px;font-weight:600;font-variant-numeric:tabular-nums}
.scr-Alternatives .tsum__res .ic{width:13px;height:13px;flex:none}
.scr-Alternatives .tr-name{display:flex;align-items:center;gap:6px}
.scr-Alternatives .tr-row{text-decoration:none}
.scr-Alternatives .tr-row .opt__body{gap:2px}
.scr-Alternatives .tr-meta{font-size:13px;color:var(--ink-muted);font-variant-numeric:tabular-nums}
.scr-Alternatives .tr-hint{display:flex;gap:6px;align-items:flex-start;margin:0;font-size:13px;color:var(--ink-muted)}
/* 줄인 뒤 모습 (줄인 시간 합계 계산을 붙일 때 써요) */
.scr-Alternatives .tsum__bar i.ok{background:var(--success)}
.scr-Alternatives .tsum__res .textlink{margin-left:auto;min-height:24px;padding:0 2px;font-size:12px;font-weight:500}
.scr-Alternatives .tr-meta s{color:var(--ink-subtle)}
.scr-Alternatives .tr-meta b{color:var(--success)}
.scr-Alternatives .tr-cut{flex:none;font-size:14px;font-weight:700;color:var(--success);font-variant-numeric:tabular-nums}
.scr-Alternatives .tr-row.is-set{border-color:var(--success);background:var(--success-soft,#EAF6EF)}
.scr-Alternatives .tr-row.is-gone .t-sub{text-decoration:line-through;color:var(--ink-subtle)}
</style>
