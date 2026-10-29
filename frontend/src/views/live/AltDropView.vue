<script setup>
// S16-b 대안 · 한 곳 빼기 (④): 남은 장소 중 뺄 곳을 고르고, 남는 시간을 다음 장소에 쓸지 정해요
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'
import AltTabs from '@/components/live/AltTabs.vue'

const router = useRouter()

const dropId = ref('shoes') // 뺄 곳
const useExtra = ref(true) // 남는 시간을 다음 장소에 쓰기

const extraText = computed(() => {
  if (!useExtra.value) return '남는 시간은 비워 둬요'
  return dropId.value === 'shoes' ? '천호 로데오거리 40분 → 70분' : '성내동 소품샵 60분 → 85분'
})
const buttonText = computed(() => (dropId.value === 'shoes' ? '성내동 소품샵 빼고 적용' : '천호 로데오거리 빼고 적용'))
</script>

<template>
  <div class="scr scr-AltDrop">
    <div class="app">
      <TopBar title="대안 고르기" back="/live/delay">
        <span class="badge badge--warning tn" style="margin-right: 8px"><i class="ic ic--s i-warning" aria-hidden="true"></i>14분 늦음</span>
      </TopBar>

      <div class="scroll" style="padding-top: 4px; gap: 12px; bottom: 102px">
        <AltTabs active="drop" />

        <div class="now-block">
          <span class="me" aria-hidden="true"></span>
          <img class="thumb" src="/img/daelim.jpg" alt="" style="width: 40px; height: 40px" />
          <span style="flex: 1"><span class="t-strong" style="display: block">지금 · 성내동 카페</span><span class="t-cap tn">16:21</span></span>
        </div>

        <p class="divlabel">남은 일정 · 뺄 곳을 골라요</p>

        <div role="radiogroup" aria-label="뺄 장소" style="display: flex; flex-direction: column; gap: 8px">
          <button type="button" role="radio" :aria-checked="dropId === 'shoes'" class="opt" :class="{ 'is-on': dropId === 'shoes' }" style="text-align: left" @click="dropId = 'shoes'">
            <img class="thumb" src="/img/sopum.jpg" alt="" />
            <span class="opt__body">
              <span class="drop-name"><b class="t-sub">성내동 소품샵</b><span class="imp imp--maybe">가능하면</span></span>
              <span class="t-cap tn c-success">17:25 도착 · 여유 65분</span>
            </span>
            <span class="radio"></span>
          </button>
          <div class="opt is-locked">
            <img class="thumb" src="/img/understand.jpg" alt="" />
            <span class="opt__body">
              <span class="drop-name"><b class="t-sub">천호 로데오거리</b><span class="imp imp--must">꼭</span></span>
              <span class="t-cap tn">올리브영·다이소 · 꼭 갈 곳이라 빼지 않아요</span>
            </span>
            <i class="ic i-lock subtle" aria-hidden="true"></i>
          </div>
          <div class="opt is-locked">
            <img class="thumb" src="/img/concert.svg" alt="" />
            <span class="opt__body"><b class="t-sub">잔나비 콘서트</b><span class="t-cap tn">18:30 고정 · 뺄 수 없어요</span></span>
            <i class="ic i-lock subtle" aria-hidden="true"></i>
          </div>
        </div>

        <button type="button" class="opt" role="checkbox" :aria-checked="useExtra" style="text-align: left; box-shadow: none" @click="useExtra = !useExtra">
          <span class="check" :class="{ 'is-on': useExtra }"><i class="ic ic--s i-check" aria-hidden="true"></i></span>
          <span class="opt__body"><b class="t-strong">남는 시간은 다음 장소에 쓰기</b><span class="t-cap tn">{{ extraText }}</span></span>
        </button>
      </div>

      <div class="cta">
        <button type="button" class="btn btn--primary btn--lg btn--block" @click="router.push('/live/applied')">{{ buttonText }}</button>
      </div>
    </div>
  </div>
</template>

<style>
.scr-AltDrop .drop-name { display: flex; align-items: center; gap: 6px; }
</style>
