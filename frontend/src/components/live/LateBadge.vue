<script setup>
// 대안 화면 위쪽 오른쪽 배지: 그대로 가면 몇 분 늦는지 (안 늦으면 여유)
import { useReplanStore } from '@/stores/replan'

const replan = useReplanStore()
</script>

<template>
  <span v-if="replan.base && replan.lateMin > 0" class="badge badge--warning tn" style="margin-right: 8px"><i class="ic ic--s i-warning" aria-hidden="true"></i>{{ replan.lateMin }}분 늦음</span>
  <span v-else-if="replan.base" class="badge badge--success tn" style="margin-right: 8px"><i class="ic ic--s i-check" aria-hidden="true"></i>여유 {{ replan.base.slackMin }}분</span>
</template>
