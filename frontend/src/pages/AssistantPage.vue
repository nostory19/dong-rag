<template>
  <a-card title="投诉智能客服（流式）" :bordered="false">
    <a-alert v-if="!groupStore.currentGroupId" type="warning" message="请先选择当前组" />
    <a-space direction="vertical" style="width: 100%">
      <a-textarea v-model:value="messageText" :rows="4" placeholder="输入投诉内容..." />
      <a-space>
        <a-button type="primary" :disabled="!groupStore.currentGroupId || streaming" @click="startChat">
          {{ streaming ? '处理中...' : '开始对话' }}
        </a-button>
        <a-button danger :disabled="!streaming" @click="stopChat">停止</a-button>
      </a-space>
      <a-card size="small" title="回答">
        <div class="answer-content">{{ prettyAnswerText || '暂无' }}</div>
      </a-card>
      <a-card size="small" title="事件流">
        <div class="event-list">
          <div v-for="(e, i) in events" :key="i" class="event-row">
            <a-tag :color="eventColor(e.event)">{{ e.event }}</a-tag>
            <span class="event-data">{{ formatEventData(e.data) }}</span>
          </div>
        </div>
      </a-card>
    </a-space>
  </a-card>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import { useGroupStore } from '../stores/group';
import { message } from 'ant-design-vue';

type StreamEvent = { event: string; data: any };

const groupStore = useGroupStore();
const messageText = ref('');
const answerText = ref('');
const events = ref<StreamEvent[]>([]);
const streaming = ref(false);
const prettyAnswerText = computed(() => String(answerText.value ?? ''));
const controller = ref<AbortController | null>(null);
let pendingTokenBuffer = '';
let rafId: number | null = null;
const MAX_EVENTS = 300;

async function startChat() {
  if (!groupStore.currentGroupId) return;
  stopChat();
  streaming.value = true;
  answerText.value = '';
  events.value = [];
  let token = '';
  try {
    const raw = localStorage.getItem('dong-rag-user-auth');
    token = raw ? (JSON.parse(raw).token as string) : '';
  } catch {
    token = '';
  }
  try {
    const aborter = new AbortController();
    controller.value = aborter;
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
      signal: aborter.signal,
    });
    if (!res.ok) {
      throw new Error(`请求失败(${res.status})`);
    }
    const reader = res.body?.getReader();
    if (!reader) {
      throw new Error('未获取到流式响应');
    }
    const decoder = new TextDecoder();
    let buffer = '';
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';
      for (const line of lines) {
        parseEventLine(line);
      }
    }
    if (buffer.trim()) {
      parseEventLine(buffer);
    }
  } catch (error: any) {
    if (error?.name === 'AbortError') {
      message.info('已停止');
    } else {
      message.error(error?.message || '流式对话失败');
    }
  } finally {
    streaming.value = false;
    controller.value = null;
    flushPendingTokens();
  }
}

function parseEventLine(text: string) {
  const line = text.trim();
  if (!line) return;
  try {
    const payload = JSON.parse(line) as StreamEvent;
    events.value.push(payload);
    if (events.value.length > MAX_EVENTS) {
      events.value.splice(0, events.value.length - MAX_EVENTS);
    }
    if (payload.event === 'token') {
      pendingTokenBuffer += String(payload.data ?? '');
      scheduleFlush();
    }
    if (payload.event === 'done') {
      streaming.value = false;
    }
  } catch {
    events.value.push({ event: 'raw', data: line });
  }
}

function scheduleFlush() {
  if (rafId != null) return;
  rafId = window.requestAnimationFrame(() => {
    rafId = null;
    flushPendingTokens();
  });
}

function flushPendingTokens() {
  if (!pendingTokenBuffer) return;
  answerText.value += pendingTokenBuffer;
  pendingTokenBuffer = '';
}

function formatEventData(raw: string) {
  if (raw == null) return '';
  if (typeof raw === 'string') return raw;
  try {
    return JSON.stringify(raw, null, 2);
  } catch {
    return String(raw);
  }
}

function eventColor(event: string) {
  if (event === 'error') return 'red';
  if (event === 'done') return 'green';
  if (event === 'token') return 'blue';
  if (event.startsWith('worker')) return 'purple';
  if (event.includes('tool')) return 'orange';
  return 'default';
}

function stopChat() {
  if (controller.value) {
    controller.value.abort();
  }
  controller.value = null;
  streaming.value = false;
  if (rafId != null) {
    window.cancelAnimationFrame(rafId);
    rafId = null;
  }
}

onBeforeUnmount(() => {
  stopChat();
});
</script>

<style scoped>
.answer-content {
  white-space: pre-wrap;
  line-height: 1.8;
}

.event-list {
  max-height: 320px;
  overflow: auto;
}

.event-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px dashed #f0f0f0;
}

.event-data {
  white-space: pre-wrap;
  line-height: 1.5;
  color: #595959;
}
</style>
