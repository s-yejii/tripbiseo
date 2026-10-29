<script setup>
// S05 새 여행 · 방법 고르기 (③): AI가 짜 주기 / 지도에서 직접 고르기 / 무계획(곧 나와요)
import { ref } from 'vue'
import TopBar from '@/components/common/TopBar.vue'
import { showToast } from '@/utils/toast'

const notify = ref(false) // 무계획 여행 나오면 알림 받기

function toggleNotify() {
  notify.value = !notify.value
  showToast(notify.value ? '나오면 알림을 보낼게요' : '알림 신청을 취소했어요')
}
</script>

<template>
  <div class="scr scr-NewTrip">
    <div class="app">
      <TopBar title="새 여행" back="/" />

      <div class="scroll" style="padding-top: 4px; gap: 14px; bottom: 24px">
        <h2 class="h-display" style="margin: 0 0 4px">어떻게 시작할까요?</h2>

        <div role="list" style="display: flex; flex-direction: column; gap: 10px">
          <RouterLink to="/plan/info" class="nt-opt" role="listitem">
            <span class="ico-tile"><i class="ic ic--l i-sparkle" aria-hidden="true"></i></span>
            <span class="nt-opt__b">
              <span class="nt-opt__t"><b>AI가 일정 짜 주기</b><span class="badge badge--coral">추천</span></span>
              <span class="nt-opt__d">지역·날짜·취향만 고르면 영업시간과 예약 시간까지 맞춰 짜 드려요</span>
              <span class="nt-hook"><i class="ic ic--s i-lock" aria-hidden="true"></i>예약 하나만 넣어도 하루가 짜여요</span>
            </span>
            <i class="ic i-next subtle" aria-hidden="true"></i>
          </RouterLink>
          <RouterLink to="/plan/info?method=me" class="nt-opt" role="listitem">
            <span class="ico-tile"><i class="ic ic--l i-map" aria-hidden="true"></i></span>
            <span class="nt-opt__b">
              <span class="nt-opt__t"><b>지도에서 직접 고르기</b></span>
              <span class="nt-opt__d">가고 싶은 곳만 담으면 동선과 시간은 알아서 맞춰요</span>
            </span>
            <i class="ic i-next subtle" aria-hidden="true"></i>
          </RouterLink>
          <div class="nt-opt is-soon" role="listitem">
            <span class="ico-tile ico-tile--neutral"><i class="ic ic--l i-flag" aria-hidden="true"></i></span>
            <span class="nt-opt__b">
              <span class="nt-opt__t"><b>무계획 여행</b><span class="badge badge--neutral">곧 나와요</span></span>
              <span class="nt-opt__d">일정 없이 떠나면 그때그때 근처를 추천해요</span>
              <button type="button" class="textlink nt-noti" @click="toggleNotify">
                <i class="ic ic--s" :class="notify ? 'i-check' : 'i-bell'" aria-hidden="true"></i>{{ notify ? '나오면 알려 드릴게요' : '나오면 알림 받기' }}
              </button>
            </span>
          </div>
        </div>
        <p class="t-cap" style="margin: 2px 0 0; display: flex; gap: 6px; align-items: flex-start">
          <i class="ic ic--s i-info c-navy" aria-hidden="true" style="margin-top: 2px"></i>어느 쪽이든 다음 단계에서 인스타 링크로 가고 싶은 곳을 담을 수 있어요
        </p>
      </div>
    </div>
  </div>
</template>

<style>
.scr-NewTrip .nt-noti{justify-content:flex-start;padding:0;min-height:28px;color:var(--navy);font-weight:600}
.scr-NewTrip .nt-opt{display:flex;align-items:center;gap:14px;padding:18px 16px;border-radius:var(--radius-lg);background:var(--surface-raised);border:1px solid var(--line);text-decoration:none;color:var(--ink);cursor:pointer;text-align:left;font:inherit;width:100%}
.scr-NewTrip .nt-opt:hover{border-color:var(--navy)}
.scr-NewTrip .nt-opt .ico-tile{width:48px;height:48px;flex:none}
.scr-NewTrip .nt-opt__b{flex:1;min-width:0;display:flex;flex-direction:column;gap:4px}
.scr-NewTrip .nt-opt__t{display:flex;align-items:center;gap:6px}
.scr-NewTrip .nt-opt__t b{font-size:17px;line-height:24px}
.scr-NewTrip .nt-opt__d{font-size:13px;line-height:19px;color:var(--ink-muted)}
.scr-NewTrip .nt-hook{display:inline-flex;align-items:center;gap:6px;align-self:flex-start;margin-top:4px;padding:5px 10px;border-radius:var(--radius-full,999px);background:var(--navy-soft);color:var(--navy);font-size:13px;font-weight:600;line-height:18px}
.scr-NewTrip .nt-opt.is-soon{cursor:default;background:var(--surface)}
.scr-NewTrip .nt-opt.is-soon:hover{border-color:var(--line)}
.scr-NewTrip .nt-opt.is-soon .nt-opt__t b,.scr-NewTrip .nt-opt.is-soon .ico-tile{opacity:.6}
</style>
