<template>
  <a-row :gutter="[16, 16]">
    <a-col :xs="24" :md="12" :xl="6">
      <a-card title="用户总数" :loading="loading">{{ users.length }}</a-card>
    </a-col>
    <a-col :xs="24" :md="12" :xl="6">
      <a-card title="管理员数量" :loading="loading">{{ adminCount }}</a-card>
    </a-col>
    <a-col :xs="24" :md="12" :xl="6">
      <a-card title="入库任务总量" :loading="loading">{{ metrics?.totalJobs ?? 0 }}</a-card>
    </a-col>
    <a-col :xs="24" :md="12" :xl="6">
      <a-card title="入库失败率" :loading="loading">{{ failureRateText }}</a-card>
    </a-col>
  </a-row>
  <a-row :gutter="[16, 16]" style="margin-top: 4px">
    <a-col :xs="24" :md="12" :xl="6">
      <a-card title="成功任务数" :loading="loading">{{ metrics?.successJobs ?? 0 }}</a-card>
    </a-col>
    <a-col :xs="24" :md="12" :xl="6">
      <a-card title="失败任务数" :loading="loading">{{ metrics?.failedJobs ?? 0 }}</a-card>
    </a-col>
    <a-col :xs="24" :md="12" :xl="6">
      <a-card title="平均耗时(秒)" :loading="loading">{{ avgDurationText }}</a-card>
    </a-col>
    <a-col :xs="24" :md="12" :xl="6">
      <a-card title="快捷入口" :loading="loading">
        <a-button type="primary" block @click="toIngestionJobs">查看入库任务</a-button>
      </a-card>
    </a-col>
  </a-row>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ragIngestionApi, userApi, type IngestionMetricsVO, type LoginUserVO } from '../api/services';

const router = useRouter();
const users = ref<LoginUserVO[]>([]);
const metrics = ref<IngestionMetricsVO>();
const loading = ref(false);
const adminCount = computed(() => users.value.filter((u) => u.userRole === 'admin').length);
const failureRateText = computed(() => `${(((metrics.value?.failureRate ?? 0) * 100).toFixed(2))}%`);
const avgDurationText = computed(() => (metrics.value?.avgDurationSeconds ?? 0).toFixed(2));

onMounted(async () => {
  loading.value = true;
  try {
    const [userList, metricData] = await Promise.all([userApi.list(), ragIngestionApi.metrics()]);
    users.value = userList;
    metrics.value = metricData;
  } finally {
    loading.value = false;
  }
});

function toIngestionJobs() {
  router.push('/ingestion-jobs');
}
</script>
