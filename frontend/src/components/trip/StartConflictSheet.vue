<script setup>
// 여행을 시작하려는데 진행 중인 다른 여행이 있을 때: 보관하고 시작 / 삭제하고 시작 / 취소
// 보관·삭제가 끝나면 done을 알려요. 시작은 부르는 쪽(useStartTrip)이 해요.
import { ref } from 'vue'
import BottomSheet from '@/components/common/BottomSheet.vue'
import { archiveTrip, deleteTrip } from '@/api/trips'
import { showTrashToast } from '@/utils/trash'
import { toShortDate } from '@/utils/tripView'
import { showToast } from '@/utils/toast'

const props = defineProps({ trip: { type: Object, required: true } })
const emit = defineEmits(['done', 'cancel'])

const isAskingDelete = ref(false) // 삭제는 한 번 더 물어봐요
const isWorking = ref(false)

async function run(work) {
  isWorking.value = true
  try {
    await work()
    emit('done')
  } catch (e) {
    showToast(e.message)
  }
  isWorking.value = false
}

function archive() {
  return run(() => archiveTrip(props.trip.id))
}

function remove() {
  // 되돌리기는 지금 시작한 여행과 겹치면 서버가 막고 그 이유를 토스트로 알려요
  return run(async () => {
    await deleteTrip(props.trip.id)
    showTrashToast(props.trip.id)
  })
}
</script>

<template>
  <div class="dim" style="z-index: 40"></div>
  <BottomSheet closable data-drag :on-close="() => emit('cancel')" class="sc-sheet" role="alertdialog" aria-modal="true" aria-labelledby="sc-title">
    <template v-if="!isAskingDelete">
      <h2 id="sc-title" class="h-title">진행 중인 여행이 있어요</h2>
      <p class="sc-trip tn"><b>{{ trip.title }}</b> · {{ toShortDate(trip.startDate) }} · {{ trip.stopCount }}곳</p>
      <button type="button" class="btn btn--primary btn--lg btn--block" :disabled="isWorking" @click="archive">보관하고 시작</button>
      <button type="button" class="btn btn--danger btn--lg btn--block" :disabled="isWorking" @click="isAskingDelete = true">삭제하고 시작</button>
      <button type="button" class="btn btn--ghost btn--lg btn--block" :disabled="isWorking" @click="emit('cancel')">취소</button>
    </template>
    <template v-else>
      <h2 id="sc-title" class="h-title">삭제할까요?</h2>
      <p class="sc-trip tn"><b>{{ trip.title }}</b> · 휴지통으로 옮겨요</p>
      <div class="cta__row">
        <button type="button" class="btn btn--secondary btn--lg" style="flex: 1" :disabled="isWorking" @click="isAskingDelete = false">취소</button>
        <button type="button" class="btn btn--danger btn--lg" style="flex: 1" :disabled="isWorking" @click="remove">삭제하고 시작</button>
      </div>
    </template>
  </BottomSheet>
</template>

<style>
.sc-sheet { z-index: 41; gap: 8px; padding: 8px 16px 24px; }
.sc-trip { margin: 0 0 8px; font-size: 14px; line-height: 20px; color: var(--ink-muted); overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.sc-trip b { font-weight: 600; color: var(--ink); }
</style>
