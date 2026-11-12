<script setup>
// 캡처 사진 위에 덮는 스캔 모션 (③, 11/12): 읽는 중이면 파란 스캔 빛이 위아래로, 다 읽으면 모서리가 초록으로 + 체크
// 예약 캡처(ReserveCaptureView)에서 쓰고, ⑤ 영수증 화면에서도 같은 모양으로 가져다 쓸 수 있게 따로 뺐어요
defineProps({
  state: { type: String, default: 'idle' }, // idle(안 보임) · reading(읽는 중) · done(다 읽음)
})
</script>

<template>
  <div v-if="state !== 'idle'" class="scan" :class="'scan--' + state" aria-hidden="true">
    <i class="scan__corner"></i><i class="scan__corner"></i><i class="scan__corner"></i><i class="scan__corner"></i>
    <div v-if="state === 'reading'" class="scan__beam"></div>
    <div v-else class="scan__check">
      <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.5l4.2 4.2L19 7" /></svg>
    </div>
  </div>
</template>

<style>
.scan{position:absolute;inset:0;border-radius:inherit;overflow:hidden;pointer-events:none;background:rgba(10,16,30,.28)}
.scan__corner{position:absolute;width:26px;height:26px;border:3px solid #fff;transition:border-color .25s}
.scan__corner:nth-child(1){left:10px;top:10px;border-right:0;border-bottom:0;border-top-left-radius:12px}
.scan__corner:nth-child(2){right:10px;top:10px;border-left:0;border-bottom:0;border-top-right-radius:12px}
.scan__corner:nth-child(3){left:10px;bottom:10px;border-right:0;border-top:0;border-bottom-left-radius:12px}
.scan__corner:nth-child(4){right:10px;bottom:10px;border-left:0;border-top:0;border-bottom-right-radius:12px}
.scan--done .scan__corner{border-color:#10B981;animation:scan-snap .3s ease-out}
.scan__beam{position:absolute;left:12px;right:12px;top:0;height:70px;background:linear-gradient(180deg,rgba(96,165,250,0) 0%,rgba(96,165,250,.3) 88%,rgba(191,219,254,1) 100%);filter:drop-shadow(0 4px 8px rgba(59,130,246,.6));animation:scan-beam 1.5s cubic-bezier(.45,0,.55,1) infinite alternate}
.scan__check{position:absolute;left:50%;top:50%;width:64px;height:64px;margin:-32px 0 0 -32px;border-radius:50%;background:#10B981;display:flex;align-items:center;justify-content:center;box-shadow:0 0 0 8px rgba(16,185,129,.22);animation:scan-pop .3s ease-out}
.scan__check svg{width:32px;height:32px}
@keyframes scan-beam{from{transform:translateY(-64px)}to{transform:translateY(200px)}}
@keyframes scan-snap{from{transform:scale(1.15)}to{transform:scale(1)}}
@keyframes scan-pop{from{transform:scale(.6);opacity:0}to{transform:scale(1);opacity:1}}
</style>
