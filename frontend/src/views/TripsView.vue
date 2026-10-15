<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { trips as tripApi } from '@/api'
import { today } from '@/utils/format'
import Confirm from '@/components/Confirm.vue'
import TripCard from '@/components/TripCard.vue'

const router = useRouter()
const list = ref([])
const error = ref('')
const now = today()
const groups = computed(() => [
  ['진행 중', list.value.filter((t) => t.status === 'ongoing')],
  ['예정', list.value.filter((t) => t.status === 'planned' && t.endDate >= now)],
  ['지난 여행', list.value.filter((t) => t.status === 'done' || (t.status === 'planned' && t.endDate < now))],
])

async function load() {
  try {
    list.value = await tripApi.list()
  } catch (e) {
    error.value = e.message
  }
}
const asking = ref(null)
async function remove(t) {
  asking.value = null
  try {
    await tripApi.remove(t.id)
    list.value = list.value.filter((x) => x.id !== t.id)
  } catch (e) {
    error.value = e.message
  }
}
const open = (t) => router.push(t.status === 'ongoing' ? `/trips/${t.id}/live` : t.status === 'done' ? `/trips/${t.id}/retro` : `/trips/${t.id}`)
onMounted(load)
</script>

<template>
  <main class="page with-tab">
    <h1 class="title">내 여행</h1>
    <p v-if="error" class="error">{{ error }}</p>
    <template v-for="[name, items] in groups" :key="name">
      <template v-if="items.length">
        <h2 class="section-title">{{ name }}</h2>
        <div class="list">
          <TripCard v-for="t in items" :key="t.id" :trip="t" @click="open(t)">
            <button class="del" aria-label="삭제" @click.stop="asking = t">🗑</button>
          </TripCard>
        </div>
      </template>
    </template>
    <p v-if="!list.length && !error" class="empty">아직 여행이 없어요</p>
    <Confirm v-if="asking" title="여행 삭제" :text="`'${asking.title}' 여행을 삭제할까요? 일정과 기록이 모두 지워져요.`" ok="삭제" danger @ok="remove(asking)" @close="asking = null" />
  </main>
</template>

<style scoped>
.title { font-size: 24px; font-weight: 800; margin: 8px 0; }
.list { display: flex; flex-direction: column; gap: 10px; }
.del { width: 32px; height: 32px; border-radius: 50%; opacity: .5; }
</style>
