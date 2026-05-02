<template>
  <a-card title="文档入库" :bordered="false">
    <a-alert v-if="!groupStore.currentGroupId" type="warning" message="请先在“我的组”选择当前组" show-icon />
    <a-row :gutter="16" style="margin-top: 12px">
      <a-col :xs="24" :lg="14">
        <a-card size="small" title="文本直传">
          <a-form layout="vertical">
            <a-form-item label="文本文件名">
              <a-input v-model:value="textForm.fileName" placeholder="例如：产品说明.md" />
            </a-form-item>
            <a-form-item label="文本内容">
              <a-textarea v-model:value="textForm.content" :rows="6" />
            </a-form-item>
            <a-button type="primary" :disabled="!groupStore.currentGroupId" @click="submitText">上传文本并入库</a-button>
          </a-form>
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="10">
        <a-card size="small" title="文件上传">
          <a-space direction="vertical" style="width: 100%">
            <div class="hint-text">支持 md / txt / pdf / docx，上传后自动异步解析与切分。</div>
            <a-upload :before-upload="beforeUpload" :show-upload-list="false">
              <a-button block :disabled="!groupStore.currentGroupId">选择文件并入库</a-button>
            </a-upload>
          </a-space>
        </a-card>
      </a-col>
    </a-row>

    <a-divider />

    <a-space direction="vertical" style="width: 100%" size="middle">
      <a-alert
        v-if="taskState"
        show-icon
        :type="taskAlertType"
        :message="`当前任务：jobId=${taskState.jobId}，documentId=${taskState.documentId}`"
        :description="`文档状态：${taskState.documentStatus}；任务状态：${taskState.jobStatus}`"
      />
      <a-steps v-if="taskState" :current="currentStep" :status="stepStatus" :items="stepItems" size="small" />
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
      <a-space v-if="taskState">
        <a-button @click="refreshTaskStatus" :loading="refreshing">刷新状态</a-button>
        <a-button v-if="taskState.documentStatus === 'READY'" type="primary" @click="goQa">去知识问答</a-button>
      </a-space>
      <a-alert
        v-if="taskState && (taskState.documentStatus === 'FAILED' || taskState.jobStatus === 'FAILED')"
        type="error"
        show-icon
        message="入库失败"
        description="请先查看错误信息，可修复后重新上传文档或联系管理员进行任务重试。"
      />
      <a-card size="small" title="最近任务（最近 5 条）">
        <a-table :data-source="ingestionStore.recentTasks" :pagination="false" row-key="jobId" size="small" :scroll="{ x: 720 }">
          <a-table-column title="任务ID" data-index="jobId" />
          <a-table-column title="文档ID" data-index="documentId" />
          <a-table-column title="文档状态">
            <template #default="{ record }">
              <a-tag :color="docStatusColor(record.documentStatus)">{{ record.documentStatus }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column title="任务状态">
            <template #default="{ record }">
              <a-tag :color="jobStatusColor(record.jobStatus)">{{ record.jobStatus }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column title="更新时间" data-index="updatedAt" />
          <a-table-column title="失败摘要">
            <template #default="{ record }">
              {{ record.failureReason || record.lastError || '-' }}
            </template>
          </a-table-column>
          <a-table-column title="操作">
            <template #default="{ record }">
              <a-button type="link" @click="viewTask(record.jobId)">查看</a-button>
            </template>
          </a-table-column>
        </a-table>
      </a-card>
    </a-space>
  </a-card>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { ragApi } from '../api/services';
import { useGroupStore } from '../stores/group';
import { useIngestionStore } from '../stores/ingestion';

const groupStore = useGroupStore();
const ingestionStore = useIngestionStore();
const router = useRouter();
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
const stepItems = [
  { title: '已上传' },
  { title: '处理中' },
  { title: '向量入库' },
  { title: '索引校验' },
  { title: '完成' },
];

const taskAlertType = computed(() => {
  if (!taskState.value) return 'info';
  if (taskState.value.documentStatus === 'READY' && taskState.value.jobStatus === 'SUCCESS') return 'success';
  if (taskState.value.documentStatus === 'FAILED' || taskState.value.jobStatus === 'FAILED') return 'error';
  return 'info';
});
const currentStep = computed(() => {
  if (!taskState.value) {
    return 0;
  }
  const task = taskState.value;
  if (task.documentStatus === 'READY' && task.jobStatus === 'SUCCESS') return 4;
  if (task.documentStatus === 'FAILED' || task.jobStatus === 'FAILED') return 3;
  if (task.jobStatus === 'RETRY_WAITING') return 3;
  if (task.documentStatus === 'PROCESSING' || task.jobStatus === 'RUNNING') return 2;
  return 0;
});
const stepStatus = computed<'process' | 'finish' | 'error'>(() => {
  if (!taskState.value) return 'process';
  if (taskState.value.documentStatus === 'FAILED' || taskState.value.jobStatus === 'FAILED') return 'error';
  if (taskState.value.documentStatus === 'READY' && taskState.value.jobStatus === 'SUCCESS') return 'finish';
  return 'process';
});

async function submitText() {
  if (!groupStore.currentGroupId) return;
  const task = await ragApi.ingestText({
    groupId: groupStore.currentGroupId,
    fileName: textForm.fileName,
    content: textForm.content,
  });
  taskState.value = task;
  ingestionStore.upsertTask(task);
  message.success(`文本上传成功，documentId=${task.documentId}，jobId=${task.jobId}`);
  startPolling();
}

async function beforeUpload(file: File) {
  if (!groupStore.currentGroupId) return false;
  const task = await ragApi.ingestFile(groupStore.currentGroupId, file);
  taskState.value = task;
  ingestionStore.upsertTask(task);
  message.success(`文件上传成功，documentId=${task.documentId}，jobId=${task.jobId}`);
  startPolling();
  return false;
}

async function refreshTaskStatus() {
  if (!taskState.value) return;
  refreshing.value = true;
  try {
    taskState.value = await ragApi.getTaskStatus(taskState.value.jobId);
    ingestionStore.upsertTask(taskState.value);
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

async function viewTask(jobId: number) {
  taskState.value = await ragApi.getTaskStatus(jobId);
  ingestionStore.upsertTask(taskState.value);
}

function goQa() {
  router.push('/qa');
}

onBeforeUnmount(() => {
  stopPolling();
});
</script>

<style scoped>
.hint-text {
  color: #8c8c8c;
  font-size: 13px;
  line-height: 1.6;
}
</style>
