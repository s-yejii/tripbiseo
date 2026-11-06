<script setup>
// S16-a 대안 · 시간 줄이기 (④): 남은 장소의 머무는 시간을 줄여서 고정 일정에 맞춰요
// 장소를 누르면 S16-a2 슬라이더에서 몇 분 머물지 정해요. 정한 만큼 서버가 다시 계산(POST /live/preview)하고,
// 적용하면 계산된 일정 그대로 저장(POST /live/apply)해요. 아무것도 안 정하면 서버 추천(A 시간 단축)을 적용해요.
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'
import PageMessage from '@/components/common/PageMessage.vue'
import AltTabs from '@/components/live/AltTabs.vue'
import QuickAltCard from '@/components/live/QuickAltCard.vue'
import LateBadge from '@/components/live/LateBadge.vue'
import { importance } from '@/data/places'
import { useReplanStore } from '@/stores/replan'
import { imageFor } from '@/utils/mapView'
import { showToast } from '@/utils/toast'

const router = useRouter()
const replan = useReplanStore()
const result = ref(null) // 사용자가 정한 대로 계산한 결과 (안 정했으면 null)
const isApplying = ref(false)

onMounted(async () => {
  await replan.load()
  await recalc()
})

const isEdited = computed(() => Object.keys(replan.trimPlan).length > 0)
const recommend = computed(() => replan.findOption('시간 단축'))

// 서버 추천에서 줄인 칸 하나 ("60분 → 30분"이 붙은 첫 칸)
const recommendStop = computed(() => {
  if (!recommend.value) return null
  const changed = recommend.value.stops.find((stop) => stop.change)
  if (!changed) return null
  const original = replan.stops.find((stop) => stop.id === changed.id)
  return { id: changed.id, name: changed.name, cut: original.stay - changed.stay }
})

// 정한 것들로 다시 계산
async function recalc() {
  if (!isEdited.value) {
    result.value = null
    return
  }
  try {
    result.value = await replan.preview(replan.trimList(replan.trimPlan))
  } catch (e) {
    showToast(e.message)
  }
}

// 줄인 시간 합계 (뺀 곳은 머묾 전체)
const cutTotal = computed(() => {
  let total = 0
  for (const stop of replan.stops) {
    const planned = replan.trimPlan[stop.id]
    if (planned != null) total += stop.stay - planned
  }
  return total
})
const slack = computed(() => (result.value ? result.value.slackMin : replan.base ? replan.base.slackMin : 0))
const isOk = computed(() => slack.value >= 0)
const percent = computed(() => (replan.lateMin ? Math.min(100, Math.round((cutTotal.value / replan.lateMin) * 100)) : 100))
const basisName = computed(() => (replan.basisStop ? replan.basisStop.name : '마지막 일정'))

const resultText = computed(() => {
  if (!isEdited.value) {
    if (recommendStop.value) return `추천 ${recommendStop.value.name} ${recommendStop.value.cut}분`
    return replan.lateMin ? '줄일 곳이 없어요' : '안 늦어요'
  }
  const arrive = result.value ? result.value.finalArrive : '-'
  return `${arrive} 도착 · ` + (isOk.value ? `여유 ${slack.value}분` : `${-slack.value}분 늦어요`)
})

const button = computed(() => {
  if (!isEdited.value) {
    if (recommendStop.value) return { text: `${recommendStop.value.name} ${recommendStop.value.cut}분 줄이기`, isOff: false }
    return replan.lateMin ? { text: '한 곳 빼러 가기', to: '/live/alt/drop', isOff: false } : { text: '그대로 가요', to: '/live', isOff: false }
  }
  if (!isOk.value && replan.lateMin) return { text: `${cutTotal.value}분 줄이기 · 아직 ${-slack.value}분 늦어요`, isOff: false }
  return { text: `${cutTotal.value}분 줄이기`, isOff: false }
})

