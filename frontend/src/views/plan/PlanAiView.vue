<script>
// 새로고침 뒤 되살리기는 페이지를 연 뒤 한 번만 (이 화면을 다시 들어와도 또 되살리지 않게)
let isRestoreChecked = false
</script>

<script setup>
// S06 여행 정보 입력 (③): 한 화면에 하나씩 묻고 "이전"·"다음"으로 넘겨요 (11/04: 긴 스크롤 → 단계별, 11/06: 건너뛰기 버튼 없앰)
// 주소: /plan/info (AI로 짜기) · /plan/info?method=me (직접 고르기)
//   순서: 가고 싶은 곳(인스타 링크) → 어디로 → 언제 → 몇 시부터 몇 시까지 → 어디서 출발 → 누구랑 → 어디 위주 → 이런 곳 → 무엇으로 → 꼭 지킬 시간
//   인스타로 담으면 그 장소의 구가 지역으로 자동 선택돼서 "어디로"를 건너뛰어요 (WishLinks)
//   꼭 알려 줘야 하는 건 지역 하나. 나머지는 안 고르고 "다음"을 눌러도 되고, 이동 수단은 안 고르면 지하철·도보·버스
//   지금 칸·고른 값은 저장소(store)에 둬서 장소 검색에 다녀와도 그대로예요
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import RangeCalendar from '@/components/plan/RangeCalendar.vue'
import WishLinks from '@/components/plan/WishLinks.vue'
import { getAreaStats, getRecommendedLinks } from '@/api/trips'
import { useTripStore } from '@/stores/trip'
import { addDays, daysBetween, toMonthDay, todayText } from '@/utils/dates'
import TripWeather from '@/components/plan/TripWeather.vue'
import { goBack } from '@/utils/back'
import { imageFor } from '@/utils/mapView'
import GuMapPicker from '@/components/plan/GuMapPicker.vue'
import DongPicker from '@/components/plan/DongPicker.vue'
import OriginPicker from '@/components/plan/OriginPicker.vue'
import ChoiceCard from '@/components/plan/ChoiceCard.vue'
import TimeRangePicker from '@/components/plan/TimeRangePicker.vue'
import VerifiedBadge from '@/components/common/VerifiedBadge.vue'
import { KIND_CHIPS, MOVE_CODES, TAG_CHIPS } from '@/utils/styleCodes'
// 서비스 아이콘: Simple Icons (CC0, 상표는 각 회사 소유). 그 서비스 바로가기에만 써요
import youtubeLogo from '@/assets/brand/youtube.svg'
import instagramLogo from '@/assets/brand/instagram.svg'
import naverLogo from '@/assets/brand/naver.svg'
import { toShortDate } from '@/utils/tripView'
import { showToast } from '@/utils/toast'
import { recommendTimeFor } from '@/utils/timeRecommend'
import { appNow, devMode } from '@/utils/devMode'
import { getDevScenario } from '@/api/dev'

const WORKSHOP_MIN_PEOPLE = 4 // 워크숍을 고르면 인원은 적어도 이만큼
const DEFAULT_MOVES = ['지하철', '도보', '버스']
const STEPS = [
  { id: 'link', title: '가고 싶은 곳이 있나요?', sub: '링크로 넣어보세요' },
  { id: 'where', title: '어디로 가요?', sub: '', isRequired: true },
  { id: 'dong', title: '어느 동이 좋아요?', sub: '안 고르면 구 전체' },
  { id: 'when', title: '언제 가요?', sub: '' },
  { id: 'time', title: '몇 시부터 몇 시까지 놀까요?', sub: '' },
  { id: 'from', title: '어디서 출발해요?', sub: '' },
  { id: 'who', title: '누구랑 가요?', sub: '' },
  { id: 'kind', title: '어디 위주로 갈까요?', sub: '밥·카페는 알아서 넣어요' },
  { id: 'tag', title: '이런 곳이면 더 좋아요', sub: '' },
  { id: 'move', title: '무엇으로 다녀요?', sub: '안 고르면 지하철·도보·버스' },
  { id: 'fixed', title: '예약한 곳이 있나요?', sub: '' },
]

const TOP_GU_COUNT = 4 // 지도에 옅게 표시할 구 수

const route = useRoute()
const router = useRouter()
const byMe = route.query.method === 'me'
const store = useTripStore()

// 11/13: 새로고침해도 고른 값이 남게. 고를 때마다 이 탭에만 저장(sessionStorage)하고,
//   이 화면(또는 만드는 중 화면)을 새로고침했을 때만 되살려요. 새 여행을 시작할 때는 되살리지 않아요
const SAVE_KEY = 'tripbiseo.planInfo'
const SAVED_FIELDS = ['planArea', 'planAreas', 'planStartDate', 'planEndDate', 'planFromTime', 'planToTime', 'planOrigin', 'planStays',
  'planKinds', 'planTags', 'planPeople', 'planCompanion', 'planMoves', 'planStep', 'planConditions']
restoreAfterReload()
watch(() => SAVED_FIELDS.map((field) => store[field]), savePlanInfo, { deep: true })

function savePlanInfo() {
  const data = {}
  for (const field of SAVED_FIELDS) data[field] = store[field]
  try {
    sessionStorage.setItem(SAVE_KEY, JSON.stringify(data))
  } catch (e) {
    // 저장이 막힌 브라우저면 그냥 넘어감 (새로고침하면 처음부터)
  }
}

function restoreAfterReload() {
  if (isRestoreChecked) return
  isRestoreChecked = true
  const page = performance.getEntriesByType('navigation')[0] // 이 페이지를 처음 연 방법 (새로고침이면 type이 'reload')
  if (!page || page.type !== 'reload') return
  if (!page.name.includes('/plan/info') && !page.name.includes('/plan/generating')) return
  try {
    const data = JSON.parse(sessionStorage.getItem(SAVE_KEY))
    if (!data) return
    for (const field of SAVED_FIELDS) {
      if (field in data) store[field] = data[field]
    }
  } catch (e) {
    // 저장한 값이 깨졌으면 처음부터
  }
}

