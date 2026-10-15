<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { trips as tripApi } from '@/api'
import { usePlan } from '@/stores/plan'
import { dateLabel, today } from '@/utils/format'
import Modal from '@/components/Modal.vue'
import TripCard from '@/components/TripCard.vue'

const router = useRouter()
const plan = usePlan()
const list = ref([])
const error = ref('')
const showNew = ref(false)

// 진행 중: 시작 버튼을 눌렀거나 오늘이 여행 기간 안 / 예정: 아직 시작 전
const now = today()
const ongoing = computed(() => list.value.filter((t) => t.status === 'ongoing' || (t.status === 'planned' && t.startDate <= now && now <= t.endDate)))
const upcoming = computed(() => list.value.filter((t) => t.status === 'planned' && t.startDate > now).sort((a, b) => a.startDate.localeCompare(b.startDate)))

onMounted(async () => {
  try {
    list.value = await tripApi.list()
  } catch (e) {
    error.value = e.message
  }
})

function startPlan(kind) {
  plan.reset()
  plan.from = kind
  router.push(kind === 'ai' ? '/plan/ai' : '/plan/manual')
}
const open = (t) => router.push(t.status === 'ongoing' ? `/trips/${t.id}/live` : `/trips/${t.id}`)
</script>

<template>
  <main class="page with-tab">
    <header class="home-head">
      <h1 class="logo">여행어때</h1>
      <p class="muted small">{{ dateLabel(now) }}</p>
    </header>

    <button class="plan-btn" @click="showNew = true">
      <span><small>AI 여행 비서</small><b>여행 계획하기</b></span><span class="arrow">→</span>
    </button>

    <p v-if="error" class="error">{{ error }}</p>

    <h2 class="section-title">현재 진행 중인 여행</h2>
    <TripCard v-for="t in ongoing" :key="t.id" :trip="t" class="now" @click="open(t)">
      <button class="btn primary sm" @click.stop="open(t)">{{ t.status === 'ongoing' ? '열기' : '시작' }}</button>
    </TripCard>
    <p v-if="!ongoing.length" class="empty">진행 중인 여행이 없어요</p>

    <h2 class="section-title">예정된 여행</h2>
    <div class="list">
      <TripCard v-for="t in upcoming" :key="t.id" :trip="t" @click="open(t)" />
    </div>
    <p v-if="!upcoming.length" class="empty">예정된 여행이 없어요. 위에서 계획해 보세요</p>

    <Modal v-if="showNew" title="새 여행" @close="showNew = false">
      <button class="option ai" @click="startPlan('ai')">
        <b>AI로 계획하기</b><small>가고 싶은 곳과 시간을 말하면 일정을 짜 드려요</small><span>→</span>
      </button>
      <button class="option" @click="startPlan('manual')">
        <b>직접 계획하기</b><small>장소를 검색하거나 지도에서 눌러 담아요</small><span>›</span>
      </button>
    </Modal>
  </main>
</template>

<style scoped>
.home-head { display: flex; align-items: baseline; justify-content: space-between; margin: 6px 0 18px; }
.logo { font-size: 30px; font-weight: 900; color: var(--blue); letter-spacing: -1px; }
.plan-btn { width: 100%; display: flex; align-items: center; justify-content: space-between; padding: 22px 20px; border-radius: 20px;
  background: linear-gradient(120deg, #2f5bff, #5b7cff); color: #fff; text-align: left; }
.plan-btn small { display: block; font-size: 12px; opacity: .85; margin-bottom: 4px; }
.plan-btn b { font-size: 22px; }
.plan-btn .arrow { width: 36px; height: 36px; border-radius: 50%; background: rgba(255, 255, 255, .2); display: grid; place-items: center; font-size: 18px; }
.list { display: flex; flex-direction: column; gap: 10px; }
.now { border-color: #bbf7d0; background: var(--green-soft); }
.option { position: relative; display: block; width: 100%; margin-top: 12px; padding: 20px 44px 20px 18px; border-radius: 16px; text-align: left; border: 1.5px dashed var(--line); }
.option.ai { border: 0; background: linear-gradient(120deg, #2f5bff, #5b7cff); color: #fff; }
.option b { display: block; font-size: 17px; margin-bottom: 4px; }
.option small { font-size: 13px; opacity: .8; }
.option span { position: absolute; right: 18px; top: 50%; transform: translateY(-50%); font-size: 18px; }
</style>
