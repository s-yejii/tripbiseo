<script setup>
// S02-c 아이디·비밀번호 찾기 (①): 가입한 이메일로 받은 인증번호를 넣어요
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'
import { showToast } from '@/utils/toast'

const router = useRouter()
const findPassword = ref(false) // false: 아이디 찾기 / true: 비밀번호 찾기

function done() {
  showToast(findPassword.value ? '새 비밀번호로 로그인해 주세요' : '아이디는 haneul_trip 이에요')
  router.push('/login')
}
</script>

<template>
  <div class="scr scr-FindAccount">
    <div class="app app--white">
      <TopBar title="계정 찾기" back="/login" />

      <div class="scroll" style="padding: 8px 24px 0; gap: 24px">
        <div class="seg" role="tablist" aria-label="찾을 항목">
          <button type="button" role="tab" :aria-selected="!findPassword" :class="{ 'is-on': !findPassword }" @click="findPassword = false">아이디 찾기</button>
          <button type="button" role="tab" :aria-selected="findPassword" :class="{ 'is-on': findPassword }" @click="findPassword = true">비밀번호 찾기</button>
        </div>

        <div v-if="findPassword" class="field">
          <label class="field__label" for="fa-id">아이디</label>
          <div class="field__box"><input id="fa-id" type="text" placeholder="가입한 아이디" /></div>
        </div>
        <div class="field is-success">
          <label class="field__label" for="fa-mail">가입한 이메일</label>
          <div class="field__box"><input id="fa-mail" type="email" value="haneul@mail.com" /><i class="ic i-check c-success" aria-hidden="true" style="margin-right: 12px"></i></div>
        </div>
        <div class="field is-focus">
          <label class="field__label" for="fa-code">인증번호</label>
          <div class="field__box"><input id="fa-code" type="text" inputmode="numeric" placeholder="6자리 숫자" /><span class="t-cap tn c-danger" style="margin-right: 12px">02:58</span></div>
          <p class="field__help">메일로 보낸 6자리 숫자를 넣어 주세요</p>
        </div>
      </div>

      <div class="cta cta--white">
        <button type="button" class="btn btn--primary btn--lg btn--block" @click="done">{{ findPassword ? '새 비밀번호 만들기' : '아이디 확인하기' }}</button>
        <button type="button" class="textlink" @click="showToast('인증번호를 다시 보냈어요')">인증번호 다시 받기</button>
      </div>
    </div>
  </div>
</template>
