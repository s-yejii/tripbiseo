<script setup>
// S13-a 여행 중 / S13-a2 여행 중 · 지연 배너 (④)
// 같은 화면을 두 상황으로 써요
//   /live      : 여행 시작 (첫 장소 가기 전, 테이블링 했는지 묻기)
//   /live/late : 16:21 아직 성내동 카페 → 공연 입장에 14분 늦음
// 두 상황 모두 지도 아래 칩 줄에서 "AI에게 묻기"(④ AskPanel)와 화장실·쓰레기통(⑤)을 쓸 수 있어요
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BottomSheet from '@/components/common/BottomSheet.vue'
import AppNoti from '@/components/common/AppNoti.vue'
import LiveCards from '@/components/live/LiveCards.vue'
import SegSheet from '@/components/live/SegSheet.vue'
import TablingModal from '@/components/live/TablingModal.vue'
import AmenityChips from '@/components/after/AmenityChips.vue'
import AskPanel from '@/components/live/AskPanel.vue'
import VisitedPin from '@/components/after/VisitedPin.vue'
import { todayStops, legs, planArrive, reserveTime, lateArrive, lateMinutes, visitedInfo } from '@/data/live'
import { toTime } from '@/utils/time'
import { showToast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const late = route.meta.late === true

const me = late ? { x: 215, y: 285 } : { x: 44, y: 352 } // 지금 위치 (지도 그림 위, 아래 칩 줄에 가리지 않게)
const selected = ref(late ? 4 : 1) // 선택한 카드·핀 번호
const tablingWait = ref(null) // null: 아직 안 물어봄 / 0: 안 함 / 숫자: 대기 분
const showTabling = ref(false)
const segCard = ref(null) // 이동 칩을 누른 카드
const info = ref(null) // 정보창을 연 다녀온 곳 번호 (1, 2, 9)
const notiOpen = ref(true)
const asking = ref(false) // AI 채팅을 연 상태 (아래 시트·버튼을 숨겨요)
const added = ref(null) // AI 추천으로 일정에 넣은 곳

// 대기 시간만큼 남은 일정이 밀려요
const wait = computed(() => tablingWait.value || 0)
const slack = computed(() => reserveTime - (planArrive + wait.value)) // 예약 여유(분)

const cards = computed(() => todayStops.map((stop) => (late ? lateCard(stop) : startCard(stop))))

// 여행 시작 때 카드 글자
function startCard(stop) {
  const card = baseCard(stop)
  if (stop.no === 1) {
    card.state = 'now'
    card.sub = wait.value ? `대기 ${wait.value}분 · ${toTime(780 + wait.value)}` : '13:00 · 도보 5분'
    card.subCls = wait.value ? 'c-warning' : 'c-coral'
  } else if (stop.fixed) {
    card.sub = slack.value < 0 ? toTime(planArrive + wait.value) + ' 도착 예상' : '18:30 고정'
    card.subCls = slack.value < 0 ? 'c-danger' : ''
  } else {
    card.sub = toTime(stop.start + wait.value) + (wait.value ? ' 도착 예상' : '')
    card.subCls = wait.value ? 'c-warning' : ''
  }
  return card
}

// 늦은 상황(16:21) 카드 글자
function lateCard(stop) {
  const card = baseCard(stop)
  if (stop.no <= 2) {
    card.state = 'done'
    card.sub = toTime(stop.start) + ' 다녀옴'
    card.legState = 'past'
  } else if (stop.no === 3) {
    card.state = 'now'
    card.sub = '지금 여기 · 14:52 도착'
    card.subCls = 'c-coral'
    card.legState = 'past'
  } else {
    card.sub = toTime(lateArrive[stop.no]) + ' 도착 예상'
    card.subCls = stop.fixed ? 'c-danger' : 'c-warning'
    card.legState = stop.no === 4 ? 'now' : ''
  }
  return card
}

function baseCard(stop) {
  return {
    no: stop.no,
    img: stop.img,
    title: stop.fixed ? stop.name : stop.no + ' ' + stop.name,
    fixed: stop.fixed,
    leg: legs[stop.no] || null,
    legState: '',
    state: '',
    sub: '',
    subCls: '',
  }
}

// AI 추천으로 넣은 곳은 "지금" 카드 바로 뒤에 끼워요
const shownCards = computed(() => {
  if (!added.value) return cards.value
  const list = [...cards.value]
  const nowIndex = list.findIndex((card) => card.state === 'now')
  list.splice(nowIndex + 1, 0, { no: 7, img: added.value.img, title: '+ ' + added.value.name, sub: '다음에 들러요 · 방금 추가', subCls: 'c-navy', state: '', leg: null })
  return list
})

function pickCard(card) {
  if (card.state === 'done') return toggleInfo(card.no)
  if (late && card.state === 'now') return locate()
  if (!late && card.no === 1 && tablingWait.value === null) return (showTabling.value = true)
  if (selected.value === card.no && !card.fixed && card.no !== 7) return router.push('/live/pin?no=' + card.no)
  selected.value = card.no
  info.value = null
}

function pickPin(stop) {
  if (!late && stop.no === 1 && tablingWait.value === null) return (showTabling.value = true)
  if (late && !stop.fixed) return router.push('/live/pin?no=' + stop.no)
  selected.value = stop.no
}

function toggleInfo(no) {
  info.value = info.value === no ? null : no
}

function saveTabling(minutes) {
  tablingWait.value = minutes
  showTabling.value = false
  if (minutes === 0) {
    showToast('알겠어요 · 13:00 바로 들어가는 걸로 둘게요')
  } else {
    const left = reserveTime - (planArrive + minutes)
    showToast(`대기 ${minutes}분을 반영했어요 · ` + (left < 0 ? `공연 입장에 ${-left}분 늦을 수 있어요` : `남은 일정을 ${minutes}분씩 미뤘어요`))
  }
}

function openAsk() {
  asking.value = true
  info.value = null
}

// AI 추천에서 고른 곳을 다음 일정으로 넣어요 (백엔드가 붙으면 POST /api/trips/{id}/live/insert)
function addPlace(place) {
  added.value = place
  asking.value = false
  selected.value = 7
  showToast(place.name + '을(를) 다음 일정으로 넣었어요 · 남은 시간을 다시 계산했어요')
}

function locate() {
  showToast('지금 위치로 지도를 옮겼어요')
}
function share() {
  showToast('링크를 복사했어요 · 같이 간 사람은 앱 없이 남은 일정과 여유 시간을 봐요')
}

// 이동 칩 시트에 보여 줄 출발·도착
const segFrom = computed(() => todayStops.find((s) => s.no === segCard.value.no - 1))
const segTo = computed(() => todayStops.find((s) => s.no === segCard.value.no))

// 다녀온 곳 정보창 위치·내용
const infoBox = computed(() => {
  if (info.value === 9) return { name: '천호 소품 편집숍', time: '14:22–14:30 · 일정 밖에 들른 곳', x: 178, y: 318 }
  return visitedInfo[info.value]
})
</script>

<template>
  <div class="scr scr-Live">
    <div class="app">
      <!-- 지도 + 동선 -->
      <div class="map" aria-hidden="true">
        <img class="base live" src="/img/mapB.svg" alt="" style="top: -126px" />
        <svg class="route live-route" viewBox="0 0 390 514">
          <template v-if="late">
            <polyline points="90,330 140,250 215,285" fill="none" stroke="#646D81" stroke-width="5" stroke-linecap="round" stroke-linejoin="round" />
            <polyline points="215,285 290,200 300,330 220,400" fill="none" stroke="#A65800" stroke-width="5" stroke-linecap="round" stroke-linejoin="round" stroke-dasharray="2 9" />
          </template>
          <template v-else>
            <polyline points="44,352 90,330" fill="none" stroke="#1F3A68" stroke-width="5" stroke-linecap="round" stroke-dasharray="2 9" />
            <polyline points="90,330 140,250 215,285 290,200 300,330 220,400" fill="none" stroke="#1F3A68" stroke-width="5" stroke-linecap="round" stroke-linejoin="round" opacity=".55" />
          </template>
        </svg>
      </div>

      <!-- 핀: 여행 시작 -->
      <template v-if="!late">
        <span class="me" :style="{ left: me.x + 'px', top: me.y + 'px' }" aria-label="지금 위치, 천호역 1번 출구"></span>
        <span class="maplabel" style="left: 8px; top: 366px">천호역 · 지금</span>
        <button
          v-for="stop in todayStops"
          :key="stop.no"
          type="button"
          class="pin"
          :class="{ 'pin--fixed': stop.fixed, 'pin--sel': selected === stop.no, 'pin--tbl': stop.no === 1 && tablingWait === null }"
          :style="{ left: stop.x + 'px', top: stop.y + 'px' }"
          :aria-label="stop.no + ' ' + stop.name"
          @click="pickPin(stop)"
        >
          <i v-if="stop.fixed" class="ic ic--s i-lock" aria-hidden="true" style="width: 13px; height: 13px"></i>
          <template v-else>{{ stop.no }}</template>
          <span v-if="stop.no === 1 && wait" class="tbl-badge tn">대기 {{ wait }}분</span>
        </button>
        <button v-if="tablingWait === null" type="button" class="tbl-bubble" style="left: 90px; top: 330px" @click="showTabling = true">
          <i class="ic ic--s i-clock" aria-hidden="true"></i>테이블링 하셨나요?
        </button>
      </template>

      <!-- 핀: 늦은 상황 -->
      <template v-else>
        <button
          v-for="stop in todayStops.slice(0, 2)"
          :key="stop.no"
          type="button"
          class="pin pin--done"
          :class="{ 'pin--sel': info === stop.no }"
          :style="{ left: stop.x + 'px', top: stop.y + 'px' }"
          :aria-label="stop.no + ' ' + stop.name + ', 다녀옴'"
          @click="toggleInfo(stop.no)"
        >
          <i class="ic ic--s i-check" aria-hidden="true" style="width: 13px; height: 13px"></i>
          <span v-if="stop.no === 1" class="paydot" aria-hidden="true"><i class="ic i-won"></i></span>
        </button>
        <VisitedPin :selected="info === 9" @info="toggleInfo(9)" />
        <span class="me" :style="{ left: me.x + 'px', top: me.y + 'px' }" aria-label="지금 위치, 성내동 카페"></span>
        <span class="maplabel" style="left: 150px; top: 240px">성내동 카페 · 지금</span>
        <button
          v-for="stop in todayStops.slice(3)"
          :key="stop.no"
          type="button"
          class="pin"
          :class="{ 'pin--fixed': stop.fixed, 'pin--warn': !stop.fixed, 'pin--sel': selected === stop.no }"
          :style="{ left: stop.x + 'px', top: stop.y + 'px' }"
          :aria-label="stop.no + ' ' + stop.name"
          @click="pickPin(stop)"
        >
          <i v-if="stop.fixed" class="ic ic--s i-lock" aria-hidden="true" style="width: 13px; height: 13px"></i>
          <template v-else>{{ stop.no }}</template>
        </button>
      </template>

      <span v-if="added && !asking" class="pin pin--added" :style="{ left: added.x + 'px', top: added.y + 'px' }" :aria-label="'추가한 곳 ' + added.name">
        <i class="ic ic--s i-plus" aria-hidden="true" style="width: 13px; height: 13px"></i>
      </span>

      <!-- 다녀온 곳 정보창 -->
      <div v-if="infoBox" class="payinfo" :style="{ left: Math.max(12, infoBox.x - 30) + 'px', bottom: 844 - infoBox.y + 24 + 'px' }" role="dialog" aria-label="다녀온 곳 정보">
        <b>{{ infoBox.name }}</b>
        <span class="t-cap tn">{{ infoBox.time }}</span>
        <span v-if="info === 9" class="t-cap tn live-pay"><i class="ic ic--s i-won" aria-hidden="true"></i> 6,500원 결제</span>
        <RouterLink v-else-if="infoBox.pay" to="/live/pay" class="btn btn--primary btn--sm" style="margin-top: 4px">
          <i class="ic ic--s i-won" aria-hidden="true"></i>32,000원 누가 냈는지 적기
        </RouterLink>
        <span v-else class="t-cap">결제 내역 없음</span>
      </div>

      <header class="topbar topbar--float">
        <RouterLink to="/" class="iconbtn iconbtn--float" aria-label="홈으로"><i class="ic ic--l i-back" aria-hidden="true"></i></RouterLink>
        <span style="flex: 1"></span>
        <RouterLink to="/notifications" class="iconbtn iconbtn--float" aria-label="알림 2개" style="position: relative">
          <i class="ic ic--l i-bell" aria-hidden="true"></i><span class="dotbadge"></span>
        </RouterLink>
      </header>

      <!-- 지연 알림 (늦은 상황) -->
      <template v-if="late">
        <AppNoti v-if="notiOpen" warn icon="i-warning" :title="`공연 입장에 ${lateMinutes}분 늦을 것 같아요`" body="눌러서 대안 보기 · 10분 더 늦으면 다시 알려요" to="/live/delay" @close="notiOpen = false" />
        <button v-else type="button" class="pill-alert live-pill" aria-label="지연 배너 펼치기" @click="notiOpen = true">
          <i class="ic ic--s i-warning" aria-hidden="true"></i><span class="tn">+{{ lateMinutes }}분</span><i class="ic ic--s i-down" aria-hidden="true"></i>
        </button>
      </template>

      <!-- 오른쪽 떠 있는 버튼 -->
      <div class="live-side">
        <RouterLink to="/trips/today" class="iconbtn iconbtn--float" :aria-label="`내 일정 보기 · 잔나비 공연 데이트 ${late ? 3 : 0}/6곳`" title="내 일정" style="color: var(--navy)">
          <i class="ic i-receipt" aria-hidden="true"></i>
        </RouterLink>
        <button type="button" class="iconbtn iconbtn--float" aria-label="같이 간 사람에게 공유" title="같이 간 사람에게 공유" style="color: var(--navy)" @click="share">
          <i class="ic i-share" aria-hidden="true"></i>
        </button>
        <button type="button" class="iconbtn iconbtn--float" aria-label="내 위치로" style="color: var(--navy)" @click="locate">
          <i class="ic i-locate" aria-hidden="true"></i>
        </button>
      </div>

      <!-- AI 채팅(④) · 화장실·쓰레기통(⑤) -->
      <AskPanel v-if="asking" :me="me" :late="late" @close="asking = false" @add="addPlace" />
      <AmenityChips v-else :me="me" @ask="openAsk" />

      <!-- 아래 시트: 오늘 일정 카드 -->
      <BottomSheet v-if="!asking" class="sheet--peek live-sheet" :aria-label="late ? '다음 일정' : '오늘 일정'">
        <div class="live-head">
          <div v-if="late" style="flex: 1">
            <p class="t-cap">다음 장소 출발 시간이 지났어요</p>
            <p class="t-big c-warning" style="margin: 0">+29분 <span class="t-cap tn">15:52 출발 예정</span></p>
          </div>
          <div v-else style="flex: 1">
            <p class="t-cap">{{ wait ? `테이블링 대기 ${wait}분을 반영했어요` : '첫 장소 천호 쭈꾸미골목까지 도보 5분' }}</p>
            <p class="t-big" style="margin: 0">{{ toTime(780 + wait) }} <span class="t-strong c-navy">{{ wait ? '입장 예상' : '도착 예정' }}</span></p>
          </div>
          <span v-if="late" class="badge badge--warning"><i class="ic ic--s i-lock" aria-hidden="true"></i>예약 {{ lateMinutes }}분 늦음</span>
          <span v-else-if="slack < 0" class="badge badge--warning"><i class="ic ic--s i-lock" aria-hidden="true"></i>예약 {{ -slack }}분 늦음</span>
          <span v-else class="badge badge--success"><i class="ic ic--s i-lock" aria-hidden="true"></i>예약 여유 {{ slack }}분</span>
        </div>

        <LiveCards :cards="shownCards" :selected="selected" @pick="pickCard" @seg="segCard = $event" @scrolled="selected = $event" />

        <p v-if="late" class="t-cap live-event"><i class="ic ic--s i-users" aria-hidden="true"></i>18시부터 공연 입장 · 종합운동장역이 붐벼요. 출발 알림을 5분 일찍 드려요</p>
        <p v-else-if="slack < 0" class="t-cap live-event c-warning"><i class="ic ic--s i-warning" aria-hidden="true"></i>이대로면 공연 18:30 입장에 {{ -slack }}분 늦어요 · 일정 변경에서 한 곳을 빼거나 줄여 보세요</p>
        <p v-else class="t-cap live-event"><i class="ic ic--s i-info" aria-hidden="true"></i>14–16시 비 60% · 강풀만화거리는 우산을 챙겨요</p>
      </BottomSheet>

      <div v-if="!asking" class="cta cta--white" style="box-shadow: none">
        <div class="cta__row">
          <RouterLink v-if="late" to="/live/delay" class="btn btn--secondary btn--lg">대안 보기</RouterLink>
          <RouterLink v-else to="/plan/edit" class="btn btn--secondary btn--lg"><i class="ic i-edit" aria-hidden="true"></i>일정 변경</RouterLink>
          <RouterLink to="/live/route" class="btn btn--primary btn--lg"><i class="ic i-route" aria-hidden="true"></i>길 안내</RouterLink>
        </div>
      </div>

      <TablingModal v-if="showTabling" @close="showTabling = false" @save="saveTabling" />
      <SegSheet v-if="segCard" :from="segFrom" :to="segTo" :leg="segCard.leg" @close="segCard = null" />
    </div>
  </div>
</template>

<style>
.scr-Live .live-route { position: absolute; left: 0; top: 0; width: 390px; height: 514px; }
.scr-Live .live-side { position: absolute; right: 16px; top: 228px; display: flex; flex-direction: column; gap: 8px; z-index: 2; }
.scr-Live .live-sheet { top: 440px; padding: 8px 0 0; gap: 12px; }
.scr-Live .live-head { display: flex; align-items: flex-end; gap: 12px; padding: 0 16px; }
.scr-Live .live-pill { position: absolute; top: 112px; left: 16px; z-index: 3; }
.scr-Live .live-pay { color: var(--coral-ink); font-weight: 600; }
</style>
