<script setup>
// ③ "저장" 버튼: 누르면 마이 > 내 장소에 들어가요 (식당 · 카페 · 놀거리는 서버가 나눠요). 한 번 더 누르면 빼요
//   예) <SavePlaceButton name="로라멘" from-name="민지" />  또는  <SavePlaceButton :place-id="place.id" :name="place.name" />
import { computed, onMounted, ref } from 'vue'
import { addMyPlace, findMyPlace, loadMyPlaces, removeMyPlace } from '@/utils/myPlaces'
import { showToast } from '@/utils/toast'

const props = defineProps({
  placeId: { type: String, default: '' },
  name: { type: String, default: '' },
  fromName: { type: String, default: '' }, // 누구 소식에서 저장했는지
})

const isBusy = ref(false)
const saved = computed(() => findMyPlace(props.placeId, props.name))

onMounted(() => loadMyPlaces().catch(() => {})) // 못 읽어도 버튼은 보여요 (누를 때 서버가 답해요)

async function toggle() {
  if (isBusy.value) return
  isBusy.value = true
  try {
    if (saved.value) {
      const name = saved.value.name
      await removeMyPlace(saved.value.placeId)
      showToast(name + ' 내 장소에서 뺐어요')
    } else {
      const place = await addMyPlace({ placeId: props.placeId || null, name: props.name, fromName: props.fromName || null })
      showToast(place.name + ' 내 장소 ' + place.group + '에 저장했어요')
    }
  } catch (e) {
    showToast(e.message)
  }
  isBusy.value = false
}
</script>

<template>
  <button type="button" class="save-place" :class="{ 'is-on': saved }" :aria-pressed="!!saved" :aria-label="name + (saved ? ' 저장 빼기' : ' 내 장소에 저장')" :disabled="isBusy" @click.stop.prevent="toggle">
    <i class="ic ic--s i-bookmark" aria-hidden="true"></i>{{ saved ? '저장됨' : '저장' }}
  </button>
</template>

<style>
.save-place { flex: none; display: inline-flex; align-items: center; gap: 4px; height: 32px; padding: 0 10px; border: 1px solid var(--line); border-radius: 999px; background: var(--surface-raised); font-size: 13px; font-weight: 600; color: var(--ink-muted); cursor: pointer; }
.save-place.is-on { border-color: var(--navy); background: var(--navy-soft); color: var(--navy); }
.save-place:disabled { opacity: .6; }
</style>
