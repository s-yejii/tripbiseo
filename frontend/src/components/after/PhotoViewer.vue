<script setup>
// ⑤ 사진·영상 크게 보기 (앨범·회고에서 같이 씀). ◁ ▷로 넘기고, 영상은 바로 재생해요
import { computed, ref } from 'vue'

const props = defineProps({
  photos: { type: Array, required: true }, // PhotoResponse 목록
  start: { type: Number, default: 0 },     // 처음 보여 줄 칸 번호
})
const emit = defineEmits(['close'])

const index = ref(props.start)
const photo = computed(() => props.photos[index.value]) // 지금 보는 한 장

// step = -1(이전) / 1(다음). 처음·끝을 넘으면 그대로
function move(step) {
  const next = index.value + step
  if (next < 0 || next >= props.photos.length) return
  index.value = next
}
</script>

<template>
  <div class="photo-viewer" role="dialog" aria-modal="true" aria-label="사진 크게 보기" @click.self="emit('close')">
    <button type="button" class="photo-viewer__close" aria-label="닫기" @click="emit('close')"><i class="ic i-close" aria-hidden="true"></i></button>

    <!-- :key를 바꿔야 다른 영상으로 넘어갈 때 새로 재생돼요 -->
    <video v-if="photo.mediaType === 'video'" :key="photo.id" :src="photo.url" class="photo-viewer__media" controls autoplay playsinline></video>
    <img v-else :src="photo.url" alt="" class="photo-viewer__media" />

    <button type="button" class="photo-viewer__nav photo-viewer__nav--prev" aria-label="이전" :disabled="index === 0" @click="move(-1)"><i class="ic i-back" aria-hidden="true"></i></button>
    <button type="button" class="photo-viewer__nav photo-viewer__nav--next" aria-label="다음" :disabled="index === photos.length - 1" @click="move(1)"><i class="ic i-next" aria-hidden="true"></i></button>

    <p class="photo-viewer__cap tn">{{ index + 1 }} / {{ photos.length }} · {{ photo.day }}일째 {{ photo.takenAt }}</p>
  </div>
</template>

<style>
.photo-viewer{position:absolute;inset:0;z-index:50;background:rgba(10,14,28,.94);display:flex;align-items:center;justify-content:center}
.photo-viewer__media{max-width:100%;max-height:70%;object-fit:contain;display:block}
.photo-viewer__close{position:absolute;top:56px;right:12px;width:44px;height:44px;border:0;border-radius:50%;background:rgba(255,255,255,.14);color:#fff;display:flex;align-items:center;justify-content:center;cursor:pointer}
.photo-viewer__nav{position:absolute;top:50%;transform:translateY(-50%);width:44px;height:44px;border:0;border-radius:50%;background:rgba(255,255,255,.14);color:#fff;display:flex;align-items:center;justify-content:center;cursor:pointer}
.photo-viewer__nav:disabled{opacity:.25;cursor:default}
.photo-viewer__nav--prev{left:8px}
.photo-viewer__nav--next{right:8px}
.photo-viewer__cap{position:absolute;left:0;right:0;bottom:48px;margin:0;text-align:center;color:rgba(255,255,255,.85);font-size:14px}
</style>
