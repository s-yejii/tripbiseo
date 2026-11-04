<script setup>
// 요즘 인기 장소 목록 (②): GET /api/places/popular 결과를 줄로 보여 줘요. 누르면 장소 상세
// places: [{ place: 장소 카드, visitors, reason }]
import { ref, watch } from 'vue'
import { getPlacePhotos } from '@/api/trips'
import { imageFor } from '@/utils/mapView'

const props = defineProps({
  title: { type: String, required: true },
  caption: { type: String, default: '' },
  places: { type: Array, required: true },
})

const photos = ref({}) // 장소 id → { imageUrl, siteName, linkUrl }

// 진짜 사진을 찾아 봐요. 못 찾은 곳은 기본 사진 그대로
watch(() => props.places, async (list) => {
  try {
    const found = await getPlacePhotos(list.map((item) => item.place.id))
    const next = {}
    for (const photo of found) next[photo.placeId] = photo
    photos.value = next
  } catch {
    photos.value = {}
  }
}, { immediate: true })

function imageOf(item) {
  const photo = photos.value[item.place.id]
  return photo ? photo.imageUrl : imageFor(item.place.category)
}
function openSource(item) {
  window.open(photos.value[item.place.id].linkUrl, '_blank', 'noopener')
}

function distanceText(meters) {
  if (meters == null) return ''
  return meters >= 1000 ? (meters / 1000).toFixed(1) + 'km' : meters + 'm'
}
</script>

<template>
  <section class="pop" :aria-label="title">
    <div class="pop__head">
      <h2 class="h-title">{{ title }}</h2>
      <p v-if="caption" class="t-cap">{{ caption }}</p>
    </div>
    <div class="group">
      <RouterLink v-for="item in places" :key="item.place.id" :to="'/place?id=' + encodeURIComponent(item.place.id)" class="row">
        <img class="thumb" :src="imageOf(item)" alt="" />
        <span class="row__body">
          <span class="row__title">{{ item.place.name }}</span>
          <span class="row__meta">{{ item.place.category }}<template v-if="item.place.distance != null"> · {{ distanceText(item.place.distance) }}</template></span>
          <span class="row__meta pop__reason" :class="{ 'is-hot': item.visitors }">
            <i class="ic ic--s" :class="item.visitors ? 'i-users' : 'i-star'" aria-hidden="true"></i>{{ item.reason.replace(/^★/, '') }}
          </span>
        </span>
        <button v-if="photos[item.place.id] && photos[item.place.id].siteName" type="button" class="pop__credit" :aria-label="'사진 출처 ' + photos[item.place.id].siteName" @click.prevent.stop="openSource(item)">{{ photos[item.place.id].siteName }}</button>
        <i class="ic i-next subtle" aria-hidden="true"></i>
      </RouterLink>
    </div>
  </section>
</template>

<style>
.pop { display: flex; flex-direction: column; gap: 12px; }
.pop__head { display: flex; flex-direction: column; gap: 2px; }
.pop__head .t-cap { margin: 0; }
.pop__reason { display: inline-flex; align-items: center; gap: 4px; }
.pop__credit { position: relative; flex: none; max-width: 64px; padding: 0; border: 0; background: none; font-size: 10px; line-height: 12px; color: var(--ink-subtle); text-decoration: underline; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; cursor: pointer; }
.pop__credit::before { content: ""; position: absolute; left: -8px; right: -8px; top: -16px; bottom: -16px; }
.pop__reason .ic { width: 13px; height: 13px; }
.pop__reason.is-hot { color: var(--coral-ink, var(--navy)); font-weight: 600; }
</style>
