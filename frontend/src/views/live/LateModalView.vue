<script setup>
// S18 예약에 늦어요 (④): 택시를 타도 늦을 때, 가는 방법별 도착 시간과 같이 가는 사람에게 알리기
import { useRouter } from 'vue-router'
import EventTimeline from '@/components/live/EventTimeline.vue'
import { showToast } from '@/utils/toast'

const router = useRouter()

// 가짜 데이터: 가는 방법별 도착 예상
const ways = [
  { time: '18:40', timeCls: 'c-danger', color: 'var(--danger)', icon: 'i-car', text: '택시 15분', badgeCls: 'badge--danger', badge: '+10분' },
  { time: '18:45', color: 'var(--ink-subtle)', icon: 'i-subway', text: '지하철 20분', badgeCls: 'badge--neutral', badge: '+15분' },
  { time: '18:55', color: 'var(--ink-subtle)', icon: 'i-bus', text: '버스 30분', badgeCls: 'badge--neutral', badge: '+25분' },
]

function callTaxi() {
  showToast('택시 앱을 열어요 · 천호 로데오거리 → 잠실실내체육관')
}
function tellFriend() {
  router.push('/live')
  showToast('지은에게 보냈어요 · "택시 탔어, 18:40쯤 도착해"')
}
</script>

<template>
  <div class="scr scr-LateModal">
    <div class="app">
      <div class="map" aria-hidden="true"><img class="base live" src="/img/mapB.svg" alt="" style="top: -126px" /></div>
      <span class="me" style="left: 300px; top: 330px"></span>
      <span class="pin pin--fixed" style="left: 220px; top: 400px"><i class="ic ic--s i-lock" aria-hidden="true" style="width: 13px; height: 13px"></i></span>
      <div class="dim" aria-hidden="true"></div>

      <section class="modal" role="alertdialog" aria-modal="true" style="gap: 16px">
        <div style="display: flex; align-items: center; gap: 12px">
          <span class="ico-tile ico-tile--danger" style="width: 48px; height: 48px"><i class="ic ic--l i-clock" aria-hidden="true"></i></span>
          <h1 class="h-title" style="font-size: 20px; line-height: 28px">택시를 타도<br />입장에 <span class="c-danger tn">10분</span> 늦어요</h1>
        </div>
        <p class="t-cap tn" style="margin: -6px 0 0">지금 18:25 · 천호 로데오거리 · 18:30 입장, 19:00 공연</p>

        <EventTimeline :items="ways" label="가는 방법별 도착 시간" compact />
        <p class="t-cap" style="margin: -4px 0 0; display: flex; gap: 6px; align-items: flex-start">
          <i class="ic ic--s i-info c-navy" aria-hidden="true" style="margin-top: 2px"></i>공연은 19:00 시작이라 택시면 시작 20분 전에 들어가요
        </p>

        <div style="display: flex; flex-direction: column; gap: 8px">
          <button type="button" class="btn btn--primary btn--lg btn--block" @click="callTaxi"><i class="ic i-car" aria-hidden="true"></i>택시로 길 안내</button>
          <button type="button" class="btn btn--secondary btn--lg btn--block" @click="tellFriend"><i class="ic i-send" aria-hidden="true"></i>같이 가는 지은에게 알리기</button>
          <RouterLink to="/live" class="textlink">닫기</RouterLink>
        </div>
      </section>
    </div>
  </div>
</template>
