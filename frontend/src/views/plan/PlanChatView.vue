<script setup>
// S07 원하는 것 말하기 (③): 하고 싶은 말을 적거나 빠른 칩을 누르면 "일정에 넣을 조건" 카드에 쌓여요
// 지금은 말한 그대로 조건이 돼요. 백엔드가 붙으면 POST /api/plans/chat (Gemini)이 조건으로 바꿔 줘요
import { nextTick, ref } from 'vue'
import TopBar from '@/components/common/TopBar.vue'
import { showToast } from '@/utils/toast'

// 빠른 칩 → 조건
const quickRules = [
  { say: '비 오면 실내로', icon: 'i-rain', text: '14–16시 비 → 실내 장소 먼저' },
  { say: '디저트 카페 하나 더', icon: 'i-plus', text: '디저트 카페 1곳 추가' },
  { say: '점심은 한식', icon: 'i-check', text: '점심은 한식으로' },
  { say: '계단 적은 길', icon: 'i-walk', text: '계단 적은 길로 이동' },
  { say: '카페에서 오래 쉬기', icon: 'i-clock', text: '카페 1시간 넉넉히' },
]

const messages = ref([
  { me: false, text: '꼭 가고 싶은 곳이나 피하고 싶은 게 있나요?' },
  { me: true, text: '공연 전에 올리브영·다이소랑 소품샵은 꼭 들르고 싶어. 웨이팅 긴 데는 싫어' },
])
// tags가 있으면 이름 옆에 중요도 배지를 붙여요
const rules = ref([
  { icon: 'i-pin', tags: [{ text: '올리브영·다이소', imp: 'must', label: '꼭' }, { text: '소품샵', imp: 'maybe', label: '가능하면' }] },
  { icon: 'i-clock', text: '웨이팅 길거나 쉬는 날인 곳 빼기' },
])
const usedChips = ref([])
const draft = ref('')
const freshIndex = ref(-1) // 방금 생긴 조건 (잠깐 강조)
const logBox = ref(null)

async function say(text, rule) {
  messages.value.push({ me: true, text })
  rules.value.push(rule)
  freshIndex.value = rules.value.length - 1
  usedChips.value.push(text)
  draft.value = ''
  await nextTick()
  logBox.value.scrollTop = logBox.value.scrollHeight
}
function sayChip(chip) {
  say(chip.say, { icon: chip.icon, text: chip.text })
}
function send() {
  const text = draft.value.trim()
  if (!text) return showToast('보낼 말을 적어 주세요')
  say(text, { icon: 'i-check', text })
}
</script>

<template>
  <div class="scr scr-PlanChat">
    <div class="app">
      <TopBar title="원하는 것 말하기" back="/plan/info"><span class="topbar__side tn">3 / 4</span></TopBar>

      <RouterLink to="/plan/info" aria-label="조건 수정하기" class="pc-sum">
        <span class="badge badge--navy tn">10/24 당일</span>
        <span class="badge badge--navy">연인 2명</span>
        <span class="badge badge--navy">맛집·카페</span>
        <span class="badge badge--navy tn"><i class="ic ic--s i-lock" aria-hidden="true"></i>없음</span>
        <span class="badge badge--neutral" style="margin-left: auto"><i class="ic ic--s i-edit" aria-hidden="true"></i>수정</span>
      </RouterLink>

      <div ref="logBox" class="pc-log" aria-live="polite">
        <p v-for="(msg, i) in messages" :key="i" class="bub" :class="msg.me ? 'bub--me' : 'bub--ai'">{{ msg.text }}</p>
        <div class="card card--pad pc-rules">
          <p class="t-cap">일정에 넣을 조건 {{ rules.length }}개</p>
          <p v-for="(rule, i) in rules" :key="i" class="t-strong pc-rule" :class="{ 'is-new': i === freshIndex }">
            <i class="ic i-success c-success" aria-hidden="true"></i><i class="ic ic--s" :class="rule.icon" aria-hidden="true"></i>
            <span v-if="rule.tags">
              <template v-for="(tag, j) in rule.tags" :key="tag.text">{{ j > 0 ? ' · ' : '' }}{{ tag.text }} <span class="imp" :class="'imp--' + tag.imp">{{ tag.label }}</span></template>
            </span>
            <span v-else>{{ rule.text }}</span>
          </p>
        </div>
      </div>

      <div class="pc-input">
        <div class="chips noscroll pc-chips">
          <template v-for="chip in quickRules" :key="chip.say">
            <button v-if="!usedChips.includes(chip.say)" type="button" class="chip chip--sm" @click="sayChip(chip)">
              <i class="ic ic--s" :class="chip.icon" aria-hidden="true"></i>{{ chip.say }}
            </button>
          </template>
        </div>
        <form class="field__box" style="border-radius: var(--radius-full); padding-left: 16px" @submit.prevent="send">
          <label for="pc-msg" class="sr">메시지</label>
          <input id="pc-msg" v-model="draft" type="text" placeholder="예: 저녁 전에 카페 한 곳 더" autocomplete="off" />
          <button type="submit" class="iconbtn" aria-label="보내기" style="color: var(--navy)"><i class="ic i-send" aria-hidden="true"></i></button>
        </form>
      </div>

      <div class="cta">
        <RouterLink :to="usedChips.includes('디저트 카페 하나 더') ? '/plan/generating?extra=1' : '/plan/generating'" class="btn btn--primary btn--lg btn--block">이대로 일정 만들기</RouterLink>
      </div>
    </div>
  </div>
</template>

<style>
.scr-PlanChat .pc-input{position:absolute;left:0;right:0;bottom:102px;padding:0 16px;display:flex;flex-direction:column;gap:10px}
.scr-PlanChat .pc-chips{flex-wrap:nowrap;overflow-x:auto;margin:0 -16px;padding:0 16px}
.scr-PlanChat .pc-sum{position:absolute;top:103px;left:16px;right:16px;display:flex;align-items:center;gap:6px;overflow:hidden;text-decoration:none;color:var(--ink);border-radius:var(--radius-md);padding:4px;margin:-4px;transition:background .15s}
.scr-PlanChat .pc-sum:hover{background:var(--navy-soft)}
.scr-PlanChat .pc-log{position:absolute;top:148px;left:16px;right:16px;bottom:216px;display:flex;flex-direction:column;gap:12px;overflow-y:auto;padding-bottom:8px}
.scr-PlanChat .pc-log>*{flex-shrink:0}
.scr-PlanChat .bub{margin:0;max-width:80%;padding:10px 14px;border-radius:16px;font-size:15px;line-height:22px}
.scr-PlanChat .bub--ai{align-self:flex-start;background:var(--surface-raised);border:1px solid var(--line);border-top-left-radius:4px}
.scr-PlanChat .bub--me{align-self:flex-end;background:var(--navy);color:#fff;border-top-right-radius:4px}
.scr-PlanChat .pc-rules{align-self:flex-start;width:290px;display:flex;flex-direction:column;gap:8px}
.scr-PlanChat .pc-rule{display:flex;align-items:center;gap:8px;margin:0}
.scr-PlanChat .pc-rule.is-new{animation:ruleIn .5s ease}
@keyframes ruleIn{from{background:var(--coral-soft)}to{background:transparent}}
</style>
