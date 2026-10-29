<script setup>
// 여행 중 "근처 찾기" (⑤): 검색어 → 지금 위치 반경 안 장소를 지도 핀으로 → 골라서 다음 경로에 추가
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { kindOf, nearPlaces } from '@/data/near'

const props = defineProps({
  me: { type: Object, required: true }, // 지금 위치 { x, y }
})
const emit = defineEmits(['close', 'add'])

const router = useRouter()
const draft = ref('') // 입력칸 글자
const word = ref('') // 찾은 검색어
const radius = ref(500) // m
const pickedId = ref(null)

const kind = computed(() => kindOf(word.value))

// 지도 1px ≈ 4m 라서 거리 = 픽셀 거리 × 4 (10m 단위로 반올림)
function distanceOf(place) {
  const px = Math.hypot(place.x - props.me.x, place.y - props.me.y)
  return Math.round((px * 4) / 10) * 10
}
function distText(m) {
  return m >= 1000 ? (m / 1000).toFixed(1) + 'km' : m + 'm'
}

const results = computed(() => {
  if (!kind.value) return []
  return nearPlaces
    .filter((p) => p.kind === kind.value)
    .map((p) => ({ ...p, dist: distanceOf(p) }))
    .filter((p) => p.dist <= radius.value)
    .sort((a, b) => a.dist - b.dist)
})
const picked = computed(() => results.value.find((p) => p.id === pickedId.value))
const pinIcon = computed(() => (kind.value === 'cafe' ? 'i-cup' : kind.value === 'pub' ? 'i-flag' : 'i-star'))

function search(text) {
  draft.value = text
  word.value = text.trim()
  pickedId.value = null
}
</script>

<template>
  <template v-if="word">
    <span
      class="near-ring"
      :style="{ left: me.x + 'px', top: me.y + 'px', width: radius / 2 + 'px', height: radius / 2 + 'px', margin: `-${radius / 4}px 0 0 -${radius / 4}px` }"
      aria-hidden="true"
    ></span>
    <button
      v-for="p in results"
      :key="p.id"
      type="button"
      class="pin pin--res"
      :class="{ 'pin--sel': p.id === pickedId }"
      :style="{ left: p.x + 'px', top: p.y + 'px' }"
      :aria-label="p.name + ', ' + distText(p.dist)"
      @click="pickedId = p.id"
    >
      <i class="ic ic--s" :class="pinIcon" aria-hidden="true" style="width: 14px; height: 14px"></i>
    </button>
  </template>

  <div class="addbar">
    <!-- 고른 장소 카드 -->
    <div v-if="picked" class="addcard">
      <div style="display: flex; gap: 12px; align-items: center">
        <img class="thumb" :src="picked.img" alt="" style="width: 64px; height: 64px" />
        <div style="flex: 1; min-width: 0">
          <b class="t-strong">{{ picked.name }}</b>
          <p class="t-cap tn" style="margin: 2px 0 0"><i class="ic ic--s i-star c-warning" aria-hidden="true"></i> {{ picked.rate }} · {{ picked.cat }} · {{ picked.hours }}</p>
          <p class="t-cap tn" style="margin: 2px 0 0">지금 위치에서 {{ distText(picked.dist) }} · 걸어서 {{ Math.max(1, Math.round(picked.dist / 70)) }}분</p>
        </div>
        <button type="button" class="iconbtn" aria-label="목록으로" @click="pickedId = null"><i class="ic i-close muted" aria-hidden="true"></i></button>
      </div>
      <div class="cta__row" style="margin-top: 12px">
        <button type="button" class="btn btn--secondary btn--md" @click="router.push('/place')">자세히</button>
        <button type="button" class="btn btn--primary btn--md" @click="emit('add', picked)"><i class="ic ic--s i-plus" aria-hidden="true"></i>다음 경로에 추가</button>
      </div>
    </div>

    <!-- 찾은 뒤: 반경 고르기 -->
    <div v-else-if="word" class="addcard">
      <div class="addcard__row">
        <span class="addcard__k" style="width: auto">현위치에서</span>
        <div class="chips" style="flex-wrap: nowrap">
          <button v-for="r in [300, 500, 1000]" :key="r" type="button" class="chip chip--sm" :class="{ 'is-on': radius === r }" :aria-pressed="radius === r" @click="radius = r; pickedId = null">
            {{ r < 1000 ? r + 'm' : '1km' }}
          </button>
        </div>
      </div>
      <p class="t-cap tn" style="margin: 2px 0 0; display: flex; align-items: center; gap: 6px">
        <template v-if="!kind"><i class="ic ic--s i-info" aria-hidden="true"></i>'{{ word }}' 결과가 없어요 · 커피, 맛집, 술집으로 찾아보세요</template>
        <template v-else-if="results.length"><i class="ic ic--s i-pin c-navy" aria-hidden="true"></i>'{{ word }}' {{ results.length }}곳 · 지도에서 핀을 눌러 보세요</template>
        <template v-else><i class="ic ic--s i-info" aria-hidden="true"></i>이 반경엔 없어요 · 반경을 넓혀 보세요</template>
      </p>
    </div>

    <!-- 처음: 안내 + 빠른 검색어 -->
    <div v-else class="addcard addcard--hint">
      <p class="t-cap" style="margin: 0">찾고 싶은 곳을 적어 주세요. 지금 위치 주변에서 찾아 지도에 핀으로 보여 드려요.</p>
      <div class="chips" style="flex-wrap: nowrap; margin-top: 8px">
        <button v-for="w in ['커피', '맛집', '술집']" :key="w" type="button" class="chip chip--sm" @click="search(w)">{{ w }}</button>
      </div>
    </div>

    <form class="addform" @submit.prevent="search(draft)">
      <label for="add-q" class="sr">찾을 곳</label>
      <input id="add-q" v-model="draft" type="search" placeholder="근처에서 무엇을 찾을까요? 예: 커피" autocomplete="off" />
      <button type="submit" class="iconbtn" aria-label="찾기" style="color: var(--navy)"><i class="ic i-search" aria-hidden="true"></i></button>
      <button type="button" class="iconbtn" aria-label="근처 찾기 닫기" @click="emit('close')"><i class="ic i-close muted" aria-hidden="true"></i></button>
    </form>
  </div>
</template>
