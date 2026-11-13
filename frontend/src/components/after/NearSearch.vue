<script setup>
// 여행 중 "근처 찾기" (⑤): 검색어 → 지금 위치 반경 안 장소를 지도 핀으로 → 골라서 일정에 넣기
// 장소는 GET /api/places/search (지금 위치에서 가까운 순 20곳) 에서 받고, 반경(300m·500m·1km) 안만 보여 줘요.
// 핀은 부모(여행 중 화면)의 카카오 지도에 그려요 → 결과가 바뀌면 results 이벤트로 알려 줘요.
// "일정에 넣기"는 넣을 자리 고르기 시트(InsertSlotSheet, 11/13)로 → 거기서 POST /api/trips/{id}/live/insert
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { searchPlaces } from '@/api/trips'
import { photoFor } from '@/utils/placePhoto'
import '@/assets/css/live-panel.css' // 지도 위 패널 생김새 (AI 채팅·근처 찾기·비슷한 가게 같이, 11/13)

const RADIUS_LIST = [300, 500, 1000] // m

const props = defineProps({
  tripId: { type: Number, required: true },
  position: { type: Object, required: true }, // 지금 위치 { lat, lng }
  planned: { type: Array, default: () => [] }, // 지금 있는 곳·남은 일정의 장소 id (이미 있으면 '일정에 있어요', 11/13)
  pickedId: { type: String, default: null }, // 지도에서 누른 핀 (부모와 같이 씀)
})
const emit = defineEmits(['close', 'added', 'results', 'update:pickedId', 'choose']) // choose: 일정에 넣기 → 자리 고르기

const router = useRouter()
const draft = ref('') // 입력칸 글자
const word = ref('') // 찾은 검색어
const radius = ref(500)
const found = ref([]) // 서버 결과 (가까운 순)
const isLoading = ref(false)
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

// 일정에 넣기 → 넣을 자리 고르기 시트로 (LiveView의 InsertSlotSheet, 11/13)
function addPicked() {
  emit('choose', picked.value)
}
</script>

<template>
  <div class="addbar">
    <!-- 11/13 디자인: 머리줄 + 내용 + 입력창을 카드 하나에 (AI 채팅과 같은 모양, 동작은 그대로) -->
    <section class="addcard lp-panel" aria-label="근처 찾기">
      <header class="lp-head">
        <span class="lp-avatar lp-avatar--near" aria-hidden="true"><i class="ic ic--s i-search"></i></span>
        <b class="lp-head__title">근처 찾기</b>
        <button type="button" class="iconbtn" aria-label="근처 찾기 닫기" @click="emit('close')"><i class="ic i-close muted" aria-hidden="true"></i></button>
      </header>

      <!-- 고른 장소 카드 -->
      <div v-if="picked" class="lp-pick">
        <div class="lp-place">
          <img class="lp-place__img" :src="photoFor(picked)" alt="" />
          <div class="lp-place__body">
            <b class="lp-place__name">{{ picked.name }}</b>
            <p class="lp-place__meta tn">
              <template v-if="picked.rating != null"><i class="ic ic--s i-star c-warning" aria-hidden="true"></i> {{ picked.rating }} · </template>{{ picked.category || '장소' }}
            </p>
          </div>
          <button type="button" class="iconbtn" aria-label="목록으로" @click="emit('update:pickedId', null)"><i class="ic i-close muted" aria-hidden="true"></i></button>
        </div>
        <p class="lp-place__dist tn">지금 위치에서 {{ distText(picked.distance) }}</p>
        <div class="lp-actions">
          <button type="button" class="btn btn--secondary btn--md" @click="router.push('/place?id=' + encodeURIComponent(picked.id))">자세히</button>
          <span v-if="planned.includes(picked.id)" class="lp-card__planned"><i class="ic ic--s i-check" aria-hidden="true"></i>일정에 있어요</span>
          <button v-else type="button" class="btn btn--primary btn--md" @click="addPicked">
            <i class="ic ic--s i-plus" aria-hidden="true"></i>일정에 넣기
          </button>
        </div>
      </div>

      <!-- 찾은 뒤: 반경 고르기 -->
      <template v-else-if="word">
        <div class="lp-radius">
          <span class="lp-radius__k">현위치에서</span>
          <div class="lp-chips">
            <button v-for="r in RADIUS_LIST" :key="r" type="button" class="chip chip--sm" :class="{ 'is-on': radius === r }" :aria-pressed="radius === r" @click="setRadius(r)">
              {{ r < 1000 ? r + 'm' : '1km' }}
            </button>
          </div>
        </div>
        <p class="lp-status tn">
          <template v-if="isLoading"><i class="ic ic--s i-search" aria-hidden="true"></i>'{{ word }}' 찾는 중…</template>
          <template v-else-if="error"><i class="ic ic--s i-warning c-warning" aria-hidden="true"></i>{{ error }}</template>
          <template v-else-if="!found.length"><i class="ic ic--s i-info" aria-hidden="true"></i>'{{ word }}' 결과가 없어요 · 카페, 음식점처럼 찾아보세요</template>
          <template v-else-if="results.length"><i class="ic ic--s i-pin c-navy" aria-hidden="true"></i>'{{ word }}' {{ results.length }}곳 · 지도에서 핀을 눌러 보세요</template>
          <template v-else><i class="ic ic--s i-info" aria-hidden="true"></i>이 반경엔 없어요 · 가장 가까운 곳이 {{ distText(found[0].distance) }}예요</template>
        </p>
      </template>

      <!-- 처음: 안내 + 빠른 검색어 -->
      <template v-else>
        <p class="lp-hint">찾고 싶은 곳을 적어 주세요. 지금 위치 주변에서 찾아 지도에 핀으로 보여 드려요.</p>
        <div class="lp-chips">
          <button v-for="w in ['카페', '음식점', '술집']" :key="w" type="button" class="chip chip--sm" @click="search(w)">{{ w }}</button>
        </div>
      </template>

      <form class="lp-form" @submit.prevent="search(draft)">
        <label for="add-q" class="sr">찾을 곳</label>
        <input id="add-q" v-model="draft" type="search" placeholder="카페, 음식점처럼 적어 주세요" autocomplete="off" />
        <button type="submit" class="lp-send" :class="{ 'is-empty': !draft.trim() }" aria-label="찾기"><i class="ic ic--s i-search" aria-hidden="true"></i></button>
      </form>
    </section>
  </div>
</template>
