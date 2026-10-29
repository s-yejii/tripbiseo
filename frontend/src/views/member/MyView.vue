<script setup>
// S19 마이 (①): 내 정보 · 여행 스타일 · 친구 · 결제 카드 · 알림 설정 · 권한
import { ref } from 'vue'
import TabBar from '@/components/common/TabBar.vue'
import TopBar from '@/components/common/TopBar.vue'
import { showToast } from '@/utils/toast'

// 가짜 데이터: 내 정보 (백엔드가 붙으면 member · member_style · friend · card 테이블)
const me = { name: '하늘', id: 'haneul_trip', area: '서울' }
const styleChips = ['보통 여행', '카페', '맛집', '쇼핑']
const friends = ['엄마', '민지', '준호']
const cards = ref([
  { name: '신한카드', last: '4821', main: true },
])

// 알림 켜기/끄기
const alarms = ref([
  { key: 'depart', icon: 'i-walk', tile: '', title: '출발 알림', on: true },
  { key: 'delay', icon: 'i-warning', tile: 'warn', title: '지연·대안 알림', on: true },
  { key: 'weather', icon: 'i-cloud', tile: '', title: '날씨 알림', on: true },
])
const departBefore = ref(10) // 출발 몇 분 전에 알릴지

function setMainCard(index) {
  cards.value.forEach((card, i) => (card.main = i === index))
  showToast('기본 결제 카드를 바꿨어요')
}
</script>

<template>
  <div class="scr scr-My">
    <div class="app">
      <TopBar title="마이" icon="none">
        <button type="button" class="my-lang" aria-label="언어 바꾸기, 지금 한국어" @click="showToast('English 화면은 준비 중이에요')">
          <i class="ic ic--s i-chat" aria-hidden="true"></i><b>한</b><span aria-hidden="true">/</span><span>EN</span>
        </button>
      </TopBar>

      <div class="scroll" style="padding-top: 4px; gap: 16px; bottom: 96px">
        <div style="display: flex; align-items: center; gap: 14px">
          <span class="ava" style="width: 60px; height: 60px; font-size: 22px">{{ me.name[0] }}</span>
          <div style="flex: 1; min-width: 0"><p class="h-title" style="margin: 0">{{ me.name }}</p><p class="t-cap">{{ me.id }} · {{ me.area }}</p></div>
          <RouterLink to="/my/profile" class="btn btn--secondary btn--sm"><i class="ic ic--s i-edit" aria-hidden="true"></i>정보 수정</RouterLink>
        </div>

        <RouterLink to="/my/style" class="card my-style" aria-label="내 여행 스타일 수정">
          <span style="display: flex; justify-content: space-between; align-items: center"><b class="t-sub">내 여행 스타일</b><span class="t-cap" style="display: inline-flex; align-items: center; gap: 2px">수정<i class="ic ic--s i-next" aria-hidden="true"></i></span></span>
          <span class="chips" style="flex-wrap: wrap"><span v-for="(chip, i) in styleChips" :key="chip" class="chip chip--sm" :class="{ 'is-on': i > 0 }">{{ chip }}</span></span>
          <span class="t-cap tn">20분 넘게 걸으면 지하철</span>
          <span class="my-style__why"><i class="ic ic--s i-sparkle" aria-hidden="true"></i>AI가 일정 짜고 장소 추천할 때 이 기준으로 해요</span>
        </RouterLink>

        <section class="my-sec" aria-labelledby="fr-h">
          <h2 id="fr-h" class="t-sub">친구 <span class="muted tn">{{ friends.length }}</span></h2>
          <div class="friends">
            <RouterLink to="/my/friends"><span class="ava ava--add"><i class="ic i-plus" aria-hidden="true"></i></span>친구 추가</RouterLink>
            <span v-for="friend in friends" :key="friend"><span class="ava">{{ friend[0] }}</span>{{ friend }}</span>
          </div>
        </section>

        <section class="my-sec" aria-labelledby="cd-h">
          <h2 id="cd-h" class="t-sub">결제 카드 <span class="muted tn">{{ cards.length }}</span></h2>
          <div class="group">
            <div v-for="(card, i) in cards" :key="card.last" class="row">
              <span class="ico-tile"><i class="ic i-won" aria-hidden="true"></i></span>
              <span class="row__body"><span class="row__title">{{ card.name }} <span class="tn muted" style="font-weight: 500">· {{ card.last }}</span></span><span class="row__meta">{{ card.main ? '결제 알림 받는 중' : '등록됨' }}</span></span>
              <span v-if="card.main" class="badge badge--navy">기본</span>
              <button v-else type="button" class="btn btn--secondary btn--sm" @click="setMainCard(i)">기본으로</button>
            </div>
            <RouterLink to="/my/card" class="row">
              <span class="ico-tile ico-tile--add"><i class="ic i-plus" aria-hidden="true"></i></span>
              <span class="row__body"><span class="row__title c-navy">카드 등록</span><span class="row__meta">이 카드로 결제하면 장소 핀에 금액이 남아요</span></span>
              <i class="ic i-next subtle" aria-hidden="true"></i>
            </RouterLink>
          </div>
        </section>

        <section class="my-sec" aria-labelledby="nt-h">
          <h2 id="nt-h" class="t-sub">알림</h2>
          <div class="group">
            <template v-for="alarm in alarms" :key="alarm.key">
              <div class="row">
                <span class="ico-tile" :class="alarm.tile ? 'ico-tile--' + alarm.tile : ''"><i class="ic" :class="alarm.icon" aria-hidden="true"></i></span>
                <span class="row__body"><span class="row__title">{{ alarm.title }}</span></span>
                <button type="button" role="switch" class="toggle" :class="{ 'is-on': alarm.on }" :aria-checked="alarm.on" :aria-label="alarm.title" @click="alarm.on = !alarm.on"></button>
              </div>
              <!-- 출발 알림이 켜져 있을 때만 몇 분 전인지 골라요 -->
              <div v-if="alarm.key === 'depart' && alarm.on" class="row" style="padding-top: 0">
                <span style="width: 40px"></span>
                <div class="chips" role="radiogroup" aria-label="출발 몇 분 전" style="flex-wrap: nowrap">
                  <button v-for="min in [5, 10, 15]" :key="min" type="button" role="radio" class="chip chip--sm tn" :class="{ 'is-on': departBefore === min }" :aria-checked="departBefore === min" @click="departBefore = min">{{ min }}분 전</button>
                </div>
              </div>
            </template>
          </div>
        </section>

        <section class="my-sec" aria-labelledby="st-h">
          <h2 id="st-h" class="t-sub">여행 기본값 · 권한</h2>
          <div class="group">
            <RouterLink to="/my/style" class="row"><span class="ico-tile ico-tile--neutral"><i class="ic i-subway" aria-hidden="true"></i></span><span class="row__body"><span class="row__title">이동 수단</span></span><span class="t-cap">지하철 · 걷기 20분까지</span><i class="ic i-next subtle" aria-hidden="true"></i></RouterLink>
            <RouterLink to="/permission" class="row"><span class="ico-tile ico-tile--neutral"><i class="ic i-pin" aria-hidden="true"></i></span><span class="row__body"><span class="row__title">위치 권한</span></span><span class="t-cap c-success">항상 허용</span><i class="ic i-next subtle" aria-hidden="true"></i></RouterLink>
            <RouterLink to="/my/location" class="row"><span class="ico-tile ico-tile--neutral"><i class="ic i-route" aria-hidden="true"></i></span><span class="row__body"><span class="row__title">내 위치 기록</span><span class="row__meta">보기 · 삭제 · 보관 기간</span></span><span class="t-cap tn">90일 보관</span><i class="ic i-next subtle" aria-hidden="true"></i></RouterLink>
            <a href="tel:15880000" class="row"><span class="ico-tile ico-tile--neutral"><i class="ic i-help" aria-hidden="true"></i></span><span class="row__body"><span class="row__title">고객센터</span></span><i class="ic i-next subtle" aria-hidden="true"></i></a>
          </div>
        </section>

        <div style="display: flex; justify-content: space-between; align-items: center">
          <RouterLink to="/onboarding" class="textlink" style="padding-left: 0"><i class="ic ic--s i-logout" aria-hidden="true"></i>로그아웃</RouterLink>
          <span class="t-cap subtle tn">버전 1.0.0</span>
        </div>
      </div>

      <TabBar active="my" />
    </div>
  </div>
