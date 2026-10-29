<script setup>
// S19-a 정보 수정 (①): 프로필 사진과 이름을 바꿔요 (아이디는 못 바꿔요)
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'
import { showToast } from '@/utils/toast'

const router = useRouter()

// 가짜 데이터: 내 정보 (백엔드가 붙으면 member 테이블)
const name = ref('하늘')
const loginId = 'haneul_trip'
const photo = ref('') // 고른 사진 미리보기 주소

function onPhoto(e) {
  const file = e.target.files[0]
  if (file) photo.value = URL.createObjectURL(file)
}

function save() {
  router.push('/my')
  showToast('정보를 저장했어요')
}
</script>

<template>
  <div class="scr scr-ProfileEdit">
    <div class="app app--white">
      <TopBar title="정보 수정" back="/my" />

      <div class="scroll" style="padding-top: 8px; gap: 20px; bottom: 102px">
        <label class="ava" for="pe-photo" aria-label="프로필 사진 바꾸기">
          <img v-if="photo" :src="photo" alt="" />
          <template v-else>{{ name[0] }}</template>
          <span class="cam" aria-hidden="true"><i class="ic ic--s i-camera" aria-hidden="true"></i></span>
          <input id="pe-photo" type="file" accept="image/*" style="position: absolute; width: 1px; height: 1px; opacity: 0" @change="onPhoto" />
        </label>

        <div class="field">
          <label class="field__label" for="pe-name">이름</label>
          <div class="field__box"><input id="pe-name" v-model="name" type="text" /></div>
        </div>
        <div class="field is-disabled">
          <label class="field__label" for="pe-id">아이디</label>
          <div class="field__box"><input id="pe-id" type="text" :value="loginId" disabled /><i class="ic i-lock subtle" aria-hidden="true" style="margin-right: 12px"></i></div>
        </div>
      </div>

      <div class="cta cta--white">
        <button type="button" class="btn btn--primary btn--lg btn--block" @click="save">저장</button>
      </div>
    </div>
  </div>
</template>

<style>
.scr-ProfileEdit .ava{position:relative;width:84px;height:84px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-weight:700;font-size:30px;background:var(--navy-soft);color:var(--navy);margin:0 auto;border:0;cursor:pointer;font-family:inherit}
.scr-ProfileEdit .ava img{width:100%;height:100%;border-radius:50%;object-fit:cover}
.scr-ProfileEdit .ava .cam{position:absolute;right:-2px;bottom:-2px;width:32px;height:32px;border-radius:50%;background:var(--surface-raised);border:1px solid var(--line);display:flex;align-items:center;justify-content:center;color:var(--ink)}
</style>
