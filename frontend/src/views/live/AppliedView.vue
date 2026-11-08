<script setup>
// S17 바뀐 일정 (④): 대안을 적용한 뒤 무엇이 바뀌었는지 색으로 보여 줘요. 5분 안에는 되돌릴 수 있어요
// 방금 적용한 결과(POST /live/apply 응답)와 적용 직전 원래 칸을 비교해요. 위 배너는 적용 뒤 다시 계산한 지금 상황(POST /live/status)
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'
import PageMessage from '@/components/common/PageMessage.vue'
import { getLiveStatus } from '@/api/trips'
import { useReplanStore } from '@/stores/replan'
import { imageFor } from '@/utils/mapView'
import { toDuration } from '@/utils/time'
import { showToast } from '@/utils/toast'
import KakaoShareDemo from '@/components/live/KakaoShareDemo.vue'
import { shareToKakao } from '@/utils/kakaoShare'
import { devMode } from '@/utils/devMode'

const UNDO_CHECK_MS = 15000 // 되돌리기 버튼을 숨길지 다시 보는 간격

const router = useRouter()
const replan = useReplanStore()
const after = ref(null) // 적용 뒤 지금 상황
const isUndoing = ref(false)
const canUndo = ref(replan.canUndo())
let undoTimer = null

onMounted(async () => {
  undoTimer = setInterval(() => (canUndo.value = replan.canUndo()), UNDO_CHECK_MS)
  if (!replan.applied) return
  try {
    after.value = await getLiveStatus(replan.tripId, replan.position.lat, replan.position.lng)
  } catch (e) {
    showToast(e.message)
  }
})
onUnmounted(() => clearInterval(undoTimer))

// 적용 전 칸 (id → 칸)
function beforeOf(id) {
  return (replan.before || []).find((stop) => stop.id === id) || null
}
// 적용한 계산에서 앞에서 오는 이동 분
function moveOf(row) {
  if (!replan.plan) return null
  const found = replan.plan.stops.find((stop) => (row.id && stop.id === row.id) || (!stop.id && stop.name === row.name))
  return found ? found.moveMin : null
}

// 한 줄 모양: 배지·글자·색
function toRow(row) {
  const old = beforeOf(row.id)
  const isCurrent = after.value && after.value.currentStopId === row.id
  if (row.tag === 'done') return { ...row, cls: 'is-done', badge: null, meta: isCurrent ? '지금 · 여기서 출발' : '다녀옴', isCurrent }
  if (row.tag === 'added') return { ...row, cls: 'is-changed', badge: { text: '새로 넣음', cls: 'badge--danger' }, meta: toDuration(row.stay) }
  if (old && old.fixed) {
    const fixed = after.value && after.value.fixed
    const meta = fixed ? `${fixed.eta} 도착 · ` + (fixed.slackMin < 0 ? -fixed.slackMin + '분 늦음' : fixed.slackMin + '분 여유') : '계획 시각 그대로'
    return { ...row, cls: 'is-fixed', isFixed: true, badge: { text: '예약 그대로', cls: 'badge--navy' }, meta }
  }
  if (old && old.stay !== row.stay) {
    return { ...row, cls: 'is-changed', badge: { text: row.stay < old.stay ? '줄임' : '늘림', cls: 'badge--danger' }, was: old.stay + '분', now: row.stay + '분' }
  }
  if (old && old.time !== row.time) {
    return { ...row, cls: 'is-changed', badge: { text: '시간 이동', cls: 'badge--danger' }, was: old.time, now: row.time, after: ' · ' + toDuration(row.stay) }
  }
  return { ...row, cls: '', badge: { text: '그대로', cls: 'badge--neutral' }, meta: toDuration(row.stay) }
}

const rows = computed(() => (replan.applied ? replan.applied.stops.map(toRow) : []))
// 적용하면서 뺀 곳 (원래 있었는데 결과에 없는 칸)
const gone = computed(() => {
  if (!replan.applied || !replan.before) return []
  return replan.before.filter((stop) => !replan.applied.stops.some((row) => row.id === stop.id))
})

