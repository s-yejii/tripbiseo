<script setup>
// 여행 중 "근처 찾기" (⑤): 검색어 → 지금 위치 반경 안 장소를 지도 핀으로 → 골라서 다음 경로에 추가
// 장소는 GET /api/places/search (지금 위치에서 가까운 순 20곳) 에서 받고, 반경(300m·500m·1km) 안만 보여 줘요.
// 핀은 부모(여행 중 화면)의 카카오 지도에 그려요 → 결과가 바뀌면 results 이벤트로 알려 줘요.
// "다음 경로에 추가"는 POST /api/trips/{id}/live/insert (지금 있는 곳 바로 다음 칸으로 넣고 남은 시간을 다시 계산)
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { insertPlace, searchPlaces } from '@/api/trips'
import { imageFor } from '@/utils/mapView'
import { showToast } from '@/utils/toast'

const RADIUS_LIST = [300, 500, 1000] // m
const WALK_M_PER_MIN = 70 // 걸어서 1분에 가는 거리

const props = defineProps({
  tripId: { type: Number, required: true },
  position: { type: Object, required: true }, // 지금 위치 { lat, lng }
  pickedId: { type: String, default: null }, // 지도에서 누른 핀 (부모와 같이 씀)
})
const emit = defineEmits(['close', 'added', 'results', 'update:pickedId'])

const router = useRouter()
const draft = ref('') // 입력칸 글자
const word = ref('') // 찾은 검색어
const radius = ref(500)
const found = ref([]) // 서버 결과 (가까운 순)
const isLoading = ref(false)
const isAdding = ref(false)
const error = ref('')

const results = computed(() => found.value.filter((place) => place.distance != null && place.distance <= radius.value))
const picked = computed(() => results.value.find((place) => place.id === props.pickedId))

// 결과·반경이 바뀌면 부모 지도에 핀과 원을 알려 줌
watch([results, radius, word], () => {
  emit('results', { places: results.value, radius: word.value ? radius.value : null })
})

function distText(m) {
  return m >= 1000 ? (m / 1000).toFixed(1) + 'km' : m + 'm'
}
function walkMin(m) {
  return Math.max(1, Math.round(m / WALK_M_PER_MIN))
}

async function search(text) {
  const keyword = text.trim()
  if (!keyword) return
  draft.value = keyword
  word.value = keyword
  emit('update:pickedId', null)
  isLoading.value = true
  error.value = ''
  try {
    found.value = await searchPlaces(keyword, props.position.lat, props.position.lng)
  } catch (e) {
    found.value = []
    error.value = e.message
  } finally {
    isLoading.value = false
  }
}

function setRadius(value) {
  radius.value = value
  emit('update:pickedId', null)
}

async function addPicked() {
  if (isAdding.value) return
  isAdding.value = true
  try {
    const response = await insertPlace(props.tripId, picked.value.id, props.position.lat, props.position.lng)
    emit('added', response.message || picked.value.name + '을(를) 다음 일정으로 넣었어요')
  } catch (e) {
    showToast(e.message)
  } finally {
    isAdding.value = false
  }
}
</script>

<template>
  <div class="addbar">
    <!-- 고른 장소 카드 -->
    <div v-if="picked" class="addcard">
      <div style="display: flex; gap: 12px; align-items: center">
        <img class="thumb" :src="imageFor(picked.category)" alt="" style="width: 64px; height: 64px" />
        <div style="flex: 1; min-width: 0">
          <b class="t-strong">{{ picked.name }}</b>
          <p class="t-cap tn" style="margin: 2px 0 0">
            <template v-if="picked.rating != null"><i class="ic ic--s i-star c-warning" aria-hidden="true"></i> {{ picked.rating }} · </template>{{ picked.category || '장소' }}
          </p>
          <p class="t-cap tn" style="margin: 2px 0 0">지금 위치에서 {{ distText(picked.distance) }} · 걸어서 {{ walkMin(picked.distance) }}분</p>
        </div>
        <button type="button" class="iconbtn" aria-label="목록으로" @click="emit('update:pickedId', null)"><i class="ic i-close muted" aria-hidden="true"></i></button>
      </div>
      <div class="cta__row" style="margin-top: 12px">
        <button type="button" class="btn btn--secondary btn--md" @click="router.push('/place?id=' + encodeURIComponent(picked.id))">자세히</button>
        <button type="button" class="btn btn--primary btn--md" :disabled="isAdding" @click="addPicked">
          <i class="ic ic--s i-plus" aria-hidden="true"></i>{{ isAdding ? '넣는 중…' : '다음 경로에 추가' }}
        </button>
      </div>
    </div>

    <!-- 찾은 뒤: 반경 고르기 -->
    <div v-else-if="word" class="addcard">
      <div class="addcard__row">
        <span class="addcard__k" style="width: auto">현위치에서</span>
        <div class="chips" style="flex-wrap: nowrap">
          <button v-for="r in RADIUS_LIST" :key="r" type="button" class="chip chip--sm" :class="{ 'is-on': radius === r }" :aria-pressed="radius === r" @click="setRadius(r)">
            {{ r < 1000 ? r + 'm' : '1km' }}
          </button>
        </div>
      </div>
      <p class="t-cap tn" style="margin: 2px 0 0; display: flex; align-items: center; gap: 6px">
        <template v-if="isLoading"><i class="ic ic--s i-search" aria-hidden="true"></i>'{{ word }}' 찾는 중…</template>
        <template v-else-if="error"><i class="ic ic--s i-warning c-warning" aria-hidden="true"></i>{{ error }}</template>
        <template v-else-if="!found.length"><i class="ic ic--s i-info" aria-hidden="true"></i>'{{ word }}' 결과가 없어요 · 카페, 음식점처럼 찾아보세요</template>
        <template v-else-if="results.length"><i class="ic ic--s i-pin c-navy" aria-hidden="true"></i>'{{ word }}' {{ results.length }}곳 · 지도에서 핀을 눌러 보세요</template>
        <template v-else><i class="ic ic--s i-info" aria-hidden="true"></i>이 반경엔 없어요 · 가장 가까운 곳이 {{ distText(found[0].distance) }}예요</template>
      </p>
    </div>

    <!-- 처음: 안내 + 빠른 검색어 -->
    <div v-else class="addcard addcard--hint">
      <p class="t-cap" style="margin: 0">찾고 싶은 곳을 적어 주세요. 지금 위치 주변에서 찾아 지도에 핀으로 보여 드려요.</p>
      <div class="chips" style="flex-wrap: nowrap; margin-top: 8px">
        <button v-for="w in ['카페', '음식점', '술집']" :key="w" type="button" class="chip chip--sm" @click="search(w)">{{ w }}</button>
      </div>
    </div>

    <form class="addform" @submit.prevent="search(draft)">
      <label for="add-q" class="sr">찾을 곳</label>
      <input id="add-q" v-model="draft" type="search" placeholder="근처에서 무엇을 찾을까요? 예: 카페" autocomplete="off" />
      <button type="submit" class="iconbtn" aria-label="찾기" style="color: var(--navy)"><i class="ic i-search" aria-hidden="true"></i></button>
      <button type="button" class="iconbtn" aria-label="근처 찾기 닫기" @click="emit('close')"><i class="ic i-close muted" aria-hidden="true"></i></button>
    </form>
  </div>
</template>
