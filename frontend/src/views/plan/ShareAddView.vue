<script setup>
// S09-b 인스타 릴스에서 담기 (③): 인스타에서 "공유 → 여행어때"를 누르면 뜨는 창
// 찾은 장소를 고르고, 어느 여행에 넣을지 고르면 동선에 맞는 자리에 넣어요
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import BottomSheet from '@/components/common/BottomSheet.vue'
import { showToast } from '@/utils/toast'

const router = useRouter()

// 가짜 데이터: 여행마다 넣을 자리 (백엔드가 동선을 계산해 줄 값)
const trips = [
  { id: 'plan', title: '잔나비 공연 데이트', meta: '10/24 · 6곳', after: '천호 로데오거리', note: '가는 길에 있어서 이동 시간이 거의 안 늘어요', to: '/plan/check' },
  { id: 'plan2', title: '성내동 카페 2차', meta: '11/1 · 5곳', after: '천호 작은 갤러리숍', note: '동선에 맞춘 자리 · 이동 +4분', already: true, to: '/trips/detail' },
]

const checked = ref(true) // 천호 소품 편집숍 담기
const tripId = ref('plan')
const trip = computed(() => trips.find((t) => t.id === tripId.value))

function add() {
  if (trip.value.already) return showToast('이미 담겨 있어요')
  router.push(trip.value.to)
  showToast('천호 소품 편집숍을 담았어요')
}
</script>

<template>
  <div class="scr scr-ShareAdd">
    <div class="app">
      <img src="/img/sopum.jpg" alt="" aria-hidden="true" class="sa-bg" />
      <div class="dim" aria-hidden="true"></div>

      <BottomSheet role="dialog" aria-modal="true" style="gap: 14px">
        <div class="sheet__head">
          <span class="ico-tile sa-logo"><i class="ic ic--s i-pin" aria-hidden="true"></i></span>
          <h1 class="h-title">여행어때에 담기</h1>
          <RouterLink to="/new" class="iconbtn iconbtn--soft" aria-label="닫기"><i class="ic i-close" aria-hidden="true"></i></RouterLink>
        </div>
        <p class="t-cap" style="margin-top: -8px">릴스에서 장소 2곳을 찾았어요</p>

        <div style="display: flex; flex-direction: column; gap: 8px">
          <button type="button" role="checkbox" :aria-checked="checked" class="opt" :class="{ 'is-on': checked }" style="text-align: left" @click="checked = !checked">
            <img class="thumb" src="/img/sopum.jpg" alt="" />
            <span class="opt__body"><b class="t-sub">천호 소품 편집숍</b><span class="t-cap tn">쇼핑 · 영상 0:04</span></span>
            <span class="check" :class="{ 'is-on': checked }"><i class="ic ic--s i-check" aria-hidden="true"></i></span>
          </button>
          <div class="opt is-locked">
            <img class="thumb" src="/img/daelim.jpg" alt="" />
            <span class="opt__body"><b class="t-sub">성내동 카페</b><span class="t-cap">이미 담겨 있어요</span></span>
            <i class="ic i-check subtle" aria-hidden="true"></i>
          </div>
        </div>

        <div style="display: flex; flex-direction: column; gap: 8px">
          <p class="t-strong">어느 여행에 담을까요?</p>
          <div role="radiogroup" aria-label="여행 선택" class="sa-trips">
            <button v-for="t in trips" :key="t.id" type="button" role="radio" :aria-checked="t.id === tripId" class="opt sa-trip" :class="{ 'is-on': t.id === tripId }" @click="tripId = t.id">
              <b class="t-strong">{{ t.title }}</b><span class="t-cap tn">{{ t.meta }}</span>
            </button>
          </div>
        </div>

        <div v-if="checked" class="sa-where">
          <i class="ic i-route c-navy" aria-hidden="true"></i>
          <span><b class="t-strong">{{ trip.after }} 다음</b>에 넣을게요<span class="t-cap tn" style="display: block">{{ trip.note }}</span></span>
        </div>

        <button type="button" class="btn btn--primary btn--lg btn--block" :disabled="!checked" @click="add">{{ checked ? '1곳 담기' : '담을 곳을 골라 주세요' }}</button>
      </BottomSheet>
    </div>
  </div>
</template>

<style>
.scr-ShareAdd .sa-bg{position:absolute;inset:0;width:100%;height:100%;object-fit:cover}
.scr-ShareAdd .sa-logo{width:32px;height:32px;background:var(--navy);color:#fff}
.scr-ShareAdd .sa-trips{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}
.scr-ShareAdd .sa-trip{flex-direction:column;align-items:flex-start;gap:0;text-align:left}
.scr-ShareAdd .sa-where{display:flex;align-items:center;gap:10px;padding:12px;border-radius:var(--radius-md);background:var(--navy-soft);font-size:14px;line-height:20px}
</style>
