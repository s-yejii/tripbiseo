<script setup>
// S16-c 장소 바꾸기 (④): 남은 장소 하나(?id=, 없으면 서버 추천 C가 바꾼 곳)를 근처 다른 곳으로 바꿔요
// 후보는 서버(GET /live/swap-candidates): 1.5km 안 · 도착할 때 영업 중 · 평점 있음 · 가까운 순, 바꿨을 때 여유까지 계산돼 와요.
// 고르면 바꾼 일정을 계산(POST /live/preview)해서 그대로 적용(POST /live/apply)해요.
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'
import PageMessage from '@/components/common/PageMessage.vue'
import KakaoMap from '@/components/common/KakaoMap.vue'
import AltTabs from '@/components/live/AltTabs.vue'
import LateBadge from '@/components/live/LateBadge.vue'
import { getSwapCandidates } from '@/api/trips'
import { useReplanStore } from '@/stores/replan'
import { imageFor } from '@/utils/mapView'
import { toDuration } from '@/utils/time'
import { showToast } from '@/utils/toast'

const SWAP_MAP_PADDING = { top: 24, right: 24, bottom: 24, left: 24 }

const route = useRoute()
const router = useRouter()
const replan = useReplanStore()
const candidates = ref(null)
const pickedId = ref(null)
const openId = ref(null) // 정보를 펼친 줄
const fromId = ref(null) // 바꿀 칸 id
const isApplying = ref(false)
const error = ref('')
const isMapOk = ref(true) // 카카오 지도를 못 쓰면 지도 칸을 숨김

onMounted(async () => {
  await replan.load()
  if (!replan.base) return
  fromId.value = route.query.id ? Number(route.query.id) : recommendedFromId()
  if (!fromId.value) return
  try {
    const { lat, lng } = replan.position
    candidates.value = await getSwapCandidates(replan.tripId, fromId.value, lat, lng)
    if (candidates.value.length) pickedId.value = candidates.value[0].id
  } catch (e) {
    error.value = e.message
  }
})

// 서버 추천(C 장소 변경)이 바꾼 곳, 없으면 바꿀 수 있는 첫 칸
function recommendedFromId() {
  const option = replan.findOption('장소 변경')
  const swappable = replan.stops.filter((stop) => !stop.fixed && stop.importance !== 'must')
  if (option) {
    const keptIds = option.stops.map((stop) => stop.id)
    const replaced = swappable.find((stop) => !keptIds.includes(stop.id))
    if (replaced) return replaced.id
  }
  if (swappable.length) return swappable[0].id
  const notFixed = replan.stops.find((stop) => !stop.fixed) // 모두 꼭 갈 곳이면 그중 첫 칸
  return notFixed ? notFixed.id : null
}

const fromStop = computed(() => replan.stops.find((stop) => stop.id === fromId.value) || null)
const picked = computed(() => (candidates.value || []).find((place) => place.id === pickedId.value) || null)
const basisName = computed(() => (replan.basisStop ? replan.basisStop.name : '마지막 일정'))
const whyText = computed(() => {
  if (replan.lateMin) return `${replan.lateMin}분 늦어요`
  return '안 늦어요'
})

// 지도 핀: 후보 번호 (고른 곳 강조)
const mapPins = computed(() => (candidates.value || []).map((place, i) => ({
  key: place.id,
  id: place.id,
  lat: place.lat,
  lng: place.lng,
  text: i + 1,
  cls: place.id === pickedId.value || place.id === openId.value ? ' pin--sel' : '',
  label: i + 1 + ' ' + place.name + ' 정보 보기',
})))

function slackText(place) {
  return place.slackMin < 0 ? -place.slackMin + '분 늦음' : '여유 ' + place.slackMin + '분'
}
function reasonText(place) {
  return `${place.distance}m · ${place.eta} 영업 중 · ★${place.rating}`
}
function toggleInfo(id) {
  openId.value = openId.value === id ? null : id
}

// 바꾼 일정: 바꿀 칸 자리에 고른 장소 (머묾은 원래 칸 그대로)
function swappedList() {
  const list = []
  for (const stop of replan.stops) {
    if (stop.id !== fromId.value) {
      list.push({ id: stop.id })
      continue
    }
    const place = picked.value
    list.push({ placeId: place.id, name: place.name, category: place.category, lat: place.lat, lng: place.lng, stay: stop.stay, eta: place.eta })
  }
  return list
}

async function apply() {
  if (!picked.value || isApplying.value) return
  isApplying.value = true
  try {
    const result = await replan.preview(swappedList())
    await replan.apply(result)
    router.replace('/live/applied')
  } catch (e) {
    showToast(e.message)
  }
  isApplying.value = false
}
</script>