const step = computed(() => STEPS[store.planStep] || STEPS[0])
const isLast = computed(() => store.planStep === STEPS.length - 1)
const progress = computed(() => Math.round(((store.planStep + 1) / STEPS.length) * 100) + '%')
// 다음 버튼: 어디로는 지역을 고른 뒤에. 출발지는 안 골라도 돼요 (그러면 첫 장소에서 시작)
const canNext = computed(() => !step.value.isRequired || !!store.planArea)

// 1단계: 플랫폼 검색 바로가기 (새 창). 이 칸에선 아직 지역을 안 골라서 검색어는 늘 서울로 고정 (지난번 지역이 저장소에 남아 있어요)
const YOUTUBE_WORD = '서울 핫플 브이로그'
const NAVER_WORD = '서울 가볼만한곳'
const INSTAGRAM_ACCOUNT_URL = 'https://www.instagram.com/yeohaenge_ottae/' // 여행어때 인스타그램 계정 (모든 계정 같음)
const SEARCH_LINKS = [
  { key: 'mukeul', label: '먹을텐데', caption: '착착트립 파트너스', isVerified: true, img: '/img/shortcut/mukeul.jpg', text: '유튜브에서 먹을텐데 서울 찾기', url: () => 'https://www.youtube.com/results?search_query=' + encodeURIComponent('성시경 먹을텐데 서울') },
  { key: 'youtube', label: 'YouTube', logo: youtubeLogo, cls: 'pa-sns--yt', url: () => 'https://www.youtube.com/results?search_query=' + encodeURIComponent(YOUTUBE_WORD) },
  { key: 'instagram', label: 'Instagram', labelCls: 'pa-sns__script', logo: instagramLogo, cls: 'pa-sns--ig', url: () => INSTAGRAM_ACCOUNT_URL }, // 11/08 주형: 인스타는 우리 계정 페이지로
  { key: 'naver', label: '네이버', logo: naverLogo, cls: 'pa-sns--nv', url: () => 'https://search.naver.com/search.naver?query=' + encodeURIComponent(NAVER_WORD) }, // 통합검색: 플레이스 지도·사진 목록이 나와요
  // 캐치테이블: 아이콘은 공식 사이트 favicon. 주소는 캐치테이블 웹에서 "서울"을 검색했을 때 뜨는 지도 + 가게 목록 화면
  { key: 'catchtable', label: '캐치테이블', img: '/img/shortcut/catchtable.png', text: '캐치테이블에서 서울 가게 찾기', url: () => 'https://app.catchtable.co.kr/ct/map/search-map?showTabs=true&bottomSheetHeightType=PARTIAL_MAP&serviceType=INTEGRATION&keyword=' + encodeURIComponent('서울') + '&keywordSearch=' + encodeURIComponent('서울') + '&isInitSearch=1' },
  { key: 'blackwhite', label: '흑백요리사', img: '/img/shortcut/blackwhite.jpg', isTitle: true, text: '네이버에서 흑백요리사 찾기', url: () => 'https://search.naver.com/search.naver?query=' + encodeURIComponent('흑백요리사') },
  { key: 'michelin', label: '미쉐린 가이드', img: '/img/shortcut/michelin.png', isLogo: true, text: '미쉐린 가이드 사이트 열기', url: () => 'https://guide.michelin.com/kr/ko' },
]
const PLATFORM_LABEL = { instagram: '인스타', youtube: 'YouTube', naver: '네이버' }
const recommended = ref([]) // 추천 게시물 (없으면 칸을 숨김)
const wishLinks = ref(null)
// 추천 게시물은 링크 칸에 넣어 바로 장소를 찾아요
function openRecommended(item) {
  if (wishLinks.value) wishLinks.value.fill(item.url)
  else window.open(item.url, '_blank', 'noopener')
}
watch(() => step.value.id, async (id) => {
  if (id !== 'link' || recommended.value.length) return
  try {
    recommended.value = await getRecommendedLinks()
  } catch {
    recommended.value = []
  }
}, { immediate: true })

// 리뷰 많은 구 (GET /api/plans/areas 종류를 합쳐 상위 몇 곳). 지도에 옅게 표시. 실패하면 표시 없음
const areaStats = ref([])
const topGus = computed(() => {
  const reviews = {}
  for (const area of areaStats.value) reviews[area.name] = (reviews[area.name] || 0) + area.reviewCount
  return Object.keys(reviews).sort((a, b) => reviews[b] - reviews[a]).slice(0, TOP_GU_COUNT)
})
watch(() => step.value.id, loadAreas, { immediate: true })
async function loadAreas(id) {
  if (id !== 'where' || areaStats.value.length) return
  try {
    areaStats.value = await getAreaStats()
  } catch {
    areaStats.value = []
  }
}
// 지도에서 구를 고름: 첫 구를 planArea로 (다음 단계·제목 등 기존 흐름이 그대로 쓰게)
function setAreas(areas) {
  store.planAreas = areas
  const first = areas[0]
  store.planArea = first ? { name: first.name, fullName: '서울 ' + first.name, type: 'gu', lat: first.lat, lng: first.lng } : null
}

// 꼭 지킬 시간: 장소 검색·예약 캡처가 store.planConditions.fixed 에 넣은 { name, time } 목록
const fixedList = computed(() => store.planConditions?.fixed || [])