// 칸 한 줄에 보일 글자
function rowMeta(stop) {
  const planned = replan.trimPlan[stop.id]
  if (planned === 0) return '뺌'
  if (planned != null) return `<s>${stop.stay}분</s> → <b>${planned}분</b>`
  return stop.stay + '분'
}

function reset() {
  replan.trimPlan = {}
  result.value = null
}

async function apply() {
  if (button.value.to) return router.replace(button.value.to)
  if (button.value.isOff || isApplying.value) return
  isApplying.value = true
  try {
    await replan.apply(isEdited.value ? result.value : recommend.value)
    router.replace('/live/applied')
  } catch (e) {
    showToast(e.message)
  }
  isApplying.value = false
}
</script>

<template>
  <div class="scr scr-Alternatives">
    <div class="app">
      <TopBar title="대안 고르기" back="/live/delay"><LateBadge /></TopBar>

      <div v-if="!replan.base" class="scroll">
        <PageMessage v-if="replan.error" icon="i-warning" title="대안을 만들 수 없어요" :text="replan.error" to="/live" button="여행 중 화면으로" />
        <PageMessage v-else is-loading title="지금 위치에서 다시 계산하는 중이에요" />
      </div>

      <template v-else>
      <div class="scroll" style="padding-top: 4px; gap: 12px; bottom: 102px">
        <QuickAltCard />
        <AltTabs active="trim" />

        <section class="tsum" aria-label="줄인 시간 합계" aria-live="polite">
          <div class="tsum__top">
            <span class="tsum__lbl">줄인 시간</span>
            <span v-if="replan.lateMin" class="tsum__need">필요 <b>{{ replan.lateMin }}분</b></span>
          </div>
          <div class="tsum__mid">
            <span class="tsum__big" :class="cutTotal === 0 ? '' : isOk ? 'c-success' : 'c-warning'">{{ cutTotal }}분</span>
            <div class="tsum__bar" role="progressbar" aria-label="필요한 만큼 줄였는지" :aria-valuenow="percent" aria-valuemin="0" aria-valuemax="100"><i :class="{ ok: isOk && cutTotal > 0 }" :style="{ width: (isEdited ? percent : 0) + '%' }"></i></div>
          </div>
          <p class="tsum__res" :class="!isEdited ? 'muted' : isOk ? 'c-success' : 'c-warning'">
            <i class="ic ic--s" :class="!isEdited ? 'i-info' : isOk ? 'i-success' : 'i-warning'" aria-hidden="true"></i><span>{{ resultText }}</span>
            <button v-if="isEdited" type="button" class="textlink" @click="reset"><i class="ic ic--s i-undo" aria-hidden="true"></i>처음부터</button>
          </p>
        </section>

        <div class="now-block">
          <span class="me" aria-hidden="true"></span>
          <img v-if="replan.currentStop" class="thumb" :src="imageFor(replan.currentStop.category)" alt="" style="width: 40px; height: 40px" />
          <span style="flex: 1">
            <span class="t-strong" style="display: block">지금 · {{ replan.currentStop ? replan.currentStop.name : '이동 중' }}</span>
            <span class="t-cap tn">{{ replan.status.now }}</span>
          </span>
        </div>

        <div style="display: flex; flex-direction: column; gap: 8px">
          <template v-for="stop in replan.stops" :key="stop.id">
            <!-- 고정 일정: 못 줄여요 -->
            <div v-if="stop.fixed" class="opt is-locked">
              <img class="thumb" :src="imageFor(stop.category)" alt="" />
              <span class="opt__body"><b class="t-sub">{{ stop.name }}</b><span class="t-cap tn">{{ stop.eta }} 고정</span></span>
              <i class="ic i-lock subtle" aria-hidden="true"></i>
            </div>
            <!-- 줄일 수 있는 곳 (꼭 갈 곳도 20분까지) -->
            <RouterLink
              v-else
              :to="'/live/alt/trim?id=' + stop.id"
              class="opt tr-row"
              :class="{ 'is-set': replan.trimPlan[stop.id] != null, 'is-gone': replan.trimPlan[stop.id] === 0 }"
            >
              <img class="thumb" :src="imageFor(stop.category)" alt="" />
              <span class="opt__body">
                <span class="tr-name"><b class="t-sub">{{ stop.name }}</b><span class="imp" :class="'imp--' + (stop.importance || 'maybe')">{{ importance[stop.importance || 'maybe'] }}</span></span>
                <span class="tr-meta" v-html="rowMeta(stop)"></span>
              </span>
              <span v-if="replan.trimPlan[stop.id] != null" class="tr-cut">−{{ stop.stay - replan.trimPlan[stop.id] }}분</span>
              <span v-else-if="!isEdited && recommendStop && recommendStop.id === stop.id" class="badge badge--coral">추천 −{{ recommendStop.cut }}분</span>
              <i class="ic i-next subtle" aria-hidden="true"></i>
            </RouterLink>
          </template>
        </div>
      </div>

      <div class="cta">
        <button type="button" class="btn btn--primary btn--lg btn--block" :disabled="button.isOff || isApplying" @click="apply">{{ button.text }}</button>
      </div>
      </template>
    </div>
  </div>
