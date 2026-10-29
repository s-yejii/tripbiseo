<script setup>
// S19-d 내 위치 기록 (①): 여행 중 기록된 경로를 보고, 보관 기간을 정하고, 모두 지울 수 있어요
import { ref } from 'vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import TopBar from '@/components/common/TopBar.vue'
import { showToast } from '@/utils/toast'

// 가짜 데이터: 위치가 기록된 여행 (백엔드가 붙으면 location_log 테이블)
const records = ref([
  { title: '잔나비 공연 데이트', date: '10/24(토) · 오늘', meta: '6곳 · 4.2km', to: '/live' },
  { title: '천호 카페 투어', date: '10/11(토)', meta: '5곳 · 6.8km', to: '/retro' },
  { title: '을지로 하루', date: '10/4(토)', meta: '4곳 · 5.1km', to: '/retro' },
])
const keepOptions = [
  { days: 30, label: '30일' },
  { days: 90, label: '90일' },
  { days: 365, label: '1년' },
]
const keepDays = ref(90)
const asking = ref(false) // 지울지 묻는 창

function setKeep(option) {
  keepDays.value = option.days
  showToast('위치 기록을 ' + option.label + ' 동안 보관해요')
}

function clearAll() {
  records.value = []
  asking.value = false
  showToast('위치 기록을 모두 지웠어요')
}
</script>

<template>
  <div class="scr scr-LocHistory">
    <div class="app app--white">
      <TopBar title="내 위치 기록" back="/my" />

      <div class="scroll" style="padding-top: 4px; gap: 18px; bottom: 24px">
        <p class="t-cap" style="margin: 0; display: flex; gap: 6px; align-items: flex-start"><i class="ic ic--s i-info c-navy" aria-hidden="true" style="margin-top: 2px"></i>위치는 여행 중에만 기록해요. 도착·출발 자동 기록, 늦을 때 미리 알림, 다녀온 경로 다시보기에 써요.</p>

        <template v-if="records.length">
          <div class="lh-map" aria-label="최근 여행 이동 경로">
            <div class="map" aria-hidden="true"><img class="base live" src="/img/mapB.svg" alt="" style="top: -180px" /></div>
            <svg viewBox="0 0 390 180" style="position: absolute; inset: 0; width: 100%; height: 100%" aria-hidden="true"><polyline points="70,150 120,80 195,110 270,40 280,120 200,165" fill="none" stroke="#1F3A68" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" /></svg>
          </div>
          <section style="display: flex; flex-direction: column; gap: 8px" aria-labelledby="lh-list">
            <h2 id="lh-list" class="t-sub">기록된 여행</h2>
            <div class="group">
              <RouterLink v-for="r in records" :key="r.title" :to="r.to" class="row">
                <span class="ico-tile"><i class="ic i-route" aria-hidden="true"></i></span>
                <span class="row__body"><span class="row__title">{{ r.title }}</span><span class="row__meta tn">{{ r.date }} · {{ r.meta }}</span></span>
                <i class="ic i-next subtle" aria-hidden="true"></i>
              </RouterLink>
            </div>
          </section>
        </template>
        <div v-else class="lh-empty">
          <i class="ic ic--l i-route" aria-hidden="true"></i>
          <b class="t-strong" style="color: var(--ink)">저장된 위치 기록이 없어요</b>
          <span class="t-cap">다음 여행부터 다시 기록해요</span>
        </div>

        <section style="display: flex; flex-direction: column; gap: 8px" aria-labelledby="lh-keep">
          <h2 id="lh-keep" class="t-sub">보관 기간 <span class="t-cap" style="font-weight: 400">지나면 자동으로 지워요</span></h2>
          <div class="lh-keep" role="radiogroup" aria-label="보관 기간">
            <button v-for="option in keepOptions" :key="option.days" type="button" role="radio" class="chip" :class="{ 'is-on': keepDays === option.days }" :aria-checked="keepDays === option.days" style="justify-content: center" @click="setKeep(option)">{{ option.label }}</button>
          </div>
        </section>

        <button type="button" class="btn btn--secondary btn--lg btn--block c-danger" :disabled="!records.length" @click="asking = true"><i class="ic i-trash" aria-hidden="true"></i>위치 기록 모두 지우기</button>
      </div>

      <ConfirmModal
        v-if="asking"
        title="위치 기록을 모두 지울까요?"
        :text="'여행 ' + records.length + '개의 이동 경로가 지워져요. 회고의 경로 다시보기와 걸은 거리도 볼 수 없게 돼요. 일정과 결제 메모는 그대로 남아요.'"
        ok-text="지우기"
        ok-icon="i-trash"
        danger
        @cancel="asking = false"
        @ok="clearAll"
      />
    </div>
  </div>
</template>

<style>
.scr-LocHistory .lh-map{position:relative;height:180px;border-radius:var(--radius-lg);overflow:hidden;border:1px solid var(--line)}
.scr-LocHistory .lh-keep{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}
.scr-LocHistory .lh-empty{display:flex;flex-direction:column;align-items:center;gap:6px;padding:28px 0;color:var(--ink-muted);text-align:center}
</style>