const companions = [
  { name: '혼자', icon: 'i-user', desc: '내 속도대로' },
  { name: '친구', icon: 'i-users', desc: '같이 놀기' },
  { name: '연인', icon: 'i-gift', desc: '둘이서' },
  { name: '가족', icon: 'i-home', desc: '다 같이' },
  { name: '아이와', icon: 'i-star', desc: '아이와 함께' },
  { name: '워크숍', icon: 'i-flag', desc: '회사 팀과' }, // 11/06: 단체석·회식 맞춤
]
// 큰 선택 카드: 코드는 styleCodes 그대로, 아이콘·한 줄 설명만 더해요
const CARD_LOOK = {
  food: ['i-food', '밥집 위주'], cafe: ['i-cup', '쉬어 가기'], bar: ['i-glass', '저녁 한잔'], tour: ['i-walk', '걷고 구경'], culture: ['i-album', '전시·공연'], shop: ['i-gift', '구경·쇼핑'],
  mood: ['i-star', '사진·데이트'], quiet: ['i-cloud', '붐비지 않게'], value: ['i-won', '부담 없이'], no_wait: ['i-timer', '바로 입장'], solo: ['i-user', '혼자도 편하게'],
}
const withLook = (chip) => ({ ...chip, icon: CARD_LOOK[chip.code][0], desc: CARD_LOOK[chip.code][1] })
const KIND_COLORS = { food: '#FF7A59', cafe: '#C8915B', bar: '#B4436C', tour: '#2FA67A', culture: '#7B61FF', shop: '#2F80ED' } // 어디 위주로: 종류마다 고유 색
const KIND_CARDS = KIND_CHIPS.map((chip) => ({ ...withLook(chip), color: KIND_COLORS[chip.code] }))
const TAG_CARDS = TAG_CHIPS.map(withLook)
const CENTER_STEPS = ['time', 'who', 'kind', 'tag', 'move', 'fixed'] // 질문을 화면 가운데로 모으는 칸
const isCentered = computed(() => CENTER_STEPS.includes(step.value.id))
const moveList = [
  { label: '지하철', icon: 'i-subway', desc: '역 따라 이동' },
  { label: '도보', icon: 'i-walk', desc: '가까우면 걸어서' },
  { label: '버스', icon: 'i-bus', desc: '정류장 따라 이동' },
  { label: '자동차', icon: 'i-car', desc: '직접 운전' },
]

// 여행 날짜는 저장소(store.planStartDate · planEndDate)에 둬요 → 숙소 화면이 이 기간 안으로만 고르게 해요
const nights = computed(() => (store.planEndDate ? daysBetween(store.planStartDate, store.planEndDate) : 0))
// 달력 밑 날씨 미리 보기: 고른 날(최대 3일), 고른 지역 가운데 (없으면 서울시청)
const WEATHER_PREVIEW_DAYS = 3
const SEOUL_CENTER = { lat: 37.5665, lng: 126.978 }
const weatherSpot = computed(() => (store.planArea && store.planArea.lat != null ? { lat: store.planArea.lat, lng: store.planArea.lng } : SEOUL_CENTER))
const weatherDays = computed(() => {
  if (!store.planStartDate) return []
  const list = []
  const lastForecast = addDays(todayText(), WEATHER_PREVIEW_DAYS) // 기상청 단기예보는 오늘부터 3일 뒤까지
  for (let i = 0; i <= Math.min(nights.value, WEATHER_PREVIEW_DAYS - 1); i++) {
    const date = addDays(store.planStartDate, i)
    if (date <= lastForecast) list.push(date) // 예보가 없는 날은 날마다 같은 안내를 반복하지 않게 뺌 (11/07 문구 점검)
  }
  return list
})
const isWeatherLater = computed(() => !!store.planStartDate && weatherDays.value.length === 0)
const whenLabel = computed(() => (nights.value ? `${nights.value}박 ${nights.value + 1}일 · ${toShortDate(store.planStartDate)}–${toShortDate(store.planEndDate)}` : `당일치기 · ${toShortDate(store.planStartDate)}`))
// 여행 날짜를 바꿔서 기간 밖으로 나간 숙소 (목록에 "고쳐 주세요" 표시)
function isOutside(stay) {
  const tripEnd = store.planEndDate || store.planStartDate
  return stay.checkInDate < store.planStartDate || stay.checkOutDate > tripEnd
}

// 시작·끝 시간 기본값: 오늘이면 지금+30분을 30분 단위로 올린 시각부터(22:00 넘으면 내일), 다른 날이면 10:00~20:00
const TIME_STEP_MIN = 30
const MIN_SPAN_MIN = 120
const LAST_START_MIN = 22 * 60
const LAST_END_MIN = 24 * 60
const DEFAULT_END_MIN = 20 * 60
const LATE_NOTE = '오늘은 늦어서 내일로 바꿨어요'
const timeNote = ref('')
let isMovingDate = false // 늦어서 날짜를 내일로 바꾸는 중 (이때는 고른 시각을 그대로 둠)
const toMinutes = (time) => Number(time.slice(0, 2)) * 60 + Number(time.slice(3))
const toTime = (minutes) => String(Math.floor(minutes / 60)).padStart(2, '0') + ':' + String(minutes % 60).padStart(2, '0')
function dateText(date) {
  return date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0') + '-' + String(date.getDate()).padStart(2, '0')
}
// 지금: 시연 계정은 시연 시계(예: 오늘 15:00), 아니면 진짜 시계 (11/08: 밤에 연습하면 "늦어서 내일로"가 뜨던 문제)
const appToday = () => (devMode.isOn ? appNow() : new Date())
const isToday = () => store.planStartDate === dateText(appToday())
function nowMinutes() {
  const now = appToday()
  return now.getHours() * 60 + now.getMinutes()
}
// 날짜를 내일로: 끝 날짜가 내일보다 앞이거나 같으면 당일치기로
function moveToTomorrow() {
  const tomorrow = addDays(dateText(appToday()), 1)
  isMovingDate = true
  store.planStartDate = tomorrow
  if (store.planEndDate && store.planEndDate <= tomorrow) store.planEndDate = null
  timeNote.value = LATE_NOTE
}
function setDefaultTimes() {
  if (isToday()) {
    const start = Math.ceil((nowMinutes() + TIME_STEP_MIN) / TIME_STEP_MIN) * TIME_STEP_MIN
    if (start <= LAST_START_MIN) {
      store.planFromTime = toTime(start)
      store.planToTime = toTime(Math.min(LAST_END_MIN, Math.max(DEFAULT_END_MIN, start + MIN_SPAN_MIN)))
      return
    }
    moveToTomorrow()
  }
  store.planFromTime = '10:00'
  store.planToTime = toTime(DEFAULT_END_MIN)
}
// 달력에서 날짜를 바꾸면 기본값으로 (늦어서 자동으로 옮긴 건 빼고)
watch(() => store.planStartDate, () => {
  if (isMovingDate) {
    isMovingDate = false
    return
  }
  timeNote.value = ''
  setDefaultTimes()
})
// 오늘인데 이미 지난 시작 시각을 고르면 내일로
//   시연 계정은 빼요: 시나리오 출발 15:00을 [시간 추천]으로 넣을 때 시연 시계가 15:07이면 첫날이 11/11로 밀리던 문제 (11/09)
watch(() => store.planFromTime, (time) => {
  if (scenario.value) return
  if (time && isToday() && toMinutes(time) < nowMinutes()) moveToTomorrow()
})
if (!store.planFromTime) setDefaultTimes()