const banner = computed(() => {
  const fixed = after.value && after.value.fixed
  if (!fixed) return { cls: 'banner--success', icon: 'i-success', title: replan.applied.message }
  const isLate = fixed.slackMin < 0
  return {
    cls: isLate ? 'banner--danger' : 'banner--success',
    icon: isLate ? 'i-danger' : 'i-success',
    title: `${fixed.name} ${fixed.eta} · ` + (isLate ? -fixed.slackMin + '분 늦어요' : '여유 ' + fixed.slackMin + '분'),
  }
})

// 팀원 공유 (11/08 DEMO.md 8번 → 주형: 카카오톡 공유): 바뀐 일정을 카카오톡 피드 메시지로
//   시연 계정은 발표장에서 카카오 로그인이 뜨거나 진짜 사람에게 가지 않게 앱 안의 공유 화면(KakaoShareDemo)으로
const isShared = ref(false)
const isDemoShareOpen = ref(false)
const shareMessage = computed(() => {
  const fixed = after.value && after.value.fixed
  const method = (replan.plan && replan.plan.title) || '' // 고른 대안 이름 (예: 지하철로 바로 가기)
  const arrive = fixed ? `${fixed.name} ${fixed.eta} 도착` : banner.value.title
  const current = rows.value.find((row) => row.isCurrent) || rows.value[0]
  return {
    title: '일정이 바뀌었어요',
    description: arrive + (method ? ' · ' + method : '') + (fixed && fixed.slackMin >= 0 ? ' · 여유 ' + fixed.slackMin + '분' : ''),
    imageUrl: current ? location.origin + imageFor(current.category) : '',
    link: location.origin + '/live',
  }
})
async function share() {
  if (devMode.isOn) {
    isDemoShareOpen.value = true
    return
  }
  try {
    const result = await shareToKakao(shareMessage.value)
    if (result === 'copied') showToast('카카오톡을 쓸 수 없어서 링크를 복사했어요')
    isShared.value = true
  } catch (e) {
    showToast('공유하지 못했어요 · ' + e.message)
  }
}

async function undo() {
  if (!replan.canUndo()) {
    canUndo.value = false
    return showToast('5분이 지나서 되돌릴 수 없어요')
  }
  isUndoing.value = true
  try {
    await replan.undo()
    router.replace('/live/delay')
    showToast('원래 일정으로 되돌렸어요')
  } catch (e) {
    showToast('되돌리는 중에 실패했어요 · ' + e.message)
  }
  isUndoing.value = false
}
</script>

