<script setup>
// "가고 싶은 곳": 인스타·유튜브·네이버 링크를 붙여넣으면 글에 나온 장소를 찾아 담아요 (③ POST /api/plans/links)
// 담은 곳은 일정 조건의 "꼭 갈 곳"(store.planConditions.mustVisit)에 이름으로 들어가요
// 글을 못 읽었거나(422) 찾은 곳이 없으면 글을 복사해 붙이는 칸을 열어요 (유튜브는 키가 없으면 제목만 읽어요)
import { computed, ref } from 'vue'
import { findLinkPlaces } from '@/api/trips'
import { useTripStore } from '@/stores/trip'
import { areaFromPlaces } from '@/utils/planArea'
import { showToast } from '@/utils/toast'

const store = useTripStore()
const emit = defineEmits(['added'])

const url = ref('')
const caption = ref('')
const showCaption = ref(false)
const error = ref('')
const loading = ref(false)
const found = ref(null) // 찾은 장소 카드 목록 (찾기 전에는 null)
const picked = ref({}) // 찾은 장소 중 고른 것 { 이름: true }

const wish = computed(() => store.planConditions?.mustVisit || [])
const pickCount = computed(() => (found.value ? found.value.filter((place) => picked.value[place.name]).length : 0))

// 추천 게시물을 누르면 링크를 넣고 바로 찾기 (여행 정보 화면이 불러요)
function fill(link) {
  url.value = link
  found.value = null
  findPlaces()
}
defineExpose({ fill })

// 찾은 곳 표시: 링크 주소로 플랫폼 이름
const sourceName = computed(() => {
  if (/youtu/.test(url.value)) return '유튜브'
  if (/naver/.test(url.value)) return '네이버'
  return '인스타'
})

async function findPlaces() {
  const link = url.value.trim()
  if (!link) return (error.value = '게시물 링크를 붙여넣어 주세요')
  error.value = ''
  loading.value = true
  try {
    const result = await findLinkPlaces(link, caption.value.trim() || null)
    if (!result.places.length) {
      error.value = '장소를 못 찾았어요. 설명 글을 붙여 넣어 주세요'
      showCaption.value = true
      return
    }
    found.value = result.places
    picked.value = {}
    for (const place of result.places) {
      if (!wish.value.includes(place.name)) picked.value[place.name] = true
    }
  } catch (e) {
    error.value = e.message
    if (e.status === 422) showCaption.value = true
  } finally {
    loading.value = false
  }
}
function cancel() {
  found.value = null
  url.value = ''
  caption.value = ''
  showCaption.value = false
}
function setWish(names) {
  store.planConditions = { ...(store.planConditions || {}), mustVisit: names }
}
function addPicked() {
  const places = found.value.filter((place) => picked.value[place.name])
  setWish([...wish.value, ...places.map((place) => place.name)])
  const area = setAreaFrom(places)
  cancel()
  showToast(places.length + '곳 담음' + (area ? ' · ' + area.fullName : ''))
  emit('added', places)
}
// 담은 곳 주소에서 가장 많이 나온 구를 지역으로 (직접 고른 지역이 있으면 그대로 둠). 좌표는 그 구 장소들의 가운데
function setAreaFrom(places) {
  if (store.planArea && !store.planArea.isAuto) return null
  const area = areaFromPlaces(places)
  if (area) store.planArea = area
  return area
}
function removeWish(name) {
  setWish(wish.value.filter((item) => item !== name))
}
</script>

<template>
  <div v-for="name in wish" :key="name" class="opt" style="padding: 10px 12px">
    <span class="ico-tile"><i class="ic i-pin" aria-hidden="true"></i></span>
    <span class="opt__body"><b class="t-strong">{{ name }}</b></span>
    <button type="button" class="iconbtn" :aria-label="name + ' 빼기'" @click="removeWish(name)"><i class="ic i-close muted" aria-hidden="true"></i></button>
  </div>

  <template v-if="!found">
    <form class="wl-form" @submit.prevent="findPlaces">
      <div class="field" :class="{ 'is-error': error }" style="flex: 1; min-width: 0">
        <label for="pa-url" class="sr">게시물 링크</label>
        <div class="field__box"><input id="pa-url" v-model="url" type="text" inputmode="url" placeholder="링크 붙여넣기" autocomplete="off" autocapitalize="off" @input="error = ''" /></div>
      </div>
      <button type="submit" class="btn btn--secondary btn--md" :disabled="loading">{{ loading ? '찾는 중' : '장소 찾기' }}</button>
    </form>
    <p v-if="error" class="field__help c-danger" style="margin: -4px 0 0" role="alert"><i class="ic ic--s i-danger" aria-hidden="true"></i>{{ error }}</p>
    <div v-if="showCaption" class="field">
      <label for="pa-caption" class="t-cap">게시물 글</label>
      <textarea id="pa-caption" v-model="caption" class="wl-caption" rows="4" maxlength="2200" placeholder="글 붙여 넣기"></textarea>
    </div>
    <button v-else type="button" class="wl-more" @click="showCaption = true">글 붙여 넣기</button>
  </template>

  <div v-else class="wl-found" role="group" aria-label="찾은 장소">
    <span style="display: flex; justify-content: space-between; align-items: center">
      <b class="t-strong">{{ found.length ? found.length + '곳 찾았어요' : '장소를 찾지 못했어요' }}</b><span class="wl-src"><i class="ic ic--s i-link" aria-hidden="true"></i>{{ sourceName }}</span>
    </span>
    <template v-for="place in found" :key="place.name">
      <div v-if="wish.includes(place.name)" class="opt is-locked">
        <span class="opt__body"><b class="t-strong">{{ place.name }}</b><span class="t-cap">이미 담았어요</span></span>
        <i class="ic i-check c-success" aria-hidden="true"></i>
      </div>
      <button v-else type="button" role="checkbox" :aria-checked="!!picked[place.name]" class="opt" :class="{ 'is-on': picked[place.name] }" style="text-align: left" @click="picked[place.name] = !picked[place.name]">
        <span class="opt__body"><b class="t-strong">{{ place.name }}</b><span class="t-cap tn"><template v-if="place.rating"><i class="ic ic--s i-star c-warning" aria-hidden="true"></i> {{ place.rating }} · </template>{{ place.category || place.address }}</span></span>
        <span class="check" :class="{ 'is-on': picked[place.name] }"><i class="ic ic--s i-check" aria-hidden="true"></i></span>
      </button>
    </template>
    <div class="cta__row">
      <button type="button" class="btn btn--secondary btn--md" @click="cancel">{{ found.length ? '취소' : '닫기' }}</button>
      <button v-if="found.length" type="button" class="btn btn--primary btn--md" :disabled="!pickCount" @click="addPicked">{{ pickCount ? pickCount + '곳 담기' : '골라 주세요' }}</button>
    </div>
  </div>
</template>

<style>
.wl-form{display:flex;gap:8px;align-items:flex-start}
.wl-form .btn{flex:none;height:48px}
.wl-caption{width:100%;box-sizing:border-box;padding:10px 12px;border-radius:12px;border:1px solid var(--line-strong);font:inherit;font-size:16px;resize:vertical}
.wl-more{align-self:flex-start;padding:0;border:0;background:none;color:var(--navy);font:inherit;font-size:13px;cursor:pointer;text-decoration:underline}
.wl-found{display:flex;flex-direction:column;gap:8px;padding:12px;border-radius:14px;background:var(--surface);border:1px solid var(--line)}
.wl-found .opt{padding:10px 12px}
.wl-src{display:inline-flex;align-items:center;gap:4px;font-size:12px;color:var(--ink-muted)}
</style>
