<script setup>
// 달력에서 하루(당일치기) 또는 기간(1박 이상)을 골라요. 오늘 전 날짜는 못 골라요
// 날짜는 '2026-11-14' 글자로 주고받아요 (글자끼리 크기 비교가 그대로 돼요)
// 예) <RangeCalendar v-model:start="startDate" v-model:end="endDate" />  (당일치기면 end는 null)
import { computed, ref } from 'vue'
import { todayText, toDateText } from '@/utils/dates'

const MONTHS_AHEAD = 12 // 오늘부터 몇 달 뒤까지 넘겨 볼 수 있는지

const props = defineProps({
  start: { type: String, default: null },
  end: { type: String, default: null },
})
const emit = defineEmits(['update:start', 'update:end'])

const today = todayText()
const firstMonth = new Date(today + 'T00:00:00')
firstMonth.setDate(1)
// 보고 있는 달 (고른 시작일이 있으면 그 달부터)
const shown = ref(props.start ? new Date(props.start.slice(0, 7) + '-01T00:00:00') : new Date(firstMonth))
const monthIndex = computed(() => (shown.value.getFullYear() - firstMonth.getFullYear()) * 12 + shown.value.getMonth() - firstMonth.getMonth())

// 달력 칸: 앞쪽 빈칸(null) + 1일~말일('2026-11-01' …)
const cells = computed(() => {
  const year = shown.value.getFullYear()
  const month = shown.value.getMonth()
  const blank = new Date(year, month, 1).getDay()
  const days = new Date(year, month + 1, 0).getDate()
  const list = []
  for (let i = 0; i < blank; i++) list.push(null)
  for (let d = 1; d <= days; d++) list.push(toDateText(new Date(year, month, d)))
  return list
})

function moveMonth(diff) {
  const next = new Date(shown.value)
  next.setMonth(next.getMonth() + diff)
  shown.value = next
}

// 처음 누르면 시작일, 그 뒤 날짜를 누르면 끝날, 다시 누르면 처음부터
function pick(day) {
  if (!props.start || props.end || day <= props.start) {
    emit('update:start', day)
    emit('update:end', null)
  } else {
    emit('update:end', day)
  }
}

function dayClass(day) {
  const { start, end } = props
  return {
    sel: day === start || day === end,
    mid: start && end && day > start && day < end,
    rs: start && end && day === start,
    re: start && end && day === end,
    today: day === today,
  }
}
</script>

<template>
  <div class="range-cal">
    <div class="range-cal__head">
      <button type="button" class="iconbtn" aria-label="이전 달" :disabled="monthIndex <= 0" @click="moveMonth(-1)"><i class="ic i-back" aria-hidden="true"></i></button>
      <b class="t-strong tn">{{ shown.getFullYear() }}년 {{ shown.getMonth() + 1 }}월</b>
      <button type="button" class="iconbtn" aria-label="다음 달" :disabled="monthIndex >= MONTHS_AHEAD" @click="moveMonth(1)"><i class="ic i-next" aria-hidden="true"></i></button>
    </div>
    <div class="cal" role="grid" :aria-label="`${shown.getFullYear()}년 ${shown.getMonth() + 1}월 달력`">
      <span v-for="w in ['일', '월', '화', '수', '목', '금', '토']" :key="w" class="dow">{{ w }}</span>
      <template v-for="(day, i) in cells" :key="i">
        <span v-if="day === null"></span>
        <span v-else-if="day < today" class="off" aria-disabled="true">{{ Number(day.slice(8)) }}</span>
        <button v-else type="button" class="day" :class="dayClass(day)" :aria-pressed="day === start || day === end" :aria-label="day" @click="pick(day)">{{ Number(day.slice(8)) }}</button>
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
.range-cal .cal button.today{font-weight:700;color:var(--navy)}
.range-cal .cal button.mid{background:var(--navy-soft)}
.range-cal .cal button.rs{background:linear-gradient(90deg,transparent 50%,var(--navy-soft) 50%)}
.range-cal .cal button.re{background:linear-gradient(90deg,var(--navy-soft) 50%,transparent 50%)}
.range-cal .cal button.sel{color:#fff;font-weight:700}
.range-cal .cal button.sel::before{background:var(--navy)}
</style>
