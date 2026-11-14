<script setup>
// 링크로 넣을 때 늦어지거나 겹치면 (③·④): 서버가 만든 방법(바꾸기·둘 다 가기·빼기) 중 고르거나, 곳마다 직접 줄이고 빼요
// 숫자는 전부 서버 계산 (POST /live/link-fit, 직접 고치면 POST /live/preview). 저장은 POST /live/apply → 바뀐 일정 화면
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'
import PageMessage from '@/components/common/PageMessage.vue'
import { useReplanStore } from '@/stores/replan'
import { showToast } from '@/utils/toast'
import { toDuration } from '@/utils/time'

const STAY_STEP_MIN = 10
const MIN_STAY_MIN = 10
const PREVIEW_WAIT_MS = 400 // 연속으로 누를 때 마지막 한 번만 계산
const LATE_END = '22:00' // 이보다 늦게 끝나면 빨간 글자

const router = useRouter()
const replan = useReplanStore()
const fit = computed(() => (replan.linkFit ? replan.linkFit.result : null))
const places = computed(() => (replan.linkFit ? replan.linkFit.places : []))
const placeTitle = computed(() => (places.value.length > 1 ? places.value[0].name + ' 외 ' + (places.value.length - 1) + '곳' : places.value.length ? places.value[0].name : ''))
const fixedName = computed(() => (fit.value ? fit.value.fixedName : null))

const options = computed(() => (fit.value ? fit.value.alternatives : []))
const pickedKey = ref(null) // 고른 방법 key, 직접 고르면 'custom'
const custom = ref(null) // 직접 고친 일정 (서버 계산 결과)
const isManual = ref(false)
const rows = ref([]) // 직접 고르기 줄 [{ id, name, stay, fixed, isNew, isDropped, ... }]
const isCalculating = ref(false)
const isApplying = ref(false)
const error = ref('')
let previewTimer = null

onMounted(() => {
  if (!fit.value) return router.replace('/live')
  const recommended = options.value.find((option) => option.recommended) || options.value[0]
  pickedKey.value = recommended ? recommended.key : null
  rows.value = fit.value.best.stops.map((stop) => ({ ...stop, isNew: stop.id == null, isDropped: false }))
})

const picked = computed(() => (pickedKey.value === 'custom' ? custom.value : options.value.find((option) => option.key === pickedKey.value) || null))

// 고정 일정이 있으면 여유, 없으면 끝나는 시각
function slackText(option) {
  if (!fixedName.value || option.finalArrive.startsWith('다음날')) return option.finalArrive + ' 끝'
  return option.slackMin < 0 ? toDuration(-option.slackMin) + ' 늦어요' : '여유 ' + toDuration(option.slackMin)
}
function isLate(option) {
  if (option.finalArrive.startsWith('다음날')) return true
  return fixedName.value ? option.slackMin < 0 : option.finalArrive >= LATE_END
}
// 초록 = 안 늦음, 빨강 = 늦음, 주황 = 이 방법에만 문 닫는 곳이 있음
function slackClass(option) {
  if (isLate(option)) return 'c-danger'
  return closedNames(option).some((name) => !sharedNames.value.includes(name)) ? 'c-warning' : 'c-success'
}
function closedNames(option) {
  return option.stops.filter((stop) => stop.closedReason).map((stop) => stop.name)
}
// 모든 방법에서 문 닫는 곳은 맨 위에 한 번만
const sharedNames = computed(() => {
  if (!options.value.length) return []
  return closedNames(options.value[0]).filter((name) => options.value.every((option) => closedNames(option).includes(name)))
})
const sharedClosed = computed(() => {
  if (!sharedNames.value.length) return ''
  const stop = options.value[0].stops.find((item) => item.name === sharedNames.value[0])
  return stop.name + (stop.closeAt ? ' ' + stop.closeAt + ' 마감' : ' 영업 끝') + (sharedNames.value.length > 1 ? ' 외 ' + (sharedNames.value.length - 1) + '곳' : '')
})
// 맨 위 한 줄: 다 늦으면 그 말을 한 번만
const topText = computed(() => {
  if (options.value.length && options.value.every(isLate)) return '모두 늦어요 · 가장 덜 늦는 곳 추천'
  return fit.value ? fit.value.message : ''
})

function openManual() {
  isManual.value = true
  pickedKey.value = 'custom'
  calculate()
}
function pickOption(key) {
  pickedKey.value = key
  isManual.value = false
}

// 직접 고르기: 머묾 늘리기·줄이기 (새 장소·고정 일정도 고정 일정 빼고 조절)
function changeStay(row, diff) {
  row.stay = Math.max(MIN_STAY_MIN, (row.stay || 0) + diff)
  schedule()
}
function toggleDrop(row) {
  row.isDropped = !row.isDropped
  schedule()
}
function schedule() {
  isCalculating.value = true
  clearTimeout(previewTimer)
  previewTimer = setTimeout(calculate, PREVIEW_WAIT_MS)
}
async function calculate() {
  isCalculating.value = true
  error.value = ''
  try {
    await replan.load()
    if (replan.error) throw new Error(replan.error)
    const list = []
    for (const row of rows.value) {
      if (row.isDropped) continue
      if (row.isNew) list.push({ lat: row.lat, lng: row.lng, placeId: row.placeId, name: row.name, category: row.category, stay: row.stay, eta: row.eta })
      else list.push({ id: row.id, stay: row.stay })
    }
    custom.value = await replan.preview(list)
  } catch (e) {
    error.value = e.message
  }
  isCalculating.value = false
}

