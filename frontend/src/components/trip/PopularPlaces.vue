<script setup>
// 요즘 인기 장소 목록 (②): GET /api/places/popular 결과를 줄로 보여 줘요. 누르면 장소 상세
// places: [{ place: 장소 카드, visitors, reason }]
import { imageFor } from '@/utils/mapView'

defineProps({
  title: { type: String, required: true },
  caption: { type: String, default: '' },
  places: { type: Array, required: true },
})

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
        <img class="thumb" :src="imageFor(item.place.category)" alt="" />
        <span class="row__body">
          <span class="row__title">{{ item.place.name }}</span>
          <span class="row__meta">{{ item.place.category }}<template v-if="item.place.distance != null"> · {{ distanceText(item.place.distance) }}</template></span>
          <span class="row__meta pop__reason" :class="{ 'is-hot': item.visitors }">
            <i class="ic ic--s" :class="item.visitors ? 'i-users' : 'i-star'" aria-hidden="true"></i>{{ item.reason }}
          </span>
        </span>
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
.pop__reason .ic { width: 13px; height: 13px; }
.pop__reason.is-hot { color: var(--coral-ink, var(--navy)); font-weight: 600; }
</style>
