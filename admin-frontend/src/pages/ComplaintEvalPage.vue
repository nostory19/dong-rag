<template>
  <a-card title="投诉评测">
    <a-space>
      <a-input-number v-model:value="groupId" :min="1" placeholder="groupId" />
      <a-button type="primary" :loading="loading" @click="runEval">执行评测</a-button>
    </a-space>
    <a-descriptions bordered :column="1" style="margin-top: 16px" v-if="result">
      <a-descriptions-item label="样例数量">{{ result.caseCount }}</a-descriptions-item>
      <a-descriptions-item label="转人工率">{{ result.handoffRate }}</a-descriptions-item>
      <a-descriptions-item label="平均子任务数">{{ result.avgSubTaskCount }}</a-descriptions-item>
    </a-descriptions>
    <a-table v-if="result?.details" :data-source="result.details" style="margin-top: 12px" row-key="message">
      <a-table-column title="消息" data-index="message" />
      <a-table-column title="子任务数" data-index="subTaskCount" />
      <a-table-column title="转人工" data-index="handoff" />
      <a-table-column title="原因" data-index="reason" />
    </a-table>
  </a-card>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { assistantApi } from '../api/services';

const groupId = ref<number | null>(null);
const loading = ref(false);
const result = ref<any>(null);

async function runEval() {
  if (!groupId.value) return;
  loading.value = true;
  try {
    result.value = await assistantApi.evalComplaint(groupId.value);
  } finally {
    loading.value = false;
  }
}
</script>
