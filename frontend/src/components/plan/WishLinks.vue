<script setup>
// "가고 싶은 곳": 인스타·유튜브 링크를 붙여넣으면 게시물 속 장소를 찾아 담아요 (③)
// 찾기는 지금 가짜 값이에요. 백엔드가 붙으면 Gemini로 게시물에서 장소를 뽑아요
import { computed, ref } from 'vue'
import { places } from '@/data/places'
import { showToast } from '@/utils/toast'

const url = ref('')
const error = ref('')
const wish = ref([]) // 담은 장소 id
const found = ref(null) // 찾은 장소 id (없으면 null)
const picked = ref({}) // 찾은 장소 중 고른 것

const pickCount = computed(() => (found.value ? found.value.filter((id) => picked.value[id]).length : 0))

function pasteClip() {
  url.value = 'https://www.instagram.com/reel/C9x2seongsu/'
  error.value = ''
}
function findPlaces() {
  const link = url.value.trim()
  if (!link) return (error.value = '게시물 링크를 붙여넣어 주세요')
  if (!/instagram\.com|youtu|blog\.naver|naver\.me|tiktok/i.test(link)) return (error.value = '인스타·유튜브·네이버 블로그 링크만 찾을 수 있어요')
  found.value = ['sopum', 'dessert', 'daelim']
  picked.value = {}
  found.value.forEach((id) => {
    if (!wish.value.includes(id)) picked.value[id] = true
  })
  error.value = ''
}
function cancel() {
  found.value = null
  url.value = ''
}
function addPicked() {
  const add = found.value.filter((id) => picked.value[id])
  wish.value.push(...add)
  cancel()
  showToast(add.length + '곳을 담았어요 · 일정에 꼭 넣을게요')
}
function removeWish(id) {
  wish.value = wish.value.filter((x) => x !== id)
}
</script>

<template>
  <div v-for="id in wish" :key="id" class="opt" style="padding: 10px 12px">
    <img class="thumb" :src="places[id].img" alt="" />
    <span class="opt__body"><b class="t-strong">{{ places[id].name }}</b><span class="t-cap">{{ places[id].cat }} · 인스타에서 담음</span></span>
    <button type="button" class="iconbtn" :aria-label="places[id].name + ' 빼기'" @click="removeWish(id)"><i class="ic i-close muted" aria-hidden="true"></i></button>
  </div>

  <template v-if="!found">
    <button v-if="!url" type="button" class="wl-clip" @click="pasteClip"><i class="ic ic--s i-link" aria-hidden="true"></i><span>복사한 링크 · instagram.com/reel/C9x2…</span><b>붙여넣기</b></button>
    <form class="wl-form" @submit.prevent="findPlaces">
      <div class="field" :class="{ 'is-error': error }" style="flex: 1; min-width: 0">
        <label for="pa-url" class="sr">게시물 링크</label>
        <div class="field__box"><input id="pa-url" v-model="url" type="text" inputmode="url" placeholder="게시물 링크 붙여넣기" autocomplete="off" autocapitalize="off" @input="error = ''" /></div>
      </div>
      <button type="submit" class="btn btn--secondary btn--md">장소 찾기</button>
    </form>
    <p v-if="error" class="field__help c-danger" style="margin: -4px 0 0"><i class="ic ic--s i-danger" aria-hidden="true"></i>{{ error }}</p>
    <p class="t-cap" style="margin: 0">게시물 속 장소를 찾아 드려요. 담은 곳은 일정에 꼭 넣어요.</p>
  </template>

  <div v-else class="wl-found" role="group" aria-label="찾은 장소">
    <span style="display: flex; justify-content: space-between; align-items: center">
      <b class="t-strong">게시물에서 {{ found.length }}곳을 찾았어요</b><span class="wl-src"><i class="ic ic--s i-link" aria-hidden="true"></i>인스타 릴스</span>
    </span>
    <template v-for="id in found" :key="id">
      <div v-if="wish.includes(id)" class="opt is-locked">
        <img class="thumb" :src="places[id].img" alt="" />
        <span class="opt__body"><b class="t-strong">{{ places[id].name }}</b><span class="t-cap">이미 담았어요</span></span>
        <i class="ic i-check c-success" aria-hidden="true"></i>
      </div>
      <button v-else type="button" role="checkbox" :aria-checked="!!picked[id]" class="opt" :class="{ 'is-on': picked[id] }" style="text-align: left" @click="picked[id] = !picked[id]">
        <img class="thumb" :src="places[id].img" alt="" />
        <span class="opt__body"><b class="t-strong">{{ places[id].name }}</b><span class="t-cap tn"><i class="ic ic--s i-star c-warning" aria-hidden="true"></i> {{ places[id].rate }} · {{ places[id].cat }}</span></span>
        <span class="check" :class="{ 'is-on': picked[id] }"><i class="ic ic--s i-check" aria-hidden="true"></i></span>
      </button>
    </template>
    <div class="cta__row">
      <button type="button" class="btn btn--secondary btn--md" @click="cancel">취소</button>
      <button type="button" class="btn btn--primary btn--md" :disabled="!pickCount" @click="addPicked">{{ pickCount ? pickCount + '곳 담기' : '담을 곳을 골라요' }}</button>
    </div>
  </div>
</template>

<style>
.wl-clip{display:flex;align-items:center;gap:8px;padding:10px 12px;border-radius:12px;background:var(--navy-soft);color:var(--navy);font:inherit;font-size:13px;border:0;text-align:left;cursor:pointer;width:100%}
.wl-clip:hover{background:#DCE4F2}
.wl-clip span{flex:1;min-width:0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.wl-form{display:flex;gap:8px;align-items:flex-start}
.wl-form .btn{flex:none;height:48px}
.wl-found{display:flex;flex-direction:column;gap:8px;padding:12px;border-radius:14px;background:var(--surface);border:1px solid var(--line)}
.wl-found .opt{padding:10px 12px}
.wl-src{display:inline-flex;align-items:center;gap:4px;font-size:12px;color:var(--ink-muted)}
</style>
