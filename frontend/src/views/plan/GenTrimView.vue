<script setup>
// S08-c 한 곳 빼고 다시 만들기 (③): 중요도가 낮은 곳부터 보여 주고, 뺄 곳 하나를 골라 다시 만들어요
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'

const router = useRouter()

// 가짜 데이터: 뺄 수 있는 곳(중요도 낮은 순)과 뺐을 때 예약 여유 (백엔드 계산값)
const choices = [
  { id: 'forest', name: '강풀만화거리 산책', img: '/img/alley.jpg', imp: 'drop', impLabel: '빼도 됨', slack: 2 },
  { id: 'gamja', name: '천호 쭈꾸미골목', img: '/img/gamjatang.jpg', imp: 'maybe', impLabel: '가능하면', slack: 29 },
  { id: 'dessert', name: '천호 디저트 카페', img: '/img/dessert.jpg', imp: 'maybe', impLabel: '가능하면', slack: 15 },
  { id: 'shoes', name: '성내동 소품샵', img: '/img/sopum.jpg', imp: 'maybe', impLabel: '가능하면', slack: 34 },
]
const locked = [
  { name: '성내동 카페', img: '/img/daelim.jpg', label: '꼭', note: '꼭 갈 곳은 뺄 수 없어요' },
  { name: '천호 로데오거리', img: '/img/understand.jpg', label: '꼭', note: '꼭 갈 곳은 뺄 수 없어요' },
  { name: '잔나비 콘서트', img: '/img/concert.svg', label: '18:30 고정', note: '고정 일정은 뺄 수 없어요' },
]

const pickedId = ref(choices[0].id)
const picked = computed(() => choices.find((c) => c.id === pickedId.value))

function remake() {
  router.replace('/plan/generating')
}
</script>

<template>
  <div class="scr scr-GenTrim">
    <div class="app">
      <TopBar title="어디를 뺄까요?" back="/plan/fail" />

      <div class="scroll" style="padding-top: 4px; gap: 8px; bottom: 102px">
        <p class="t-cap" style="margin-bottom: 4px">중요도가 낮은 곳이 위에 있어요</p>
        <div role="radiogroup" aria-label="뺄 장소" style="display: flex; flex-direction: column; gap: 8px">
          <button
            v-for="item in choices"
            :key="item.id"
            type="button"
            role="radio"
            :aria-checked="item.id === pickedId"
            class="opt"
            :class="{ 'is-on': item.id === pickedId }"
            style="text-align: left"
            @click="pickedId = item.id"
          >
            <img class="thumb" :src="item.img" alt="" />
            <span class="opt__body">
              <span class="gt-name"><b class="t-sub">{{ item.name }}</b><span class="imp" :class="'imp--' + item.imp">{{ item.impLabel }}</span></span>
              <span class="t-cap tn c-success">빼면 예약 여유 {{ item.slack }}분</span>
            </span>
            <span class="radio"></span>
          </button>
          <div v-for="item in locked" :key="item.name" class="opt is-locked">
            <img class="thumb" :src="item.img" alt="" />
            <span class="opt__body">
              <span class="gt-name"><b class="t-sub">{{ item.name }}</b><span class="imp imp--must">{{ item.label }}</span></span>
              <span class="t-cap">{{ item.note }}</span>
            </span>
            <i class="ic i-lock subtle" aria-hidden="true"></i>
          </div>
        </div>
      </div>

      <div class="cta">
        <button type="button" class="btn btn--primary btn--lg btn--block" @click="remake"><i class="ic i-refresh" aria-hidden="true"></i>{{ picked.name }} 빼고 다시 만들기</button>
      </div>
    </div>
  </div>
</template>

<style>
.scr-GenTrim .gt-name { display: flex; align-items: center; gap: 6px; }
</style>
