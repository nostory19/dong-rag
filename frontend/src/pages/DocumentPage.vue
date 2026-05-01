<template>
  <a-card title="文档入库">
    <a-alert v-if="!groupStore.currentGroupId" type="warning" message="请先在“我的组”选择当前组" show-icon />
    <a-form layout="vertical" style="margin-top: 12px">
      <a-form-item label="文本文件名">
        <a-input v-model:value="textForm.fileName" />
      </a-form-item>
      <a-form-item label="文本内容">
        <a-textarea v-model:value="textForm.content" :rows="6" />
      </a-form-item>
      <a-button type="primary" :disabled="!groupStore.currentGroupId" @click="submitText">文本入库</a-button>
    </a-form>
    <a-divider />
    <a-upload :before-upload="beforeUpload" :show-upload-list="false">
      <a-button :disabled="!groupStore.currentGroupId">选择文件并入库</a-button>
    </a-upload>
    <a-divider />
    <a-space direction="vertical" style="width: 100%">
      <a-alert
        v-if="taskState"
        show-icon
        :type="taskAlertType"
        :message="`当前任务：jobId=${taskState.jobId}，documentId=${taskState.documentId}`"
        :description="`文档状态：${taskState.documentStatus}；任务状态：${taskState.jobStatus}`"
      />
      <a-descriptions v-if="taskState" bordered :column="1" size="small">
        <a-descriptions-item label="文档状态">
          <a-tag :color="docStatusColor(taskState.documentStatus)">{{ taskState.documentStatus }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="任务状态">
          <a-tag :color="jobStatusColor(taskState.jobStatus)">{{ taskState.jobStatus }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="文档失败原因">{{ taskState.failureReason || '-' }}</a-descriptions-item>
        <a-descriptions-item label="任务错误信息">{{ taskState.lastError || '-' }}</a-descriptions-item>
      </a-descriptions>
      <a-button v-if="taskState" @click="refreshTaskStatus" :loading="refreshing">刷新状态</a-button>
    </a-space>
  </a-card>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { ragApi } from '../api/services';
import { useGroupStore } from '../stores/group';

const groupStore = useGroupStore();
const textForm = reactive({ fileName: '', content: '' });
const taskState = ref<{
  documentId: number;
  jobId: number;
  documentStatus: string;
  jobStatus: string;
  failureReason?: string;
  lastError?: string;
}>();
const refreshing = ref(false);
let pollTimer: number | undefined;

const taskAlertType = computed(() => {
  if (!taskState.value) return 'info';
  if (taskState.value.documentStatus === 'READY' && taskState.value.jobStatus === 'SUCCESS') return 'success';
  if (taskState.value.documentStatus === 'FAILED' || taskState.value.jobStatus === 'FAILED') return 'error';
  return 'info';
});

async function submitText() {
  if (!groupStore.currentGroupId) return;
  const task = await ragApi.ingestText({
    groupId: groupStore.currentGroupId,
    fileName: textForm.fileName,
    content: textForm.content,
  });
  taskState.value = task;
  message.success(`文本上传成功，documentId=${task.documentId}，jobId=${task.jobId}`);
  startPolling();
}

async function beforeUpload(file: File) {
  if (!groupStore.currentGroupId) return false;
  const task = await ragApi.ingestFile(groupStore.currentGroupId, file);
  taskState.value = task;
  message.success(`文件上传成功，documentId=${task.documentId}，jobId=${task.jobId}`);
  startPolling();
  return false;
}

async function refreshTaskStatus() {
  if (!taskState.value) return;
  refreshing.value = true;
  try {
    taskState.value = await ragApi.getTaskStatus(taskState.value.jobId);
    if (isTaskFinished(taskState.value)) {
      stopPolling();
    }
  } finally {
    refreshing.value = false;
  }
}

function startPolling() {
  stopPolling();
  pollTimer = window.setInterval(() => {
    refreshTaskStatus();
  }, 3000);
}

function stopPolling() {
  if (pollTimer) {
    window.clearInterval(pollTimer);
    pollTimer = undefined;
  }
}

function isTaskFinished(task: { documentStatus: string; jobStatus: string }) {
  return ['READY', 'FAILED'].includes(task.documentStatus) || ['SUCCESS', 'FAILED'].includes(task.jobStatus);
}

function docStatusColor(status: string) {
  if (status === 'READY') return 'green';
  if (status === 'FAILED') return 'red';
  if (status === 'PROCESSING') return 'blue';
  if (status === 'UPLOADED') return 'orange';
  return 'default';
}

function jobStatusColor(status: string) {
  if (status === 'SUCCESS') return 'green';
  if (status === 'FAILED') return 'red';
  if (status === 'RUNNING') return 'blue';
  if (status === 'RETRY_WAITING') return 'orange';
  if (status === 'PENDING') return 'gold';
  return 'default';
}

onBeforeUnmount(() => {
  stopPolling();
});
</script>
