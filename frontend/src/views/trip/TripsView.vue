<script setup>
// S04-b 내 여행 (②): 지금 여행 중 · 다가오는 여행 · 지난 여행
import PhotoCard from '@/components/common/PhotoCard.vue'
import TabBar from '@/components/common/TabBar.vue'

// 가짜 데이터: 지난 여행 (백엔드가 붙으면 GET /api/trips 의 status=done)
const pastTrips = [
  { title: '천호 카페 투어', img: '/img/dessert.jpg', meta: '10/11(토) · 5곳 모두 · 예약 지킴' },
  { title: '을지로 하루', img: '/img/euljiro.jpg', meta: '10/4(토) · 5곳 중 4곳' },
  { title: '전주 한옥마을 1박', img: '/img/jeonju.jpg', meta: '9/20–9/21 · 9곳 중 8곳' },
]
</script>

<template>
  <div class="scr scr-Trips">
    <div class="app">
      <header class="topbar" style="padding: 0 8px 0 16px">
        <h1 class="topbar__title" style="font-size: 24px; line-height: 32px">내 여행</h1>
      </header>

      <div class="scroll" style="bottom: 84px; gap: 20px; padding-top: 4px">
        <section aria-labelledby="t-now" class="trips-sec">
          <h2 id="t-now" class="t-strong muted">지금 여행 중</h2>
          <PhotoCard to="/live" img="/img/cafe.jpg" :height="132">
            <template #badge><span class="badge badge--success"><i class="ic ic--s i-check" aria-hidden="true"></i>여유 15분</span></template>
            <span class="photo__txt now-trip">
              <span style="display: flex; flex-direction: column"><b style="font-size: 18px; line-height: 26px">잔나비 공연 데이트</b><small class="tn">3/6곳 · 다음 성내동 소품샵</small></span>
              <span class="btn btn--sm" style="background: #fff; color: var(--navy)">여행 이어가기</span>
            </span>
          </PhotoCard>
        </section>

        <section aria-labelledby="t-next" class="trips-sec">
          <h2 id="t-next" class="t-strong muted">다가오는 여행</h2>
          <div class="group">
            <RouterLink to="/trips/detail" class="row">
              <img class="thumb" src="/img/dessert.jpg" alt="" />
              <span class="row__body"><span class="row__title">성내동 카페 2차</span><span class="row__meta">11/1(토) · 5곳</span></span>
              <span class="badge badge--navy tn">D-8</span>
            </RouterLink>
            <RouterLink to="/plan/info" class="row">
              <img class="thumb" src="/img/busan.jpg" alt="" />
              <span class="row__body">
                <span class="row__title">부산 1박 2일</span>
                <span style="display: flex; align-items: center; gap: 8px"><span class="bar" style="flex: 1"><i style="width: 50%"></i></span><span class="row__meta">짜는 중 50%</span></span>
              </span>
              <i class="ic i-next subtle" aria-hidden="true"></i>
            </RouterLink>
            <RouterLink to="/new" class="row tr-new">
              <span class="thumb tr-new__ic"><i class="ic i-plus" aria-hidden="true"></i></span>
              <span class="row__body"><span class="row__title">새 여행 만들기</span><span class="row__meta">AI로 짜거나 직접 골라요</span></span>
              <i class="ic i-next" aria-hidden="true"></i>
            </RouterLink>
          </div>
        </section>

        <section aria-labelledby="t-past" class="trips-sec">
          <h2 id="t-past" class="t-strong muted">지난 여행 {{ pastTrips.length }}</h2>
          <div class="group">
            <RouterLink v-for="trip in pastTrips" :key="trip.title" to="/retro" class="row">
              <img class="thumb" :src="trip.img" alt="" />
              <span class="row__body"><span class="row__title">{{ trip.title }}</span><span class="row__meta">{{ trip.meta }}</span></span>
              <i class="ic i-next subtle" aria-hidden="true"></i>
            </RouterLink>
          </div>
        </section>
      </div>

      <TabBar active="trips" />
    </div>
  </div>
</template>

<style>
.scr-Trips .trips-sec { display: flex; flex-direction: column; gap: 10px; }
.scr-Trips .now-trip { flex-direction: row; align-items: flex-end; justify-content: space-between; gap: 8px; }
.scr-Trips .tr-new{background:var(--navy-soft);color:var(--navy)}
.scr-Trips .tr-new:hover{background:#DCE4F2}
.scr-Trips .tr-new .row__title{color:var(--navy);font-weight:700}
.scr-Trips .tr-new .row__meta{color:var(--navy);opacity:.75}
.scr-Trips .tr-new .ic{color:var(--navy)}
.scr-Trips .tr-new__ic{display:flex;align-items:center;justify-content:center;background:#fff;border:1px solid #C9D5EA}
.scr-Trips .tr-new__ic .ic{color:var(--navy);width:22px;height:22px}
</style>