<template>
  <div class="scr scr-AltSwap">
    <div class="app">
      <TopBar title="대안 고르기" back="/live/delay"><LateBadge /></TopBar>

      <div v-if="!replan.base" class="scroll">
        <PageMessage v-if="replan.error" icon="i-warning" title="대안을 만들 수 없어요" :text="replan.error" to="/live" button="여행 중 화면으로" />
        <PageMessage v-else is-loading title="지금 위치에서 다시 계산하는 중이에요" />
      </div>

      <template v-else>
      <div class="scroll" style="padding-top: 4px; gap: 10px; bottom: 102px">
        <AltTabs active="swap" />

        <PageMessage v-if="!fromStop" title="바꿀 수 있는 곳이 없어요" text="남은 곳이 고정 일정이거나 꼭 갈 곳이에요" />
        <PageMessage v-else-if="error" icon="i-warning" title="후보를 불러오지 못했어요" :text="error" />
        <PageMessage v-else-if="!candidates" is-loading title="근처에서 바꿀 곳을 찾는 중이에요" />

        <template v-else>
        <div v-if="isMapOk" class="sw-map">
          <KakaoMap :pins="mapPins" :me="replan.position" :show-route="false" :padding="SWAP_MAP_PADDING" @pick="toggleInfo($event.id)" @fail="isMapOk = false" />
        </div>

        <div class="opt is-locked swap-from">
          <img class="thumb" :src="imageFor(fromStop.category)" alt="" />
          <span class="opt__body">
            <span class="t-cap">이 장소 대신</span>
            <b class="t-sub" style="text-decoration: line-through; color: var(--ink-subtle)">{{ fromStop.name }} · {{ toDuration(fromStop.stay) }}</b>
            <span class="t-cap" :class="replan.lateMin ? 'c-warning' : ''">{{ whyText }}</span>
          </span>
          <i class="ic ic--l i-down c-navy" aria-hidden="true"></i>
        </div>

        <p class="divlabel"><span class="badge badge--navy" style="height: 22px">추천 기준: 그 시간 영업 중 · 평점 있는 곳</span>가까운 순</p>

        <p v-if="!candidates.length" class="t-cap">근처에 연 곳이 없어요</p>

        <div class="group" role="radiogroup" aria-label="바꿀 장소">
          <template v-for="(place, i) in candidates" :key="place.id">
            <div class="row sw-row" :class="{ 'is-on': place.id === pickedId }" :aria-expanded="openId === place.id" @click="toggleInfo(place.id)">
              <img class="thumb" :src="imageFor(place.category)" alt="" />
              <span class="row__body">
                <span class="row__title">{{ i + 1 }} {{ place.name }}</span>
                <span class="row__meta tn">
                  <i class="ic ic--s i-star c-warning" aria-hidden="true"></i>{{ place.rating }} · 이동 {{ place.moveMin }}분 · <span :class="place.slackMin < 0 ? 'c-warning' : 'c-success'">{{ slackText(place) }}</span>
                </span>
                <span class="sw-more">{{ openId === place.id ? '정보 접기' : '눌러서 정보 보기' }}<i class="ic ic--s" :class="openId === place.id ? 'i-up' : 'i-down'" aria-hidden="true"></i></span>
              </span>
              <button type="button" role="radio" :aria-checked="place.id === pickedId" class="btn btn--sm" :class="place.id === pickedId ? 'btn--primary' : 'btn--secondary'" @click.stop="pickedId = place.id">
                {{ place.id === pickedId ? '선택됨' : '선택' }}
              </button>
            </div>
            <div v-if="openId === place.id" class="sw-info" role="region" :aria-label="place.name + ' 정보'">
              <dl>
                <dt>종류</dt><dd>{{ place.category }}</dd>
                <dt>주소</dt><dd>{{ place.address || '-' }}</dd>
                <dt>별점</dt><dd>{{ place.rating }}<template v-if="place.reviewCount"> · 리뷰 {{ place.reviewCount }}개</template></dd>
                <dt>도착</dt><dd>{{ place.eta }} · 머무는 시간 {{ toDuration(fromStop.stay) }}</dd>
                <dt>추천 이유</dt><dd>{{ reasonText(place) }}</dd>
              </dl>
              <RouterLink :to="'/place?id=' + place.id" class="textlink" style="justify-content: flex-start; padding: 0; min-height: 32px">장소 상세 보기<i class="ic ic--s i-next" aria-hidden="true"></i></RouterLink>
            </div>
          </template>
        </div>
        </template>
      </div>

      <div class="cta">
        <button type="button" class="btn btn--primary btn--lg btn--block" :disabled="!picked || isApplying" @click="apply">{{ picked ? picked.name + '(으)로 바꾸기' : '바꿀 곳을 골라 주세요' }}</button>
      </div>
      </template>
    </div>
  </div>
</template>

<style>
.scr-AltSwap .sw-map{position:relative;height:150px;border-radius:var(--radius-lg);overflow:hidden;flex:none}
.scr-AltSwap .swap-from{background:var(--surface-raised);border:1.5px dashed var(--line-strong)}
.scr-AltSwap .group .row.is-on{background:var(--navy-soft)}
.scr-AltSwap .sw-row{cursor:pointer}
.scr-AltSwap .sw-row:hover{background:var(--surface)}
.scr-AltSwap .sw-more{display:flex;align-items:center;gap:2px;font-size:12px;color:var(--ink-muted)}
.scr-AltSwap .sw-info{display:flex;flex-direction:column;gap:8px;padding:4px 16px 14px 72px;border-bottom:1px solid var(--line);background:var(--navy-soft)}
.scr-AltSwap .sw-info dl{display:grid;grid-template-columns:auto 1fr;gap:4px 10px;margin:0;font-size:13px;line-height:18px}
.scr-AltSwap .sw-info dt{color:var(--ink-muted)}
.scr-AltSwap .sw-info dd{margin:0;color:var(--ink);font-variant-numeric:tabular-nums}
</style>
