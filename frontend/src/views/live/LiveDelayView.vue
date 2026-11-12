<script setup>
// S15 지연 감지 (④): 왜 늦는지 보여 주고, 그냥 갈지 대안을 볼지 고르게 해요
// 지금 있는 곳의 실제 도착(GPS 기록 arrivedAt)·계획보다 더 머문 시간·고정 일정 예상 도착(서버 계산)으로 줄을 만들어요.
// "그냥 갈게요"를 누르면 여행 중 화면의 지연 알림을 접고, 10분 더 늦어지면 다시 띄워요.
// 11/04: 시트 어디를 잡고 끌어도(버튼 빼고), 시트 위에서 휠을 굴려도 내려가요 (data-drag).
//   전에는 위 손잡이(작은 막대)만 잡혀서, 내용을 끌거나 스크롤하면 아무 반응이 없어 멈춘 것처럼 보였어요
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import BottomSheet from '@/components/common/BottomSheet.vue'
import PageMessage from '@/components/common/PageMessage.vue'
import KakaoMap from '@/components/common/KakaoMap.vue'
import EventTimeline from '@/components/live/EventTimeline.vue'
import { useReplanStore } from '@/stores/replan'
import { toMinutes } from '@/utils/time'

const DELAY_MAP_PADDING = { top: 60, right: 40, bottom: 470, left: 40 } // 아래 시트에 핀이 가리지 않게

const router = useRouter()
const replan = useReplanStore()

onMounted(() => replan.load(true))

const basisName = computed(() => (replan.basisStop ? replan.basisStop.name : '마지막 일정'))
const mapPins = computed(() => replan.stops.map((stop, i) => ({
  key: stop.id,
  lat: stop.lat,
  lng: stop.lng,
  text: i + 1,
  icon: stop.fixed ? 'i-lock' : '',
  cls: stop.fixed ? ' pin--fixed' : '',
  label: stop.name,
})))

// "2026-11-03T14:15:00" → "14:15"
function toClock(dateTime) {
  return dateTime ? dateTime.substring(11, 16) : null
}

// 왜 늦는지 (실제 기록과 서버 계산만으로)
const reasons = computed(() => {
  const items = []
  const current = replan.currentStop
  const now = replan.status.now
  if (current && current.arrivedAt) {
    const arrived = toClock(current.arrivedAt)
    const lateArrive = toMinutes(arrived) - toMinutes(current.time)
    items.push({
      time: arrived, color: 'var(--success)', text: current.name + ' 도착',
      badgeCls: lateArrive > 0 ? 'badge--warning' : 'badge--success', badgeIcon: lateArrive > 0 ? 'i-clock' : 'i-check',
      badge: lateArrive > 0 ? '+' + lateArrive + '분' : '제시간',
    })
  }
  if (current) {
    const start = current.arrivedAt ? toMinutes(toClock(current.arrivedAt)) : toMinutes(current.time)
    const overStay = toMinutes(now) - (start + current.stay)
    items.push({
      time: now, color: overStay > 0 ? 'var(--warning)' : 'var(--success)', text: '아직 ' + current.name,
      badgeCls: overStay > 0 ? 'badge--warning' : 'badge--neutral', badgeIcon: 'i-clock',
      badge: overStay > 0 ? '+' + overStay + '분' : '계획 안',
    })
  } else {
    items.push({ time: now, color: 'var(--ink-subtle)', text: '이동 중', badgeCls: 'badge--neutral', badge: '지금' })
  }
  const slack = replan.base.slackMin
  items.push({
    time: replan.base.finalArrive, color: slack < 0 ? 'var(--danger)' : 'var(--success)', icon: replan.basisStop && replan.basisStop.fixed ? 'i-lock' : '',
    text: basisName.value + ' 도착 예상', badgeCls: slack < 0 ? 'badge--danger' : 'badge--success', badgeIcon: slack < 0 ? 'i-danger' : 'i-check',
    badge: slack < 0 ? '+' + -slack + '분' : '여유 ' + slack + '분',
  })
  return items
})

