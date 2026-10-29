<script setup>
// S21-d 결제 카드 등록 (⑤): 결제는 하지 않고, 이 카드로 결제하면 그 장소 핀에 금액이 남게 해요
// 카드 번호 전체는 저장하지 않아요 (별명과 끝 4자리만)
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from '@/utils/toast'

const router = useRouter()

const cardNo = ref('') // 숫자만 16자리
const expiry = ref('') // 숫자만 4자리 (MMYY)
const nickname = ref('')
const agreed = ref(false)
const leftNo = ref(false) // 카드 번호 칸을 벗어났는지 (그때부터 오류를 보여 줘요)

// 1234567812345678 → '1234 5678 1234 5678'
function spaced(digits) {
  return digits.replace(/(\d{4})(?=\d)/g, '$1 ')
}
// 1027 → '10/27'
function slashed(digits) {
  return digits.length > 2 ? digits.slice(0, 2) + '/' + digits.slice(2) : digits
}

const brand = computed(() => {
  const d = cardNo.value
  if (/^4/.test(d)) return '비자'
  if (/^5[1-5]/.test(d)) return '마스터카드'
  if (/^9/.test(d)) return '국내 전용 카드'
  if (/^3[47]/.test(d)) return '아멕스'
  return '카드사 자동 확인'
})
// 카드 그림: 가운데 8자리는 가리고 •로 채워요
const preview = computed(() => {
  const d = cardNo.value
  const filled = (d + '•'.repeat(16)).slice(0, 16)
  return [0, 4, 8, 12].map((i, k) => (k > 0 && k < 3 && d.length > i + 4 ? '••••' : filled.slice(i, i + 4))).join(' ')
})

const noOk = computed(() => cardNo.value.length === 16)
const expOk = computed(() => {
  const month = Number(expiry.value.slice(0, 2))
  const year = 2000 + Number(expiry.value.slice(2))
  return expiry.value.length === 4 && month >= 1 && month <= 12 && (year > 2026 || (year === 2026 && month >= 10))
})
const noError = computed(() => (leftNo.value && cardNo.value && !noOk.value ? '16자리를 모두 넣어 주세요' : ''))
const expError = computed(() => (expiry.value.length === 4 && !expOk.value ? '유효기간이 지났거나 맞지 않아요' : ''))
const canSave = computed(() => noOk.value && expOk.value && agreed.value)
const hint = computed(() => {
  if (canSave.value) return ''
  if (!noOk.value) return '카드 번호 16자리를 넣어 주세요'
  if (!expOk.value) return '유효기간을 넣어 주세요'
  return '필수 동의에 체크해 주세요'
})

function typeNo(e) {
  cardNo.value = e.target.value.replace(/\D/g, '').slice(0, 16)
  e.target.value = spaced(cardNo.value)
}
function typeExpiry(e) {
  expiry.value = e.target.value.replace(/\D/g, '').slice(0, 4)
  e.target.value = slashed(expiry.value)
}
function save() {
  const name = nickname.value || brand.value.replace('카드사 자동 확인', '카드')
  router.push('/my')
  showToast(`${name} · ${cardNo.value.slice(-4)} 카드를 등록했어요`)
}
</script>

