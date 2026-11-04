<script setup>
// S07 원하는 것 말하기 (③): 말로 적거나 칩을 누르면 "일정에 넣을 조건" 카드에 쌓이고, 일정 만들기가 그 조건을 그대로 써요 (11/03 진짜 동작)
//   말 → 서버(POST /api/plans/chat, Gemini)가 조건으로 정리: 꼭 갈 곳(mustVisit) · 시간 고정(fixed) · 장소 종류(kinds) · 분위기(tags) · 날짜·지역
//        + 칩에 없는 요청(needs: "4명이 앉을 자리") → 일정 만들 때 서버가 후보 리뷰로 확인 (11/04)
//        + 꼭/가능하면 갈 곳(mustVisit·wishVisit) · 꼭 넣을 종류(mustKinds) · 뺄 종류(avoidKinds) · 피할 곳(avoidVisit)
//          · 끝 장소(finish) · 걷는 시간 상한(maxWalkMin) (11/04)
//   다음 질문은 서버가 골라 보내요(question): 꼭/가능하면 갈 곳 → 피하고 싶은 것 → 시간이 정해진 일정. 첫 말풍선이 첫 질문이에요
//   조건 카드의 [꼭]·[가능하면]을 누르면 서로 바뀌어요 (AI가 잘못 나눠도 사용자가 바로 고치게)
//   칩 → Gemini 없이 바로 종류·분위기 조건에 더함 (일정 만들기에서 칸 종류·리뷰 태그 점수로 쓰임)
//   조건 카드의 ×로 뺄 수 있어요. AI가 안 되면 정리는 못 하고, 한 말은 일정 만들 때 AI에게 그대로 넘겨요
import { computed, nextTick, ref } from 'vue'
import TopBar from '@/components/common/TopBar.vue'
import { chatPlan } from '@/api/trips'
import { useTripStore } from '@/stores/trip'
import { KIND_CHIPS, TAG_CHIPS } from '@/utils/styleCodes'
import { toShortDate } from '@/utils/tripView'
import { showToast } from '@/utils/toast'

const store = useTripStore()
if (!store.planConditions) store.planConditions = { kinds: store.planKinds, tags: store.planTags }
const conditions = computed(() => store.planConditions)

// 첫 질문 (서버 PlanQuestionMaker의 visit 질문과 같은 글)
const FIRST_QUESTION = '꼭 가고 싶은 곳이나, 가능하면 가고 싶은 곳이 있어요? 예: "경복궁은 꼭 가고 싶어", "시간 되면 한강도 가고 싶어"'
const messages = ref([{ me: false, text: FIRST_QUESTION }])
const draft = ref('')
const isWaiting = ref(false)
const logBox = ref(null)

// ---------------------------------------------------------------- 위 요약 (여행 정보에서 고른 값)
const dateLabel = computed(() => {
  const start = conditions.value.startDate || store.planStartDate
  const end = conditions.value.endDate || start
  return start === end ? toShortDate(start) + ' 당일' : toShortDate(start) + '–' + toShortDate(end)
})
const kindLabel = computed(() => labelsOf(KIND_CHIPS, conditions.value.kinds || []).join('·') || '맛집·카페')

// ---------------------------------------------------------------- 조건 카드
const mustList = computed(() => conditions.value.mustVisit || [])
const fixedList = computed(() => conditions.value.fixed || [])
const kindList = computed(() => conditions.value.kinds || [])
const tagList = computed(() => conditions.value.tags || [])
const needList = computed(() => conditions.value.needs || []) // 칩에 없는 요청 → 서버가 후보 리뷰로 확인 (11/04)
const wishList = computed(() => conditions.value.wishVisit || []) // 가능하면 갈 곳 (11/04)
const mustKindList = computed(() => conditions.value.mustKinds || [])
const avoidKindList = computed(() => conditions.value.avoidKinds || [])
const avoidList = computed(() => conditions.value.avoidVisit || []) // 피하고 싶은 곳 이름
const finish = computed(() => conditions.value.finish || null)
const isManyDays = computed(() => !!store.planEndDate) // 여러 날 여행이면 고정 일정에 며칠째를 같이 보여 줌
const maxWalkMin = computed(() => conditions.value.maxWalkMin || null)
const ruleCount = computed(() => {
  const lists = mustList.value.length + wishList.value.length + mustKindList.value.length + avoidKindList.value.length + avoidList.value.length
    + fixedList.value.length + kindList.value.length + tagList.value.length + needList.value.length
  return lists + (finish.value ? 1 : 0) + (maxWalkMin.value ? 1 : 0)
})

