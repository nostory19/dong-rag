<template>
  <a-card title="知识问答">
    <a-alert v-if="!groupStore.currentGroupId" type="warning" message="请先选择当前组" />
    <a-space direction="vertical" style="width: 100%">
      <a-input v-model:value="question" placeholder="请输入你的问题" />
      <a-input-number v-model:value="topK" :min="1" :max="10" />
      <a-button type="primary" :disabled="!groupStore.currentGroupId" @click="ask">提问</a-button>
      <a-spin :spinning="loading">
        <a-card v-if="answer" size="small">
          <p><strong>答案：</strong>{{ answer.answer }}</p>
          <p><strong>置信度：</strong>{{ answer.confidenceLevel }} / {{ answer.confidenceScore }}</p>
          <a-divider />
          <div v-for="(item, idx) in answer.evidences || []" :key="idx" style="margin-bottom: 8px">
            <p><strong>{{ Number(idx) + 1 }}. {{ item.fileName }}</strong> [{{ item.charStart }}-{{ item.charEnd }}]</p>
            <p>{{ item.content }}</p>
          </div>
        </a-card>
      </a-spin>
    </a-space>
  </a-card>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { ragApi } from '../api/services';
import { useGroupStore } from '../stores/group';

const groupStore = useGroupStore();
const question = ref('');
const topK = ref(5);
const answer = ref<any>(null);
const loading = ref(false);

async function ask() {
  if (!groupStore.currentGroupId) return;
  loading.value = true;
  try {
    answer.value = await ragApi.ask({
      groupId: groupStore.currentGroupId,
      question: question.value,
      topK: topK.value,
    });
  } finally {
    loading.value = false;
  }
}
</script>
