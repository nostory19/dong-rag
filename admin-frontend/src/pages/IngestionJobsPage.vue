<template>
  <a-card title="文档入库任务" :bordered="false">
    <template #extra>
      <a-space>
        <a-input-number v-model:value="limit" :min="1" :max="200" />
        <a-button :loading="loading" @click="loadJobs">刷新</a-button>
      </a-space>
    </template>
    <a-table
      :data-source="jobs"
      :columns="columns"
      :loading="loading"
      :pagination="{ pageSize: 10 }"
      row-key="id"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-tag :color="statusColor(record.status)">{{ record.status }}</a-tag>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-button type="link" @click="openDetail(record.id)">查看详情</a-button>
        </template>
      </template>
    </a-table>
  </a-card>

  <a-drawer
    :open="detailOpen"
    title="任务详情"
    width="560"
    @close="detailOpen = false"
  >
    <a-descriptions v-if="detail" bordered :column="1" size="small">
      <a-descriptions-item label="任务 ID">{{ detail.id }}</a-descriptions-item>
      <a-descriptions-item label="文档 ID">{{ detail.documentId }}</a-descriptions-item>
      <a-descriptions-item label="分组 ID">{{ detail.groupId }}</a-descriptions-item>
      <a-descriptions-item label="任务类型">{{ detail.jobType }}</a-descriptions-item>
      <a-descriptions-item label="状态">
        <a-tag :color="statusColor(detail.status)">{{ detail.status }}</a-tag>
      </a-descriptions-item>
      <a-descriptions-item label="重试次数">{{ detail.retryCount }} / {{ detail.maxRetries }}</a-descriptions-item>
      <a-descriptions-item label="开始时间">{{ detail.startedAt || '-' }}</a-descriptions-item>
      <a-descriptions-item label="结束时间">{{ detail.finishedAt || '-' }}</a-descriptions-item>
      <a-descriptions-item label="下次重试时间">{{ detail.nextRetryAt || '-' }}</a-descriptions-item>
      <a-descriptions-item label="错误信息">{{ detail.lastError || '-' }}</a-descriptions-item>
    </a-descriptions>
  </a-drawer>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import type { IngestionJobVO } from '../api/services';
import { ragIngestionApi } from '../api/services';

const loading = ref(false);
const jobs = ref<IngestionJobVO[]>([]);
const limit = ref(50);
const detailOpen = ref(false);
const detail = ref<IngestionJobVO>();

const columns = [
  { title: '任务ID', dataIndex: 'id', key: 'id' },
  { title: '文档ID', dataIndex: 'documentId', key: 'documentId' },
  { title: '分组ID', dataIndex: 'groupId', key: 'groupId' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '重试', key: 'retry', customRender: ({ record }: { record: IngestionJobVO }) => `${record.retryCount}/${record.maxRetries}` },
  { title: '创建后执行时间', dataIndex: 'startedAt', key: 'startedAt' },
  { title: '操作', key: 'action' },
];

async function loadJobs() {
  loading.value = true;
  try {
    jobs.value = await ragIngestionApi.listJobs(limit.value);
  } finally {
    loading.value = false;
  }
}

async function openDetail(jobId: number) {
  detail.value = await ragIngestionApi.getJob(jobId);
  detailOpen.value = true;
}

function statusColor(status: string) {
  if (status === 'SUCCESS') return 'green';
  if (status === 'FAILED') return 'red';
  if (status === 'RUNNING') return 'blue';
  if (status === 'RETRY_WAITING') return 'orange';
  return 'default';
}

onMounted(() => {
  loadJobs();
});
</script>
