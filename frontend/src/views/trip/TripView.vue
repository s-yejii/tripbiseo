<script setup>
// 저장된 여행 (화면 7): 지도 + 날짜별 일정. 장소 누르면 상세(화면 8), 수정·삭제·추가, 여행 시작
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { trips } from '@/api'
import { dayCount, shortDate, today, transportText } from '@/utils/format'
import { toStop } from '@/utils/stops'
import AppBar from '@/components/AppBar.vue'
import BottomSheet from '@/components/BottomSheet.vue'
import ChatLog from '@/components/ChatLog.vue'
import ConditionsCard from '@/components/ConditionsCard.vue'
import Itinerary from '@/components/Itinerary.vue'
import MapView from '@/components/map/MapView.vue'
import Modal from '@/components/Modal.vue'
import PlaceSearch from '@/components/PlaceSearch.vue'
import StopEditor from '@/components/StopEditor.vue'

const route = useRoute()
const router = useRouter()
const id = route.params.id
const trip = ref(null)
const day = ref(1)
const sheet = ref('half')
const editing = ref(null)
const adding = ref(false)
const infoOpen = ref(false)
const logOpen = ref(false)
const info = ref(null)
const error = ref('')

const days = computed(() => (trip.value ? dayCount(trip.value.startDate, trip.value.endDate) : 1))
const dayStops = computed(() => trip.value?.stops.filter((s) => s.day === day.value) ?? [])
const markers = computed(() => dayStops.value.map((s, i) => ({ id: s.id, lat: s.lat, lng: s.lng, num: s.fixed ? null : i + 1, kind: s.fixed ? 'fixed' : 'pin', color: s.fixed ? 'red' : 'blue', label: s.name, sub: s.time })))
const lines = computed(() => (dayStops.value.length > 1 ? [{ coords: dayStops.value.map((s) => [s.lat, s.lng]), color: 'blue', width: 4, dashed: true }] : []))
const canStart = computed(() => trip.value && today() >= trip.value.startDate && today() <= trip.value.endDate)

async function load() {
  try {
    trip.value = await trips.get(id)
    if (trip.value.status === 'ongoing') router.replace(`/trips/${id}/live`)
  } catch (e) {
    error.value = e.message
  }
}
onMounted(load)

async function run(task) {
  error.value = ''
  try {
    await task()
    await load()
  } catch (e) {
    error.value = e.message
  }
}
const saveStop = (f) => run(async () => (await trips.updateStop(id, editing.value.id, { ...editing.value, ...f }), (editing.value = null)))
const removeStop = () => run(async () => (await trips.removeStop(id, editing.value.id), (editing.value = null)))
const addStop = (p) => run(async () => (await trips.addStop(id, toStop(p, day.value, dayStops.value.at(-1))), (adding.value = false)))
function openInfo() {
  info.value = { ...trip.value }
  infoOpen.value = true
}
const saveInfo = () => run(async () => (await trips.update(id, info.value), (infoOpen.value = false)))
async function start() {
  await run(() => trips.start(id))
  if (!error.value) router.replace(`/trips/${id}/live`)
}
</script>

<template>
  <AppBar :title="trip?.title || '여행'"><button class="icon-btn" aria-label="여행 정보 수정" @click="openInfo">⚙</button></AppBar>
  <div v-if="trip" class="trip">
    <div class="map"><MapView :markers="markers" :lines="lines" :fit-key="day" :pad="[60, 130, 400, 30]" @marker-click="(sid) => router.push(`/places/${trip.stops.find((s) => s.id === sid)?.placeId}`)" /></div>
    <div class="days">
      <button v-for="n in days" :key="n" :class="{ on: day === n }" @click="day = n">Day {{ n }}</button>
    </div>
    <BottomSheet v-model:state="sheet">
      <div class="head">
        <div>
          <h2>{{ trip.title }}</h2>
          <p class="muted small">{{ shortDate(trip.startDate) }} ~ {{ shortDate(trip.endDate) }} · {{ trip.people }}명 · {{ transportText(trip.transports) }}</p>
        </div>
      </div>
      <div class="actions">
        <button class="btn line sm" @click="router.push(`/plan/check?trip=${id}`)">일정 검증</button>
        <button class="btn line sm" @click="logOpen = true">대화 기록</button>
        <button class="btn primary sm grow" :disabled="!canStart" @click="start">{{ canStart ? '여행 시작' : `${shortDate(trip.startDate)}에 시작할 수 있어요` }}</button>
      </div>
      <p v-if="error" class="error">{{ error }}</p>
      <Itinerary :stops="trip.stops" :start-date="trip.startDate" editable
        @select="(s) => s.placeId && router.push(`/places/${s.placeId}`)" @edit="(s) => (editing = s)" />
      <button class="fab" @click="adding = true">＋ 추가</button>
    </BottomSheet>
  </div>
  <p v-else-if="error" class="page error">{{ error }}</p>

  <StopEditor v-if="editing" :stop="editing" :days="days" @save="saveStop" @remove="removeStop" @close="editing = null" />
  <ChatLog v-if="logOpen" :trip-id="id" @close="logOpen = false" />
  <PlaceSearch v-if="adding" :title="`Day ${day}에 장소 추가`" @pick="addStop" @close="adding = false" />
  <Modal v-if="infoOpen" title="여행 정보" @close="infoOpen = false">
    <input v-model="info.title" class="field title" maxlength="40" />
    <ConditionsCard v-model="info" />
    <button class="btn primary block save" @click="saveInfo">저장</button>
  </Modal>
</template>

<style scoped>
.trip { position: relative; flex: 1; min-height: 0; }
.map { position: absolute; inset: 0; }
.days { position: absolute; top: 10px; left: 12px; z-index: 10; display: flex; gap: 6px; }
.days button { padding: 7px 14px; border-radius: 999px; background: #fff; font-weight: 700; font-size: 14px; box-shadow: 0 1px 4px rgba(0, 0, 0, .15); }
.days button.on { background: var(--ink); color: #fff; }
.head h2 { font-size: 22px; font-weight: 800; }
.actions { display: flex; gap: 8px; margin: 12px 0 4px; }
.fab { position: sticky; bottom: 4px; float: right; margin-top: 8px; padding: 12px 18px; border-radius: 999px; background: var(--ink); color: #fff; font-weight: 700; box-shadow: 0 4px 12px rgba(0, 0, 0, .2); }
.title { margin: 14px 0 10px; }
.save { margin-top: 12px; }
</style>
