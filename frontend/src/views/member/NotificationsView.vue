<script setup>
// S20-a 알림 센터 (①): 여행 중·결제·날씨·점검 알림을 종류별로 걸러 보고, 누르면 그 화면으로 가요
import { computed, ref } from 'vue'
import TopBar from '@/components/common/TopBar.vue'
import { showToast } from '@/utils/toast'

// 가짜 데이터: 알림 (백엔드가 붙으면 notification 테이블)
//   kind: trip · pay · info / tile: 아이콘 배경 색 / unread: 안 읽음
const notifications = [
  { day: '오늘', kind: 'trip', to: '/live/applied', icon: 'i-success', tile: 'ok', title: '대안을 적용했어요', meta: '성내동 소품샵 30분 · 예약 여유 16분', time: '16:24', unread: true },
  { day: '오늘', kind: 'trip', to: '/live/alt', icon: 'i-warning', tile: 'warn', title: '공연 입장에 14분 늦을 것 같아요', meta: '대안 3개 보기', time: '16:21', unread: true },
  { day: '오늘', kind: 'trip', to: '/live', icon: 'i-walk', tile: '', title: '10분 뒤 출발할 시간이에요', meta: '15:52 성내동 카페 → 성내동 소품샵', time: '15:42' },
  { day: '오늘', kind: 'pay', to: '/live/pay', icon: 'i-won', tile: 'coral', title: '천호 쭈꾸미골목 32,000원 결제', meta: '누가 얼마 냈는지 적기', time: '13:55', unread: true },
  { day: '오늘', kind: 'trip', to: '/start-check', icon: 'i-clock', tile: '', title: '첫 장소로 출발할 시간이 지났어요', meta: '지금 여행 중인지 알려 주세요', time: '12:40' },
  { day: '오늘', kind: 'info', to: '/trips/today', icon: 'i-users', tile: 'warn', title: '18시부터 잔나비 공연 입장 · 종합운동장역 혼잡', meta: '8호선·2호선이 붐벼요 · 출발을 5분 당겨 둘게요', time: '10:00' },
  { day: '오늘', kind: 'info', to: '/live/alt/swap?why=rain', icon: 'i-rain', tile: 'warn', title: '오후 2–4시 비 소식이 있어요', meta: '강풀만화거리 산책을 실내로 바꾸기', time: '09:00' },
  { day: '어제', kind: 'info', to: '/trips/today', icon: 'i-check', tile: 'ok', title: '내일 여행 점검을 마쳤어요', meta: '6곳 모두 영업 · 브레이크 타임 피했어요', time: '20:00' },
  { day: '어제', kind: 'info', to: '/trips/today', icon: 'i-receipt', tile: '', title: '챙길 것 3개가 남았어요', meta: '보조배터리 · 우산 · 알러지약', time: '20:00' },
]
const filters = [
  { key: 'all', label: '전체' },
  { key: 'trip', label: '여행 중' },
  { key: 'pay', label: '결제' },
  { key: 'info', label: '날씨·점검' },
]

const filter = ref('all')
const allRead = ref(false)

// 고른 종류만 남기고 날짜별로 묶어요
const groups = computed(() => {
  const shown = notifications.filter((n) => filter.value === 'all' || n.kind === filter.value)
  const days = [...new Set(shown.map((n) => n.day))]
  return days.map((day) => ({ day, items: shown.filter((n) => n.day === day) }))
})

function readAll() {
  allRead.value = true
  showToast('모두 읽음으로 표시했어요')
}
</script>

<template>
  <div class="scr scr-Notifications">
    <div class="app">
      <TopBar title="알림" back="/">
        <button type="button" class="btn btn--ghost btn--sm" style="margin-right: 4px" @click="readAll">모두 읽음</button>
      </TopBar>

      <div class="scroll" style="padding-top: 4px; gap: 12px; bottom: 34px">
        <div class="chips" role="tablist" aria-label="알림 종류">
          <button v-for="f in filters" :key="f.key" type="button" role="tab" class="chip" :class="{ 'is-on': filter === f.key }" :aria-selected="filter === f.key" @click="filter = f.key">{{ f.label }}</button>
        </div>
        <template v-for="group in groups" :key="group.day">
          <p class="divlabel">{{ group.day }}</p>
          <div class="group">
            <RouterLink v-for="n in group.items" :key="n.title" :to="n.to" class="row nrow" :class="{ unread: n.unread && !allRead }">
              <span class="ico-tile" :class="n.tile ? 'ico-tile--' + n.tile : ''"><i class="ic" :class="n.icon" aria-hidden="true"></i></span>
              <span class="row__body"><span class="row__title">{{ n.title }}</span><span class="row__meta">{{ n.meta }}</span></span>
              <span class="t-cap tn">{{ n.time }}</span>
            </RouterLink>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<style>
.scr-Notifications .nrow{align-items:flex-start}
.scr-Notifications .nrow .ico-tile{width:40px;height:40px}
.scr-Notifications .nrow.unread::before{content:"";position:absolute;left:6px;top:26px;width:6px;height:6px;border-radius:50%;background:var(--coral)}
.scr-Notifications .ico-tile--coral{background:var(--coral-soft);color:var(--coral-ink)}
</style>
