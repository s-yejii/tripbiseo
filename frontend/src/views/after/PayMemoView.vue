<script setup>
// S13-f 결제 메모 (⑤): 카드 결제가 잡히면 누가 얼마 냈는지 적어 그 장소 핀에 남겨요
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import BottomSheet from '@/components/common/BottomSheet.vue'
import { toWon } from '@/utils/time'
import { showToast } from '@/utils/toast'

const router = useRouter()

// 가짜 데이터: 결제 내역과 같이 간 사람 (백엔드가 붙으면 expense 테이블)
const total = 32000
const people = ref(['나', '지은']) // 계산에 들어간 사람
const myFriends = ['지은', '엄마', '민지', '준호'] // 친구 목록 (계산에서 뺀 사람도 다시 넣을 수 있어요)
const amounts = ref({}) // 이름 → 낸 돈
const adding = ref(false)

const friendsLeft = computed(() => myFriends.filter((name) => !people.value.includes(name)))
const sum = computed(() => people.value.reduce((acc, name) => acc + (amounts.value[name] || 0), 0))
const sumInfo = computed(() => {
  if (!sum.value) return { text: '합계 0원 / 결제 32,000원', cls: '', icon: 'i-info' }
  if (sum.value === total) return { text: '합계 32,000원 · 결제 금액과 같아요', cls: 'c-success', icon: 'i-success' }
  const diff = total - sum.value
  return { text: `합계 ${toWon(sum.value)} · ${toWon(Math.abs(diff))} ${diff > 0 ? '남았어요' : '넘었어요'}`, cls: 'c-warning', icon: 'i-warning' }
})

// 숫자만 받아서 1,000 단위로 보여 줘요
function setAmount(name, text) {
  amounts.value[name] = Math.min(Number(text.replace(/\D/g, '').slice(0, 7)) || 0, 9999999)
}
function showAmount(name) {
  const value = amounts.value[name]
  return value ? value.toLocaleString('ko-KR') : ''
}
// 10원 단위로 똑같이 나누고 남는 돈은 첫 사람이
function splitEven() {
  const count = people.value.length
  const each = Math.floor(total / count / 10) * 10
  people.value.forEach((name, i) => {
    amounts.value[name] = i === 0 ? total - each * (count - 1) : each
  })
}
function removePerson(name) {
  people.value = people.value.filter((x) => x !== name)
  delete amounts.value[name]
  showToast(name + ' 님을 이번 계산에서 뺐어요 · 친구 추가하기에서 다시 넣을 수 있어요')
}
function addPerson(name) {
  people.value.push(name)
  adding.value = friendsLeft.value.length > 0
  showToast(name + ' 님을 넣었어요')
}
function save() {
  router.push('/live')
  showToast('핀에 저장했어요 · 쭈꾸미 핀을 눌러 보세요')
}
</script>

<template>
  <div class="scr scr-PayMemo">
    <div class="app">
      <div class="map" aria-hidden="true"><img class="base live" src="/img/mapB.svg" alt="" style="top: -126px" /></div>
      <span class="pin pin--done pin--sel" style="left: 90px; top: 330px" aria-hidden="true"><i class="ic ic--s i-check" aria-hidden="true"></i></span>
      <div class="dim" aria-hidden="true"></div>

      <BottomSheet role="dialog" aria-modal="true" style="gap: 14px">
        <div class="sheet__head" style="gap: 12px">
          <img class="thumb" src="/img/gamjatang.jpg" alt="" />
          <div style="flex: 1; min-width: 0"><h1 class="h-title">천호 쭈꾸미골목 · 32,000원</h1><p class="t-cap tn">13:55 카드 결제 · 카드 내역에서 가져왔어요</p></div>
          <RouterLink to="/live" class="iconbtn iconbtn--soft" aria-label="닫기"><i class="ic i-close" aria-hidden="true"></i></RouterLink>
        </div>

        <div style="display: flex; flex-direction: column; gap: 8px">
          <div class="pm-head">
            <span style="display: flex; flex-direction: column; gap: 2px"><span class="field__label">누가 얼마 냈나요?</span><span class="t-cap">체크를 풀면 계산에서 빠져요</span></span>
            <button type="button" class="btn btn--secondary btn--sm" @click="splitEven">똑같이 나누기</button>
          </div>
          <div class="pm-list" role="group" aria-label="사람별 금액">
            <div v-for="(name, i) in people" :key="name" class="pm-row" :class="{ 'is-off': !amounts[name] }">
              <button type="button" class="pm-chk" role="checkbox" aria-checked="true" :aria-label="name + ' 계산에서 빼기'" @click="removePerson(name)">
                <span class="check is-on"><i class="ic ic--s i-check" aria-hidden="true"></i></span>
              </button>
              <label class="pm-name" :for="'pm-a' + i">{{ name }}</label>
              <span class="pm-amt">
                <input :id="'pm-a' + i" :value="showAmount(name)" type="text" inputmode="numeric" placeholder="0" autocomplete="off" @input="setAmount(name, $event.target.value); $event.target.value = showAmount(name)" />
                <span>원</span>
              </span>
            </div>

            <div v-if="adding" class="pm-pick" role="group" aria-label="친구 목록에서 추가">
              <div class="pm-pick__head"><span class="t-cap">내 친구 목록에서 눌러 넣어요</span><button type="button" class="textlink" @click="adding = false">닫기</button></div>
              <div v-if="friendsLeft.length" class="pm-pick__list">
                <button v-for="name in friendsLeft" :key="name" type="button" class="pm-fr" :aria-label="name + ' 추가'" @click="addPerson(name)">
                  <span class="pm-ava" aria-hidden="true">{{ name.slice(0, 1) }}</span>{{ name }}<i class="ic ic--s i-plus" aria-hidden="true"></i>
                </button>
              </div>
              <p v-else class="pm-pick__empty">친구 목록에 있는 사람을 모두 넣었어요. <RouterLink to="/my/friends" class="textlink">친구 추가하러 가기</RouterLink></p>
            </div>
            <button v-else type="button" class="pm-addrow" @click="adding = true"><span class="pm-ava" aria-hidden="true"><i class="ic ic--s i-plus" aria-hidden="true"></i></span>친구 추가하기</button>
          </div>
          <p class="field__help" :class="sumInfo.cls" style="margin: 0"><i class="ic ic--s" :class="sumInfo.icon" aria-hidden="true"></i><span class="tn">{{ sumInfo.text }}</span></p>
        </div>
        <p class="t-cap" style="display: flex; gap: 6px; align-items: center"><i class="ic ic--s i-pin c-navy" aria-hidden="true"></i>저장하면 지도 핀을 눌러 다시 볼 수 있어요</p>

        <div class="cta__row">
          <RouterLink to="/live" class="btn btn--secondary btn--lg">나중에</RouterLink>
          <button type="button" class="btn btn--primary btn--lg" :disabled="!sum" @click="save">핀에 저장</button>
        </div>
      </BottomSheet>
    </div>
  </div>
