<script setup>
// S06-b 장소 검색 · 시간 고정 (③): 지역 / 출발지 / 꼭 지킬 시간(예약) 고르기를 한 화면이 같이 해요
// 주소: /plan/search?mode=area | origin | fixed
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import BottomSheet from '@/components/common/BottomSheet.vue'
import { goBack } from '@/utils/back'
import { showToast } from '@/utils/toast'

const route = useRoute()
const mode = ['area', 'origin', 'fixed'].includes(route.query.mode) ? route.query.mode : 'fixed'

// 가짜 데이터: 검색 결과 (백엔드가 붙으면 장소 검색 API)
const results = {
  area: [
    { name: '서울 천호동', desc: '카페·편집숍·맛집이 모여 있어요', img: '/img/alley.jpg' },
    { name: '서울 을지로', desc: '노포와 골목 술집', img: '/img/euljiro.jpg' },
    { name: '전주 한옥마을', desc: '1박 2일로 많이 가요', img: '/img/jeonju.jpg' },
    { name: '부산 해운대', desc: '바다·야경', img: '/img/busan.jpg' },
  ],
  origin: [
    { name: '천호역 1번 출구', desc: '5·8호선 · 성내동 카페까지 도보 8분', img: '/img/alley.jpg' },
    { name: '강동역 8번 출구', desc: '5호선 · 천호공원까지 도보 5분', img: '/img/seoulforest.jpg' },
    { name: '천호공원역 4번 출구', desc: '수인분당선', img: '/img/forestshop.jpg' },
  ],
  fixed: [
    { name: '잔나비 콘서트', desc: '공연 · 잠실실내체육관 · 19:00 공연 · 18:00 입장', img: '/img/concert.svg', time: '18:30' },
    { name: '잔나비 콘서트 (일요일 회차)', desc: '공연 · 잠실실내체육관 · 18:00 공연', img: '/img/concert.svg', time: '17:30' },
    { name: '천호 아트홀 전시', desc: '전시 · 천호동 · 10:00–19:00', img: '/img/gallery.jpg', time: '15:00' },
    { name: '천호 40년 순댓국', desc: '노포·한식 · 천호역 · 07:00–21:00', img: '/img/euljiro.jpg', time: '12:00' },
  ],
}
const texts = {
  area: { title: '어디로 가요?', placeholder: '지역 이름', button: '이 지역으로' },
  origin: { title: '어디서 출발해요?', placeholder: '역·건물 이름', button: '여기서 출발' },
  fixed: { title: '꼭 지킬 시간 추가', placeholder: '예약한 곳 이름', button: '고정 일정으로 넣기' },
}
const text = texts[mode]

const keyword = ref(mode === 'fixed' ? '천호' : '')
const now = { area: '서울 천호동', origin: '천호역 1번 출구', fixed: null }
const pickedName = ref(null) // null 이면 첫 결과(또는 지금 값)를 골라 둔 상태
const time = ref('')

const list = computed(() => {
  const word = keyword.value.trim()
  return results[mode].filter((item) => !word || item.name.includes(word) || item.desc.includes(word))
})
const picked = computed(() => {
  const name = pickedName.value || (mode === 'fixed' ? list.value[0]?.name : now[mode])
  return list.value.find((item) => item.name === name) || null
})

function pick(item) {
  pickedName.value = item.name
  time.value = item.time || ''
}
function search(value) {
  keyword.value = value
  pickedName.value = null
}
function done() {
  const item = picked.value
  if (mode === 'area') showToast('지역을 ' + item.name + '(으)로 바꿨어요')
  else if (mode === 'origin') showToast('출발지를 바꿨어요')
  else showToast(item.name + ' ' + (time.value || item.time) + ' 고정했어요')
  goBack('/plan/info')
}
</script>

<template>
  <div class="scr scr-PlaceSearch">
    <div class="app">
      <div class="dim" aria-hidden="true"></div>
      <BottomSheet role="dialog" aria-modal="true" style="top: 60px">
        <div class="sheet__head">
          <h1 class="h-title">{{ text.title }}</h1>
          <button type="button" class="iconbtn iconbtn--soft" aria-label="닫기" @click="goBack('/plan/info')"><i class="ic i-close" aria-hidden="true"></i></button>
        </div>
        <div class="field is-focus">
          <label class="sr" for="ps-q">장소 검색</label>
          <div class="field__box">
            <i class="ic i-search subtle" aria-hidden="true"></i>
            <input id="ps-q" :value="keyword" type="search" :placeholder="text.placeholder" autocomplete="off" @input="search($event.target.value)" />
            <button v-if="keyword" type="button" class="iconbtn" aria-label="검색어 지우기" @click="search('')"><i class="ic i-close subtle" aria-hidden="true"></i></button>
          </div>
        </div>

        <div role="radiogroup" aria-label="검색 결과" class="ps-list">
          <div v-for="item in list" :key="item.name" class="opt ps-item" :class="{ 'is-on': picked === item }">
            <button type="button" role="radio" :aria-checked="picked === item" class="ps-hit" @click="pick(item)">
              <img class="thumb" :src="item.img" alt="" />
              <span class="opt__body"><b class="t-sub">{{ item.name }}</b><span class="t-cap">{{ item.desc }}</span></span>
              <span class="radio"></span>
            </button>
            <template v-if="picked === item && mode === 'fixed'">
              <div style="display: flex; align-items: center; gap: 8px; padding: 0 12px">
                <label class="field__box" style="flex: 1; height: 44px" for="ps-time">
                  <i class="ic i-clock subtle" aria-hidden="true"></i>
                  <input id="ps-time" :value="time || item.time" type="time" style="font-variant-numeric: tabular-nums" @input="time = $event.target.value" />
                </label>
                <span class="badge badge--navy" style="height: 32px"><i class="ic ic--s i-lock" aria-hidden="true"></i>시간 고정</span>
              </div>
              <p class="t-cap" style="display: flex; gap: 4px; align-items: center; padding: 0 12px 12px"><i class="ic ic--s i-info c-navy" aria-hidden="true"></i>다른 일정이 이 시간에 맞춰 움직여요</p>
            </template>
          </div>
          <p v-if="!list.length" class="t-cap" style="text-align: center; padding: 24px 0">'{{ keyword }}' 결과가 없어요. 다른 이름으로 찾아보세요.</p>
        </div>

        <button type="button" class="btn btn--primary btn--lg btn--block" :disabled="!picked" @click="done">{{ text.button }}</button>
      </BottomSheet>
    </div>
  </div>
</template>

<style>
.scr-PlaceSearch .ps-list{display:flex;flex-direction:column;gap:8px;overflow:auto;min-height:0;flex:1;margin:0 -4px;padding:0 4px 4px}
.scr-PlaceSearch .ps-item{flex-direction:column;align-items:stretch;gap:12px;padding:0}
.scr-PlaceSearch .ps-hit{display:flex;align-items:center;gap:12px;padding:12px;border:0;background:none;text-align:left;font:inherit;color:inherit;cursor:pointer;width:100%;border-radius:var(--radius-lg)}
.scr-PlaceSearch .sheet{bottom:0}
</style>