</template>

<style>
.scr-Alternatives .tsum{position:sticky;top:0;z-index:3;display:flex;flex-direction:column;gap:4px;padding:8px 12px;border-radius:var(--radius-lg);background:var(--surface-raised);border:1px solid var(--line)}
.scr-Alternatives .tsum__top{display:flex;align-items:center;justify-content:space-between;gap:8px;font-size:12px;line-height:16px;color:var(--ink-muted)}
.scr-Alternatives .tsum__need{font-variant-numeric:tabular-nums}
.scr-Alternatives .tsum__need b{color:var(--ink);font-weight:700}
.scr-Alternatives .tsum__mid{display:flex;align-items:center;gap:10px}
.scr-Alternatives .tsum__big{flex:none;font-size:20px;line-height:26px;font-weight:700;font-variant-numeric:tabular-nums}
.scr-Alternatives .tsum__bar{flex:1;position:relative;height:6px;border-radius:999px;background:var(--surface-sunken);overflow:hidden}
.scr-Alternatives .tsum__bar i{position:absolute;left:0;top:0;bottom:0;border-radius:999px;background:var(--warning);transition:width .2s}
.scr-Alternatives .tsum__res{display:flex;align-items:center;gap:4px;margin:0;font-size:12px;line-height:16px;font-weight:600;font-variant-numeric:tabular-nums}
.scr-Alternatives .tsum__res .ic{width:13px;height:13px;flex:none}
.scr-Alternatives .tr-name{display:flex;align-items:center;gap:6px}
.scr-Alternatives .tr-row{text-decoration:none}
.scr-Alternatives .tr-row .opt__body{gap:2px}
.scr-Alternatives .tr-meta{font-size:13px;color:var(--ink-muted);font-variant-numeric:tabular-nums}
.scr-Alternatives .tr-hint{display:flex;gap:6px;align-items:flex-start;margin:0;font-size:13px;color:var(--ink-muted)}
/* 줄인 뒤 모습 */
.scr-Alternatives .tsum__bar i.ok{background:var(--success)}
.scr-Alternatives .tsum__res .textlink{margin-left:auto;min-height:24px;padding:0 2px;font-size:12px;font-weight:500}
.scr-Alternatives .tr-meta s{color:var(--ink-subtle)}
.scr-Alternatives .tr-meta b{color:var(--success)}
.scr-Alternatives .tr-cut{flex:none;font-size:14px;font-weight:700;color:var(--success);font-variant-numeric:tabular-nums}
.scr-Alternatives .tr-row.is-set{border-color:var(--success);background:var(--success-soft,#EAF6EF)}
.scr-Alternatives .tr-row.is-gone .t-sub{text-decoration:line-through;color:var(--ink-subtle)}
</style>