</template>

<style>
.scr-PayMemo .pm-list{display:flex;flex-direction:column;border:1px solid var(--line);border-radius:var(--radius-md);background:var(--surface-raised);overflow:hidden}
.scr-PayMemo .pm-row{display:flex;align-items:center;gap:12px;min-height:56px;padding:0 12px 0 14px;border-top:1px solid var(--line)}
.scr-PayMemo .pm-row:first-child{border-top:0}
.scr-PayMemo .pm-ava{flex:none;width:32px;height:32px;border-radius:999px;background:var(--navy-soft);color:var(--navy);display:flex;align-items:center;justify-content:center;font-size:13px;font-weight:700}
.scr-PayMemo .pm-name{flex:1;min-width:0;font-size:15px;font-weight:600;color:var(--ink);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.scr-PayMemo .pm-amt{flex:none;display:flex;align-items:center;gap:6px;width:148px;height:40px;padding:0 12px;border:1px solid var(--line-strong);border-radius:var(--radius-md);background:#fff;cursor:text;transition:border-color .15s,box-shadow .15s}
.scr-PayMemo .pm-amt:hover{border-color:var(--navy)}
.scr-PayMemo .pm-amt:focus-within{border-color:var(--navy);box-shadow:0 0 0 3px var(--navy-soft)}
.scr-PayMemo .pm-amt input{flex:1;min-width:0;border:0;outline:0;background:none;font:inherit;font-size:16px;font-weight:600;color:var(--ink);text-align:right;font-variant-numeric:tabular-nums;padding:0}
.scr-PayMemo .pm-amt input::placeholder{color:var(--line-strong);font-weight:400}
.scr-PayMemo .pm-amt span{font-size:15px;color:var(--ink-2,var(--muted))}
.scr-PayMemo .pm-row.is-off .pm-ava{background:var(--surface);color:var(--muted)}
.scr-PayMemo .pm-row.is-off .pm-name{color:var(--muted);font-weight:500}
.scr-PayMemo .pm-row.is-off .pm-amt{background:var(--surface)}
.scr-PayMemo .pm-addrow{display:flex;align-items:center;gap:12px;min-height:52px;padding:0 14px;border:0;border-top:1px solid var(--line);background:none;font:inherit;font-size:15px;font-weight:600;color:var(--navy);cursor:pointer;width:100%;text-align:left}
.scr-PayMemo .pm-addrow:hover{background:var(--navy-soft)}
.scr-PayMemo .pm-addrow .pm-ava{background:none;border:1px dashed var(--navy)}
.scr-PayMemo .pm-pick{display:flex;flex-direction:column;gap:10px;padding:12px 14px 14px;border-top:1px solid var(--line);background:var(--surface)}
.scr-PayMemo .pm-pick__head{display:flex;align-items:center;justify-content:space-between;gap:8px}
.scr-PayMemo .pm-pick__list{display:flex;flex-wrap:wrap;gap:8px}
.scr-PayMemo .pm-fr{display:inline-flex;align-items:center;gap:8px;height:40px;padding:0 14px 0 6px;border:1px solid var(--line-strong);border-radius:999px;background:#fff;font:inherit;font-size:15px;font-weight:600;color:var(--ink);cursor:pointer;transition:border-color .15s,background .15s}
.scr-PayMemo .pm-fr:hover{border-color:var(--navy);background:var(--navy-soft)}
.scr-PayMemo .pm-fr .pm-ava{width:28px;height:28px;font-size:12px}
.scr-PayMemo .pm-fr .i-plus{color:var(--navy)}
.scr-PayMemo .pm-pick__empty{margin:0;font-size:14px;color:var(--ink-muted)}
.scr-PayMemo .pm-chk{flex:none;width:44px;height:44px;margin-left:-8px;border:0;background:none;padding:0;display:flex;align-items:center;justify-content:center;cursor:pointer;border-radius:999px}
.scr-PayMemo .pm-chk:hover{background:var(--navy-soft)}
.scr-PayMemo .pm-chk .check{width:22px;height:22px}
.scr-PayMemo .pm-head{display:flex;align-items:flex-end;justify-content:space-between;gap:8px}
</style>
