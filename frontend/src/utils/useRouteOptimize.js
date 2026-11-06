// 동선 최적화 버튼 (상세·편집 공통): 이동 시간이 짧은 순서 후보를 서버에서 받아, 누를 때마다 다음 후보로
// 시각·머무는 시간은 자리에 그대로 두고 장소만 바꿔요 (식사 때·고정 칸을 지키려고). 후보는 눌렀을 때의 순서 기준이라,
// 사용자가 끌어서 순서를 바꾸면 reset()으로 다시 받아요
// stopsOf: 지금 칸 목록 · dateOf: 그날 날짜 · transportsOf: 이동 수단 · apply(새 칸 목록): 화면에 적용 (async 가능)
import { ref } from 'vue'
import { getRouteOptions } from '@/api/trips'
import { showToast } from '@/utils/toast'

function isSameOrder(order) {
  return order.every((value, i) => value === i)
}

// 후보 순서대로 칸 복사본 만들기: 자리 i의 시각·머무는 시간 + order[i]번 장소
function toOrderedStops(base, order) {
  return order.map((index, place) => ({ ...base[index], time: base[place].time, stay: base[place].stay }))
}

export function useRouteOptimize({ stopsOf, dateOf, transportsOf, apply }) {
  const isBusy = ref(false)
  let base = null // 후보를 받을 때의 칸 목록
  let options = []
  let index = -1

  function reset() {
    base = null
  }

  async function next() {
    if (isBusy.value) return
    isBusy.value = true
    try {
      if (!base) {
        base = JSON.parse(JSON.stringify(stopsOf())) // 깊은 복사 (칸 객체까지 새로)
        const result = await getRouteOptions(base, dateOf(), transportsOf())
        options = result.options.filter((option) => !isSameOrder(option.order)) // 지금 순서는 빼고
        index = -1
      }
      if (!options.length) {
        showToast('지금이 가장 짧은 순서예요')
        return
      }
      index = (index + 1) % options.length
      await apply(toOrderedStops(base, options[index].order))
    } catch (e) {
      base = null
      showToast(e.message || '동선을 바꾸지 못했어요')
    } finally {
      isBusy.value = false
    }
  }

  return { next, reset, isBusy }
}
