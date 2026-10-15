<script setup>
// 개발용 API 키 관리 (관리자). 저장하면 서버 재시작 없이 바로 적용
import { onMounted, ref } from 'vue'
import { config } from '@/api'
import AppBar from '@/components/AppBar.vue'

const keys = ref([])
const input = ref({})
const msg = ref('')
const error = ref('')
onMounted(async () => {
  try {
    keys.value = await config.keys()
  } catch (e) {
    error.value = e.message
  }
})
async function save() {
  error.value = msg.value = ''
  const changed = Object.fromEntries(Object.entries(input.value).filter(([, v]) => v !== undefined && v !== null))
  if (!Object.keys(changed).length) return (msg.value = '바꾼 값이 없어요')
  try {
    keys.value = await config.saveKeys(changed)
    input.value = {}
    msg.value = '저장했어요. 바로 적용돼요.'
  } catch (e) {
    error.value = e.message
  }
}
</script>

<template>
  <AppBar title="API 키 관리" />
  <div class="page keys">
    <p class="muted small">개발용 화면이에요. 키는 서버 DB(api_key)에만 저장되고 화면에는 앞뒤 4글자만 보여요. 빈칸으로 저장하면 지워져요.</p>
    <section v-for="k in keys" :key="k.name" class="card">
      <div class="row"><b class="grow">{{ k.label }}</b><span class="chip" :class="k.set ? 'green' : 'red'">{{ k.set ? '설정됨' : '없음' }}</span></div>
      <p class="muted small">{{ k.help }}</p>
      <p v-if="k.masked" class="small mono">현재: {{ k.masked }}</p>
      <input v-model="input[k.name]" class="field" :placeholder="k.set ? '바꿀 때만 입력' : '키 입력'" autocomplete="off" />
    </section>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="msg" class="ok small">{{ msg }}</p>
    <button class="btn primary block" @click="save">저장</button>
  </div>
</template>

<style scoped>
.keys { display: flex; flex-direction: column; gap: 10px; }
.card { display: flex; flex-direction: column; gap: 6px; }
.mono { font-family: ui-monospace, monospace; }
.ok { color: var(--green); }
</style>
