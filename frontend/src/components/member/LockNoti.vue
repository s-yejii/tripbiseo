<script setup>
// 잠금화면 알림 한 개: 앱 아이콘 · 앱 이름과 시간 · 제목 · 설명 · (사진) · (아래 버튼들은 slot)
// 예) <LockNoti to="/live" icon="i-walk" title="10분 뒤 출발할 시간이에요" desc="..." is-new />
defineProps({
  to: { type: String, default: '' }, // 누르면 갈 화면 (없으면 눌러도 안 움직여요)
  app: { type: String, default: '여행어때' }, // '문자'면 초록 문자 아이콘
  when: { type: String, default: '지금' },
  icon: { type: String, default: '' }, // 제목 앞 작은 아이콘
  title: { type: String, required: true },
  desc: { type: String, default: '' },
  thumb: { type: String, default: '' },
  isNew: { type: Boolean, default: false }, // 방금 온 알림 (위에서 내려와요)
  old: { type: Boolean, default: false }, // 지난 알림 (조금 흐리게)
})
</script>

<template>
  <div class="lk-n" :class="{ 'lk-in': isNew, 'lk-old': old }" :aria-label="title">
    <component :is="to ? 'RouterLink' : 'span'" :to="to || undefined" class="lk-n__row">
      <span class="lk-app" :class="{ 'lk-app--sms': app === '문자' }" aria-hidden="true"><i class="ic" :class="app === '문자' ? 'i-send' : 'i-pin'"></i></span>
      <span class="lk-b">
        <span class="lk-h"><b>{{ app }}</b><span>{{ when }}</span></span>
        <span class="lk-t"><i v-if="icon" class="ic ic--s lk-kind" :class="icon" aria-hidden="true"></i>{{ title }}</span>
        <span v-if="desc" class="lk-d">{{ desc }}</span>
      </span>
      <img v-if="thumb" class="lk-thumb" :src="thumb" alt="" />
    </component>
    <span v-if="$slots.default" class="lk-acts"><slot /></span>
  </div>
</template>

<style>
.lk-n{display:flex;flex-direction:column;border-radius:var(--radius-xl);background:rgba(255,255,255,.92);color:var(--ink);text-decoration:none;overflow:hidden;cursor:pointer}
.lk-n__row{display:flex;gap:12px;align-items:flex-start;padding:12px 14px;color:inherit;text-decoration:none}
a.lk-n__row:hover{background:rgba(255,255,255,.97)}
.lk-app{flex:none;width:36px;height:36px;border-radius:9px;background:var(--navy);color:#fff;display:flex;align-items:center;justify-content:center}
.lk-app .ic{width:20px;height:20px}
.lk-app--sms{background:#34C759}
.lk-b{flex:1;min-width:0;display:flex;flex-direction:column;gap:2px}
.lk-h{display:flex;justify-content:space-between;font-size:12px;line-height:16px;color:var(--ink-muted)}
.lk-h b{font-weight:600}
.lk-t{font-size:15px;line-height:21px;font-weight:700;display:flex;align-items:center;gap:4px}
.lk-d{font-size:13px;line-height:18px;color:var(--ink-muted)}
.lk-kind{width:15px;height:15px;flex:none;color:var(--navy)}
.lk-kind.i-warning{color:var(--warning)}
.lk-thumb{flex:none;width:44px;height:44px;border-radius:10px;object-fit:cover}
.lk-acts{display:grid;grid-auto-flow:column;grid-auto-columns:minmax(0,1fr);border-top:1px solid rgba(24,33,58,.1)}
.lk-acts a,.lk-acts button{padding:11px 8px;border:0;background:none;text-align:center;font:inherit;font-size:14px;font-weight:600;color:var(--ink);text-decoration:none;cursor:pointer}
.lk-acts > * + *{border-left:1px solid rgba(24,33,58,.1)}
.lk-acts a:hover,.lk-acts button:hover{background:rgba(24,33,58,.05)}
.lk-acts .is-main{color:var(--navy);font-weight:700}
.lk-old{opacity:.9}
.lk-in{animation:lkIn .5s cubic-bezier(.2,.9,.25,1.05) both}
@keyframes lkIn{from{transform:translateY(-16px);opacity:0}to{transform:none;opacity:1}}
</style>
