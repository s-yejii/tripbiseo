<script setup>
// 놓친 예약과 비슷한 근처 가게 (④ 11/12): 놓침 카드의 [비슷한 근처 가게 찾기]에서 열려요.
//   POST /live/recommend + similarTo(놓친 장소): 업종(biz_type)이 겹치는 곳, 같은 브랜드 다른 지점 먼저 (AI 안 씀, SQL 반경 검색 + 규칙)
//   결과는 부모(여행 중 화면) 지도에 핀으로 → 핀을 누르고 [담기] → 놓친 칸 자리에 넣고 바로 다음 목적지로 (POST /live/insert + replaceStopId)
//   모양·반경 버튼은 근처 찾기(NearSearch, ⑤)와 같게 맞췄어요
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { askRecommend, insertPlace } from '@/api/trips'
import { radiusOptions } from '@/data/ask'
import { imageFor } from '@/utils/mapView'
import { showToast } from '@/utils/toast'

const DEFAULT_RADIUS = 500 // 처음 반경: 근처 찾기와 같은 값
const WALK_M_PER_MIN = 70 // 걸어서 1분에 가는 거리 (근처 찾기와 같은 값)

const props = defineProps({
  tripId: { type: Number, required: true },
  position: { type: Object, required: true }, // 지금 위치 { lat, lng }
  stop: { type: Object, required: true },     // 놓친 칸 { id, placeId, name }
  pickedId: { type: String, default: null },  // 지도에서 누른 핀 (부모와 같이 씀)
})
const emit = defineEmits(['close', 'added', 'results', 'update:pickedId'])

const router = useRouter()
const radius = ref(DEFAULT_RADIUS)
const places = ref([])
const closedSkipped = ref(0)
const isLoading = ref(false)
const isAdding = ref(false)
const error = ref('')

const picked = computed(() => places.value.find((place) => place.id === props.pickedId))

// 결과·반경이 바뀌면 부모 지도에 핀과 원을 알려 줌
watch([places, radius], () => emit('results', { places: places.value, radius: radius.value }))

onMounted(() => search(DEFAULT_RADIUS))

function distText(m) {
  return m >= 1000 ? (m / 1000).toFixed(1) + 'km' : m + 'm'
}
function walkMin(m) {
  return Math.max(1, Math.round(m / WALK_M_PER_MIN))
}

// 이 반경에서 비슷한 가게 찾기
async function search(meters) {
  radius.value = meters
  emit('update:pickedId', null)
  isLoading.value = true
  error.value = ''
  try {
    const res = await askRecommend(props.tripId, props.position.lat, props.position.lng, meters, null, [], props.stop.placeId)
    places.value = res.places
    closedSkipped.value = res.closedSkipped
  } catch (e) {
    places.value = []
    error.value = e.message
  } finally {
    isLoading.value = false
  }
}

// 담기: 놓친 칸 자리에 넣고 바로 다음 목적지로
async function addPicked() {
  if (isAdding.value) return
  isAdding.value = true
  try {
    const res = await insertPlace(props.tripId, picked.value.id, props.position.lat, props.position.lng, props.stop.id)
    emit('added', res.message || picked.value.name + '(으)로 바꿨어요')
  } catch (e) {
    showToast(e.message)
  } finally {
    isAdding.value = false
  }
}
</script>

<template>
  <div class="addbar">
    <!-- 고른 가게 카드 -->
    <div v-if="picked" class="addcard">
      <div style="display: flex; gap: 12px; align-items: center">
        <img class="thumb" :src="imageFor(picked.category)" alt="" style="width: 64px; height: 64px" />
        <div style="flex: 1; min-width: 0">
          <b class="t-strong">{{ picked.name }}</b>
          <p class="t-cap tn" style="margin: 2px 0 0">
            <template v-if="picked.rating != null"><i class="ic ic--s i-star c-warning" aria-hidden="true"></i> {{ picked.rating }} · </template>{{ picked.category || '장소' }}
          </p>
          <p class="t-cap tn" style="margin: 2px 0 0">지금 위치에서 {{ distText(picked.distance) }} · 걸어서 {{ walkMin(picked.distance) }}분<template v-if="picked.closeAt"> · {{ picked.closeAt }}까지</template></p>
        </div>
        <button type="button" class="iconbtn" aria-label="목록으로" @click="emit('update:pickedId', null)"><i class="ic i-close muted" aria-hidden="true"></i></button>
      </div>
      <div class="cta__row" style="margin-top: 12px">
        <button type="button" class="btn btn--secondary btn--md" @click="router.push('/place?id=' + encodeURIComponent(picked.id))">자세히</button>
        <button type="button" class="btn btn--primary btn--md" :disabled="isAdding" @click="addPicked">
          <i class="ic ic--s i-plus" aria-hidden="true"></i>{{ isAdding ? '담는 중…' : '담기 · 다음 목적지로' }}
        </button>
      </div>
    </div>

    <!-- 반경 고르기 + 결과 안내 -->
    <div v-else class="addcard">
      <p class="t-strong" style="margin: 0 0 6px">{{ stop.name }}와(과) 비슷한 근처 가게</p>
      <div class="addcard__row">
        <span class="addcard__k" style="width: auto">현위치에서</span>
        <div class="chips" style="flex-wrap: nowrap">
          <button v-for="r in radiusOptions" :key="r" type="button" class="chip chip--sm" :class="{ 'is-on': radius === r }" :aria-pressed="radius === r" @click="search(r)">
            {{ r < 1000 ? r + 'm' : '1km' }}
          </button>
        </div>
      </div>
      <p class="t-cap tn" style="margin: 2px 0 0; display: flex; align-items: center; gap: 6px">
        <template v-if="isLoading"><i class="ic ic--s i-search" aria-hidden="true"></i>비슷한 가게 찾는 중…</template>
        <template v-else-if="error"><i class="ic ic--s i-warning c-warning" aria-hidden="true"></i>{{ error }}</template>
        <template v-else-if="places.length"><i class="ic ic--s i-pin c-navy" aria-hidden="true"></i>{{ places.length }}곳 · 지도에서 핀을 눌러 보세요<template v-if="closedSkipped"> · 지금 문 닫은 {{ closedSkipped }}곳은 뺐어요</template></template>
        <template v-else><i class="ic ic--s i-info" aria-hidden="true"></i>이 반경엔 비슷한 가게가 없어요 · 더 넓게 찾아보세요</template>
      </p>
      <div class="cta__row" style="margin-top: 8px">
        <button type="button" class="btn btn--secondary btn--md" @click="emit('close')">닫기</button>
      </div>
    </div>
  </div>
</template>
