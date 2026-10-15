<script setup>
// 장소 검색 창 (DB). 고르면 pick
import { ref } from 'vue'
import { places } from '@/api'
import { placeIcon } from '@/utils/format'
import Modal from './Modal.vue'

const props = defineProps({ title: { type: String, default: '장소 추가' }, near: Object })
const emit = defineEmits(['pick', 'close'])
const keyword = ref('')
const list = ref([])
const error = ref('')
async function search() {
  error.value = ''
  try {
    list.value = await places.search(keyword.value.trim(), props.near?.lat, props.near?.lng)
    if (!list.value.length) error.value = '저장된 장소 중에 없어요. 다른 이름으로 찾아보세요.'
  } catch (e) {
    error.value = e.message
  }
}
</script>

<template>
  <Modal :title="title" @close="emit('close')">
    <form class="row search" @submit.prevent="search">
      <input v-model="keyword" class="field grow" placeholder="장소 이름" required />
      <button class="btn primary sm">검색</button>
    </form>
    <p v-if="error" class="muted small">{{ error }}</p>
    <ul class="list">
      <li v-for="p in list" :key="p.id" @click="emit('pick', p)">
        <span>{{ placeIcon(p.category) }}</span>
        <div class="grow"><b>{{ p.name }}</b><small class="muted ellipsis">{{ p.category }} · {{ p.address }}</small></div>
        <span class="add">＋</span>
      </li>
    </ul>
  </Modal>
</template>

<style scoped>
.search { margin: 14px 0 8px; gap: 8px; }
.list { list-style: none; margin: 0; padding: 0; }
.list li { display: flex; align-items: center; gap: 10px; padding: 12px 4px; border-bottom: 1px solid var(--line); cursor: pointer; }
.list b, .list small { display: block; }
.add { color: var(--blue); font-size: 20px; }
</style>