function changePeople(diff) {
  store.planPeople = Math.max(1, Math.min(10, store.planPeople + diff))
  if (store.planPeople === 1) store.planCompanion = '혼자'
  else if (store.planCompanion === '혼자') store.planCompanion = null // 2명 이상이면 누구랑인지 다시 고르게
}
function pickCompanion(name) {
  store.planCompanion = name
  const fewest = name === '워크숍' ? WORKSHOP_MIN_PEOPLE : 2
  store.planPeople = name === '혼자' ? 1 : Math.max(fewest, store.planPeople)
  // 11/09: 고르자마자 넘기지 않음 (같은 화면에서 인원도 맞추고 [다음]으로 넘어가요)
}
// 담은 숙소의 박 수 합 (체크인~체크아웃 날짜 차이). 여행 박 수만큼 다 담으면 "숙소 더 담기"를 숨김
const stayNights = computed(() => {
  let total = 0
  for (const stay of store.planStays) total += daysBetween(stay.checkInDate, stay.checkOutDate)
  return total
})
// 일차 칸 글: 위 "2일차"(당일치기면 "당일"), 아래 "12/5"
function dayTileOf(day) {
  const index = (day || 1) - 1
  return { title: nights.value ? index + 1 + '일차' : '당일', date: toMonthDay(addDays(store.planStartDate, index)) }
}
// 꼭 지킬 시간 빼기 (일정 만들기 조건과 직접 고르기 초안에서 같이)
// 예약 하나만 빼기 (11/13: 이름으로만 지워서 같은 이름 예약이 둘이면 X 하나에 둘 다 빠지던 문제)
function removeFixed(index) {
  const target = fixedList.value[index]
  store.planConditions = { ...store.planConditions, fixed: fixedList.value.filter((item, i) => i !== index) }
  const stopIndex = store.draft.stops.findIndex((stop) => stop.fixed && stop.name === target.name && stop.time === target.time)
  if (stopIndex >= 0) store.draft.stops.splice(stopIndex, 1)
}
// 칩 켜고 끄기 (코드 목록에 넣거나 빼기)
function toggleCode(list, code) {
  return list.includes(code) ? list.filter((item) => item !== code) : [...list, code]
}

// 여기서 고른 값을 일정 만들기 조건(store.planConditions)으로 → 대화(③ chat)가 이어서 채우고, 만들기(③ generate)가 씀
function saveConditions() {
  if (store.planMoves.length === 0) store.planMoves = [...DEFAULT_MOVES] // 안 고르면 대중교통 + 도보
  const transports = []
  for (const m of moveList) {
    if (store.planMoves.includes(m.label)) transports.push(MOVE_CODES[m.label])
  }
  const area = store.planArea
  const origin = store.planOrigin
  if (byMe) store.planStartTime = store.planFromTime // 직접 고르기도 이 시각부터
  store.planConditions = {
    ...(store.planConditions || {}),
    region: area ? area.fullName : null,
    areas: store.planAreas.map((item) => ({ name: item.name, lat: item.lat, lng: item.lng, dongs: item.dongs.map((dong) => ({ name: dong.name, geometry: dong.geometry })) })),
    areaLat: area?.lat ?? null,
    areaLng: area?.lng ?? null,
    areaType: area?.type ?? null,
    startDate: store.planStartDate,
    endDate: store.planEndDate || store.planStartDate,
    startTime: store.planFromTime,
    endTime: store.planToTime,
    people: store.planPeople,
    companion: store.planCompanion, // 누구랑: 서버가 머무는 시간·리뷰 확인에 씀 (11/06)
    transports,
    kinds: store.planKinds,
    tags: store.planTags,
    originName: origin?.name ?? null,
    originLat: origin?.lat ?? null,
    originLng: origin?.lng ?? null,
    stays: store.planStays,
  }
}

// 한 칸 앞뒤로. 지도에서 구를 안 골랐으면 동 칸은 건너뜀
function moveStep(direction) {
  let next = store.planStep + direction
  if (STEPS[next] && STEPS[next].id === 'dong' && !store.planAreas.length) next += direction
  store.planStep = next
  // 11/13: 단계를 주소(?step=)에도 적어서 폰·브라우저 뒤로가기가 앞 단계로 가게. 앞으로 갈 때만 기록을 쌓고, 뒤로 갈 때는 바꾸기만
  const query = { ...route.query, step: String(next) }
  if (direction > 0) router.push({ query })
  else router.replace({ query })
}

// 브라우저 뒤로·앞으로로 주소의 단계가 바뀌면 화면 단계도 맞춤
watch(() => route.query.step, (value) => {
  if (route.path !== '/plan/info') return
  const stepIndex = Number(value || 0)
  if (stepIndex !== store.planStep) store.planStep = stepIndex
})

