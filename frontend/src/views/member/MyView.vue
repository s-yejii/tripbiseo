<script setup>
// S19 마이 (①): 내 정보 · 여행 스타일 · 친구 · 결제 카드 · 알림 설정 · 권한
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { get, post } from '@/api/http'
import { getTripList } from '@/api/trips'
import TabBar from '@/components/common/TabBar.vue'
import BottomSheet from '@/components/common/BottomSheet.vue'
import TopBar from '@/components/common/TopBar.vue'
import VersionTap from '@/components/member/VersionTap.vue'
import { turnOffDevMode } from '@/utils/devMode'
import { showToast } from '@/utils/toast'
import { friendNews } from '@/data/friendNews'
import { useTripStore } from '@/stores/trip'

// 가짜 데이터: 내 정보 (백엔드가 붙으면 member · member_style · friend · card 테이블)
const router = useRouter()
const store = useTripStore()
const WHEN_STEP = 3 // 여행 정보 단계 번호: 언제
const SEEN_KEY = 'friendNewsSeen' // 이 브라우저에서 본 소식 번호

// 내 정보: 화면이 열리면 서버(GET /api/auth/me)에서 받아 와요
const me = ref({ name: '', id: '', area: '서울' }) // 지역은 아직 DB에 없어서 그대로
const tripStats = ref({ done: 0, planned: 0, ongoing: 0 }) // 내 여행 수 (GET /api/trips)

// onMounted: 화면이 처음 뜰 때 한 번 실행
onMounted(async () => {
  try {
    const user = await get('/auth/me')
    me.value.name = user.nickname || user.username // 닉네임이 없으면 아이디
    me.value.id = user.username
  } catch (e) {
    router.replace('/login') // 로그인 안 했으면(401) 로그인 화면으로
    return
  }
  try {
    const trips = await getTripList()
    tripStats.value = {
      done: trips.filter((trip) => trip.status === 'done').length,
      planned: trips.filter((trip) => trip.status === 'planned').length,
      ongoing: trips.filter((trip) => trip.status === 'ongoing').length,
    }
  } catch {
    // 여행 수를 못 받아도 마이 화면은 보여 줌 (0으로)
  }
})
const styleChips = ['보통 여행', '카페', '맛집', '쇼핑']
const friends = ['엄마', '민지', '준호']

// 친구 소식 (시연용 데이터, data/friendNews.js). 안 본 소식은 하늘색 테두리, 본 소식은 회색
const seenNews = ref(readSeen())
const openFriend = ref(null) // 소식 시트를 연 친구 이름
const openNews = computed(() => (openFriend.value ? friendNews[openFriend.value] : null))
const newsCount = computed(() => friends.filter((friend) => friendNews[friend]).length)

function readSeen() {
  try {
    return JSON.parse(localStorage.getItem(SEEN_KEY)) || []
  } catch {
    return []
  }
}
function showNews(friend) {
  openFriend.value = friend
  const id = friendNews[friend].id
  if (seenNews.value.includes(id)) return
  seenNews.value = [...seenNews.value, id]
  try {
    localStorage.setItem(SEEN_KEY, JSON.stringify(seenNews.value))
  } catch {
    // 저장이 안 돼도 소식은 보여 줌
  }
}
// 이 코스 따라하기: 그 장소들을 꼭 갈 곳으로 넣고 여행 정보의 날짜 칸부터
function followCourse() {
  const news = openNews.value
  store.resetPlan()
  store.planArea = { ...news.area }
  store.planConditions = { mustVisit: [...news.places] }
  store.planStep = WHEN_STEP
  router.push('/plan/info')
}
const cards = ref([
  { name: '신한카드', last: '4821', main: true },
])

// 알림 켜기/끄기
const alarms = ref([
  { key: 'depart', icon: 'i-walk', tile: 'sky', title: '출발 알림', on: true },
  { key: 'delay', icon: 'i-warning', tile: 'warn', title: '지연·대안 알림', on: true },
  { key: 'weather', icon: 'i-cloud', tile: 'lilac', title: '날씨 알림', on: true },
])
const departBefore = ref(10) // 출발 몇 분 전에 알릴지

function setMainCard(index) {
  cards.value.forEach((card, i) => (card.main = i === index))
  showToast('기본 결제 카드를 바꿨어요')
}

// 로그아웃: 서버 세션을 지우고 첫 화면으로
async function logout() {
  await post('/auth/logout')
  turnOffDevMode() // 다른 사람이 이 브라우저로 로그인해도 개발자 모드가 남지 않게
  router.replace('/login')
}

