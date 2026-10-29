<script setup>
// 폰 잠금화면 틀: 배경 · 날짜와 시계 · (여행 중이면) 진행 배너 · 알림들(slot) · 아래 안내 문구
// 잠금화면 5개(S20-b~f)가 같이 써요. 알림 한 개는 LockNoti로 만들어요
defineProps({
  time: { type: String, required: true }, // 큰 시계 '16:21'
  hint: { type: String, default: '눌러서 열기 · 길게 누르면 바로 답할 수 있어요' },
  // 여행 중 배너 (없으면 안 보여요)
  // 예) { progress: 62, next: '다음 · 성내동 소품샵', badge: 'warning', badgeIcon: 'i-warning', badgeText: '14분 늦음' }
  trip: { type: Object, default: null },
})
</script>

<template>
  <div class="app lk-screen" style="background: #101626">
    <div class="lk-bg" aria-hidden="true"><img src="/img/alley.jpg" alt="" /></div>
    <div class="lk-clock"><p>10월 24일 토요일</p><b>{{ time }}</b></div>

    <div class="lk-stack">
      <RouterLink v-if="trip" to="/live" class="lk-la" aria-label="진행 중인 여행, 잔나비 공연 데이트">
        <span class="lk-la__h"><b>잔나비 공연 데이트</b><span class="lk-la__t"><span style="opacity: 0.8">3/6곳</span><i class="ic i-lock" aria-hidden="true"></i>18:30</span></span>
        <span class="lk-track" aria-hidden="true">
          <i :style="{ width: trip.progress + '%' }"></i>
          <span :style="{ left: trip.progress + '%', background: 'var(--coral)', border: '2px solid #fff' }"></span>
          <span style="left: 100%; background: #fff"></span>
        </span>
        <span class="lk-la__f"><span>{{ trip.next }}</span><span class="badge tn" :class="'badge--' + trip.badge"><i class="ic ic--s" :class="trip.badgeIcon" aria-hidden="true"></i>{{ trip.badgeText }}</span></span>
      </RouterLink>
      <slot />
    </div>

    <p class="lk-hint">{{ hint }}</p>
    <div class="lk-bottom" aria-hidden="true"><span><i class="ic i-sun"></i></span><span><i class="ic i-camera"></i></span></div>
    <span class="lk-home" aria-hidden="true"></span>
  </div>
</template>

<style>
.lk-screen .lk-bg{position:absolute;inset:0;background:#101626}
.lk-screen .lk-bg img{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;opacity:.55}
.lk-screen .lk-clock{position:absolute;left:0;right:0;top:70px;text-align:center;color:#fff}
.lk-screen .lk-clock p{margin:0;font-size:15px;font-weight:500;opacity:.9}
.lk-screen .lk-clock b{display:block;font-size:76px;line-height:84px;font-weight:600;font-variant-numeric:tabular-nums}
.lk-screen .lk-stack{position:absolute;left:12px;right:12px;top:250px;display:flex;flex-direction:column;gap:10px}
.lk-screen .lk-la{padding:14px;border-radius:var(--radius-xl);background:rgba(16,22,38,.82);color:#fff;display:flex;flex-direction:column;gap:10px;text-decoration:none}
.lk-screen .lk-la__h{display:flex;align-items:center;justify-content:space-between;gap:8px}
.lk-screen .lk-la__h b{font-size:15px;line-height:21px}
.lk-screen .lk-la__t{display:inline-flex;align-items:center;gap:4px;font-size:13px;font-variant-numeric:tabular-nums}
.lk-screen .lk-la__t .ic{width:14px;height:14px}
.lk-screen .lk-track{position:relative;height:6px;border-radius:999px;background:rgba(255,255,255,.25)}
.lk-screen .lk-track i{position:absolute;left:0;top:0;bottom:0;border-radius:999px;background:#fff}
.lk-screen .lk-track span{position:absolute;top:50%;width:14px;height:14px;margin:-7px 0 0 -7px;border-radius:50%}
.lk-screen .lk-la__f{display:flex;justify-content:space-between;align-items:center;gap:8px;font-size:13px}
.lk-screen .lk-bottom{position:absolute;left:44px;right:44px;bottom:42px;display:flex;justify-content:space-between}
.lk-screen .lk-bottom span{width:50px;height:50px;border-radius:50%;background:rgba(255,255,255,.18);display:flex;align-items:center;justify-content:center;color:#fff}
.lk-screen .lk-home{position:absolute;left:50%;bottom:10px;width:134px;height:5px;margin-left:-67px;border-radius:3px;background:#fff;opacity:.9}
.lk-screen .lk-hint{position:absolute;left:0;right:0;bottom:104px;text-align:center;font-size:12px;color:#fff;opacity:.7}
</style>