// 아직 안 고른 종류·분위기만 칩으로
const quickChips = computed(() => {
  const chips = []
  for (const tag of TAG_CHIPS) {
    if (!tagList.value.includes(tag.code)) chips.push({ type: 'tags', code: tag.code, text: tag.label + ' 곳' })
  }
  for (const kind of KIND_CHIPS) {
    if (!kindList.value.includes(kind.code) && !avoidKindList.value.includes(kind.code)) chips.push({ type: 'kinds', code: kind.code, text: kind.label + '도 넣기' })
  }
  return chips
})

function labelsOf(chips, codes) {
  return chips.filter((chip) => codes.includes(chip.code)).map((chip) => chip.label)
}
function labelOf(chips, code) {
  const chip = chips.find((c) => c.code === code)
  return chip ? chip.label : code
}

// 조건 바꾸기: 여행 정보 화면(store.planKinds·planTags)도 같은 값을 보게 같이 바꿈
function setList(key, list) {
  store.planConditions = { ...conditions.value, [key]: list }
  if (key === 'kinds') store.planKinds = list
  if (key === 'tags') store.planTags = list
}
// 값 하나짜리 조건 빼기 (끝 장소·걷는 시간 상한)
function clearValue(key) {
  store.planConditions = { ...conditions.value, [key]: null }
}
// 꼭 ↔ 가능하면 바꾸기: 한쪽 목록에서 빼서 다른 쪽에 넣음
function moveVisit(name, from, to) {
  const fromList = (conditions.value[from] || []).filter((item) => item !== name)
  const toList = [...(conditions.value[to] || []), name]
  store.planConditions = { ...conditions.value, [from]: fromList, [to]: toList }
}
function removeItem(key, index) {
  if (key === 'kinds' && kindList.value.length === 1) return showToast('장소 종류는 하나 이상 있어야 해요')
  const list = [...(conditions.value[key] || [])]
  list.splice(index, 1)
  setList(key, list)
}

// ---------------------------------------------------------------- 대화
async function scrollDown() {
  await nextTick()
  if (logBox.value) logBox.value.scrollTop = logBox.value.scrollHeight // 답을 기다리는 사이 다른 화면으로 갔으면 없음
}

// 칩: Gemini 없이 바로 조건에 더함
function sayChip(chip) {
  setList(chip.type, [...(conditions.value[chip.type] || []), chip.code])
  messages.value.push({ me: true, text: chip.text })
  const reply = chip.type === 'tags'
    ? `리뷰에 '${labelOf(TAG_CHIPS, chip.code)}' 같은 말이 많은 곳을 먼저 고를게요.`
    : `${labelOf(KIND_CHIPS, chip.code)} 장소도 하루 일정 칸에 넣을게요.`
  messages.value.push({ me: false, text: reply })
  scrollDown()
}