</script>

<template>
  <div class="scr scr-My">
    <div class="app">
      <TopBar title="마이" icon="none">
        <button type="button" class="my-lang" aria-label="언어 바꾸기, 지금 한국어" @click="showToast('English 화면은 준비 중이에요')">
          <i class="ic ic--s i-chat" aria-hidden="true"></i><b>한</b><span aria-hidden="true">/</span><span>EN</span>
        </button>
      </TopBar>

      <div class="scroll" style="padding-top: 8px; gap: 24px; bottom: 96px">
        <section class="my-hero" aria-label="내 정보">
          <div class="my-hero__top">
            <span class="my-hero__ava">{{ me.name[0] }}</span>
            <div class="my-hero__name">
              <p>{{ me.name }}</p>
              <span class="tn">{{ me.id }}</span>
            </div>
            <RouterLink to="/my/profile" class="my-hero__edit" aria-label="정보 수정"><i class="ic ic--s i-edit" aria-hidden="true"></i></RouterLink>
          </div>
          <RouterLink to="/trips" class="my-stats" aria-label="내 여행 보기">
            <span class="my-stat"><b class="tn">{{ tripStats.done }}</b>다녀온 여행</span>
            <i aria-hidden="true"></i>
            <span class="my-stat"><b class="tn">{{ tripStats.planned }}</b>예정 여행</span>
            <i aria-hidden="true"></i>
            <span class="my-stat" :class="{ 'is-live': tripStats.ongoing }"><b class="tn">{{ tripStats.ongoing }}</b>여행 중</span>
          </RouterLink>
        </section>

        <RouterLink to="/my/style" class="my-style" aria-label="내 여행 스타일 수정">
          <span class="ico-tile ico-tile--lilac"><i class="ic i-sparkle" aria-hidden="true"></i></span>
          <span class="my-style__body">
            <b>내 여행 스타일</b>
            <span class="tn">{{ styleChips.slice(1).join(' · ') }}</span>
          </span>
          <i class="ic i-next subtle" aria-hidden="true"></i>
        </RouterLink>

        <section class="my-sec" aria-labelledby="fr-h">
          <h2 id="fr-h" class="t-sub">친구 <span class="my-count tn">{{ friends.length }}</span><span class="fr-count tn">소식 {{ newsCount }}</span></h2>
          <div class="friends">
            <RouterLink to="/my/friends"><span class="ava ava--add"><i class="ic i-plus" aria-hidden="true"></i></span>추가</RouterLink>
            <template v-for="(friend, i) in friends" :key="friend">
              <button v-if="friendNews[friend]" type="button" class="fr-btn" :aria-label="friend + ' 소식 보기'" @click="showNews(friend)"><span class="ava fr-ring" :class="['ava--c' + (i % 3), { 'is-seen': seenNews.includes(friendNews[friend].id) }]">{{ friend[0] }}</span>{{ friend }}</button>
              <span v-else><span class="ava" :class="'ava--c' + (i % 3)">{{ friend[0] }}</span>{{ friend }}</span>
            </template>
          </div>
        </section>

        <section class="my-sec" aria-labelledby="cd-h">
          <h2 id="cd-h" class="t-sub">결제 카드</h2>
          <div class="group">
            <div v-for="(card, i) in cards" :key="card.last" class="row">
              <span class="ico-tile ico-tile--coral"><i class="ic i-won" aria-hidden="true"></i></span>
              <span class="row__body"><span class="row__title">{{ card.name }} <span class="tn muted" style="font-weight: 500">{{ card.last }}</span></span></span>
              <span v-if="card.main" class="badge badge--navy">기본</span>
              <button v-else type="button" class="btn btn--secondary btn--sm" @click="setMainCard(i)">기본으로</button>
            </div>
            <RouterLink to="/my/card" class="row">
              <span class="ico-tile ico-tile--add"><i class="ic i-plus" aria-hidden="true"></i></span>
              <span class="row__body"><span class="row__title c-navy">카드 등록</span></span>
              <i class="ic i-next subtle" aria-hidden="true"></i>
            </RouterLink>
          </div>
        </section>

        <section class="my-sec" aria-labelledby="nt-h">
          <h2 id="nt-h" class="t-sub">알림</h2>
          <div class="group">
            <template v-for="alarm in alarms" :key="alarm.key">
              <div class="row">
                <span class="ico-tile" :class="alarm.tile ? 'ico-tile--' + alarm.tile : ''"><i class="ic" :class="alarm.icon" aria-hidden="true"></i></span>
                <span class="row__body"><span class="row__title">{{ alarm.title }}</span></span>
                <button type="button" role="switch" class="toggle" :class="{ 'is-on': alarm.on }" :aria-checked="alarm.on" :aria-label="alarm.title" @click="alarm.on = !alarm.on"></button>
              </div>
              <!-- 출발 알림이 켜져 있을 때만 몇 분 전인지 골라요 -->
              <div v-if="alarm.key === 'depart' && alarm.on" class="my-before">
                <div class="chips" role="radiogroup" aria-label="출발 몇 분 전" style="flex-wrap: nowrap">
                  <button v-for="min in [5, 10, 15]" :key="min" type="button" role="radio" class="chip chip--sm tn" :class="{ 'is-on': departBefore === min }" :aria-checked="departBefore === min" @click="departBefore = min">{{ min }}분 전</button>
                </div>
              </div>
            </template>
          </div>
        </section>

        <section class="my-sec" aria-labelledby="st-h">
          <h2 id="st-h" class="t-sub">설정</h2>
          <div class="group">
            <RouterLink to="/my/style" class="row"><span class="ico-tile ico-tile--sky"><i class="ic i-subway" aria-hidden="true"></i></span><span class="row__body"><span class="row__title">이동 수단</span></span><i class="ic i-next subtle" aria-hidden="true"></i></RouterLink>
            <RouterLink to="/permission" class="row"><span class="ico-tile ico-tile--ok"><i class="ic i-pin" aria-hidden="true"></i></span><span class="row__body"><span class="row__title">위치 권한</span></span><i class="ic i-next subtle" aria-hidden="true"></i></RouterLink>
            <RouterLink to="/my/location" class="row"><span class="ico-tile ico-tile--lilac"><i class="ic i-route" aria-hidden="true"></i></span><span class="row__body"><span class="row__title">내 위치 기록</span></span><i class="ic i-next subtle" aria-hidden="true"></i></RouterLink>
            <RouterLink to="/my/trash" class="row"><span class="ico-tile ico-tile--neutral"><i class="ic i-trash" aria-hidden="true"></i></span><span class="row__body"><span class="row__title">휴지통</span></span><i class="ic i-next subtle" aria-hidden="true"></i></RouterLink>
            <a href="tel:15880000" class="row"><span class="ico-tile ico-tile--neutral"><i class="ic i-help" aria-hidden="true"></i></span><span class="row__body"><span class="row__title">고객센터</span></span><i class="ic i-next subtle" aria-hidden="true"></i></a>
          </div>
        </section>

        <div class="my-foot">
          <button type="button" class="btn btn--ghost btn--sm" @click="logout"><i class="ic ic--s i-logout" aria-hidden="true"></i>로그아웃</button>
          <VersionTap />
        </div>
      </div>

      <template v-if="openNews">
        <div class="dim" style="z-index: 20"></div>
        <BottomSheet closable data-drag :on-close="() => (openFriend = null)" class="fr-sheet" role="dialog" aria-modal="true" :aria-label="openFriend + ' 소식'">
          <h2 class="h-title">{{ openFriend }}의 코스</h2>
          <p class="t-cap tn">{{ openNews.date }} · {{ openNews.area.name }} · {{ openNews.places.length }}곳</p>
          <ol class="fr-places">
            <li v-for="(place, i) in openNews.places" :key="place"><span class="fr-no tn">{{ i + 1 }}</span>{{ place }}</li>
          </ol>
          <button type="button" class="btn btn--primary btn--lg btn--block" @click="followCourse">이 코스 따라하기</button>
          <button type="button" class="btn btn--ghost btn--md btn--block" @click="openFriend = null">닫기</button>
        </BottomSheet>
      </template>
      <TabBar active="my" />
    </div>
  </div>