async function apply() {
  if (!picked.value || isApplying.value) return
  isApplying.value = true
  error.value = ''
  try {
    await replan.load()
    if (replan.error) throw new Error(replan.error)
    await replan.apply(picked.value)
    replan.linkFit = null
    showToast(places.value.length + '곳 넣었어요')
    router.replace('/live/applied')
  } catch (e) {
    error.value = e.message
    isApplying.value = false
  }
}
</script>

<template>
  <div class="scr scr-LinkFit">
    <div class="app">
      <TopBar title="어떻게 넣을까요?" back="/live" />
      <PageMessage v-if="!fit" is-loading title="불러오는 중이에요" />
      <div v-else class="scroll" style="padding-top: 4px; gap: 12px; bottom: 102px">
        <div class="lf-top">
          <span class="lf-mark" aria-hidden="true"><i class="ic i-link"></i></span>
          <span><b class="t-sub">{{ placeTitle }}</b><span class="t-cap" style="display: block">{{ topText }}</span></span>
        </div>
        <p v-if="sharedClosed" class="lf-warn"><i class="ic ic--s i-warning" aria-hidden="true"></i>{{ sharedClosed }}</p>

        <div class="lf-list" role="radiogroup" aria-label="넣는 방법">
          <button v-for="option in options" :key="option.key" type="button" role="radio" :aria-checked="pickedKey === option.key" class="lf-opt" :class="{ 'is-on': pickedKey === option.key }" @click="pickOption(option.key)">
            <b class="t-strong lf-opt__title">{{ option.title }}</b>
            <span v-if="option.recommended" class="badge badge--navy">추천</span>
            <span class="lf-slack tn" :class="slackClass(option)">{{ slackText(option) }}</span>
          </button>

          <button type="button" role="radio" :aria-checked="pickedKey === 'custom'" class="lf-opt" :class="{ 'is-on': pickedKey === 'custom' }" @click="openManual">
            <b class="t-strong lf-opt__title">직접 고르기</b>
            <span v-if="custom && pickedKey === 'custom'" class="lf-slack tn" :class="slackClass(custom)">{{ isCalculating ? '계산 중' : slackText(custom) }}</span>
            <i v-else class="ic i-edit subtle" aria-hidden="true"></i>
          </button>
        </div>

        <section v-if="isManual" class="card card--pad lf-manual" aria-label="곳마다 직접 고르기">
          <div v-for="row in rows" :key="row.id ?? row.name" class="lf-row" :class="{ 'is-dropped': row.isDropped }">
            <span class="lf-row__body">
              <b class="t-strong">{{ row.name }}</b>
              <span class="t-cap tn">
                <template v-if="row.fixed">{{ row.eta }} 고정</template>
                <template v-else-if="row.isDropped">뺌</template>
                <template v-else>{{ row.isNew ? '새 장소 · ' : '' }}{{ toDuration(row.stay) }}</template>
              </span>
            </span>
            <template v-if="!row.fixed && !row.isDropped">
              <button type="button" class="iconbtn iconbtn--soft lf-step" :aria-label="row.name + ' 10분 줄이기'" @click="changeStay(row, -STAY_STEP_MIN)"><i class="ic ic--s i-minus" aria-hidden="true"></i></button>
              <button type="button" class="iconbtn iconbtn--soft lf-step" :aria-label="row.name + ' 10분 늘리기'" @click="changeStay(row, STAY_STEP_MIN)"><i class="ic ic--s i-plus" aria-hidden="true"></i></button>
            </template>
            <button v-if="!row.fixed" type="button" class="btn btn--ghost btn--sm" @click="toggleDrop(row)">{{ row.isDropped ? '되살리기' : '빼기' }}</button>
          </div>
        </section>

        <p v-if="error" class="field__help c-danger" style="margin: 0" role="alert"><i class="ic ic--s i-danger" aria-hidden="true"></i>{{ error }}</p>
      </div>

      <div v-if="fit" class="cta">
        <button type="button" class="btn btn--primary btn--lg btn--block" :disabled="!picked || isApplying || isCalculating" @click="apply">{{ isApplying ? '넣는 중' : '넣기' }}</button>
      </div>
    </div>
  </div>
</template>

<style>
.scr-LinkFit .lf-top{display:flex;align-items:center;gap:12px;padding:4px 0}
.scr-LinkFit .lf-mark{flex:none;width:44px;height:44px;border-radius:14px;display:flex;align-items:center;justify-content:center;color:#fff;background:linear-gradient(135deg,#F58529,#DD2A7B 55%,#8134AF)}
.scr-LinkFit .lf-list{display:flex;flex-direction:column;gap:8px}
.scr-LinkFit .lf-opt{display:flex;align-items:center;gap:8px;padding:14px;border-radius:14px;border:1.5px solid var(--line);background:var(--surface-raised);font:inherit;color:var(--ink);text-align:left;cursor:pointer}
.scr-LinkFit .lf-opt.is-on{border-color:var(--navy);background:var(--navy-soft)}
.scr-LinkFit .lf-opt__title{flex:1;min-width:0}
.scr-LinkFit .lf-slack{flex:none;font-size:14px;font-weight:700}
.scr-LinkFit .lf-warn{display:flex;align-items:center;gap:6px;margin:0;padding:8px 12px;border-radius:10px;font-size:13px;font-weight:600;color:var(--warning);background:var(--warning-soft)}
.scr-LinkFit .lf-manual{display:flex;flex-direction:column;gap:10px}
.scr-LinkFit .lf-row{display:flex;align-items:center;gap:6px}
.scr-LinkFit .lf-row.is-dropped .t-strong{text-decoration:line-through;color:var(--ink-subtle)}
.scr-LinkFit .lf-row__body{flex:1;min-width:0;display:flex;flex-direction:column}
.scr-LinkFit .lf-step{width:36px;height:36px;flex:none}
</style>
