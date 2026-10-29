<script setup>
// 달력에서 하루(당일치기) 또는 기간(1박 이상)을 골라요
// 날짜는 "월*100+일" 숫자로 다뤄요 (10월 24일 → 1024)
// 예) <RangeCalendar v-model:start="start" v-model:end="end" />
import { computed, ref } from 'vue'

const props = defineProps({
  start: { type: Number, default: null },
  end: { type: Number, default: null },
  firstDay: { type: Number, default: 1020 }, // 이 날 전은 고를 수 없어요 (오늘)
})
const emit = defineEmits(['update:start', 'update:end'])

const year = 2026
const month = ref(10)

// 달력 칸: 앞쪽 빈칸 + 1일~말일
const cells = computed(() => {
  const blank = new Date(year, month.value - 1, 1).getDay()
  const days = new Date(year, month.value, 0).getDate()
  const list = []
  for (let i = 0; i < blank; i++) list.push(null)
  for (let d = 1; d <= days; d++) list.push(month.value * 100 + d)
  return list
})

// 처음 누르면 시작일, 그 뒤 날짜를 누르면 끝날, 다시 누르면 처음부터
function pick(key) {
  if (!props.start || props.end || key <= props.start) {
    emit('update:start', key)
    emit('update:end', null)
  } else {
    emit('update:end', key)
  }
}

function dayClass(key) {
  const { start, end } = props
  return {
    sel: key === start || key === end,
    mid: start && end && key > start && key < end,
    rs: start && end && key === start,
    re: start && end && key === end,
  }
}
</script>

<template>
  <div class="range-cal">
    <div class="range-cal__head">
      <button type="button" class="iconbtn" aria-label="이전 달" :disabled="month <= 10" @click="month--"><i class="ic i-back" aria-hidden="true"></i></button>
      <b class="t-strong tn">{{ year }}년 {{ month }}월</b>
      <button type="button" class="iconbtn" aria-label="다음 달" :disabled="month >= 11" @click="month++"><i class="ic i-next" aria-hidden="true"></i></button>
    </div>
    <div class="cal" role="grid" :aria-label="`${year}년 ${month}월 달력`">
      <span v-for="w in ['일', '월', '화', '수', '목', '금', '토']" :key="w" class="dow">{{ w }}</span>
      <template v-for="(key, i) in cells" :key="i">
        <span v-if="key === null"></span>
        <span v-else-if="key < firstDay" class="off" aria-disabled="true">{{ key % 100 }}</span>
        <button v-else type="button" class="day" :class="dayClass(key)" :aria-pressed="key === start || key === end" @click="pick(key)">{{ key % 100 }}</button>
      </template>
    </div>
  </div>
</template>

<style>
.range-cal { display: flex; flex-direction: column; gap: 10px; }
.range-cal__head { display: flex; align-items: center; justify-content: space-between; }
.range-cal .cal{display:grid;grid-template-columns:repeat(7,minmax(0,1fr));row-gap:2px;text-align:center;font-size:14px;font-variant-numeric:tabular-nums}
.range-cal .cal>span,.range-cal .cal>button{height:40px;display:flex;align-items:center;justify-content:center}
.range-cal .cal .dow{height:24px;font-size:12px;color:var(--ink-muted)}
.range-cal .cal .off{color:var(--ink-disabled)}
.range-cal .cal button{border:0;background:none;font:inherit;color:var(--ink);cursor:pointer;position:relative;z-index:0}
.range-cal .cal button::before{content:"";position:absolute;z-index:-1;width:38px;height:38px;border-radius:50%;left:50%;top:50%;transform:translate(-50%,-50%);transition:background .12s}
.range-cal .cal button:hover::before{background:var(--navy-soft)}
.range-cal .cal button.mid{background:var(--navy-soft)}
.range-cal .cal button.rs{background:linear-gradient(90deg,transparent 50%,var(--navy-soft) 50%)}
.range-cal .cal button.re{background:linear-gradient(90deg,var(--navy-soft) 50%,transparent 50%)}
.range-cal .cal button.sel{color:#fff;font-weight:700}
.range-cal .cal button.sel::before{background:var(--navy)}
</style>
