<script setup>
// 보관함 (②): 보관한 여행을 다시 꺼내거나(시작) 삭제해요
// 서버: GET /api/trips (archived만), POST /api/trips/{id}/start, DELETE /api/trips/{id}
import { onMounted, ref } from 'vue'
import TopBar from '@/components/common/TopBar.vue'
import PageMessage from '@/components/common/PageMessage.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import StartConflictSheet from '@/components/trip/StartConflictSheet.vue'
import { deleteTrip, getArchivedTrips } from '@/api/trips'
import { useStartTrip } from '@/utils/useStartTrip'
import { toShortDate } from '@/utils/tripView'
import { showToast } from '@/utils/toast'
import { showTrashToast } from '@/utils/trash'

const trips = ref(null) // null: 아직 못 읽음
const error = ref('')
const deleting = ref(null) // 삭제 확인 중인 여행
const menuId = ref(null) // ··· 메뉴를 연 여행
const { conflict, start, retry } = useStartTrip()

onMounted(load)

async function load() {
  error.value = ''
  try {
    trips.value = await getArchivedTrips()
  } catch (e) {
    error.value = e.message
  }
}

function takeOut(trip) {
  menuId.value = null
  start(trip.id)
}

function askDelete(trip) {
  menuId.value = null
  deleting.value = trip
}

async function remove() {
  try {
    const removedId = deleting.value.id
    await deleteTrip(removedId)
    showTrashToast(removedId, load)
    await load()
  } catch (e) {
    showToast(e.message)
  }
  deleting.value = null
}
</script>

<template>
  <div class="scr scr-TripArchive">
    <div class="app">
      <TopBar title="보관함" back="/trips" />
      <div class="scroll">
        <PageMessage v-if="error" icon="i-warning" title="보관함을 불러오지 못했어요" :text="error" button="다시 시도" @retry="load" />
        <PageMessage v-else-if="!trips" is-loading title="보관함을 불러오는 중이에요" />
        <PageMessage v-else-if="!trips.length" title="보관한 여행이 없어요" to="/trips" button="내 여행 보기" />
        <div v-else class="group ar-group">
          <div v-for="trip in trips" :key="trip.id" class="row ar-row">
            <span class="row__body"><span class="row__title">{{ trip.title }}</span><span class="row__meta">{{ toShortDate(trip.startDate) }} · {{ trip.stopCount }}곳</span></span>
            <button type="button" class="ar-more" aria-label="더 보기" :aria-expanded="menuId === trip.id" @click="menuId = menuId === trip.id ? null : trip.id"><i></i><i></i><i></i></button>
            <div v-if="menuId === trip.id" class="ar-menu" role="menu">
              <button type="button" role="menuitem" @click="takeOut(trip)"><i class="ic ic--s i-play" aria-hidden="true"></i>꺼내기</button>
              <button type="button" role="menuitem" class="c-danger" @click="askDelete(trip)"><i class="ic ic--s i-trash" aria-hidden="true"></i>삭제</button>
            </div>
          </div>
        </div>
      </div>
      <div v-if="menuId !== null" class="ar-dim" @click="menuId = null"></div>
      <StartConflictSheet v-if="conflict" :trip="conflict" @done="retry" @cancel="conflict = null" />
      <ConfirmModal v-if="deleting" title="삭제할까요?" :text="deleting.title + ' · 휴지통으로 옮겨요'" ok-text="삭제" ok-icon="i-trash" danger @cancel="deleting = null" @ok="remove" />
    </div>
  </div>
</template>

<style>
.scr-TripArchive .ar-group { overflow: visible; }
.scr-TripArchive .ar-more { flex: none; width: 44px; height: 44px; margin-right: -12px; display: flex; align-items: center; justify-content: center; gap: 3px; border: 0; background: none; cursor: pointer; }
.scr-TripArchive .ar-more i { width: 4px; height: 4px; border-radius: 50%; background: var(--ink-muted); }
.scr-TripArchive .ar-menu { position: absolute; top: 48px; right: 0; z-index: 12; display: flex; flex-direction: column; min-width: 130px; padding: 6px; border-radius: var(--radius-lg); background: #fff; box-shadow: 0 8px 24px rgba(22, 36, 63, .14); }
.scr-TripArchive .ar-menu button { display: flex; align-items: center; gap: 8px; min-height: 44px; padding: 0 10px; border: 0; border-radius: var(--radius-sm); background: none; font: inherit; font-size: 14px; font-weight: 600; color: var(--ink); cursor: pointer; }
.scr-TripArchive .ar-menu button.c-danger { color: var(--danger); }
.scr-TripArchive .ar-menu button:hover { background: var(--surface-sunken); }
.scr-TripArchive .ar-dim { position: absolute; inset: 0; z-index: 4; }
.scr-TripArchive .scroll { z-index: 5; }
</style>