// 말: 서버(Gemini)가 조건으로 정리 → 바뀐 날짜·종류·태그를 여행 정보 화면 값에도 반영
async function send() {
  const text = draft.value.trim()
  if (!text) return showToast('보낼 말을 적어 주세요')
  if (isWaiting.value) return
  messages.value.push({ me: true, text })
  store.planMessages.push(text)
  draft.value = ''
  isWaiting.value = true
  scrollDown()
  try {
    const res = await chatPlan(text, conditions.value)
    store.planConditions = res.conditions
    store.planKinds = res.conditions.kinds || store.planKinds
    store.planTags = res.conditions.tags || store.planTags
    store.planStartDate = res.conditions.startDate || store.planStartDate
    store.planEndDate = res.conditions.endDate && res.conditions.endDate !== res.conditions.startDate ? res.conditions.endDate : null
    messages.value.push({ me: false, text: res.reply })
    if (res.question) messages.value.push({ me: false, text: res.question }) // 서버가 고른 다음 질문
  } catch (e) {
    // AI가 늦거나 안 될 때(503·504)는 기술 얘기 없이 안내. 그 밖의 오류(로그인 등)는 서버 문장 그대로
    const isAiDown = e.status === 503 || e.status === 504
    messages.value.push({ me: false, text: isAiDown ? '이 말은 일정을 만들 때 같이 볼게요.' : e.message })
  } finally {
    isWaiting.value = false
    scrollDown()
  }
}
</script>

