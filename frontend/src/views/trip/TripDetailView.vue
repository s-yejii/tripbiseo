<script setup>
// S11 여행 상세 · 여행 전 (②): 다가오는 여행(?id=)의 일정과 챙길 것
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import TripPage from '@/components/trip/TripPage.vue'
import PackList from '@/components/trip/PackList.vue'
import PageMessage from '@/components/common/PageMessage.vue'
import TopBar from '@/components/common/TopBar.vue'
import { useTripDetail } from '@/utils/useTripDetail'
import { tripBadge } from '@/utils/tripView'

const route = useRoute()
const { saved, view, isLoading, error, load } = useTripDetail()
onMounted(() => load(route.query.id))
</script>

<template>
  <div class="scr scr-TripDetail">
    <TripPage v-if="view" :trip="view" :badge="tripBadge(saved)" back="/trips">
      <template #below>
        <!-- 챙길 것: API가 아직 "확인 필요"라 이 화면에서만 적어 둬요 (새로고침하면 사라짐) -->
        <PackList :items="[]" />
      </template>
      <template #cta>
        <RouterLink :to="'/plan/edit?id=' + view.id" class="btn btn--primary btn--lg btn--block"><i class="ic i-edit" aria-hidden="true"></i>일정 편집하기</RouterLink>
      </template>
    </TripPage>
    <div v-else class="app">
      <TopBar title="여행 상세" back="/trips" />
      <div class="scroll">
        <PageMessage v-if="isLoading" is-loading title="여행을 불러오는 중이에요" />
        <PageMessage v-else-if="error" icon="i-warning" title="여행을 불러오지 못했어요" :text="error" button="다시 시도" @retry="load(route.query.id)" />
        <PageMessage v-else title="여행을 찾을 수 없어요" text="내 여행에서 볼 여행을 골라 주세요" to="/trips" button="내 여행 보기" />
      </div>
    </div>
  </div>
</template>
