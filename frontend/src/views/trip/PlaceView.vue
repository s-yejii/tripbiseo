<script setup>
// S12 장소 상세 (②): 사진, 별점, 보여 준 이유, 영업시간·브레이크 타임, 주소, 머무는 시간, 일정에 담기
// 주소: /place?id=daelim (시연용 가짜 장소) 또는 /place?id={DB 장소 id} (서버 GET /api/places/{id})
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AddToTripSheet from '@/components/trip/AddToTripSheet.vue'
import { places } from '@/data/places'
import { concertTrip } from '@/data/trips'
import { getPlaceDetail, getPlacePhotos } from '@/api/trips'
import { useTripStore } from '@/stores/trip'
import { imageFor } from '@/utils/mapView'
import { goBack } from '@/utils/back'
import { toDuration } from '@/utils/time'
import { showToast } from '@/utils/toast'
import { kindOf } from '@/utils/budget'

// 종류별 보통 머무는 분 (PlanRuleMaker와 같은 값)
const STAY_MIN_OF_KIND = { food: 70, cafe: 60, bar: 90 }
const OTHER_STAY_MIN = 80

const route = useRoute()
const router = useRouter()
const store = useTripStore()
const isFake = !!places[route.query.id] || !route.query.id
const id = places[route.query.id] ? route.query.id : 'daelim'
const server = ref(null) // 서버 장소 상세 (화면이 쓰는 모양으로 바꾼 것)
const place = computed(() => server.value || places[id])
// 가짜 장소만 여기서 확인. 실제 장소는 담을 일정을 고르는 시트 안에서 일정마다 확인
const inPlan = isFake && concertTrip.stops.some((stop) => stop.id === id)
const saved = ref(false)
const isSheetOpen = ref(false) // "어디에 담을까요?" 시트

// 진짜 사진 찾기. 실패해도 상세는 보여야 해서 여기서만 잡고 빈 목록으로
async function findPhotos(placeId) {
  try {
    return await getPlacePhotos([placeId])
  } catch {
    return []
  }
}

onMounted(async () => {
  if (isFake) return
  try {
    // 상세와 사진을 같이 불러서 기다리는 시간을 줄임
    const [detail, photos] = await Promise.all([getPlaceDetail(route.query.id), findPhotos(route.query.id)])
    const photo = photos[0] // 찾은 진짜 사진 첫 장 (없으면 undefined)
    const today = detail.hours.find((hours) => hours.today)
    server.value = {
      ...detail,
      cat: detail.category || '',
      img: (photo && photo.imageUrl) || detail.imageUrl || imageFor(detail.category), // 찾은 사진 → DB 사진 → 카테고리 기본 그림
      photoSite: photo ? photo.siteName : null, // 사진 출처 (블로그 이름). DB 사진이면 null
      rate: detail.rating ?? '-',
      hours: today ? today.text : detail.useTime,
      dur: STAY_MIN_OF_KIND[kindOf(detail.category)] || OTHER_STAY_MIN,
      outdoor: false,
    }
  } catch (e) {
    showToast(e.message)
  }
})

// 네이버 지도 주소: 서버 값(DB 네이버 주소, 없으면 네이버 검색 링크), 시연용 가짜 장소는 이름으로 검색
const naverLink = computed(() => place.value.naverUrl || 'https://map.naver.com/p/search/' + encodeURIComponent(place.value.name))
const addressText = computed(() => place.value.address || '성내동 322-32')

// 글자를 클립보드에 복사. http 주소나 권한 없는 브라우저에서는 navigator.clipboard가 없어서 옛날 방식으로
async function copyText(text, doneMessage) {
  try {
    await navigator.clipboard.writeText(text)
  } catch {
    const box = document.createElement('textarea')
    box.value = text
    document.body.appendChild(box)
    box.select()
    const isCopied = document.execCommand('copy')
    box.remove()
    if (!isCopied) return showToast('복사하지 못했어요: ' + text)
  }
  showToast(doneMessage)
}

function toggleSave() {
  saved.value = !saved.value
  showToast(saved.value ? '저장했어요' : '저장을 취소했어요')
}
function addToPlan() {
  if (isFake) {
    // 시연용 가짜 장소는 예전 동작 그대로 (일정 점검에서 들어와서 뒤로 가면 거기로 돌아감)
    showToast(place.value.name + '을(를) 동선에 맞춰 넣었어요')
    return goBack('/plan/check')
  }
  // 실제 장소: 시트의 "새 여행 만들기"
  store.addDraftPlace(server.value)
  store.draft.stops[store.draft.stops.length - 1].stay = server.value.dur // store는 늘 60분으로 넣어서 방금 넣은 칸(맨 끝)만 고침
  showToast(place.value.name + '을(를) 새 여행에 넣었어요')
  router.push('/plan/check') // goBack은 앱 안에서 왔으면 전 화면(홈 등)으로 가서, 담은 뒤엔 꼭 일정 점검으로
}

// [일정에 담기]: 가짜 장소는 지금처럼 바로 담고, 실제 장소는 어디에 담을지 고르는 시트를 엶
function onAddClick() {
  if (isFake) return addToPlan()
  if (!server.value) return showToast('장소 정보를 불러오지 못했어요')
  isSheetOpen.value = true
}
</script>