// 다음 칸으로 (이미 고른 지역 칸은 건너뜀). 마지막 칸이면 바로 일정 만들기로
// (11/06: 단계에서 다 물어봐서 "원하는 것 말하기" 대화 화면은 흐름에서 뺌. 화면 파일·주소는 남겨 둠)
function goNext() {
  if (!canNext.value) return showToast('어디로 가는지 알려 주세요')
  if (isLast.value) {
    saveConditions()
    if (!byMe) store.planMessages = [] // 예전 대화가 남아 있으면 일정 만들기에 섞이지 않게
    router.push(byMe ? '/plan/manual' : '/plan/generating')
    return
  }
  moveStep(1)
}
function goPrev() {
  if (store.planStep === 0) return goBack('/new')
  moveStep(-1)
}
// 인스타로 담으면: 담은 곳의 구를 지도에도 미리 골라 두고 "어디로" 단계로 (11/13: 예전엔 "어디로·동"을 건너뛰어서 지역을 확인할 수 없었어요)
function onLinkAdded() {
  const area = store.planArea
  if (area && area.isAuto && !store.planAreas.length) {
    store.planAreas = [{ name: area.name, lat: area.lat, lng: area.lng, dongs: [] }] // 지도(GuMapPicker)가 쓰는 모양
  }
  store.planStep = STEPS.findIndex((item) => item.id === 'where')
}
// 검색 화면으로 갈 때 표시해 두면, 고르고 돌아왔을 때 바로 다음 칸으로
function markAutoNext() {
  store.planAutoNext = true
}
onMounted(() => {
  if (!store.planAutoNext) return
  store.planAutoNext = false
  const isPicked = (step.value.id === 'where' && store.planArea) || (step.value.id === 'from' && store.planOrigin)
  if (isPicked) moveStep(1)
})
// 시연 계정(개발자 모드): 발표 이야기대로 출발지(11/12 종로3가역)를 미리 채워 둠 (11/08 주형)
//   값은 서버 plan/service/DemoScenario.java 한 곳에 있어요. 사용자가 이미 고른 칸은 안 덮어써요
const scenario = ref(null)
const BOARDING_EARLY_MIN = 30 // 마지막 날 예약이 역·터미널이면 30분 전 도착
const MEAL_AFTER_MIN = 90 // 마지막 날 예약이 식당이면 먹고 끝 (11/12 촬영 시나리오: 스시도 12:30 → 14:00 해산)
onMounted(async () => {
  if (!devMode.isOn) return
  try {
    scenario.value = await getDevScenario()
  } catch (e) {
    return // 시연 계정이 아니거나 서버가 답을 못 하면 평소대로
  }
  const setup = scenario.value
  if (!store.planOrigin) store.planOrigin = { name: setup.originName, address: setup.originAddress, category: '출발지', lat: setup.originLat, lng: setup.originLng }
  // 예약(산청숯불가든 회식·스시도)은 미리 넣지 않음. 발표자가 "예약한 곳" 단계에서 장소 검색으로 직접 넣어요 (11/09 상재 제보)
})
// 숙소는 미리 넣지 않음 (11/08 주형: 시연에서 숙소 단계에서 직접 찾아 넣는 모습을 보여 줌)

// 예약 중 마지막 날·가장 늦은 시각인 것 (시연 계정 끝 시각 기준)
function lastFixedOf(fixedList) {
  let last = fixedList[0]
  for (const item of fixedList) {
    const isLater = item.day > last.day || (item.day === last.day && toMinutes(item.time) > toMinutes(last.time))
    if (isLater) last = item
  }
  return last
}
// "시간 추천": 추천 시작·끝 시각으로 맞춤 (지금은 기본값, 통계가 오면 utils/timeRecommend.js만 바꿈)
function useRecommendedTime() {
  // 시연 계정: 시나리오 시각으로 (11/08 주형). 시작은 첫날(11/12 종로3가 11:30), 끝은 마지막 날 예약 기준
  //   값은 서버 DemoScenario.java 한 곳 (startTime, fixed). 시작·끝은 PlanRuleMaker가 첫날·마지막 날에만 써요
  if (scenario.value) {
    const last = lastFixedOf(scenario.value.fixed) // 마지막 날 가장 늦은 예약 (11/12: 16:00 동서울터미널 버스)
    const isBoarding = /역$|터미널|공항/.test(last.name)
    const end = toTime(toMinutes(last.time) + (isBoarding ? -BOARDING_EARLY_MIN : MEAL_AFTER_MIN))
    store.planFromTime = scenario.value.startTime
    store.planToTime = end
    showToast('추천 시간 ' + scenario.value.startTime + '~' + end + '로 맞췄어요') // 11/09: 일반 계정과 같은 문구 (아직 안 넣은 예약을 미리 말하지 않음)
    return
  }
  // 오늘 여행이면 지금 이후로 맞추고 날짜는 그대로 (하루가 거의 끝났을 때만 내일로, 11/09)
  const time = recommendTimeFor(isToday(), nowMinutes())
  if (time.isTomorrow) moveToTomorrow()
  store.planFromTime = time.start
  store.planToTime = time.end
  showToast('추천 시간 ' + time.start + '~' + time.end + '로 맞췄어요')
}
</script>

