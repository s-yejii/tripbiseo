<script setup>
// 휴지통 (마이): 지운 여행을 복원하거나 영구 삭제해요. 계정당 20개까지, 넘치면 서버가 오래 전에 지운 것부터 지워요
// 서버: GET /api/trips/trash, POST /api/trips/{id}/restore, DELETE /api/trips/{id}/trash, DELETE /api/trips/trash
import { computed, onMounted, ref } from 'vue'
import TopBar from '@/components/common/TopBar.vue'
import PageMessage from '@/components/common/PageMessage.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import { deleteTrashTrip, emptyTrash, getTrashTrips, restoreTrip } from '@/api/trips'
import { toShortDate } from '@/utils/tripView'
import { showToast } from '@/utils/toast'
import { withoutPractice } from '@/utils/practice'

const trips = ref(null) // null: 아직 못 읽음
const error = ref('')
const isBusy = ref(false)
const purging = ref(null) // 영구 삭제 확인 중인 여행
const isEmptying = ref(false) // 전체 비우기 확인 중

const confirmTitle = computed(() => (isEmptying.value ? '여행 기록을 비울까요?' : '영구 삭제할까요?'))
const confirmText = computed(() => (isEmptying.value ? trips.value.length + '개 · 되돌릴 수 없어요' : purging.value.title + ' · 되돌릴 수 없어요'))

onMounted(load)

async function load() {
  error.value = ''
  try {
    trips.value = withoutPractice(await getTrashTrips()) // 시연 연습 복사본은 숨김
  } catch (e) {
    error.value = e.message
  }
}

// 버튼 동작 공통: 서버에 보내고 목록을 다시 읽어요. 실패하면 서버 문장을 토스트로
async function run(action, doneText) {
  isBusy.value = true
  try {
    await action()
    showToast(doneText)
    await load()
  } catch (e) {
    showToast(e.message)
  }
  isBusy.value = false
}
function restore(trip) {
  run(() => restoreTrip(trip.id), trip.title + ' 복원했어요')
}
async function confirmOk() {
  const target = purging.value
  const isAll = isEmptying.value
  purging.value = null
  isEmptying.value = false
  if (isAll) await run(emptyTrash, '여행 기록을 비웠어요')
  else await run(() => deleteTrashTrip(target.id), '영구 삭제했어요')
}
function confirmCancel() {
  purging.value = null
  isEmptying.value = false
}
function dateOf(trip) {
  return trip.endDate && trip.endDate !== trip.startDate ? toShortDate(trip.startDate) + '–' + toShortDate(trip.endDate) : toShortDate(trip.startDate)
}
</script>

<template>
  <div class="scr scr-Trash">
    <div class="app">
      <TopBar title="여행 기록" back="/my" />
      <div class="scroll" style="padding-top: 8px">
        <PageMessage v-if="error" icon="i-warning" title="여행 기록을 불러오지 못했어요" :text="error" button="다시 시도" @retry="load" />
        <PageMessage v-else-if="!trips" is-loading title="불러오는 중이에요" />
        <PageMessage v-else-if="!trips.length" icon="i-trash" title="여행 기록이 비어 있어요" />
        <template v-else>
          <div class="sec-h">
            <span class="t-cap tn">{{ trips.length }} / 20개</span>
            <button type="button" class="textlink" :disabled="isBusy" @click="isEmptying = true">전체 비우기</button>
          </div>
          <div class="group">
            <div v-for="trip in trips" :key="trip.id" class="row trash-row">
              <span class="row__body">
                <span class="row__title">{{ trip.title }}</span>
                <span class="row__meta tn">{{ dateOf(trip) }} · {{ toShortDate(trip.deletedAt.slice(0, 10)) }} 지움</span>
              </span>
              <button type="button" class="btn btn--secondary btn--sm" :disabled="isBusy" @click="restore(trip)">복원</button>
              <button type="button" class="iconbtn" :aria-label="trip.title + ' 영구 삭제'" :disabled="isBusy" @click="purging = trip"><i class="ic ic--s i-trash c-danger" aria-hidden="true"></i></button>
            </div>
          </div>
        </template>
      </div>
      <ConfirmModal v-if="purging || isEmptying" :title="confirmTitle" :text="confirmText" ok-text="영구 삭제" ok-icon="i-trash" danger @cancel="confirmCancel" @ok="confirmOk" />
    </div>
  </div>
</template>

<style>
.scr-Trash .trash-row { gap: 4px; }
.scr-Trash .trash-row .row__body { min-width: 0; }
</style>