<template>
  <div class="scr scr-PlanChat">
    <div class="app">
      <TopBar title="원하는 것 말하기" back="/plan/info"><span class="topbar__side tn">3 / 4</span></TopBar>

      <RouterLink to="/plan/info" aria-label="여행 정보 수정하기" class="pc-sum">
        <span class="badge badge--navy tn">{{ dateLabel }}</span>
        <span class="badge badge--navy tn">{{ conditions.people || 1 }}명</span>
        <span v-if="conditions.region" class="badge badge--navy">{{ conditions.region }}</span>
        <span class="badge badge--navy">{{ kindLabel }}</span>
        <span class="badge badge--neutral" style="margin-left: auto"><i class="ic ic--s i-edit" aria-hidden="true"></i>수정</span>
      </RouterLink>

      <div ref="logBox" class="pc-log" aria-live="polite">
        <p v-for="(msg, i) in messages" :key="i" class="bub" :class="msg.me ? 'bub--me' : 'bub--ai'">{{ msg.text }}</p>
        <p v-if="isWaiting" class="bub bub--ai subtle">조건으로 정리하는 중…</p>

        <div class="card card--pad pc-rules">
          <p class="t-cap">일정에 넣을 조건 {{ ruleCount }}개 · ×로 뺄 수 있어요</p>
          <p v-for="(name, i) in mustList" :key="'m' + name" class="pc-rule">
            <i class="ic ic--s i-pin c-coral" aria-hidden="true"></i><span class="pc-rule__text">갈 곳 · <b>{{ name }}</b></span>
            <button type="button" class="badge badge--navy pc-imp" :aria-label="name + ' 꼭 → 가능하면으로 바꾸기'" @click="moveVisit(name, 'mustVisit', 'wishVisit')">꼭</button>
            <button type="button" class="pc-x" :aria-label="name + ' 빼기'" @click="removeItem('mustVisit', i)"><i class="ic ic--s i-close" aria-hidden="true"></i></button>
          </p>
          <p v-for="(name, i) in wishList" :key="'w' + name" class="pc-rule">
            <i class="ic ic--s i-pin c-navy" aria-hidden="true"></i><span class="pc-rule__text">갈 곳 · <b>{{ name }}</b></span>
            <button type="button" class="badge badge--neutral pc-imp" :aria-label="name + ' 가능하면 → 꼭으로 바꾸기'" @click="moveVisit(name, 'wishVisit', 'mustVisit')">가능하면</button>
            <button type="button" class="pc-x" :aria-label="name + ' 빼기'" @click="removeItem('wishVisit', i)"><i class="ic ic--s i-close" aria-hidden="true"></i></button>
          </p>
          <p v-for="(code, i) in mustKindList" :key="'mk' + code" class="pc-rule">
            <i class="ic ic--s i-gift c-coral" aria-hidden="true"></i><span class="pc-rule__text">꼭 넣을 곳 · <b>{{ labelOf(KIND_CHIPS, code) }}</b></span>
            <span class="badge badge--navy">꼭</span>
            <button type="button" class="pc-x" :aria-label="labelOf(KIND_CHIPS, code) + ' 꼭 빼기'" @click="removeItem('mustKinds', i)"><i class="ic ic--s i-close" aria-hidden="true"></i></button>
          </p>
          <p v-if="finish" class="pc-rule">
            <i class="ic ic--s i-flag c-navy" aria-hidden="true"></i><span class="pc-rule__text tn">여행 끝 · <b>{{ finish.time }} {{ finish.name }} 도착</b></span>
            <button type="button" class="pc-x" :aria-label="finish.name + ' 빼기'" @click="clearValue('finish')"><i class="ic ic--s i-close" aria-hidden="true"></i></button>
          </p>
          <p v-if="maxWalkMin" class="pc-rule">
            <i class="ic ic--s i-walk c-navy" aria-hidden="true"></i><span class="pc-rule__text tn">한 번에 <b>{{ maxWalkMin }}분 넘게 걷지 않기</b></span>
            <button type="button" class="pc-x" aria-label="걷는 시간 조건 빼기" @click="clearValue('maxWalkMin')"><i class="ic ic--s i-close" aria-hidden="true"></i></button>
          </p>
          <p v-for="(name, i) in avoidList" :key="'av' + name" class="pc-rule">
            <i class="ic ic--s i-minus c-coral" aria-hidden="true"></i><span class="pc-rule__text">피할 곳 · <b>{{ name }}</b></span>
            <button type="button" class="pc-x" :aria-label="name + ' 피할 곳에서 지우기'" @click="removeItem('avoidVisit', i)"><i class="ic ic--s i-close" aria-hidden="true"></i></button>
          </p>
          <p v-for="(code, i) in avoidKindList" :key="'a' + code" class="pc-rule">
            <i class="ic ic--s i-minus c-coral" aria-hidden="true"></i><span class="pc-rule__text">빼기 · <b>{{ labelOf(KIND_CHIPS, code) }}</b></span>
            <button type="button" class="pc-x" :aria-label="labelOf(KIND_CHIPS, code) + ' 빼기 조건 지우기'" @click="removeItem('avoidKinds', i)"><i class="ic ic--s i-close" aria-hidden="true"></i></button>
          </p>
          <p v-for="(fixed, i) in fixedList" :key="'f' + fixed.name" class="pc-rule">
            <i class="ic ic--s i-lock c-navy" aria-hidden="true"></i><span class="pc-rule__text tn">시간 고정 · <b>{{ isManyDays && fixed.day ? fixed.day + '일차 ' : '' }}{{ fixed.time }} {{ fixed.name }}</b></span>
            <button type="button" class="pc-x" :aria-label="fixed.name + ' 빼기'" @click="removeItem('fixed', i)"><i class="ic ic--s i-close" aria-hidden="true"></i></button>
          </p>
          <p v-for="(code, i) in kindList" :key="'k' + code" class="pc-rule">
            <i class="ic ic--s i-check c-success" aria-hidden="true"></i><span class="pc-rule__text">이런 곳 위주 · <b>{{ labelOf(KIND_CHIPS, code) }}</b></span>
            <button type="button" class="pc-x" :aria-label="labelOf(KIND_CHIPS, code) + ' 빼기'" @click="removeItem('kinds', i)"><i class="ic ic--s i-close" aria-hidden="true"></i></button>
          </p>
          <p v-for="(code, i) in tagList" :key="'t' + code" class="pc-rule">
            <i class="ic ic--s i-check c-success" aria-hidden="true"></i><span class="pc-rule__text">먼저 고르기 · <b>{{ labelOf(TAG_CHIPS, code) }}</b></span>
            <button type="button" class="pc-x" :aria-label="labelOf(TAG_CHIPS, code) + ' 빼기'" @click="removeItem('tags', i)"><i class="ic ic--s i-close" aria-hidden="true"></i></button>
          </p>
          <p v-for="(need, i) in needList" :key="'n' + need" class="pc-rule">
            <i class="ic ic--s i-search c-navy" aria-hidden="true"></i><span class="pc-rule__text">리뷰로 확인 · <b>{{ need }}</b></span>
            <button type="button" class="pc-x" :aria-label="need + ' 빼기'" @click="removeItem('needs', i)"><i class="ic ic--s i-close" aria-hidden="true"></i></button>
          </p>
        </div>
      </div>

      <div class="pc-input">
        <div class="chips noscroll pc-chips">
          <button v-for="chip in quickChips" :key="chip.type + chip.code" type="button" class="chip chip--sm" @click="sayChip(chip)">
            <i class="ic ic--s i-plus" aria-hidden="true"></i>{{ chip.text }}
          </button>
        </div>
        <form class="field__box" style="border-radius: var(--radius-full); padding-left: 16px" @submit.prevent="send">
          <label for="pc-msg" class="sr">메시지</label>
          <input id="pc-msg" v-model="draft" type="text" placeholder="예: 카페는 별로 안 가고 싶어" autocomplete="off" maxlength="300" />
          <button type="submit" class="iconbtn" aria-label="보내기" :disabled="isWaiting" style="color: var(--navy)"><i class="ic i-send" aria-hidden="true"></i></button>
        </form>
      </div>

      <div class="cta">
        <RouterLink to="/plan/generating" class="btn btn--primary btn--lg btn--block">이대로 일정 만들기</RouterLink>
      </div>
    </div>
  </div>