<template>
  <div class="scr scr-PlanAI">
    <div class="app" :class="{ 'pa-app--kind': step.id === 'kind' }">
      <header class="topbar">
        <button type="button" class="iconbtn" aria-label="이전 단계로" @click="goPrev"><i class="ic ic--l i-back" aria-hidden="true"></i></button>
        <div class="pa-bar" role="progressbar" :aria-valuenow="store.planStep + 1" aria-valuemin="1" :aria-valuemax="STEPS.length"><span :style="{ width: progress }"></span></div>
        <span class="topbar__side tn">{{ store.planStep + 1 }} / {{ STEPS.length }}</span>
      </header>

      <div class="scroll pa-step" :class="{ 'pa-step--center': isCentered }" :style="{ paddingTop: '8px', gap: '14px', bottom: '102px' }">
        <div>
          <h1 class="h-title pa-title" style="margin: 0">{{ step.title }}</h1>
          <p v-if="step.id === 'when'" class="t-cap" style="margin: 4px 0 0">{{ whenLabel }}</p>
          <p v-else-if="step.sub" class="t-cap" style="margin: 4px 0 0">{{ step.sub }}</p>
        </div>

        <section v-if="step.id === 'link'" class="card card--pad pa-sec" aria-label="인스타 링크">
          <WishLinks ref="wishLinks" @added="onLinkAdded" />
        </section>

        <section v-if="step.id === 'link'" class="pa-sns-sec" aria-label="여긴어때?">
          <h2 class="t-sub" style="margin: 0">여긴어때?</h2>
          <div class="pa-sns">
            <a v-for="item in SEARCH_LINKS" :key="item.key" class="pa-sns__tile" :href="item.url()" target="_blank" rel="noopener noreferrer" :aria-label="(item.text || item.label + '에서 서울 찾기') + ' (새 창)'">
              <small v-if="item.caption" class="pa-sns__caption">{{ item.caption }}</small>
              <span class="pa-sns__ic" :class="item.cls">
                <img v-if="item.img" :src="item.img" :class="{ 'is-logo': item.isLogo, 'is-title': item.isTitle }" alt="" draggable="false" />
                <i v-else class="pa-sns__logo" :style="{ '--logo': `url(&quot;${item.logo}&quot;)` }" aria-hidden="true"></i>
              </span>
              <span class="pa-sns__name"><b :class="item.labelCls">{{ item.label }}</b><VerifiedBadge v-if="item.isVerified" /></span>
            </a>
          </div>
        </section>

        <section v-if="step.id === 'link' && recommended.length" class="pa-rec" aria-label="추천 게시물">
          <h2 class="t-sub" style="margin: 0">추천 게시물</h2>
          <button v-for="item in recommended" :key="item.url" type="button" class="pa-rec__card" @click="openRecommended(item)">
            <img v-if="item.imageUrl" :src="item.imageUrl" alt="" />
            <span v-else class="pa-rec__noimg" aria-hidden="true"><i class="ic i-play"></i></span>
            <span class="pa-rec__body">
              <b>{{ item.title }}</b>
              <span class="tn">{{ PLATFORM_LABEL[item.platform] || item.platform }}{{ item.area ? ' · ' + item.area : '' }}</span>
            </span>
            <i class="ic ic--s i-next subtle" aria-hidden="true"></i>
          </button>
        </section>

        <section v-else-if="step.id === 'where'" class="card card--pad pa-sec" aria-label="지역">
          <RouterLink to="/plan/search?mode=area" class="field__box pickbox" @click="markAutoNext"><i class="ic i-pin subtle" aria-hidden="true"></i><span class="val" :class="{ 'is-empty': !store.planArea }">{{ store.planArea ? store.planArea.fullName : '동·구 이름으로 찾기' }}</span><i class="ic i-next subtle" aria-hidden="true"></i></RouterLink>
          <p v-if="store.planArea?.isAuto" class="t-cap">인스타 장소 기준</p>
        </section>

        <GuMapPicker v-if="step.id === 'where'" :model-value="store.planAreas" :tops="topGus" @update:model-value="setAreas" />

        <DongPicker v-else-if="step.id === 'dong'" :areas="store.planAreas" />

        <section v-else-if="step.id === 'when'" class="card card--pad pa-sec" aria-label="날짜">
          <RangeCalendar v-model:start="store.planStartDate" v-model:end="store.planEndDate" />
          <!-- 숙소: 1박 이상이면 담아요. 안 담아도 넘어갈 수 있어요 -->
          <div v-if="nights || store.planStays.length" class="stays">
            <div class="sec-h"><h3 class="t-sub" style="font-size: 15px">숙소</h3><span class="t-cap tn">{{ store.planStays.length }}곳 · {{ stayNights }}박 담음{{ nights ? ' / ' + nights + '박' : '' }}</span></div>
            <RouterLink v-for="(stay, i) in store.planStays" :key="stay.name + stay.checkInDate" :to="'/plan/stay?index=' + i" class="stayrow is-set" :aria-label="stay.name + ' 수정·삭제'">
              <span class="ico-tile"><i class="ic i-home" aria-hidden="true"></i></span>
              <span class="row__body">
                <span class="row__title">{{ stay.name }}</span>
                <span class="row__meta tn">{{ toShortDate(stay.checkInDate) }} {{ stay.checkInTime }} 체크인 → {{ toShortDate(stay.checkOutDate) }} {{ stay.checkOutTime }} 체크아웃</span>
                <span v-if="isOutside(stay)" class="row__meta c-warning">여행 날짜 밖이에요</span>
              </span>
              <i class="ic i-edit subtle" aria-hidden="true"></i>
            </RouterLink>
            <RouterLink v-if="stayNights < nights" to="/plan/stay" class="stayrow">
              <span class="ico-tile"><i class="ic i-plus" aria-hidden="true"></i></span>
              <span class="row__body"><span class="row__title">{{ store.planStays.length ? '숙소 더 담기' : '숙소 잡기' }}</span></span>
              <i class="ic i-next subtle" aria-hidden="true"></i>
            </RouterLink>
          </div>
          <!-- 달력 밑: 고른 날마다 날씨·옷차림 미리 보기 (기상청 3일 예보 안의 날만, 11/06) -->
          <div v-if="weatherDays.length" class="pa-when-wx">
            <TripWeather v-for="date in weatherDays" :key="date" :date="date" :stops="[weatherSpot]" />
          </div>
          <p v-else-if="isWeatherLater" class="t-cap" style="margin: 0">날씨·옷차림은 여행 3일 전부터 보여요</p>
        </section>

        <section v-else-if="step.id === 'time'" class="pa-cards" aria-label="시간">
          <TimeRangePicker v-model:start="store.planFromTime" v-model:end="store.planToTime" :note="timeNote" />
          <button type="button" class="chip" style="align-self: center" @click="useRecommendedTime"><i class="ic ic--s i-sparkle" aria-hidden="true"></i>시간 추천</button>
        </section>

        <OriginPicker v-else-if="step.id === 'from'" />

        <section v-else-if="step.id === 'who'" class="pa-cards" aria-label="동행">
          <div class="card card--pad sec-h">
            <span class="t-strong">인원</span>
            <span style="display: flex; align-items: center; gap: 4px">
              <button type="button" class="iconbtn iconbtn--soft pa-count" aria-label="인원 줄이기" @click="changePeople(-1)"><i class="ic ic--s i-minus" aria-hidden="true"></i></button>
              <b class="tn" style="min-width: 32px; text-align: center" aria-live="polite">{{ store.planPeople }}명</b>
              <button type="button" class="iconbtn iconbtn--soft pa-count" aria-label="인원 늘리기" @click="changePeople(1)"><i class="ic ic--s i-plus" aria-hidden="true"></i></button>
            </span>
          </div>
          <div class="choice-grid" role="radiogroup" aria-label="동행">
            <ChoiceCard v-for="item in companions" :key="item.name" :icon="item.icon" :title="item.name" :desc="item.desc" :selected="store.planCompanion === item.name" @click="pickCompanion(item.name)" />
          </div>
        </section>

        <section v-else-if="step.id === 'kind'" class="pa-cards" aria-label="장소 종류">
          <div class="choice-grid">
            <ChoiceCard v-for="chip in KIND_CARDS" :key="chip.code" :icon="chip.icon" :title="chip.label" :desc="chip.desc" :color="chip.color" :selected="store.planKinds.includes(chip.code)" @click="store.planKinds = toggleCode(store.planKinds, chip.code)" />
          </div>
          <p v-if="store.planKinds.length" class="t-cap tn" style="margin: 0; text-align: center">{{ store.planKinds.length }}개 골랐어요</p>
        </section>

        <section v-else-if="step.id === 'tag'" class="choice-grid" aria-label="분위기">
          <ChoiceCard v-for="chip in TAG_CARDS" :key="chip.code" :icon="chip.icon" :title="chip.label" :desc="chip.desc" :selected="store.planTags.includes(chip.code)" @click="store.planTags = toggleCode(store.planTags, chip.code)" />
        </section>

        <section v-else-if="step.id === 'move'" class="choice-grid" aria-label="이동 수단">
          <ChoiceCard v-for="m in moveList" :key="m.label" :icon="m.icon" :title="m.label" :desc="m.desc" :selected="store.planMoves.includes(m.label)" @click="store.planMoves = toggleCode(store.planMoves, m.label)" />
        </section>

        <section v-else-if="step.id === 'fixed'" class="pa-cards" aria-label="꼭 지킬 시간">
          <div class="choice-grid">
            <ChoiceCard to="/plan/search?mode=fixed" icon="i-search" title="장소 검색" desc="식당·공연 이름으로" />
            <ChoiceCard to="/plan/capture" icon="i-camera" title="예약 캡처" desc="예약 문자·화면으로" />
          </div>
          <div v-for="(item, index) in fixedList" :key="item.name + item.day + item.time" class="stop is-fixed" style="grid-template-columns: 1fr">
            <div class="stop__card">
              <span class="day-tile"><b>{{ dayTileOf(item.day).title }}</b><span>{{ dayTileOf(item.day).date }}</span></span>
              <span class="stop__body"><span class="stop__name">{{ item.name }}</span><span class="stop__meta"><b class="c-navy tn">{{ item.time }}</b> · 시간 고정</span></span>
              <button type="button" class="iconbtn" :aria-label="item.name + ' 빼기'" @click="removeFixed(index)"><i class="ic i-close muted" aria-hidden="true"></i></button>
            </div>
          </div>
          <button v-if="!fixedList.length" type="button" class="textlink" style="align-self: center" @click="goNext">예약 없어요</button>
        </section>
      </div>

      <div class="cta">
        <div class="cta__row">
          <button v-if="store.planStep > 0" type="button" class="btn btn--secondary btn--lg" @click="goPrev">이전</button>
          <button type="button" class="btn btn--primary btn--lg" :class="{ 'is-wait': !canNext }" @click="goNext">{{ isLast ? '일정 만들기' : '다음' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style>
/* 6~10칸: 질문과 선택 카드를 진행 막대와 아래 버튼 사이 가운데로 (살짝 위) */
.scr-PlanAI .pa-app--kind { background: linear-gradient(#EEF5FF, #FFFFFF); }
.scr-PlanAI .pa-app--kind .pa-title { font-size: 22px; line-height: 30px; font-weight: 700; }
.scr-PlanAI .pa-app--kind .cta { background: #fff; }
.scr-PlanAI .pa-step--center > :first-child { margin-top: auto; }
.scr-PlanAI .pa-step--center > :last-child { margin-bottom: auto; padding-bottom: 32px; }
.scr-PlanAI .pa-cards { display: flex; flex-direction: column; gap: 10px; }
/* Instagram 글자 전용 필기체. Grand Hotel (SIL OFL, public/fonts/grand-hotel/OFL.txt) */
@font-face { font-family: 'Grand Hotel'; src: url('/fonts/grand-hotel/GrandHotel-Regular.ttf') format('truetype'); font-display: swap; }
.scr-PlanAI .pa-sns__tile b.pa-sns__script { font-family: 'Grand Hotel', cursive; font-size: 18px; line-height: 16px; font-weight: 400; }
.scr-PlanAI .pa-sec{display:flex;flex-direction:column;gap:10px}
.scr-PlanAI .pa-bar{flex:1;height:6px;margin:0 10px;border-radius:999px;background:var(--surface-sunken);overflow:hidden}
.scr-PlanAI .pa-bar span{display:block;height:100%;border-radius:999px;background:var(--navy);transition:width .2s}
.scr-PlanAI .pa-sns-sec,.scr-PlanAI .pa-rec{display:flex;flex-direction:column;gap:12px;padding-top:6px}
.scr-PlanAI .pa-sns__caption{font-size:10px;line-height:12px;color:var(--ink-muted);white-space:nowrap}
.scr-PlanAI .pa-sns__name{display:inline-flex;align-items:center;gap:3px;white-space:nowrap}
.scr-PlanAI .pa-sns__ic img{width:100%;height:100%;border-radius:14px;object-fit:cover;-webkit-user-drag:none}
.scr-PlanAI .pa-sns__ic:has(img.is-title){overflow:hidden;border-radius:14px;background:#000}
.scr-PlanAI .pa-sns__ic img.is-title{transform:scale(1.25)}
.scr-PlanAI .pa-sns__ic img.is-logo{object-fit:contain;padding:0 4px;background:#fff;box-sizing:border-box;box-shadow:inset 0 0 0 1px var(--line)}
.scr-PlanAI .pa-sns{display:grid;grid-template-columns:repeat(3,1fr);gap:10px}
.scr-PlanAI .pa-sns__tile{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px;min-height:104px;border:1.5px solid var(--line);border-radius:16px;background:var(--surface-raised);text-decoration:none;color:var(--ink);transition:border-color .15s,transform .1s}
.scr-PlanAI .pa-sns__tile:hover{border-color:var(--navy)}
.scr-PlanAI .pa-sns__tile b{font-size:13px;font-weight:700}
.scr-PlanAI .pa-sns__ic{width:44px;height:44px;border-radius:14px;display:flex;align-items:center;justify-content:center;color:#fff}
.scr-PlanAI .pa-sns--yt{background:#fff;box-shadow:inset 0 0 0 1px var(--line)}
.scr-PlanAI .pa-sns__logo{display:block;width:26px;height:26px;background:#fff;-webkit-mask:var(--logo) center/contain no-repeat;mask:var(--logo) center/contain no-repeat}
.scr-PlanAI .pa-sns--yt .pa-sns__logo{width:32px;height:32px;background:#FF0000}
.scr-PlanAI .pa-sns--nv .pa-sns__logo{width:20px;height:20px}
.scr-PlanAI .pa-sns--ig{background:linear-gradient(45deg,#FEDA75,#FA7E1E,#D62976,#962FBF,#4F5BD5)}
.scr-PlanAI .pa-sns--nv{background:#03C75A}
.scr-PlanAI .pa-rec__card{display:flex;align-items:center;gap:12px;min-height:72px;padding:10px;border:1.5px solid var(--line);border-radius:16px;background:var(--surface-raised);font:inherit;color:var(--ink);text-align:left;cursor:pointer}
.scr-PlanAI .pa-rec__card:hover{border-color:var(--navy)}
.scr-PlanAI .pa-rec__card img,.scr-PlanAI .pa-rec__noimg{flex:none;width:56px;height:56px;border-radius:12px;object-fit:cover}
.scr-PlanAI .pa-rec__noimg{display:flex;align-items:center;justify-content:center;color:var(--navy);background:var(--navy-soft)}
.scr-PlanAI .pa-rec__body{flex:1;min-width:0;display:flex;flex-direction:column}
.scr-PlanAI .pa-rec__body b{overflow:hidden;white-space:nowrap;text-overflow:ellipsis;font-size:15px;font-weight:700}
.scr-PlanAI .pa-rec__body span{font-size:12px;color:var(--ink-muted)}
.scr-PlanAI .pa-areas{display:flex;flex-direction:column;gap:12px;padding-top:6px}
.scr-PlanAI .pa-area-tabs{flex-wrap:nowrap;overflow-x:auto;margin:0;scrollbar-width:none}
.scr-PlanAI .pa-area-tabs .chip{flex:none}
.scr-PlanAI .pa-area-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}
.scr-PlanAI .pa-area{position:relative;display:flex;align-items:center;gap:10px;min-height:72px;padding:10px;border:1.5px solid var(--line);border-radius:16px;background:var(--surface-raised);font:inherit;color:var(--ink);text-align:left;cursor:pointer;transition:border-color .15s,background .15s}
.scr-PlanAI .pa-area:hover{border-color:var(--navy)}
.scr-PlanAI .pa-area.is-on{border-color:var(--navy);background:var(--navy-soft)}
.scr-PlanAI .pa-area img{flex:none;width:48px;height:48px;border-radius:12px;object-fit:cover}
.scr-PlanAI .pa-area__body{flex:1;min-width:0;display:flex;flex-direction:column}
.scr-PlanAI .pa-area__body b{font-size:15px;line-height:20px;font-weight:700}
.scr-PlanAI .pa-area__body span{font-size:12px;line-height:16px;color:var(--ink-muted)}
.scr-PlanAI .pa-area>.ic{position:absolute;top:8px;right:8px;width:14px;height:14px;color:var(--navy)}
.scr-PlanAI .pa-count{width:36px;height:36px}
.scr-PlanAI .pickbox{padding-right:12px;text-decoration:none;color:var(--ink);transition:border-color .15s,background .15s}
.scr-PlanAI .pickbox:hover{border-color:var(--navy);background:var(--navy-soft)}
.scr-PlanAI .pickbox .val{flex:1;font-size:16px}
.scr-PlanAI .pickbox .val.is-empty{color:var(--ink-subtle)}
.scr-PlanAI .stayrow{display:flex;align-items:center;gap:12px;padding:10px 12px;border-radius:var(--radius-lg);border:1.5px dashed var(--line-strong);text-decoration:none;color:var(--ink);transition:border-color .15s,background .15s}
.scr-PlanAI .stayrow:hover{border-color:var(--navy);background:var(--navy-soft)}
.scr-PlanAI .stayrow.is-set{border-style:solid;border-color:var(--line)}
.scr-PlanAI .btn.is-wait{opacity:.5}
.scr-PlanAI .pa-when-wx{display:flex;flex-direction:column;gap:8px;padding-top:4px}
.scr-PlanAI .stays{display:flex;flex-direction:column;gap:8px;padding-top:4px}
</style>
