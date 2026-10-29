<script setup>
// S12 장소 상세 (②): 사진, 별점, 보여 준 이유, 영업시간·브레이크 타임, 주소, 머무는 시간, 일정에 담기
// 주소: /place?id=daelim
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { places } from '@/data/places'
import { concertTrip } from '@/data/trips'
import { goBack } from '@/utils/back'
import { toDuration } from '@/utils/time'
import { showToast } from '@/utils/toast'

const route = useRoute()
const id = places[route.query.id] ? route.query.id : 'daelim'
const place = places[id]
const inPlan = concertTrip.stops.some((stop) => stop.id === id)
const saved = ref(false)

// "이 장소를 보여 준 이유" 꼬리표
const reasons = computed(() => {
  const list = ['★' + place.rate + ' (3점 이상만 보여요)']
  if (place.nopo) list.push('노포 선호')
  list.push(place.cat.includes('카페') || place.cat.includes('쇼핑') ? '내 취향 카페·쇼핑' : place.cat)
  list.push(place.outdoor ? '야외 · 비 오면 바꾸기 쉬움' : '실내')
  return list
})

function toggleSave() {
  saved.value = !saved.value
  showToast(saved.value ? '저장했어요' : '저장을 취소했어요')
}
function addToPlan() {
  showToast(place.name + '을(를) 동선에 맞춰 넣었어요')
  goBack('/plan/check')
}
</script>

<template>
  <div class="scr scr-Place">
    <div class="app app--white">
      <img :src="place.img" :alt="place.name + ' 사진'" class="pl-photo" />
      <span class="badge tn pl-count">1 / 12</span>
      <header class="topbar topbar--float">
        <button type="button" class="iconbtn iconbtn--float" aria-label="뒤로가기" @click="goBack('/plan/check')"><i class="ic ic--l i-back" aria-hidden="true"></i></button>
        <span style="flex: 1"></span>
        <button type="button" class="iconbtn iconbtn--float" :class="{ 'is-bm': saved }" :aria-label="saved ? '저장 취소' : '저장'" :aria-pressed="saved" @click="toggleSave"><i class="ic i-bookmark" aria-hidden="true"></i></button>
        <button type="button" class="iconbtn iconbtn--float" aria-label="공유" @click="showToast('링크를 복사했어요')"><i class="ic i-share" aria-hidden="true"></i></button>
      </header>

      <div class="scroll" style="top: 300px; padding-top: 16px; gap: 16px; bottom: 102px">
        <div style="display: flex; flex-direction: column; gap: 4px">
          <p class="t-cap">{{ place.cat }} · 천호동</p>
          <h1 class="h-display">{{ place.name }}</h1>
          <p style="margin: 0; display: flex; align-items: center; gap: 8px">
            <span class="t-strong tn" style="display: inline-flex; align-items: center; gap: 2px"><i class="ic ic--s i-star c-warning" aria-hidden="true"></i>{{ place.rate }}</span>
            <span class="badge badge--success"><i class="ic ic--s i-check" aria-hidden="true"></i>10/24 영업</span>
            <span v-if="place.nopo" class="badge badge--navy">노포 · 40년</span>
          </p>
          <button type="button" class="pl-naver" aria-label="네이버 플레이스에서 리뷰·메뉴 보기 (외부 링크)" @click="showToast('네이버 플레이스로 이동해요 · 실제 앱에선 외부 링크로 열려요')">
            <span class="pl-naver__mark" aria-hidden="true">N</span>네이버에서 리뷰·메뉴 보기<i class="ic i-next" aria-hidden="true"></i>
          </button>
        </div>

        <div class="why">
          <i class="ic i-info c-navy" aria-hidden="true"></i>
          <span><b class="t-strong">이 장소를 보여 준 이유</b><span class="why__tags"><span v-for="r in reasons" :key="r" class="badge badge--neutral">{{ r }}</span></span></span>
        </div>

        <div class="group">
          <div class="row">
            <i class="ic i-clock muted" aria-hidden="true"></i>
            <span class="row__body">
              <span class="t-strong tn">10/24(토) {{ place.hours || '언제나 열려 있어요' }}</span>
              <span v-if="place.brk" class="row__meta c-warning">브레이크 타임 {{ place.brk }} · 이 시간은 피해서 넣어요</span>
              <span v-else class="row__meta">브레이크 타임 없음 · 휴무 없음</span>
            </span>
          </div>
          <div class="row">
            <i class="ic i-pin muted" aria-hidden="true"></i>
            <span class="row__body"><span class="t-strong">성내동 322-32</span><span class="row__meta">천호역 도보 8분</span></span>
            <button type="button" class="btn btn--ghost btn--sm" @click="showToast('주소를 복사했어요')">복사</button>
          </div>
          <div class="row">
            <i class="ic i-timer muted" aria-hidden="true"></i>
            <span class="row__body"><span class="t-strong tn">보통 {{ toDuration(place.dur) }} 머물러요</span><span class="row__meta">담으면 이 시간으로 넣어요</span></span>
          </div>
        </div>
      </div>

      <div class="cta cta--white">
        <button type="button" class="btn btn--lg btn--block" :class="inPlan ? 'btn--secondary' : 'btn--primary'" :disabled="inPlan" @click="addToPlan">
          <i class="ic" :class="inPlan ? 'i-check' : 'i-plus'" aria-hidden="true"></i>{{ inPlan ? '일정에 담겨 있어요' : '일정에 담기' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style>
.scr-Place .pl-photo{position:absolute;top:0;left:0;width:100%;height:300px;object-fit:cover}
.scr-Place .pl-count{position:absolute;right:16px;top:260px;background:rgba(24,33,58,.7);color:#fff}
.scr-Place .why{display:flex;gap:10px;padding:12px;border-radius:var(--radius-md);background:var(--navy-soft)}
.scr-Place .why>span{display:flex;flex-direction:column;gap:6px}
.scr-Place .why__tags{display:flex;flex-wrap:wrap;gap:6px}
.scr-Place .why__tags .badge{background:#fff}
.scr-Place .is-bm{color:var(--coral)}
.scr-Place .pl-naver{align-self:flex-start;display:inline-flex;align-items:center;gap:8px;height:36px;margin-top:4px;padding:0 12px 0 8px;border:1px solid var(--line);border-radius:999px;background:#fff;font:inherit;font-size:13px;font-weight:600;color:var(--ink);cursor:pointer}
.scr-Place .pl-naver:hover{border-color:#03C75A;background:#F2FBF5}
.scr-Place .pl-naver__mark{width:20px;height:20px;border-radius:5px;background:#03C75A;color:#fff;font-size:12px;font-weight:800;display:flex;align-items:center;justify-content:center}
.scr-Place .pl-naver .ic{width:14px;height:14px;color:var(--ink-muted)}
</style>