</template>

<style>
.scr-My .topbar__title{padding-left:8px}
.scr-My .ava{width:44px;height:44px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-weight:700;font-size:15px;flex:none;background:var(--navy-soft);color:var(--navy)}
.scr-My .ava--add{background:transparent;border:1.5px dashed var(--line-strong);color:var(--ink-muted)}
.scr-My .ico-tile--add{background:transparent;border:1.5px dashed var(--line-strong);color:var(--navy)}
.scr-My .my-sec{display:flex;flex-direction:column;gap:8px}
.scr-My .my-style{display:flex;flex-direction:column;gap:8px;padding:14px 16px;text-decoration:none;color:var(--ink)}
.scr-My .my-style:hover{border-color:var(--navy)}
.scr-My .my-style .chip{pointer-events:none}
.scr-My .my-style__why{display:flex;align-items:center;gap:4px;font-size:12px;color:var(--navy)}
.scr-My .my-lang{flex:none;margin-right:8px;display:inline-flex;align-items:center;gap:4px;height:32px;padding:0 12px;border-radius:999px;border:1px solid var(--line);background:var(--surface-raised);font:inherit;font-size:13px;color:var(--ink-muted);cursor:pointer}
.scr-My .my-lang b{color:var(--navy)}
.scr-My .my-lang:hover{border-color:var(--navy)}
.scr-My .friends{display:flex;gap:16px}
.scr-My .friends span,.scr-My .friends a{display:flex;flex-direction:column;align-items:center;gap:4px;font-size:12px;color:var(--ink-muted);text-decoration:none}
</style>