<template>
  <div class="scr scr-CardAdd">
    <div class="app app--white">
      <header class="topbar">
        <RouterLink to="/my" class="iconbtn" aria-label="뒤로가기"><i class="ic ic--l i-back" aria-hidden="true"></i></RouterLink>
        <h1 class="topbar__title">결제 카드 등록</h1>
      </header>

      <div class="scroll" style="padding-top: 8px; gap: 18px; bottom: 102px">
        <div class="cc" aria-hidden="true">
          <div class="cc__top"><span>{{ brand }}</span><span class="cc__chip"></span></div>
          <div class="cc__no">{{ preview }}</div>
          <div class="cc__bot"><span>{{ nickname || '여행어때' }}</span><span>{{ expiry ? slashed(expiry) : 'MM/YY' }}</span></div>
        </div>

        <p class="t-cap" style="display: flex; gap: 6px; align-items: flex-start; margin: 0">
          <i class="ic ic--s i-info c-navy" aria-hidden="true" style="margin-top: 2px"></i>결제는 하지 않아요. 이 카드로 결제하면 그 장소 핀에 금액이 남고, 누가 얼마 냈는지 바로 적을 수 있어요.
        </p>

        <div class="field" :class="{ 'is-success': noOk, 'is-error': noError }">
          <label class="field__label" for="ca-no">카드 번호</label>
          <div class="field__box"><input id="ca-no" :value="spaced(cardNo)" type="text" inputmode="numeric" placeholder="0000 0000 0000 0000" autocomplete="off" @input="typeNo" @blur="leftNo = true" /></div>
          <p v-if="noError" class="field__help"><i class="ic ic--s i-danger" aria-hidden="true"></i>{{ noError }}</p>
        </div>

        <div class="ca-row">
          <div class="field" :class="{ 'is-error': expError, 'is-success': expOk }">
            <label class="field__label" for="ca-exp">유효기간</label>
            <div class="field__box"><input id="ca-exp" :value="slashed(expiry)" type="text" inputmode="numeric" placeholder="MM/YY" autocomplete="off" @input="typeExpiry" /></div>
            <p v-if="expError" class="field__help"><i class="ic ic--s i-danger" aria-hidden="true"></i>{{ expError }}</p>
          </div>
          <div class="field">
            <label class="field__label" for="ca-nick">카드 별명 <span class="t-cap" style="font-weight: 400">선택</span></label>
            <div class="field__box"><input id="ca-nick" v-model="nickname" type="text" maxlength="8" placeholder="예: 여행 카드" autocomplete="off" /></div>
          </div>
        </div>

        <button type="button" class="ca-agree" role="checkbox" :aria-checked="agreed" @click="agreed = !agreed">
          <span class="check" :class="{ 'is-on': agreed }"><i class="ic ic--s i-check" aria-hidden="true"></i></span>
          <span style="display: flex; flex-direction: column; gap: 2px">
            <span class="t-strong" style="font-size: 14px">[필수] 결제 내역을 여행 기록에 쓰는 데 동의</span>
            <span class="t-cap">결제 시각·장소·금액만 가져와요</span>
          </span>
        </button>
      </div>

      <div class="cta cta--white">
        <button type="button" class="btn btn--primary btn--lg btn--block" :disabled="!canSave" @click="save">등록하기</button>
        <p v-if="hint" class="cta__hint">{{ hint }}</p>
      </div>
    </div>
  </div>
</template>

<style>
.scr-CardAdd .cc{position:relative;height:188px;border-radius:18px;padding:20px 22px;background:var(--navy);color:#fff;display:flex;flex-direction:column;justify-content:space-between;box-shadow:0 14px 28px -14px rgba(24,33,58,.6);overflow:hidden}
.scr-CardAdd .cc::after{content:"";position:absolute;right:-60px;top:-60px;width:200px;height:200px;border-radius:50%;background:rgba(255,255,255,.07)}
.scr-CardAdd .cc__top{display:flex;justify-content:space-between;align-items:center;font-size:14px;font-weight:600;opacity:.92}
.scr-CardAdd .cc__chip{width:38px;height:28px;border-radius:6px;background:rgba(255,255,255,.28)}
.scr-CardAdd .cc__no{font-size:21px;letter-spacing:2px;font-weight:600;font-variant-numeric:tabular-nums}
.scr-CardAdd .cc__bot{display:flex;justify-content:space-between;font-size:13px;opacity:.85;font-variant-numeric:tabular-nums}
.scr-CardAdd .ca-row{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:12px}
.scr-CardAdd .ca-row>.field{min-width:0}
.scr-CardAdd .ca-row input{min-width:0;width:100%}
.scr-CardAdd .ca-agree{display:flex;align-items:flex-start;gap:10px;padding:12px 14px;border-radius:var(--radius-md);background:var(--surface);border:1px solid var(--line);cursor:pointer;text-align:left;font:inherit;color:var(--ink);width:100%}
.scr-CardAdd .ca-agree:hover{border-color:var(--navy)}
</style>