</template>

<style>
.scr-PlanChat .pc-input{position:absolute;left:0;right:0;bottom:102px;padding:0 16px;display:flex;flex-direction:column;gap:10px}
.scr-PlanChat .pc-chips{flex-wrap:nowrap;overflow-x:auto;margin:0 -16px;padding:0 16px}
.scr-PlanChat .pc-sum{position:absolute;top:103px;left:16px;right:16px;display:flex;align-items:center;gap:6px;overflow:hidden;text-decoration:none;color:var(--ink);border-radius:var(--radius-md);padding:4px;margin:-4px;transition:background .15s}
.scr-PlanChat .pc-sum .badge{flex-shrink:0;white-space:nowrap}
.scr-PlanChat .pc-sum:hover{background:var(--navy-soft)}
.scr-PlanChat .pc-log{position:absolute;top:148px;left:16px;right:16px;bottom:216px;display:flex;flex-direction:column;gap:12px;overflow-y:auto;padding-bottom:8px}
.scr-PlanChat .pc-log>*{flex-shrink:0}
.scr-PlanChat .bub{margin:0;max-width:80%;padding:10px 14px;border-radius:16px;font-size:15px;line-height:22px}
.scr-PlanChat .bub--ai{align-self:flex-start;background:var(--surface-raised);border:1px solid var(--line);border-top-left-radius:4px}
.scr-PlanChat .bub--me{align-self:flex-end;background:var(--navy);color:#fff;border-top-right-radius:4px}
.scr-PlanChat .pc-rules{align-self:stretch;display:flex;flex-direction:column;gap:6px}
.scr-PlanChat .pc-rule{display:flex;align-items:center;gap:8px;margin:0;font-size:14px;line-height:20px}
.scr-PlanChat .pc-rule__text{flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.scr-PlanChat .pc-x{width:28px;height:28px;border:0;background:transparent;border-radius:50%;display:flex;align-items:center;justify-content:center;color:var(--ink-muted);cursor:pointer}
.scr-PlanChat .pc-x:hover{background:var(--line)}
.scr-PlanChat .pc-imp{border:0;cursor:pointer;flex-shrink:0;font:inherit;font-size:12px}
</style>
