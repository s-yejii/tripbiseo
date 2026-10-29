<script setup>
// 첫 장소에서 "테이블링 하셨나요?" → 했으면 예상 대기 시간을 받아요
// 저장하면 save(대기 분)를 보내고, 화면이 남은 일정을 그만큼 미뤄요
import { computed, ref } from 'vue'
import { toTime } from '@/utils/time'
import { planArrive, reserveTime } from '@/data/live'

const emit = defineEmits(['close', 'save'])

const step = ref('ask') // ask: 했는지 묻기 / wait: 대기 시간 넣기
const wait = ref(30)
const quickWaits = [10, 20, 30, 40, 60]

// 대기 시간만큼 늦게 도착하면 공연 예약 여유가 몇 분 남는지
const slack = computed(() => reserveTime - (planArrive + wait.value))

function changeWait(diff) {
  wait.value = Math.max(5, Math.min(180, wait.value + diff))
}
</script>

<template>
  <div class="dim" style="z-index: 7" @click="emit('close')"></div>
  <section class="modal" role="dialog" aria-modal="true" style="z-index: 8">
    <div style="display: flex; align-items: center; gap: 12px">
      <img class="thumb" src="/img/gamjatang.jpg" alt="" />
      <span style="display: flex; flex-direction: column">
        <b class="t-strong">천호 쭈꾸미골목</b>
        <span class="t-cap tn">첫 장소 · 13:00 도착 예정</span>
      </span>
    </div>

    <template v-if="step === 'ask'">
      <div style="display: flex; flex-direction: column; gap: 6px">
        <h2 class="h-title">테이블링 하셨나요?</h2>
        <p class="t-body muted">테이블링으로 줄을 서 두셨다면, 앱에 보이는 예상 대기 시간에 맞춰 남은 일정을 다시 계산해 드려요.</p>
      </div>
      <div class="modal__btns">
        <button type="button" class="btn btn--secondary btn--lg" @click="emit('save', 0)">아니요</button>
        <button type="button" class="btn btn--primary btn--lg" @click="step = 'wait'">네, 했어요</button>
      </div>
    </template>

    <template v-else>
      <div style="display: flex; flex-direction: column; gap: 6px">
        <h2 class="h-title">예상 대기 시간이 얼마예요?</h2>
        <p class="t-body muted">테이블링 앱에 보이는 시간을 그대로 넣어 주세요.</p>
      </div>
      <div class="tbl-wait">
        <button type="button" class="iconbtn iconbtn--soft" aria-label="5분 줄이기" @click="changeWait(-5)"><i class="ic ic--s i-minus" aria-hidden="true"></i></button>
        <b class="tn" aria-live="polite">{{ wait }}분</b>
        <button type="button" class="iconbtn iconbtn--soft" aria-label="5분 늘리기" @click="changeWait(5)"><i class="ic ic--s i-plus" aria-hidden="true"></i></button>
      </div>
      <div class="chips" style="justify-content: center">
        <button v-for="m in quickWaits" :key="m" type="button" class="chip chip--sm" :class="{ 'is-on': m === wait }" @click="wait = m">{{ m }}분</button>
      </div>
      <div class="tbl-res tn">
        <span>13:00 도착</span><i class="ic ic--s i-next" aria-hidden="true"></i>
        <span>대기 {{ wait }}분</span><i class="ic ic--s i-next" aria-hidden="true"></i>
        <b>{{ toTime(780 + wait) }} 입장 예상</b>
      </div>
      <p v-if="slack < 0" class="t-cap tn c-warning" style="margin: 0; text-align: center">
        <i class="ic ic--s i-warning" aria-hidden="true"></i> 이대로면 공연 18:30 입장에 {{ -slack }}분 늦어요
      </p>
      <p v-else class="t-cap tn muted" style="margin: 0; text-align: center">남은 일정을 {{ wait }}분씩 미뤄도 공연 18:30 전에 도착해요</p>
      <button type="button" class="btn btn--primary btn--lg btn--block" @click="emit('save', wait)">이 시간으로 다시 계산하기</button>
    </template>
  </section>
</template>
