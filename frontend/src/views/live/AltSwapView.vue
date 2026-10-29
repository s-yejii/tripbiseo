<script setup>
// S16-c 장소 바꾸기 (④): 늦을 때(/live/alt/swap)와 비 올 때(/live/alt/swap?why=rain) 같은 화면을 써요
// 바꿀 곳을 고르고, 줄을 누르면 추천 이유를 펼쳐 봐요
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'
import AltTabs from '@/components/live/AltTabs.vue'
import { places } from '@/data/places'
import { toDuration } from '@/utils/time'
import { showToast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()

// 가짜 데이터: 상황별 바꿀 장소 후보 (xy: 작은 지도 위 위치)
const cases = {
  delay: {
    title: '대안 고르기', from: 'shoes', why: '지금 가면 공연 입장에 14분 늦어요', basis: '내 스타일 카페·쇼핑', me: [110, 95], mapTop: -200, mapLeft: -110,
    options: [
      { id: 'sopum', walk: 4, note: '여유 28분', cls: 'c-success', xy: [150, 60] },
      { id: 'gallery', walk: 6, note: '여유 24분', cls: 'c-success', xy: [175, 120] },
      { id: 'fshop', walk: 12, note: '여유 12분 빠듯', cls: 'c-warning', xy: [270, 90] },
    ],
  },
  rain: {
    title: '비 올 때 바꾸기', from: 'forest', why: '14–16시 비 60% · 야외 장소', basis: '실내 · 내 스타일 카페·전시', me: [60, 80], mapTop: -230, mapLeft: -40,
    options: [
      { id: 'gallery', walk: 8, note: '실내 전시 · 30분', cls: 'c-success', xy: [150, 50] },
      { id: 'dessert', walk: 6, note: '실내 카페 · 30분', cls: 'c-success', xy: [100, 110] },
      { id: 'fshop', walk: 10, note: '지붕 있는 상점가 · 30분', cls: 'c-success', xy: [230, 70] },
    ],
  },
}
const isRain = route.query.why === 'rain'
const info = isRain ? cases.rain : cases.delay
const fromPlace = places[info.from]

const pickedId = ref(info.options[0].id)
const openId = ref(null) // 정보를 펼친 줄

const buttonText = computed(() => places[pickedId.value].name + '(으)로 바꾸기')

function reasonOf(option) {
  return (isRain ? '실내라 비를 피할 수 있어요 · ' : `걸어서 ${option.walk}분이라 예약 전에 들를 수 있어요 · `) + info.basis
}
function toggleInfo(id) {
  openId.value = openId.value === id ? null : id
}
function apply() {
  if (!isRain) return router.push('/live/applied')
  router.push('/trips/today')
  showToast(`${fromPlace.name} → ${places[pickedId.value].name}(으)로 바꿨어요`)
}
</script>

<template>
  <div class="scr scr-AltSwap">
    <div class="app">
      <TopBar :title="info.title" back="/live/delay">
        <span v-if="isRain" class="badge badge--warning" style="margin-right: 8px"><i class="ic ic--s i-rain" aria-hidden="true"></i>14–16시 비</span>
        <span v-else class="badge badge--warning tn" style="margin-right: 8px"><i class="ic ic--s i-warning" aria-hidden="true"></i>14분 늦음</span>
      </TopBar>

      <div class="scroll" style="padding-top: 4px; gap: 10px; bottom: 102px">
        <AltTabs v-if="!isRain" active="swap" />

        <div class="sw-map">
          <div class="map"><img class="base live" src="/img/mapB.svg" alt="" :style="{ top: info.mapTop + 'px', left: info.mapLeft + 'px' }" /></div>
          <span class="me" :style="{ left: info.me[0] + 'px', top: info.me[1] + 'px' }"></span>
          <button
            v-for="(option, i) in info.options"
            :key="option.id"
            type="button"
            class="pin sw-pin"
            :class="{ 'pin--sel': option.id === pickedId || option.id === openId }"
            :style="{ left: option.xy[0] + 'px', top: option.xy[1] + 'px' }"
            :aria-label="i + 1 + ' ' + places[option.id].name + ' 정보 보기'"
            @click="toggleInfo(option.id)"
          >{{ i + 1 }}</button>
        </div>

        <div class="opt is-locked swap-from">
          <img class="thumb" :src="fromPlace.img" alt="" />
          <span class="opt__body">
            <span class="t-cap">이 장소 대신</span>
            <b class="t-sub" style="text-decoration: line-through; color: var(--ink-subtle)">{{ fromPlace.name }} · {{ toDuration(fromPlace.dur) }}</b>
            <span class="t-cap c-warning">{{ info.why }}</span>
          </span>
          <i class="ic ic--l i-down c-navy" aria-hidden="true"></i>
        </div>

        <p class="divlabel"><span class="badge badge--navy" style="height: 22px">추천 기준: {{ info.basis }}</span>가까운 순</p>

        <div class="group" role="radiogroup" aria-label="바꿀 장소">
          <template v-for="(option, i) in info.options" :key="option.id">
            <div class="row sw-row" :class="{ 'is-on': option.id === pickedId }" :aria-expanded="openId === option.id" @click="toggleInfo(option.id)">
              <img class="thumb" :src="places[option.id].img" alt="" />
              <span class="row__body">
                <span class="row__title">{{ i + 1 }} {{ places[option.id].name }}</span>
                <span class="row__meta tn">
                  <i class="ic ic--s i-star c-warning" aria-hidden="true"></i>{{ places[option.id].rate }} · 도보 {{ option.walk }}분 · <span :class="option.cls">{{ option.note }}</span>
                </span>
                <span class="sw-more">{{ openId === option.id ? '정보 접기' : '눌러서 정보 보기' }}<i class="ic ic--s" :class="openId === option.id ? 'i-up' : 'i-down'" aria-hidden="true"></i></span>
              </span>
              <button type="button" role="radio" :aria-checked="option.id === pickedId" class="btn btn--sm" :class="option.id === pickedId ? 'btn--primary' : 'btn--secondary'" @click.stop="pickedId = option.id">
                {{ option.id === pickedId ? '선택됨' : '선택' }}
              </button>
            </div>
            <div v-if="openId === option.id" class="sw-info" role="region" :aria-label="places[option.id].name + ' 정보'">
              <dl>
                <dt>종류</dt><dd>{{ places[option.id].cat }}</dd>
                <dt>영업</dt><dd>{{ places[option.id].hours || '상시 개방' }}</dd>
                <dt>별점</dt><dd>{{ places[option.id].rate }}</dd>
                <dt>머무는 시간</dt><dd>약 {{ toDuration(places[option.id].dur) }}</dd>
                <dt>추천 이유</dt><dd>{{ reasonOf(option) }}</dd>
              </dl>
              <RouterLink to="/place" class="textlink" style="justify-content: flex-start; padding: 0; min-height: 32px">장소 상세 보기<i class="ic ic--s i-next" aria-hidden="true"></i></RouterLink>
            </div>
          </template>
        </div>
      </div>

      <div class="cta">
        <button type="button" class="btn btn--primary btn--lg btn--block" @click="apply">{{ buttonText }}</button>
      </div>
    </div>
  </div>
</template>

<style>
.scr-AltSwap .sw-map{position:relative;height:150px;border-radius:var(--radius-lg);overflow:hidden}
.scr-AltSwap .swap-from{background:var(--surface-raised);border:1.5px dashed var(--line-strong)}
.scr-AltSwap .group .row.is-on{background:var(--navy-soft)}
.scr-AltSwap .sw-row{cursor:pointer}
.scr-AltSwap .sw-row:hover{background:var(--surface)}
.scr-AltSwap .sw-more{display:flex;align-items:center;gap:2px;font-size:12px;color:var(--ink-muted)}
.scr-AltSwap .sw-info{display:flex;flex-direction:column;gap:8px;padding:4px 16px 14px 72px;border-bottom:1px solid var(--line);background:var(--navy-soft)}
.scr-AltSwap .sw-info dl{display:grid;grid-template-columns:auto 1fr;gap:4px 10px;margin:0;font-size:13px;line-height:18px}
.scr-AltSwap .sw-info dt{color:var(--ink-muted)}
.scr-AltSwap .sw-info dd{margin:0;color:var(--ink);font-variant-numeric:tabular-nums}
.scr-AltSwap .sw-pin{cursor:pointer;padding:0;font:inherit}
</style>