</template>

<style>
.scr-My .topbar__title{padding-left:8px}
.scr-My .my-hero{display:flex;flex-direction:column;gap:18px;padding:20px;border-radius:var(--radius-xl);background:var(--navy-soft)}
.scr-My .my-hero__top{display:flex;align-items:center;gap:14px}
.scr-My .my-hero__ava{flex:none;width:60px;height:60px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:24px;font-weight:800;color:#fff;background:var(--navy)}
.scr-My .my-hero__name{flex:1;min-width:0}
.scr-My .my-hero__name p{margin:0;overflow:hidden;white-space:nowrap;text-overflow:ellipsis;font-size:21px;line-height:28px;font-weight:800;letter-spacing:-.3px}
.scr-My .my-hero__name span{font-size:13px;color:var(--ink-muted)}
.scr-My .my-hero__edit{flex:none;width:44px;height:44px;display:flex;align-items:center;justify-content:center;border-radius:50%;background:#fff;color:var(--navy);box-shadow:0 2px 8px rgba(31,58,104,.1)}
.scr-My .my-stats{display:flex;align-items:center;padding:14px 8px;border-radius:var(--radius-lg);background:#fff;text-decoration:none;color:var(--ink-muted)}
.scr-My .my-stats>i{flex:none;width:1px;height:28px;background:var(--line)}
.scr-My .my-stat{flex:1;min-width:0;display:flex;flex-direction:column;align-items:center;gap:2px;font-size:12px}
.scr-My .my-stat b{font-size:20px;line-height:26px;font-weight:800;color:var(--navy)}
.scr-My .my-stat.is-live b{color:var(--coral)}
.scr-My .my-style{display:flex;align-items:center;gap:12px;padding:16px;border-radius:var(--radius-lg);background:var(--lilac-soft);text-decoration:none;color:var(--ink);transition:transform .1s}
.scr-My .my-style .ico-tile{background:#fff}
.scr-My .my-style:active{transform:scale(.98)}
.scr-My .my-style__body{flex:1;min-width:0;display:flex;flex-direction:column;gap:2px}
.scr-My .my-style__body b{font-size:16px;line-height:22px;font-weight:700}
.scr-My .my-style__body span{overflow:hidden;white-space:nowrap;text-overflow:ellipsis;font-size:13px;color:var(--lilac)}
.scr-My .my-sec{display:flex;flex-direction:column;gap:10px}
.scr-My .my-count{margin-left:2px;font-weight:700;color:var(--coral)}
.scr-My .ava{width:52px;height:52px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-weight:700;font-size:17px;flex:none;background:var(--navy-soft);color:var(--navy)}
.scr-My .ava--c0{background:var(--coral-soft);color:var(--coral-ink)}
.scr-My .ava--c1{background:var(--sky-soft);color:var(--sky)}
.scr-My .ava--c2{background:var(--success-soft);color:var(--success)}
.scr-My .ava--add{background:transparent;border:1.5px dashed var(--line-strong);color:var(--ink-muted)}
.scr-My .ico-tile--add{background:transparent;border:1.5px dashed var(--line-strong);color:var(--navy)}
.scr-My .friends{display:flex;gap:18px}
.scr-My .fr-count{margin-left:8px;font-size:12px;font-weight:600;color:var(--sky)}
.scr-My .fr-btn{display:flex;flex-direction:column;align-items:center;gap:6px;padding:0;border:0;background:none;font:inherit;font-size:12px;color:var(--ink-muted);cursor:pointer}
.scr-My .fr-ring{box-shadow:0 0 0 2px #fff,0 0 0 4px var(--sky)}
.scr-My .fr-ring.is-seen{box-shadow:0 0 0 2px #fff,0 0 0 4px var(--line-strong)}
.scr-My .fr-sheet{z-index:21;gap:8px;padding:8px 16px 24px}
.scr-My .fr-sheet .h-title{margin:0}
.scr-My .fr-places{list-style:none;margin:8px 0;padding:0;display:flex;flex-direction:column;gap:12px}
.scr-My .fr-places li{display:flex;align-items:center;gap:10px;font-size:15px;font-weight:600;color:var(--ink)}
.scr-My .fr-no{flex:none;width:24px;height:24px;display:flex;align-items:center;justify-content:center;border-radius:50%;background:var(--navy);color:#fff;font-size:12px;font-weight:700}
.scr-My .friends span,.scr-My .friends a{display:flex;flex-direction:column;align-items:center;gap:6px;font-size:12px;color:var(--ink-muted);text-decoration:none}
.scr-My .my-before{padding:0 16px 12px 68px}
.scr-My .my-foot{display:flex;justify-content:space-between;align-items:center;padding-bottom:16px}
.scr-My .my-lang{flex:none;margin-right:8px;display:inline-flex;align-items:center;gap:4px;height:32px;padding:0 12px;border-radius:999px;border:1px solid var(--line);background:var(--surface-raised);font:inherit;font-size:13px;color:var(--ink-muted);cursor:pointer}
.scr-My .my-lang b{color:var(--navy)}
.scr-My .my-lang:hover{border-color:var(--navy)}
</style>
