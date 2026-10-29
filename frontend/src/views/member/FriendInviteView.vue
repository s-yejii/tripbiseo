<script setup>
// S19-c 친구 초대 (①): 카카오톡·링크로 초대하거나, 아이디로 찾아 친구 요청을 보내요
import { computed, ref } from 'vue'
import TopBar from '@/components/common/TopBar.vue'
import { showToast } from '@/utils/toast'

// 가짜 데이터: 아이디 검색 결과 (백엔드가 붙으면 member · friend 테이블)
const people = ref([
  { name: '민지', loginId: 'minji_go', meta: '이미 친구', friend: true },
  { name: '민수', loginId: 'minji_bro', meta: '함께 아는 친구 1', requested: false },
  { name: '지민', loginId: 'minji_jm', meta: '', requested: false },
])
const keyword = ref('minji')
const copied = ref(false)

// 아이디에 검색어가 들어간 사람만 보여 줘요
const found = computed(() => people.value.filter((p) => p.loginId.includes(keyword.value.trim())))
</script>

<template>
  <div class="scr scr-FriendInvite">
    <div class="app app--white">
      <TopBar title="친구 초대" back="/my" />

      <div class="scroll" style="padding-top: 4px; gap: 18px; bottom: 34px">
        <div style="display: flex; flex-direction: column; gap: 8px">
          <button type="button" class="btn btn--kakao btn--lg btn--block" @click="showToast('카카오톡 공유 창을 열어요')"><i class="ic i-chat" aria-hidden="true"></i>카카오톡으로 초대</button>
          <button type="button" class="btn btn--secondary btn--lg btn--block" @click="copied = true">
            <i class="ic" :class="copied ? 'i-check' : 'i-link'" aria-hidden="true"></i>{{ copied ? '링크를 복사했어요' : '초대 링크 복사' }}
          </button>
        </div>

        <p class="divlabel">아이디로 찾기</p>
        <label class="search" for="fi-q" style="flex: none">
          <i class="ic i-search" aria-hidden="true"></i>
          <input id="fi-q" v-model="keyword" type="search" style="border: 0; outline: 0; background: transparent; flex: 1; min-width: 0; font-size: 16px" />
        </label>

        <div class="group">
          <div v-for="p in found" :key="p.loginId" class="row">
            <span class="ava">{{ p.name[0] }}</span>
            <span class="row__body"><span class="row__title">{{ p.name }}</span><span class="row__meta">{{ p.loginId }}{{ p.meta ? ' · ' + p.meta : '' }}</span></span>
            <span v-if="p.friend" class="badge badge--success"><i class="ic ic--s i-check" aria-hidden="true"></i>친구</span>
            <button v-else type="button" class="btn btn--sm" :class="p.requested ? 'btn--secondary' : 'btn--primary'" @click="p.requested = !p.requested">{{ p.requested ? '요청됨' : '친구 요청' }}</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style>
.scr-FriendInvite .ava{width:40px;height:40px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-weight:700;font-size:14px;flex:none;background:var(--navy-soft);color:var(--navy)}
</style>
