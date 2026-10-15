<script setup>
// 대안 비교 (목업 4): 서버(규칙)·제미나이가 만든 A·B·C를 지도에서 비교하고 하나를 적용
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { live } from '@/api'
import { useLive } from '@/stores/live'
import { duration } from '@/utils/format'
import AppBar from '@/components/AppBar.vue'
import MapView from '@/components/map/MapView.vue'

const route = useRoute()
const router = useRouter()
const store = useLive()
const id = route.params.id
const data = store.alternatives
if (!data) router.replace(`/trips/${id}/live`)
const pick = ref(data?.alternatives.find((a) => a.recommended)?.key ?? data?.alternatives[0]?.key)
const busy = ref(false)
const error = ref('')
const alt = computed(() => data?.alternatives.find((a) => a.key === pick.value))

const lines = computed(() => [
  ...data.alternatives.filter((a) => a.key !== pick.value).map((a) => ({ coords: a.coords, color: 'gray', dashed: true, width: 4 })),
  { coords: alt.value.coords, color: 'blue', width: 5 },
])
const markers = computed(() => [
  ...alt.value.stops.filter((s) => s.lat).map((s, i) => ({ id: i, lat: s.lat, lng: s.lng, num: s.fixed ? null : i + 1, kind: s.fixed ? 'fixed' : 'pin', color: s.fixed ? 'red' : s.change ? 'purple' : 'blue', label: s.name, sub: s.change || s.eta, subColor: s.change ? '' : 'gray' })),
  ...(store.pos ? [{ id: 'me', ...store.pos, kind: 'me' }] : []),
])

async function apply() {
  busy.value = true
  try {
    store.applied = await live.apply(id, alt.value, store.pos?.lat, store.pos?.lng)
    router.replace(`/trips/${id}/applied`)
  } catch (e) {
    error.value = e.message
    busy.value = false
  }
}
</script>

<template>
  <AppBar title="대안 비교" />
  <div v-if="data" class="page alts">
    <h2>{{ data.title }}</h2>
    <p class="muted small">{{ data.subtitle }}</p>
    <p v-if="data.weather" class="weather small">🌦 {{ data.weather }}</p>

    <div class="tabs">
      <button v-for="a in data.alternatives" :key="a.key" :class="{ on: pick === a.key }" @click="pick = a.key">{{ a.key }} {{ a.short }}</button>
    </div>
    <div class="map"><MapView :markers="markers" :lines="lines" :fit-key="pick" /></div>

    <section class="main card">
      <div class="row">
        <span class="badge">{{ alt.key }}안</span><b class="grow">{{ alt.title }}</b>
        <span v-if="alt.recommended" class="chip orange">추천</span>
      </div>
      <p v-if="alt.reason" class="small muted reason">{{ alt.reason }}</p>
      <p v-if="alt.closedCount" class="small warn">⚠ 도착하면 문을 닫은 곳이 {{ alt.closedCount }}곳 있어요</p>
      <div class="flow">
        <template v-for="(s, i) in alt.stops" :key="i">
          <span v-if="i > 0" class="move">→ {{ s.moveMin }}분</span>
          <span class="node" :class="{ ch: s.change, shut: s.closedReason, soon: s.closingSoon }"><b>{{ s.name }}</b><small>{{ s.closedReason ? s.closedReason : s.fixed ? (alt.slackMin >= 0 ? `${alt.slackMin}분 여유` : `${-alt.slackMin}분 지각`) : s.change || duration(s.stay) }}</small><small v-if="!s.closedReason && s.closingSoon" class="soon">{{ s.closeAt }} 마감</small></span>
        </template>
      </div>
      <a v-for="s in alt.stops.filter((x) => x.naverUrl)" :key="s.name" class="naver small" :href="s.naverUrl" target="_blank" rel="noopener">{{ s.name }} — 네이버 지도에서 확인 ↗</a>
    </section>

    <button v-for="a in data.alternatives.filter((x) => x.key !== pick)" :key="a.key" class="other" @click="pick = a.key">
      <b>{{ a.key }}</b><span class="grow ellipsis">{{ a.title }}</span>
      <span class="small">{{ a.finalArrive }} 도착 · <em :class="a.slackMin >= 0 && !a.closedCount ? 'ok' : 'late'">{{ a.closedCount ? `영업 끝 ${a.closedCount}곳` : a.slackMin >= 0 ? `${duration(a.slackMin)} 여유` : `${-a.slackMin}분 지각` }}</em></span>
    </button>

    <p v-if="error" class="error">{{ error }}</p>
    <button class="btn primary block apply" :disabled="busy" @click="apply">{{ alt.key }}안으로 적용하기 →</button>
  </div>
</template>

<style scoped>
.alts { display: flex; flex-direction: column; gap: 10px; }
h2 { font-size: 21px; font-weight: 800; }
.weather { padding: 8px 12px; border-radius: 10px; background: var(--blue-soft); }
.tabs { display: flex; gap: 6px; }
.tabs button { flex: 1; padding: 9px 4px; border-radius: 10px; border: 1.5px solid var(--line); font-weight: 700; font-size: 13px; }
.tabs button.on { border-color: var(--blue); color: var(--blue); background: var(--blue-soft); }
.map { height: 260px; flex: none; border-radius: 16px; overflow: hidden; }
.main { border: 2px solid var(--blue); }
.badge { padding: 3px 8px; border-radius: 8px; background: var(--blue); color: #fff; font-size: 12px; font-weight: 800; }
.reason { margin-top: 6px; }
.flow { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin-top: 12px; }
.node { text-align: center; }
.node b { display: block; font-size: 13px; }
.node small { font-size: 12px; color: var(--sub); }
.node.ch small { color: var(--orange); font-weight: 700; }
.node.shut b { text-decoration: line-through; color: var(--sub); }
.node.shut small { color: var(--red); font-weight: 700; }
.node small.soon { display: block; color: #b45309; }
.warn { color: var(--red); font-weight: 700; margin-top: 4px; }
.move { font-size: 12px; color: var(--sub); }
.naver { display: block; margin-top: 8px; color: #03c75a; }
.other { display: flex; align-items: center; gap: 10px; width: 100%; padding: 14px; border-radius: 14px; background: var(--bg); text-align: left; }
.other em { font-style: normal; font-weight: 700; }
.ok { color: var(--green); } .late { color: var(--red); }
.apply { margin-top: 4px; }
</style>
