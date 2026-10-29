<script setup>
// 사진 위에 글씨가 올라간 카드 (테마 코스, 앨범, 지금 여행 중 카드)
// 예) <PhotoCard to="/theme" img="/img/alley.jpg" title="천호 골목" sub="5곳 · 하루" badge="노포" />
defineProps({
  to: { type: String, required: true },
  img: { type: String, required: true },
  title: { type: String, default: '' },
  sub: { type: String, default: '' },
  badge: { type: String, default: '' },
  height: { type: Number, default: 150 },
})
</script>

<template>
  <RouterLink :to="to" class="photo" :style="{ height: height + 'px' }">
    <img :src="img" alt="" />
    <slot name="badge"><span v-if="badge" class="badge badge--navy">{{ badge }}</span></slot>
    <slot>
      <span class="photo__txt"><b>{{ title }}</b><small class="tn">{{ sub }}</small></span>
    </slot>
  </RouterLink>
</template>
