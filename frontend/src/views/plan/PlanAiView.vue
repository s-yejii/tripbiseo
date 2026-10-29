<script setup>
// S06 여행 정보 입력 (③): 지역·날짜(달력)·출발지·인원·취향·이동 수단·꼭 지킬 시간·가고 싶은 곳
// 주소: /plan/info (AI로 짜기) · /plan/info?method=me (직접 고르기)
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'
import RangeCalendar from '@/components/plan/RangeCalendar.vue'
import WishLinks from '@/components/plan/WishLinks.vue'
import { showToast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const byMe = route.query.method === 'me'

const start = ref(1024)
const end = ref(null)
const people = ref(2)
const companion = ref('친구')
const styles = ref({ 맛집: true, 카페: true })
const moves = ref({ 지하철: true, 도보: true })
const fixedList = ref([]) // 꼭 지킬 시간 (장소 검색·예약 캡처로 넣어요)

const companions = ['혼자', '친구', '연인', '가족', '아이와']
const styleList = ['맛집', '카페', '노포', '산책', '쇼핑', '전시', '야경']
const moveList = [
  { label: '지하철', icon: 'i-subway' },
  { label: '도보', icon: 'i-walk' },
  { label: '버스', icon: 'i-bus' },
  { label: '자동차', icon: 'i-car' },
]

// 1024 → '10/24(토)'
function dateLabel(key) {
  const month = Math.floor(key / 100)
  const day = key % 100
  const week = '일월화수목금토'[new Date(2026, month - 1, day).getDay()]
  return `${month}/${day}(${week})`
}
const nights = computed(() => {
  if (!end.value) return 0
  const toDate = (key) => new Date(2026, Math.floor(key / 100) - 1, key % 100)
  return (toDate(end.value) - toDate(start.value)) / 86400000
})
const whenLabel = computed(() => (nights.value ? `${nights.value}박 ${nights.value + 1}일 · ${dateLabel(start.value)}–${dateLabel(end.value)}` : `당일치기 · ${dateLabel(start.value)}`))
const noStyle = computed(() => !styleList.some((s) => styles.value[s]))

function changePeople(diff) {
  people.value = Math.max(1, Math.min(10, people.value + diff))
  if (people.value === 1) companion.value = '혼자'
  else if (companion.value === '혼자') companion.value = '친구'
}
function pickCompanion(name) {
  companion.value = name
  people.value = name === '혼자' ? 1 : Math.max(2, people.value)
}
function toggleMove(name) {
  const next = { ...moves.value, [name]: !moves.value[name] }
  if (!Object.values(next).some(Boolean)) return showToast('이동 수단을 하나는 골라 주세요')
  moves.value = next
}
function goNext() {
  router.push(byMe ? '/plan/manual' : '/plan/chat')
}
</script>

<template>
  <div class="scr scr-PlanAI">
    <div class="app">
      <TopBar title="여행 정보" back="/new"><span class="topbar__side tn">{{ byMe ? '2 / 3' : '2 / 4' }}</span></TopBar>

      <div class="scroll" style="padding-top: 4px; gap: 12px; bottom: 102px">
        <section class="card card--pad pa-sec" aria-labelledby="pa-where">
          <h2 id="pa-where" class="t-sub">어디로 가요?</h2>
          <RouterLink to="/plan/search?mode=area" class="field__box pickbox"><i class="ic i-pin subtle" aria-hidden="true"></i><span class="val">서울 천호동</span><i class="ic i-next subtle" aria-hidden="true"></i></RouterLink>
        </section>

        <section class="card card--pad pa-sec" aria-labelledby="pa-when">
          <div class="sec-h"><h2 id="pa-when" class="t-sub">언제 가요?</h2><span class="badge badge--navy tn">{{ whenLabel }}</span></div>
          <RangeCalendar v-model:start="start" v-model:end="end" />
          <p class="t-cap">{{ end ? '다른 날을 누르면 처음부터 다시 골라요' : '다른 날짜를 하나 더 누르면 1박 이상으로 바뀌어요' }}</p>
          <RouterLink v-if="nights" to="/plan/stay" class="stayrow">
            <span class="ico-tile"><i class="ic i-home" aria-hidden="true"></i></span>
            <span class="row__body"><span class="row__title">숙소 위치</span><span class="row__meta">다음 날은 숙소에서 출발해요</span></span>
            <span class="btn btn--sm btn--primary">지도에서 찍기</span>
          </RouterLink>
        </section>

        <section class="card card--pad pa-sec" aria-labelledby="pa-from">
          <h2 id="pa-from" class="t-sub">어디서 출발해요?</h2>
          <RouterLink to="/plan/search?mode=origin" class="field__box pickbox"><i class="ic i-search subtle" aria-hidden="true"></i><span class="val">천호역 1번 출구</span><i class="ic i-next subtle" aria-hidden="true"></i></RouterLink>
        </section>

        <section class="card card--pad pa-sec" aria-labelledby="pa-who">
          <div class="sec-h">
            <h2 id="pa-who" class="t-sub">누구랑 가요?</h2>
            <span style="display: flex; align-items: center; gap: 4px">
              <button type="button" class="iconbtn iconbtn--soft pa-step" aria-label="인원 줄이기" @click="changePeople(-1)"><i class="ic ic--s i-minus" aria-hidden="true"></i></button>
              <b class="tn" style="min-width: 32px; text-align: center" aria-live="polite">{{ people }}명</b>
              <button type="button" class="iconbtn iconbtn--soft pa-step" aria-label="인원 늘리기" @click="changePeople(1)"><i class="ic ic--s i-plus" aria-hidden="true"></i></button>
            </span>
          </div>
          <div class="chips" role="radiogroup" aria-label="동행">
            <button v-for="name in companions" :key="name" type="button" class="chip chip--sm" :class="{ 'is-on': companion === name }" :aria-pressed="companion === name" @click="pickCompanion(name)">{{ name }}</button>
          </div>
        </section>

        <section class="card card--pad pa-sec" aria-labelledby="pa-style">
          <div class="sec-h"><h2 id="pa-style" class="t-sub">어떤 여행이 좋아요?</h2><span class="t-cap">여러 개</span></div>
          <div class="chips">
            <button v-for="name in styleList" :key="name" type="button" class="chip chip--sm" :class="{ 'is-on': styles[name] }" :aria-pressed="!!styles[name]" @click="styles[name] = !styles[name]">{{ name }}</button>
          </div>
        </section>

        <section class="card card--pad pa-sec" aria-labelledby="pa-move">
          <div class="sec-h"><h2 id="pa-move" class="t-sub">무엇으로 다녀요?</h2><span class="t-cap">여러 개</span></div>
          <div class="chips">
            <button v-for="m in moveList" :key="m.label" type="button" class="chip chip--sm" :class="{ 'is-on': moves[m.label] }" :aria-pressed="!!moves[m.label]" @click="toggleMove(m.label)">
              <i class="ic ic--s" :class="m.icon" aria-hidden="true"></i>{{ m.label }}
            </button>
          </div>
        </section>

        <section class="card card--pad pa-sec" aria-labelledby="pa-fix">
          <div class="sec-h"><h2 id="pa-fix" class="t-sub">꼭 지킬 시간</h2><span class="t-cap">예약·공연</span></div>
          <div v-for="(item, i) in fixedList" :key="item.name" class="stop is-fixed" style="grid-template-columns: 1fr">
            <div class="stop__card">
              <img class="thumb" :src="item.img" alt="" />
              <span class="stop__body"><span class="stop__name">{{ item.name }}</span><span class="stop__meta"><i class="ic ic--s i-lock c-navy" aria-hidden="true"></i><b class="c-navy tn">{{ item.time }} 고정</b> · {{ item.note }}</span></span>
              <button type="button" class="iconbtn" :aria-label="item.name + ' 빼기'" @click="fixedList.splice(i, 1)"><i class="ic i-close muted" aria-hidden="true"></i></button>
            </div>
          </div>
          <p v-if="!fixedList.length" class="t-cap">예약한 곳이 있으면 넣어 두세요. 그 시간에 맞춰 일정을 짜요.</p>
          <div class="cta__row">
            <RouterLink to="/plan/search?mode=fixed" class="btn btn--secondary btn--md"><i class="ic i-search" aria-hidden="true"></i>장소 검색</RouterLink>
            <RouterLink to="/plan/capture" class="btn btn--secondary btn--md"><i class="ic i-camera" aria-hidden="true"></i>예약 캡처</RouterLink>
          </div>
        </section>

        <section class="card card--pad pa-sec" aria-labelledby="pa-wish">
          <div class="sec-h"><h2 id="pa-wish" class="t-sub">가고 싶은 곳</h2><span class="t-cap">인스타·유튜브 링크</span></div>
          <WishLinks />
        </section>
      </div>

      <div class="cta">
        <button type="button" class="btn btn--primary btn--lg btn--block" :disabled="noStyle" @click="goNext">다음</button>
      </div>
    </div>
  </div>
</template>

<style>
.scr-PlanAI .pa-sec{display:flex;flex-direction:column;gap:10px}
.scr-PlanAI .pa-step{width:36px;height:36px}
.scr-PlanAI .pickbox{padding-right:12px;text-decoration:none;color:var(--ink);transition:border-color .15s,background .15s}
.scr-PlanAI .pickbox:hover{border-color:var(--navy);background:var(--navy-soft)}
.scr-PlanAI .pickbox .val{flex:1;font-size:16px}
.scr-PlanAI .stayrow{display:flex;align-items:center;gap:12px;padding:10px 12px;border-radius:var(--radius-lg);border:1.5px dashed var(--line-strong);text-decoration:none;color:var(--ink);transition:border-color .15s,background .15s}
.scr-PlanAI .stayrow:hover{border-color:var(--navy);background:var(--navy-soft)}
.scr-PlanAI .stayrow.is-set{border-style:solid;border-color:var(--line)}
</style>