<template>
  <div class="scr scr-Place">
    <div class="app app--white">
      <img :src="place.img" :alt="place.name + ' 사진'" class="pl-photo" />
      <span v-if="place.photoSite" class="pl-credit">사진: {{ place.photoSite }}</span>
      <header class="topbar topbar--float">
        <button type="button" class="iconbtn iconbtn--float" aria-label="뒤로가기" @click="goBack('/')"><i class="ic ic--l i-back" aria-hidden="true"></i></button>
        <span style="flex: 1"></span>
        <button type="button" class="iconbtn iconbtn--float" :class="{ 'is-bm': saved }" :aria-label="saved ? '저장 취소' : '저장'" :aria-pressed="saved" @click="toggleSave"><i class="ic i-bookmark" aria-hidden="true"></i></button>
        <button type="button" class="iconbtn iconbtn--float" aria-label="가게 링크 복사" title="가게 링크 복사" @click="copyText(naverLink, '가게 링크를 복사했어요')"><i class="ic i-link" aria-hidden="true"></i></button>
      </header>

      <div class="scroll" style="top: 300px; padding-top: 16px; gap: 16px; bottom: 102px">
        <div style="display: flex; flex-direction: column; gap: 4px">
          <p class="t-cap">{{ place.cat }}{{ isFake ? ' · 천호동' : '' }}</p>
          <h1 class="h-display">{{ place.name }}</h1>
          <p style="margin: 0; display: flex; align-items: center; gap: 8px">
            <span class="t-strong tn" style="display: inline-flex; align-items: center; gap: 2px"><i class="ic ic--s i-star c-warning" aria-hidden="true"></i>{{ place.rate }}</span>
            <span v-if="isFake" class="badge badge--success"><i class="ic ic--s i-check" aria-hidden="true"></i>10/24 영업</span>
            <span v-else-if="place.openNow === true" class="badge badge--success"><i class="ic ic--s i-check" aria-hidden="true"></i>지금 영업 중</span>
            <span v-else-if="place.openNow === false" class="badge badge--neutral">지금 영업 전·후</span>
            <span v-if="place.nopo" class="badge badge--navy">노포 · 40년</span>
          </p>
          <a class="pl-naver" :href="naverLink" target="_blank" rel="noopener noreferrer" aria-label="네이버에서 리뷰·메뉴 보기 (새 창)">
            <span class="pl-naver__mark" aria-hidden="true">N</span>리뷰·메뉴 보기<i class="ic i-next" aria-hidden="true"></i>
          </a>
        </div>

        <div class="group">
          <div class="row">
            <i class="ic i-clock muted" aria-hidden="true"></i>
            <span class="row__body">
              <span class="t-strong tn">{{ isFake ? '10/24(토) ' : '오늘 ' }}{{ place.hours || (isFake ? '언제나 열려 있어요' : '영업시간 정보 없음') }}</span>
              <span v-if="place.brk" class="row__meta c-warning">브레이크 타임 {{ place.brk }} · 이 시간은 피해서 넣어요</span>
              <span v-else-if="isFake" class="row__meta">브레이크 타임 없음 · 휴무 없음</span>
              <span v-else-if="place.restDate" class="row__meta">휴무 {{ place.restDate }}</span>
            </span>
          </div>
          <div class="row">
            <i class="ic i-pin muted" aria-hidden="true"></i>
            <span class="row__body"><span class="t-strong">{{ addressText }}</span><span v-if="isFake" class="row__meta">천호역 도보 8분</span></span>
            <button type="button" class="btn btn--ghost btn--sm" @click="copyText(addressText, '주소를 복사했어요')">복사</button>
          </div>
          <div class="row">
            <i class="ic i-timer muted" aria-hidden="true"></i>
            <span class="row__body"><span class="t-strong tn">보통 {{ toDuration(place.dur) }} 머물러요</span></span>
          </div>
        </div>
      </div>

      <div class="cta cta--white">
        <button type="button" class="btn btn--lg btn--block" :class="inPlan ? 'btn--secondary' : 'btn--primary'" :disabled="inPlan" @click="onAddClick">
          <i class="ic" :class="inPlan ? 'i-check' : 'i-plus'" aria-hidden="true"></i>{{ inPlan ? '일정에 담겨 있어요' : '일정에 담기' }}
        </button>
      </div>

      <AddToTripSheet v-if="isSheetOpen" :place="server" @close="isSheetOpen = false" @new-trip="addToPlan" />
    </div>
  </div>
</template>

<style>
.scr-Place .pl-photo{position:absolute;top:0;left:0;width:100%;height:300px;object-fit:cover}
.scr-Place .pl-credit{position:absolute;right:16px;top:268px;max-width:60%;padding:2px 8px;border-radius:8px;background:rgba(24,33,58,.7);color:#fff;font-size:11px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.scr-Place .why{display:flex;gap:10px;padding:12px;border-radius:var(--radius-md);background:var(--navy-soft)}
.scr-Place .why>span{display:flex;flex-direction:column;gap:6px}
.scr-Place .why__tags{display:flex;flex-wrap:wrap;gap:6px}
.scr-Place .why__tags .badge{background:#fff}
.scr-Place .is-bm{color:var(--coral)}
.scr-Place .pl-naver{align-self:flex-start;display:inline-flex;align-items:center;gap:8px;height:36px;margin-top:4px;padding:0 12px 0 8px;border:1px solid var(--line);border-radius:999px;background:#fff;font:inherit;font-size:13px;font-weight:600;color:var(--ink);cursor:pointer}
.scr-Place .pl-naver:hover{border-color:#03C75A;background:#F2FBF5}
.scr-Place .pl-naver__mark{width:20px;height:20px;border-radius:5px;background:#03C75A;color:#fff;font-size:12px;font-weight:800;display:flex;align-items:center;justify-content:center}
.scr-Place .pl-naver .ic{width:14px;height:14px;color:var(--ink-muted)}
</style>