// 아직 안 늦었지만 여유가 거의 없음 (여행 중 화면의 "지금 나가지 않으면 늦어요" 알림과 같은 기준)
const TIGHT_SLACK_MIN = 5
const isTight = computed(() => !replan.lateMin && replan.base && replan.base.slackMin <= TIGHT_SLACK_MIN)

const optionCount = computed(() => (replan.options ? replan.options.alternatives.length : 0))
// 예약 늦음 빠른 대안(지하철·택시로 바로 가기)이 있으면 ③ 빠른 대안 화면으로, 아니면 ④ 대안 고르기 그대로 (11/09)
const altLink = computed(() => (replan.findOption('택시') || replan.findOption('지하철') ? '/live/quick' : '/live/alt'))

function goAnyway() {
  // 11/12: 늦은 걸 알고 "그냥 갈게요" → 이 예약 지연 알림은 더 안 띄움. 빠듯할 때 "조금만 더 있을게요"는 예전처럼 10분 더 늦으면 다시
  replan.muteLate(replan.lateMin, replan.lateMin > 0)
  router.replace('/live')
}
</script>

<template>
  <div class="scr scr-LiveDelay">
    <div class="app">
      <KakaoMap v-if="replan.base" :pins="mapPins" :me="replan.position" :padding="DELAY_MAP_PADDING" />
      <div class="dim" aria-hidden="true"></div>

      <BottomSheet closable role="alertdialog" aria-modal="true" data-drag style="gap: 16px">
        <PageMessage v-if="replan.error" icon="i-warning" title="지금 상황을 계산할 수 없어요" :text="replan.error" to="/live" button="여행 중 화면으로" />
        <PageMessage v-else-if="!replan.base" is-loading title="지금 위치에서 다시 계산하는 중이에요" />

        <template v-else>
        <div style="display: flex; align-items: center; gap: 12px">
          <span class="ico-tile" :class="replan.lateMin || isTight ? 'ico-tile--warn' : ''" style="width: 48px; height: 48px"><i class="ic ic--l" :class="replan.lateMin || isTight ? 'i-warning' : 'i-success'" aria-hidden="true"></i></span>
          <h1 v-if="replan.lateMin" class="h-title" style="font-size: 20px; line-height: 28px">{{ basisName }}<br /><span class="c-warning">{{ replan.lateMin }}분</span> 늦어요</h1>
          <h1 v-else-if="isTight" class="h-title" style="font-size: 20px; line-height: 28px">지금 나가지 않으면<br />{{ basisName }}에 <span class="c-warning">늦어요</span></h1>
          <h1 v-else class="h-title" style="font-size: 20px; line-height: 28px">{{ basisName }}<br />안 늦어요</h1>
        </div>

        <EventTimeline :items="reasons" label="왜 늦는지" />

        <div style="display: flex; flex-direction: column; gap: 4px">
          <div class="cta__row">
            <template v-if="isTight">
              <button type="button" class="btn btn--secondary btn--lg" @click="goAnyway">조금만 더 있을게요</button>
              <RouterLink :to="altLink" class="btn btn--primary btn--lg">대안 {{ optionCount }}개 보기</RouterLink>
            </template>
            <template v-else-if="replan.lateMin">
              <button type="button" class="btn btn--secondary btn--lg" @click="goAnyway">그냥 갈게요</button>
              <RouterLink :to="altLink" class="btn btn--primary btn--lg">대안 {{ optionCount }}개 보기</RouterLink>
            </template>
            <template v-else>
              <RouterLink to="/live/alt" class="btn btn--ghost btn--lg" style="padding: 0 12px; font-size: 14px; font-weight: 600">그래도 바꾸기</RouterLink>
              <button type="button" class="btn btn--primary btn--lg" @click="goAnyway">그대로 가요</button>
            </template>
          </div>
        </div>
        </template>
      </BottomSheet>
    </div>
  </div>
</template>
