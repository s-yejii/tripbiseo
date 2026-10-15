<script setup>
// 비·눈 내리는 효과 (3초 보이고 1초에 걸쳐 사라짐). 동작 줄이기 설정이면 그리지 않음
import { onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({ snow: Boolean })
const el = ref(null)
let frame

onMounted(() => {
  const c = el.value
  if (matchMedia('(prefers-reduced-motion: reduce)').matches) return
  c.width = c.clientWidth
  c.height = c.clientHeight
  const g = c.getContext('2d')
  const drops = Array.from({ length: props.snow ? 70 : 110 }, () => ({
    x: Math.random() * c.width, y: Math.random() * -c.height, v: props.snow ? 1 + Math.random() * 1.5 : 7 + Math.random() * 6, s: props.snow ? 2 + Math.random() * 2.5 : 10 + Math.random() * 8,
  }))
  const t0 = performance.now()
  const tick = (t) => {
    const age = (t - t0) / 1000
    g.clearRect(0, 0, c.width, c.height)
    if (age > 4) return
    g.globalAlpha = age < 3 ? 1 : 4 - age
    g.strokeStyle = props.snow ? 'rgba(120,140,170,.4)' : 'rgba(70,110,200,.45)'
    g.fillStyle = 'rgba(255,255,255,.95)'
    for (const d of drops) {
      d.y += d.v
      d.x += props.snow ? Math.sin((d.y + d.x) / 30) * 0.6 : -1.2
      if (d.y > c.height) (d.y = -10), (d.x = Math.random() * c.width)
      g.beginPath()
      if (props.snow) g.arc(d.x, d.y, d.s, 0, 7), g.fill(), g.stroke()
      else g.moveTo(d.x, d.y), g.lineTo(d.x - 2, d.y + d.s), g.stroke()
    }
    frame = requestAnimationFrame(tick)
  }
  frame = requestAnimationFrame(tick)
})
onBeforeUnmount(() => cancelAnimationFrame(frame))
</script>

<template>
  <canvas ref="el" class="fx" aria-hidden="true" />
</template>

<style scoped>
.fx { position: absolute; inset: 0; width: 100%; height: 100%; z-index: 12; pointer-events: none; }
</style>