<template>
  <div class="scr scr-Applied">
    <div class="app">
      <TopBar title="바뀐 일정" icon="close" to="/live">
        <button v-if="replan.applied && canUndo" type="button" class="btn btn--sm btn--secondary" style="margin-right: 8px" :disabled="isUndoing" @click="undo"><i class="ic ic--s i-undo" aria-hidden="true"></i>되돌리기</button>
      </TopBar>

      <div v-if="!replan.applied" class="scroll">
        <PageMessage title="방금 바꾼 일정이 없어요" text="여행 중 화면에서 대안을 고르면 여기에 보여요" to="/live" button="여행 중 화면으로" />
      </div>

      <template v-else>
      <div class="scroll" style="padding-top: 4px; gap: 14px; bottom: 102px">
        <div class="banner" :class="banner.cls">
          <i class="ic ic--l" :class="banner.icon" aria-hidden="true"></i>
          <span class="banner__body">
            <span class="t-strong tn">{{ banner.title }}</span>
            <span v-if="banner.text" class="t-cap">{{ banner.text }}</span>
          </span>
        </div>

        <div class="legend" aria-label="색 안내">
          <span><i class="sw" style="background: var(--danger)"></i>바뀜</span>
          <span><i class="sw" style="background: var(--surface-raised); border: 1px solid var(--ink-subtle)"></i>그대로</span>
          <span><i class="sw" style="background: var(--surface-sunken); border: 1px dashed var(--line-strong)"></i>뺌</span>
          <span><i class="sw" style="background: var(--ink-subtle)"></i>다녀옴</span>
        </div>

        <ol class="tl" style="list-style: none; margin: 0; padding: 0">
          <template v-for="(row, i) in rows" :key="(row.id || '') + row.name">
            <li v-if="i > 0" class="leg"><span></span><span v-if="row.tag !== 'done' && moveOf(row) != null"><i class="ic ic--s i-walk" aria-hidden="true"></i>이동 {{ moveOf(row) }}분</span><span v-else></span></li>
            <li class="stop" :class="row.cls">
              <span class="stop__time">{{ row.time }}</span>
              <span class="stop__card">
                <img v-if="row.tag !== 'done' || row.isCurrent" class="thumb" :src="imageFor(row.category)" alt="" />
                <span class="stop__body">
                  <span class="stop__name">
                    <i v-if="row.tag === 'done' && !row.isCurrent" class="ic ic--s i-check c-success" aria-hidden="true"></i>
                    <i v-if="row.isFixed" class="ic ic--s i-lock c-navy" aria-hidden="true"></i>{{ row.name }}
                  </span>
                  <span v-if="row.was" class="stop__meta tn"><span class="was">{{ row.was }}</span><b class="c-danger">{{ row.now }}</b>{{ row.after }}</span>
                  <span v-else class="stop__meta tn">{{ row.meta }}</span>
                </span>
                <span v-if="row.isCurrent" class="me" aria-label="지금 위치" style="position: static; margin: 0 8px 0 0; flex: none"></span>
                <span v-if="row.badge" class="badge" :class="row.badge.cls">{{ row.badge.text }}</span>
              </span>
            </li>
          </template>
          <li v-for="stop in gone" :key="'gone' + stop.id" class="stop is-gone">
            <span class="stop__time"></span>
            <span class="stop__card">
              <img class="thumb" :src="imageFor(stop.category)" alt="" />
              <span class="stop__body"><span class="stop__name"><s>{{ stop.name }}</s></span><span class="stop__meta">일정에서 뺐어요</span></span>
              <span class="badge badge--neutral">뺌</span>
            </span>
          </li>
        </ol>

        <div v-if="replan.applied.nextAlert" class="banner banner--info">
          <i class="ic i-bell" aria-hidden="true"></i>
          <span class="banner__body"><span class="t-cap">출발 10분 전에 알려 드려요</span></span>
        </div>
        
      </div>

      <div class="cta" style="display: flex; gap: 8px">
        <!-- 카카오톡 공유 버튼: 카카오 디자인 가이드 색(#FEE500 · 검은 글씨). 공식 말풍선 심볼은 developers.kakao.com 리소스에서 받아 넣기 (아직 없음) -->
        <button type="button" class="btn btn--lg kakao-share" style="flex: 1" @click="share">{{ isShared ? '공유했어요' : '카카오톡으로 공유' }}</button>
        <RouterLink to="/live" class="btn btn--primary btn--lg" style="flex: 1"><i class="ic i-trip" aria-hidden="true"></i>지도로 돌아가기</RouterLink>
      </div>
      </template>
      <KakaoShareDemo v-if="isDemoShareOpen" :message="shareMessage" @close="isDemoShareOpen = false; isShared = true" />
    </div>
  </div>
</template>

<style>
.scr-Applied .legend{display:flex;gap:14px;align-items:center}
.scr-Applied .kakao-share{background:#FEE500;color:#191919;border:0;font-weight:700}
.scr-Applied .legend span{display:inline-flex;align-items:center;gap:6px;font-size:13px;line-height:18px;color:var(--ink-muted)}
.scr-Applied .legend i.sw{width:12px;height:12px;border-radius:4px;display:inline-block}
.scr-Applied .topbar .btn{flex:none}
.scr-Applied .stop.is-gone .stop__card{background:var(--surface-sunken);border:1px dashed var(--line-strong);opacity:.8}
.scr-Applied .was{font-size:13px;color:var(--ink-subtle);text-decoration:line-through;margin-right:4px}
</style>
