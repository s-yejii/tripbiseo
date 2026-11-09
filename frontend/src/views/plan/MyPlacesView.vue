<script setup>
// ③ 마이 > 내 장소: 친구 소식·장소 상세에서 저장한 곳. 식당 · 카페 · 놀거리로 나눠 보여요 (나누기는 서버가 장소 분류로)
// 서버: GET /api/my/places, DELETE /api/my/places/{placeId}
import { computed, onMounted, ref } from 'vue'
import TopBar from '@/components/common/TopBar.vue'
import PageMessage from '@/components/common/PageMessage.vue'
import { loadMyPlaces, myPlaces, removeMyPlace } from '@/utils/myPlaces'
import { showToast } from '@/utils/toast'

const GROUPS = ['식당', '카페', '놀거리']
const GROUP_ICON = { 식당: 'i-food', 카페: 'i-cup', 놀거리: 'i-star' }

const tab = ref('전체')
const error = ref('')
const isBusy = ref(false)

const tabs = computed(() => ['전체', ...GROUPS].map((name) => ({ name, count: countOf(name) })))
const shown = computed(() => (myPlaces.value || []).filter((place) => tab.value === '전체' || place.group === tab.value))

onMounted(load)

async function load() {
  error.value = ''
  try {
    await loadMyPlaces(true)
  } catch (e) {
    error.value = e.message
  }
}
function countOf(name) {
  const list = myPlaces.value || []
  return name === '전체' ? list.length : list.filter((place) => place.group === name).length
}
function metaOf(place) {
  const parts = [place.category ? place.category.replace(/,/g, ' · ') : place.group]
  if (place.fromName) parts.push(place.fromName + ' 소식에서')
  return parts.join(' · ')
}
async function remove(place) {
  isBusy.value = true
  try {
    await removeMyPlace(place.placeId)
    showToast(place.name + ' 뺐어요')
  } catch (e) {
    showToast(e.message)
  }
  isBusy.value = false
}
</script>

<template>
  <div class="scr scr-MyPlaces">
    <div class="app">
      <TopBar title="내 장소" back="/my" />
      <div class="scroll mp-body">
        <PageMessage v-if="error" icon="i-warning" title="내 장소를 불러오지 못했어요" :text="error" button="다시 시도" @retry="load" />
        <PageMessage v-else-if="!myPlaces" is-loading title="불러오는 중이에요" />
        <PageMessage v-else-if="!myPlaces.length" icon="i-bookmark" title="저장한 곳이 없어요" text="친구 소식에서 마음에 드는 곳을 저장해 보세요" />
        <template v-else>
          <div class="seg" role="tablist" aria-label="장소 종류">
            <button v-for="item in tabs" :key="item.name" type="button" role="tab" :aria-selected="tab === item.name" :class="{ 'is-on': tab === item.name }" @click="tab = item.name">
              {{ item.name }}<span class="tn mp-count">{{ item.count }}</span>
            </button>
          </div>
          <p v-if="!shown.length" class="t-cap mp-none">{{ tab }}은 아직 없어요</p>
          <div v-else class="group">
            <div v-for="place in shown" :key="place.placeId" class="row mp-row">
              <RouterLink :to="'/place?id=' + encodeURIComponent(place.placeId)" class="mp-link">
                <span class="ico-tile" aria-hidden="true"><i class="ic" :class="GROUP_ICON[place.group]"></i></span>
                <span class="row__body">
                  <span class="row__title">{{ place.name }}</span>
                  <span class="row__meta">{{ metaOf(place) }}</span>
                </span>
              </RouterLink>
              <button type="button" class="iconbtn" :aria-label="place.name + ' 빼기'" :disabled="isBusy" @click="remove(place)"><i class="ic ic--s i-trash" aria-hidden="true"></i></button>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<style>
.scr-MyPlaces .mp-body { display: flex; flex-direction: column; gap: 12px; padding-top: 8px; }
.scr-MyPlaces .mp-count { margin-left: 4px; font-size: 12px; color: var(--ink-muted); }
.scr-MyPlaces .mp-none { margin: 24px 0; text-align: center; }
.scr-MyPlaces .mp-row { gap: 4px; padding-right: 8px; }
.scr-MyPlaces .mp-link { flex: 1; min-width: 0; display: flex; align-items: center; gap: 12px; color: inherit; text-decoration: none; }
</style>
