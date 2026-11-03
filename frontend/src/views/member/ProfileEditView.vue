<script setup>
// S19-a 정보 수정 (①): 이름 바꾸기 + 비밀번호 바꾸기 (아이디는 못 바꿔요)
//   내 정보: GET /api/members/me · 이름 저장: PUT /api/members/me · 비밀번호: PUT /api/members/me/password
// 프로필 사진은 member 테이블에 사진 칸이 없어서 이름 첫 글자로 보여 줘요.
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import TopBar from '@/components/common/TopBar.vue'
import PageMessage from '@/components/common/PageMessage.vue'
import { changePassword, getMe, updateMe } from '@/api/members'
import { showToast } from '@/utils/toast'

const NAME_MAX = 20
const PASSWORD_MIN = 8
const PASSWORD_MAX = 72

const router = useRouter()
const savedName = ref('') // 서버에 저장된 이름
const name = ref('')
const loginId = ref('')
const isLoading = ref(true)
const error = ref('')
const isSavingName = ref(false)

const currentPassword = ref('')
const newPassword = ref('')
const newPassword2 = ref('')
const isSavingPassword = ref(false)

// onMounted: 화면이 처음 뜰 때 한 번 실행
onMounted(async () => {
  try {
    const user = await getMe()
    savedName.value = user.nickname || user.username // 닉네임이 없으면 아이디
    name.value = savedName.value
    loginId.value = user.username
  } catch (e) {
    if (e.status === 401) {
      router.replace('/login')
      return
    }
    error.value = e.message
  } finally {
    isLoading.value = false
  }
})

const initial = computed(() => (name.value.trim() ? name.value.trim()[0] : '?'))
const nameCheck = computed(() => {
  const text = name.value.trim()
  if (!text) return { cls: 'is-error', msg: '이름을 적어 주세요', ok: false }
  if (text.length > NAME_MAX) return { cls: 'is-error', msg: `이름은 ${NAME_MAX}자까지예요`, ok: false }
  return { cls: '', msg: '', ok: true }
})
const isNameChanged = computed(() => name.value.trim() !== savedName.value)

const passwordCheck = computed(() => {
  if (!newPassword.value) return { cls: '', msg: `${PASSWORD_MIN}~${PASSWORD_MAX}자`, ok: false }
  if (newPassword.value.length < PASSWORD_MIN || newPassword.value.length > PASSWORD_MAX) return { cls: 'is-error', msg: `${PASSWORD_MIN}~${PASSWORD_MAX}자로 정해 주세요`, ok: false }
  return { cls: 'is-success', msg: '쓸 수 있는 비밀번호예요', ok: true }
})
const password2Check = computed(() => {
  if (!newPassword2.value) return { cls: '', msg: '', ok: false }
  if (newPassword2.value !== newPassword.value) return { cls: 'is-error', msg: '새 비밀번호가 서로 달라요', ok: false }
  return { cls: 'is-success', msg: '맞아요', ok: true }
})
const canChangePassword = computed(() => currentPassword.value && passwordCheck.value.ok && password2Check.value.ok && !isSavingPassword.value)

async function saveName() {
  if (!nameCheck.value.ok || !isNameChanged.value || isSavingName.value) return
  isSavingName.value = true
  try {
    const user = await updateMe(name.value.trim())
    savedName.value = user.nickname
    name.value = user.nickname
    showToast('이름을 바꿨어요')
  } catch (e) {
    showToast(e.message)
  } finally {
    isSavingName.value = false
  }
}

async function savePassword() {
  if (!canChangePassword.value) return
  isSavingPassword.value = true
  try {
    await changePassword(currentPassword.value, newPassword.value)
    currentPassword.value = ''
    newPassword.value = ''
    newPassword2.value = ''
    showToast('비밀번호를 바꿨어요 · 다음 로그인부터 새 비밀번호를 써요')
  } catch (e) {
    showToast(e.message)
  } finally {
    isSavingPassword.value = false
  }
}
</script>

<template>
  <div class="scr scr-ProfileEdit">
    <div class="app app--white">
      <TopBar title="정보 수정" back="/my" />

      <PageMessage v-if="isLoading" is-loading title="내 정보를 불러오는 중이에요" />
      <PageMessage v-else-if="error" icon="i-warning" title="내 정보를 불러오지 못했어요" :text="error" to="/my" button="마이로" />

      <div v-else class="scroll" style="padding-top: 8px; gap: 20px; bottom: 0; padding-bottom: 32px">
        <span class="ava" aria-hidden="true">{{ initial }}</span>

        <!-- 기본 정보 -->
        <section class="pe-sec" aria-labelledby="pe-basic">
          <h2 id="pe-basic" class="t-sub">기본 정보</h2>
          <div class="field" :class="nameCheck.cls">
            <label class="field__label" for="pe-name">이름</label>
            <div class="field__box"><input id="pe-name" v-model="name" type="text" autocomplete="nickname" @keydown.enter="saveName" /></div>
            <p v-if="nameCheck.msg" class="field__help">{{ nameCheck.msg }}</p>
          </div>
          <div class="field is-disabled">
            <label class="field__label" for="pe-id">아이디</label>
            <div class="field__box"><input id="pe-id" type="text" :value="loginId" disabled /><i class="ic i-lock subtle" aria-hidden="true" style="margin-right: 12px"></i></div>
            <p class="field__help">아이디는 바꿀 수 없어요</p>
          </div>
          <button type="button" class="btn btn--primary btn--lg btn--block" :disabled="!nameCheck.ok || !isNameChanged || isSavingName" @click="saveName">
            {{ isSavingName ? '저장하는 중…' : '이름 저장' }}
          </button>
        </section>

        <!-- 비밀번호 바꾸기 -->
        <form class="pe-sec" aria-labelledby="pe-pw" @submit.prevent="savePassword">
          <h2 id="pe-pw" class="t-sub">비밀번호 바꾸기</h2>
          <input type="text" :value="loginId" autocomplete="username" hidden />
          <div class="field">
            <label class="field__label" for="pe-cur">지금 비밀번호</label>
            <div class="field__box"><input id="pe-cur" v-model="currentPassword" type="password" autocomplete="current-password" /></div>
          </div>
          <div class="field" :class="passwordCheck.cls">
            <label class="field__label" for="pe-new">새 비밀번호</label>
            <div class="field__box"><input id="pe-new" v-model="newPassword" type="password" autocomplete="new-password" /></div>
            <p class="field__help">{{ passwordCheck.msg }}</p>
          </div>
          <div class="field" :class="password2Check.cls">
            <label class="field__label" for="pe-new2">새 비밀번호 확인</label>
            <div class="field__box"><input id="pe-new2" v-model="newPassword2" type="password" autocomplete="new-password" /></div>
            <p v-if="password2Check.msg" class="field__help">{{ password2Check.msg }}</p>
          </div>
          <button type="submit" class="btn btn--secondary btn--lg btn--block" :disabled="!canChangePassword">
            {{ isSavingPassword ? '바꾸는 중…' : '비밀번호 바꾸기' }}
          </button>
        </form>
      </div>
    </div>
  </div>
</template>

<style>
.scr-ProfileEdit .ava{width:84px;height:84px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-weight:700;font-size:30px;background:var(--navy-soft);color:var(--navy);margin:0 auto;flex:none}
.scr-ProfileEdit .pe-sec{display:flex;flex-direction:column;gap:14px}
.scr-ProfileEdit .pe-sec+.pe-sec{padding-top:20px;border-top:1px solid var(--line)}
.scr-ProfileEdit .pe-sec .t-sub{margin:0}
</style>
