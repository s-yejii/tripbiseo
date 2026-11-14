<script setup>
import { toDuration } from '@/utils/time'
// 여행 중 링크 추가: 인스타·유튜브·네이버 링크 → 장소 찾기(POST /plans/links) → 하나 고르기 → 남은 일정에 넣어 보기(POST /live/link-fit)
//   방해 없이 들어가면 여기서 바로 넣고, 늦어지거나 식당·카페가 겹치면 고르는 화면(/live/link-fit)으로 넘어가요
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { findLinkPlaces, fitLinkPlaces } from '@/api/trips'
import { useReplanStore } from '@/stores/replan'

const props = defineProps({ tripId: { type: Number, required: true }, position: { type: Object, required: true } })
const emit = defineEmits(['close', 'added'])

const router = useRouter()
const replan = useReplanStore()

const url = ref('')
const caption = ref('')
const showCaption = ref(false)
const error = ref('')
const isFinding = ref(false)
const isFitting = ref(false)
const found = ref(null) // 찾은 장소 카드 (찾기 전에는 null)
const picked = ref({}) // 고른 장소 { 이름: true }
const fit = ref(null) // 바로 넣어도 될 때의 넣어 본 결과
const MAX_PICK = 5
const pickedPlaces = computed(() => (found.value ? found.value.filter((place) => picked.value[place.name]) : []))

function slackText(option, fixedName) {
  if (!fixedName) return option.finalArrive + ' 끝'
  return option.slackMin < 0 ? toDuration(-option.slackMin) + ' 늦어요' : '여유 ' + toDuration(option.slackMin)
}

async function findPlaces() {
  const link = url.value.trim()
  if (!link) return (error.value = '게시물 링크를 붙여넣어 주세요')
  error.value = ''
  isFinding.value = true
  try {
    const result = await findLinkPlaces(link, caption.value.trim() || null)
    found.value = result.places
    picked.value = {}
    result.places.slice(0, MAX_PICK).forEach((place) => (picked.value[place.name] = true)) // 처음엔 다 골라 둠
  } catch (e) {
    error.value = e.message
    if (e.status === 422) showCaption.value = true
  } finally {
    isFinding.value = false
  }
}

// 고른 장소를 넣어 보기: 바로 넣어도 되면 확인만, 아니면 고르는 화면으로
async function tryFit() {
  error.value = ''
  isFitting.value = true
  try {
    const result = await fitLinkPlaces(props.tripId, pickedPlaces.value, props.position.lat, props.position.lng)
    if (result.fits && result.alternatives.length === 0) {
      fit.value = result
    } else {
      replan.linkFit = { places: pickedPlaces.value, result }
      router.push('/live/link-fit')
    }
  } catch (e) {
    error.value = e.message
  } finally {
    isFitting.value = false
  }
}

async function addNow() {
  error.value = ''
  isFitting.value = true
  try {
    await replan.load(true)
    if (replan.error) throw new Error(replan.error)
    await replan.apply(fit.value.best)
    emit('added', pickedPlaces.value.length + '곳 넣었어요')
  } catch (e) {
    error.value = e.message
    isFitting.value = false
  }
}

// 고르기·풀기 (한 번에 5곳까지)
function togglePick(place) {
  if (!picked.value[place.name] && pickedPlaces.value.length >= MAX_PICK) return (error.value = MAX_PICK + '곳까지 고를 수 있어요')
  error.value = ''
  picked.value[place.name] = !picked.value[place.name]
}

function backToSearch() {
  found.value = null
  picked.value = {}
  fit.value = null
  error.value = ''
}
</script>

