<template>
  <a-card title="投诉智能客服（流式）">
    <a-alert v-if="!groupStore.currentGroupId" type="warning" message="请先选择当前组" />
    <a-space direction="vertical" style="width: 100%">
      <a-textarea v-model:value="messageText" :rows="4" placeholder="输入投诉内容..." />
      <a-button type="primary" :disabled="!groupStore.currentGroupId || streaming" @click="startChat">
        {{ streaming ? '处理中...' : '开始对话' }}
      </a-button>
      <a-card size="small" title="回答">
        <div style="white-space: pre-wrap">{{ answerText || '暂无' }}</div>
      </a-card>
      <a-card size="small" title="事件流">
        <div style="max-height: 260px; overflow: auto">
          <div v-for="(e, i) in events" :key="i">{{ e.event }}: {{ e.data }}</div>
        </div>
      </a-card>
    </a-space>
  </a-card>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useGroupStore } from '../stores/group';

type StreamEvent = { event: string; data: string };

const groupStore = useGroupStore();
const messageText = ref('');
const answerText = ref('');
const events = ref<StreamEvent[]>([]);
const streaming = ref(false);

async function startChat() {
  if (!groupStore.currentGroupId) return;
  streaming.value = true;
  answerText.value = '';
  events.value = [];
  const raw = localStorage.getItem('dong-rag-user-auth');
  const token = raw ? (JSON.parse(raw).token as string) : '';
  const res = await fetch('/api/assistant/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: token,
    },
    body: JSON.stringify({
      groupId: groupStore.currentGroupId,
      message: messageText.value,
      topK: 5,
    }),
  });
  const reader = res.body?.getReader();
  if (!reader) {
    streaming.value = false;
    return;
  }
  const decoder = new TextDecoder();
  let buffer = '';
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const chunks = buffer.split('\n');
    buffer = chunks.pop() || '';
    for (const chunk of chunks) {
      parseEvent(chunk);
    }
    parseEvent(buffer);
    buffer = '';
  }
  streaming.value = false;
}

function parseEvent(text: string) {
  const line = text.trim();
  if (!line) return;
  try {
    const payload = JSON.parse(line) as StreamEvent;
    events.value.push(payload);
    if (payload.event === 'token') {
      answerText.value += payload.data;
    }
  } catch {
    // ignore parse error lines
  }
}
</script>
