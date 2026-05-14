<template>
  <a-card title="编排评测（研发遗留）">
    <a-alert
      type="warning"
      show-icon
      style="margin-bottom: 12px"
      message="基于历史投诉编排样例的回归评测；内部知识库主能力请使用用户端「知识问答 / 知识助手」。菜单入口已隐藏，路由仍为 /complaint-eval。"
    />
    <a-space direction="vertical" style="width: 100%">
      <a-space wrap>
        <a-input-number v-model:value="groupId" :min="1" placeholder="groupId" />
        <a-select v-model:value="evalTemplate" style="min-width: 220px" :options="evalTemplateOptions" />
        <a-button type="primary" :loading="loading" @click="runEval">执行评测</a-button>
      </a-space>
    </a-space>
    <a-descriptions bordered :column="1" style="margin-top: 16px" v-if="result && !result.error">
      <a-descriptions-item label="模板">{{ result.templateId }}</a-descriptions-item>
      <a-descriptions-item label="样例数量">{{ result.caseCount }}</a-descriptions-item>
      <a-descriptions-item label="转人工率">{{ result.handoffRate }}</a-descriptions-item>
      <a-descriptions-item label="平均子任务数">{{ result.avgSubTaskCount }}</a-descriptions-item>
    </a-descriptions>
    <a-alert v-if="result?.error" type="error" :message="result.error" style="margin-top: 12px" />
    <a-table v-if="result?.details" :data-source="result.details" style="margin-top: 12px" row-key="message">
      <a-table-column title="模板" data-index="templateId" />
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
const evalTemplate = ref('COMPLAINT_MULTI_LEGACY');
const evalTemplateOptions = [
  { label: '投诉编排 (COMPLAINT_MULTI_LEGACY)', value: 'COMPLAINT_MULTI_LEGACY' },
  { label: '内部知识库多专家 (INTERNAL_KB_MULTI)', value: 'INTERNAL_KB_MULTI' },
];
const loading = ref(false);
const result = ref<any>(null);

async function runEval() {
  if (!groupId.value) return;
  loading.value = true;
  try {
    result.value = await assistantApi.evalComplaint(groupId.value, evalTemplate.value);
  } finally {
    loading.value = false;
  }
}
</script>
