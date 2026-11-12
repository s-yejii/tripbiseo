<script setup>
// S21-g 이동 수단 (①, 11/06): 평소 타는 이동 수단을 골라 저장해요. 여러 개 고를 수 있고, 처음 고른 게 "주로"
// 아직 서버 칸이 없어서 이 브라우저에 저장해요 (localStorage). AI 일정의 "무엇으로 다녀요?" 기본값으로 쓸 수 있어요
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'
import { showToast } from '@/utils/toast'
import { DEFAULT_TRANSPORTS, readTransports, saveTransports } from '@/utils/transports'

const router = useRouter()
const options = [
  { name: '지하철', icon: 'i-subway', desc: '정해진 시간에 가요' },
  { name: '버스', icon: 'i-bus', desc: '갈아타기 적게' },
  { name: '도보', icon: 'i-walk', desc: '가까운 곳은 걸어서' },
  { name: '택시', icon: 'i-car', desc: '늦을 때 빠르게' },
  { name: '자동차', icon: 'i-car', desc: '직접 운전' },
]
const picked = ref(readTransports())

// 누르면 넣고, 다시 누르면 빼요. 하나는 남겨야 해요
function toggle(name) {
  const isOn = picked.value.includes(name)
  if (isOn && picked.value.length === 1) return showToast('이동 수단을 하나는 골라 주세요')
  picked.value = isOn ? picked.value.filter((item) => item !== name) : [...picked.value, name]
}

function save() {
  saveTransports(picked.value)
  showToast('이동 수단을 저장했어요')
  router.push('/my')
}
function reset() {
  picked.value = [...DEFAULT_TRANSPORTS]
}
</script>

<template>
  <div class="scr scr-Transport">
    <div class="app app--white">
      <TopBar title="이동 수단" back="/my" />

      <div class="scroll" style="padding-top: 8px; gap: 16px; bottom: 102px">
        <p class="t-cap" style="margin: 0">평소에 타는 걸 골라 주세요. 이동 시간 계산과 길 안내, AI 일정에 써요.</p>
        <div class="group" role="group" aria-label="이동 수단">
          <button v-for="option in options" :key="option.name" type="button" class="row tr-row" :aria-pressed="picked.includes(option.name)" @click="toggle(option.name)">
            <span class="ico-tile" :class="picked.includes(option.name) ? 'ico-tile--sky' : ''"><i class="ic" :class="option.icon" aria-hidden="true"></i></span>
            <span class="row__body"><span class="row__title">{{ option.name }}<span v-if="picked[0] === option.name" class="badge badge--navy tr-main">주로</span></span><span class="row__meta">{{ option.desc }}</span></span>
            <i class="ic" :class="picked.includes(option.name) ? 'i-check c-navy' : 'i-plus subtle'" aria-hidden="true"></i>
          </button>
        </div>
        <button type="button" class="textlink" style="align-self: flex-start" @click="reset">기본값으로 (지하철·도보·버스)</button>
      </div>

      <div class="cta">
        <button type="button" class="btn btn--primary btn--lg btn--block" @click="save">저장</button>
      </div>
    </div>
  </div>
</template>

<style>
.scr-Transport .tr-row { width: 100%; border: 0; background: none; font: inherit; text-align: left; cursor: pointer; }
.scr-Transport .tr-main { margin-left: 8px; vertical-align: 1px; }
</style>
