<script setup>
// S19-c 앨범 (⑤): 여행별 사진 앨범. 사진을 올리면 찍은 장소에 맞춰 지도에 붙어요
import { ref } from 'vue'
import PhotoCard from '@/components/common/PhotoCard.vue'
import TabBar from '@/components/common/TabBar.vue'

// 가짜 데이터: 여행 앨범 (백엔드가 붙으면 media 테이블)
const albums = [
  { title: '천호 카페', img: '/img/cafe.jpg', sub: '10/11 · 24장' },
  { title: '을지로', img: '/img/euljiro.jpg', sub: '10/4 · 31장' },
  { title: '전주 한옥', img: '/img/jeonju.jpg', sub: '9/20 · 58장' },
]

const uploaded = ref(false)
</script>

<template>
  <div class="scr scr-Album">
    <div class="app">
      <header class="topbar">
        <h1 class="topbar__title" style="padding-left: 8px">앨범</h1>
        <button type="button" class="btn btn--secondary btn--sm" style="margin-right: 8px" @click="uploaded = true"><i class="ic ic--s i-plus" aria-hidden="true"></i>사진 올리기</button>
      </header>

      <div class="scroll" style="padding-top: 4px; gap: 16px; bottom: 96px">
        <PhotoCard to="/retro" img="/img/dessert.jpg" :height="200" title="천호 카페 투어" sub="10/11 · 사진 24장 · 5곳">
          <template #badge><span class="badge badge--solid"><i class="ic ic--s i-play" aria-hidden="true"></i>다시보기</span></template>
        </PhotoCard>

        <section aria-labelledby="ppl-h" style="display: flex; flex-direction: column; gap: 8px">
          <h2 id="ppl-h" class="t-sub">함께 보는 사람</h2>
          <div class="people">
            <span class="p"><span class="ava album-ava">엄</span>가족</span>
            <span class="p"><span class="ava album-ava">민</span>대학 친구</span>
            <RouterLink to="/my/friends"><span class="ava album-invite"><i class="ic i-plus" aria-hidden="true"></i></span>초대</RouterLink>
          </div>
        </section>

        <section aria-labelledby="alb-h" style="display: flex; flex-direction: column; gap: 8px">
          <div class="sec-h"><h2 id="alb-h" class="t-sub">여행 앨범 <span class="muted tn">{{ albums.length }}</span></h2><span class="t-cap">최근 순</span></div>
          <div class="grid2">
            <PhotoCard v-for="album in albums" :key="album.title" to="/retro" :img="album.img" :title="album.title" :sub="album.sub" />
            <button type="button" class="card album-upload" @click="uploaded = true">
              <i class="ic ic--xl i-camera" aria-hidden="true"></i><span class="t-cap">찍은 장소에 맞춰<br />지도에 붙어요</span>
            </button>
          </div>
        </section>
      </div>

      <div v-if="uploaded" class="snack album-snack" role="status">
        <i class="ic i-success" aria-hidden="true"></i><span>사진 3장을 지도에 붙였어요</span><RouterLink to="/retro">보기</RouterLink>
      </div>

      <TabBar active="trips" />
    </div>
  </div>
</template>

<style>
.scr-Album .album-ava{background:var(--navy-soft);color:var(--navy)}
.scr-Album .album-invite{border:1.5px dashed var(--line-strong);color:var(--ink-muted)}
.scr-Album .album-upload{height:150px;border:1.5px dashed var(--line-strong);background:transparent;box-shadow:none;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:6px;color:var(--ink-muted);cursor:pointer;font:inherit}
.scr-Album .album-snack{position:absolute;left:16px;right:16px;bottom:100px;z-index:5}
.scr-Album .ava{width:44px;height:44px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-weight:700;font-size:15px;flex:none}
.scr-Album .people{display:flex;align-items:center;gap:12px}
.scr-Album .people a,.scr-Album .people span.p{display:flex;flex-direction:column;align-items:center;gap:4px;font-size:12px;color:var(--ink-muted);text-decoration:none}
.scr-Album .grid2{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}
</style>