<template>
  <div class="dim" style="z-index: 20" @click="emit('close')"></div>
  <section class="lk-sheet" role="dialog" aria-modal="true" aria-label="링크로 장소 추가">
    <div class="lk-head">
      <span class="lk-mark" aria-hidden="true"><i class="ic i-link"></i></span>
      <h2 class="t-sub" style="flex: 1; margin: 0">링크로 추가</h2>
      <button type="button" class="iconbtn" aria-label="닫기" @click="emit('close')"><i class="ic i-close" aria-hidden="true"></i></button>
    </div>

    <!-- 1. 링크 넣기 -->
    <template v-if="!found">
      <form class="lk-form" @submit.prevent="findPlaces">
        <div class="field" :class="{ 'is-error': error }" style="flex: 1; min-width: 0">
          <label for="lk-url" class="sr">게시물 링크</label>
          <div class="field__box"><input id="lk-url" v-model="url" type="text" inputmode="url" placeholder="인스타·유튜브·네이버 링크" autocomplete="off" autocapitalize="off" @input="error = ''" /></div>
        </div>
        <button type="submit" class="btn btn--primary btn--md" :disabled="isFinding">{{ isFinding ? '찾는 중' : '찾기' }}</button>
      </form>
      <textarea v-if="showCaption" v-model="caption" class="lk-caption" rows="3" maxlength="2200" aria-label="게시물 글" placeholder="게시물 글 붙여 넣기"></textarea>
    </template>

    <!-- 3. 바로 넣기 확인 -->
    <template v-else-if="fit">
      <div class="lk-ok">
        <i class="ic i-success c-success" aria-hidden="true"></i>
        <span><b class="t-strong">{{ fit.message }}</b><span class="t-cap tn" style="display: block">{{ slackText(fit.best, fit.fixedName) }}</span></span>
      </div>
      <div class="cta__row">
        <button type="button" class="btn btn--secondary btn--md" @click="fit = null">뒤로</button>
        <button type="button" class="btn btn--primary btn--md" :disabled="isFitting" @click="addNow">{{ isFitting ? '넣는 중' : pickedPlaces.length + '곳 넣기' }}</button>
      </div>
    </template>

    <!-- 2. 장소 고르기 -->
    <template v-else>
      <p class="t-strong" style="margin: 0">{{ found.length ? '넣을 곳을 골라요' : '장소를 찾지 못했어요' }}</p>
      <div v-if="found.length" class="lk-list" role="group" aria-label="찾은 장소">
        <button v-for="place in found" :key="place.name" type="button" role="checkbox" :aria-checked="!!picked[place.name]" class="opt" :class="{ 'is-on': picked[place.name] }" style="text-align: left" @click="togglePick(place)">
          <span class="opt__body"><b class="t-strong">{{ place.name }}</b><span class="t-cap tn">{{ place.category }}</span></span>
          <span class="check" :class="{ 'is-on': picked[place.name] }"><i class="ic ic--s i-check" aria-hidden="true"></i></span>
        </button>
      </div>
      <div class="cta__row">
        <button type="button" class="btn btn--secondary btn--md" @click="backToSearch">뒤로</button>
        <button v-if="found.length" type="button" class="btn btn--primary btn--md" :disabled="!pickedPlaces.length || isFitting" @click="tryFit">{{ isFitting ? '자리 찾는 중' : pickedPlaces.length + '곳 넣기' }}</button>
      </div>
    </template>

    <p v-if="error" class="field__help c-danger" style="margin: 0" role="alert"><i class="ic ic--s i-danger" aria-hidden="true"></i>{{ error }}</p>
  </section>
</template>

<style>
.lk-sheet{position:absolute;left:0;right:0;bottom:0;z-index:21;display:flex;flex-direction:column;gap:12px;max-height:80%;overflow-y:auto;padding:16px 16px 24px;border-radius:20px 20px 0 0;background:var(--surface-raised)}
.lk-head{display:flex;align-items:center;gap:10px}
.lk-mark{width:36px;height:36px;border-radius:12px;display:flex;align-items:center;justify-content:center;color:#fff;background:linear-gradient(135deg,#F58529,#DD2A7B 55%,#8134AF)}
.lk-form{display:flex;gap:8px;align-items:flex-start}
.lk-form .btn{flex:none;height:48px}
.lk-caption{width:100%;box-sizing:border-box;padding:10px 12px;border-radius:12px;border:1px solid var(--line-strong);font:inherit;font-size:16px;resize:vertical}
.lk-list{display:flex;flex-direction:column;gap:8px}
.lk-list .opt{padding:10px 12px}
.lk-ok{display:flex;gap:10px;padding:12px;border-radius:12px;background:var(--success-soft)}
</style>
