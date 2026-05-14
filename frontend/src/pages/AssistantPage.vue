<template>
  <a-card title="部门知识助手（多专家编排 · 流式）" :bordered="false">
    <a-alert v-if="!groupStore.currentGroupId" type="warning" message="请先选择当前组（部门知识库空间）" />
    <a-alert
      type="info"
      show-icon
      style="margin-bottom: 12px"
      message="多轮记忆由服务端会话表 + ContextBuilder 注入 Planner/汇总：请保持同一「会话 ID」连续提问（勿点「新会话」）。下方对话窗口展示历史；意图/引导/回答/事件流为当前轮诊断视图。"
    />
    <a-space direction="vertical" style="width: 100%">
      <a-space wrap>
        <a-button size="small" @click="clearConversation" :disabled="!groupStore.currentGroupId">新会话</a-button>
        <span v-if="storedConversationId" class="conv-hint">会话 ID: {{ storedConversationId }}</span>
      </a-space>

      <div ref="chatScrollRef" class="chat-window">
        <div v-if="!turns.length" class="chat-empty">在下方输入并发送，对话将显示在这里。</div>
        <div v-for="(t, idx) in turns" :key="t.id" class="turn-block">
          <div class="msg-row user-row">
            <div class="bubble user-bubble">{{ t.user }}</div>
          </div>
          <div v-if="t.intent || t.guide?.missingSlots?.length || t.guide?.questions?.length" class="meta-chips">
            <a-tag v-if="t.intent?.intent" color="geekblue">意图: {{ t.intent.intent }}</a-tag>
            <a-tag v-if="t.guide?.missingSlots?.length" color="magenta">
              待补: {{ t.guide!.missingSlots!.join(', ') }}
            </a-tag>
          </div>
          <div class="msg-row asst-row">
            <div class="bubble asst-bubble">
              <div v-if="t.guide?.questions?.length" class="guide-inline">
                <div class="guide-label">引导</div>
                <ul>
                  <li v-for="(q, qi) in t.guide!.questions" :key="qi">{{ q }}</li>
                </ul>
              </div>
              <div class="asst-body">{{ t.assistant || (idx === turns.length - 1 && streaming ? '…' : '') }}</div>
            </div>
          </div>
        </div>
      </div>

      <a-textarea
        v-model:value="messageText"
        :rows="3"
        placeholder="继续补充订单号、地址等信息…（与上文同一会话即带记忆）"
      />
      <a-space>
        <a-button type="primary" :disabled="!groupStore.currentGroupId || streaming" @click="startChat">
          {{ streaming ? '处理中...' : '发送' }}
        </a-button>
        <a-button danger :disabled="!streaming" @click="stopChat">停止</a-button>
      </a-space>

      <a-card v-if="lastIntent" size="small" title="本轮意图">
        <pre class="json-snippet">{{ formatEventData(lastIntent) }}</pre>
      </a-card>
      <a-card v-if="lastGuide && (lastGuide.questions?.length || lastGuide.missingSlots?.length)" size="small" title="引导与待补充">
        <div v-if="lastGuide.missingSlots?.length"><strong>待补充槽位：</strong>{{ lastGuide.missingSlots.join(', ') }}</div>
        <ul v-if="lastGuide.questions?.length" class="guide-list">
          <li v-for="(q, idx) in lastGuide.questions" :key="idx">{{ q }}</li>
        </ul>
      </a-card>
      <a-card size="small" title="回答（本轮）">
        <div class="answer-content">{{ prettyAnswerText || '暂无' }}</div>
      </a-card>
      <a-card size="small" title="事件流（本轮）">
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
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { useGroupStore } from '../stores/group';
import { message } from 'ant-design-vue';

type StreamEvent = { event: string; data: any };

type GuideShape = { questions?: string[]; missingSlots?: string[] } | null;

type ChatTurn = {
  id: string;
  user: string;
  assistant: string;
  intent: any;
  guide: GuideShape;
};

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

const storedConversationId = ref('');
const lastIntent = ref<any>(null);
const lastGuide = ref<{ questions?: string[]; missingSlots?: string[] } | null>(null);

const turns = ref<ChatTurn[]>([]);
const activeTurnIndex = ref(-1);
const chatScrollRef = ref<HTMLElement | null>(null);

const convStorageKey = computed(() =>
  groupStore.currentGroupId ? `dong-rag-assistant-conv-${groupStore.currentGroupId}` : '',
);

function scrollChatToBottom() {
  nextTick(() => {
    const el = chatScrollRef.value;
    if (el) {
      el.scrollTop = el.scrollHeight;
    }
  });
}

watch(
  () => groupStore.currentGroupId,
  (gid) => {
    if (gid && typeof sessionStorage !== 'undefined') {
      storedConversationId.value = sessionStorage.getItem(convStorageKey.value) || '';
    } else {
      storedConversationId.value = '';
    }
    lastIntent.value = null;
    lastGuide.value = null;
    turns.value = [];
  },
  { immediate: true },
);

watch(
  () => turns.value.length,
  () => scrollChatToBottom(),
);

watch(answerText, () => scrollChatToBottom());

function clearConversation() {
  if (convStorageKey.value) {
    sessionStorage.removeItem(convStorageKey.value);
  }
  storedConversationId.value = '';
  lastIntent.value = null;
  lastGuide.value = null;
  events.value = [];
  answerText.value = '';
  turns.value = [];
  activeTurnIndex.value = -1;
  message.info('已清除本地会话绑定与对话展示，下一轮将创建新会话');
}

async function startChat() {
  if (!groupStore.currentGroupId) return;
  const userInput = messageText.value.trim();
  if (!userInput) {
    message.warning('请输入内容');
    return;
  }
  stopChat();
  streaming.value = true;
  answerText.value = '';
  events.value = [];
  lastIntent.value = null;
  lastGuide.value = null;

  const turn: ChatTurn = {
    id: `t-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
    user: userInput,
    assistant: '',
    intent: null,
    guide: null,
  };
  turns.value.push(turn);
  activeTurnIndex.value = turns.value.length - 1;
  messageText.value = '';
  scrollChatToBottom();

  let token = '';
  try {
    const raw = localStorage.getItem('dong-rag-user-auth');
    token = raw ? (JSON.parse(raw).token as string) : '';
  } catch {
    token = '';
  }
  const sid = convStorageKey.value ? sessionStorage.getItem(convStorageKey.value) || '' : '';
  try {
    const aborter = new AbortController();
    controller.value = aborter;
    const body: Record<string, unknown> = {
      groupId: groupStore.currentGroupId,
      message: userInput,
      topK: 5,
      templateId: 'COMPLAINT_MULTI_LEGACY',
    };
    if (sid) {
      body.conversationId = sid;
    }
    const res = await fetch('/api/assistant/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: token,
      },
      body: JSON.stringify(body),
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
      const i = activeTurnIndex.value;
      if (i >= 0 && turns.value[i]) {
        const suffix = `（本轮失败：${error?.message || '未知错误'}）`;
        turns.value[i].assistant = turns.value[i].assistant
          ? `${turns.value[i].assistant}\n${suffix}`
          : suffix;
      }
    }
  } finally {
    streaming.value = false;
    controller.value = null;
    flushPendingTokens();
    activeTurnIndex.value = -1;
  }
}

function patchActiveTurn(mut: (t: ChatTurn) => void) {
  const i = activeTurnIndex.value;
  if (i < 0 || i >= turns.value.length) return;
  const t = turns.value[i];
  mut(t);
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
    if (payload.event === 'start' && payload.data && typeof payload.data === 'object') {
      const cid = (payload.data as any).conversationId;
      if (cid && convStorageKey.value) {
        sessionStorage.setItem(convStorageKey.value, String(cid));
        storedConversationId.value = String(cid);
      }
    }
    if (payload.event === 'intent' && payload.data) {
      const parsed =
        typeof payload.data === 'string' ? tryParseJson(payload.data) : payload.data;
      lastIntent.value = parsed;
      patchActiveTurn((t) => {
        t.intent = parsed;
      });
    }
    if (payload.event === 'guide' && payload.data) {
      const g = typeof payload.data === 'string' ? tryParseJson(payload.data) : payload.data;
      lastGuide.value = g || null;
      patchActiveTurn((t) => {
        t.guide = g || null;
      });
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

function tryParseJson(s: string) {
  try {
    return JSON.parse(s);
  } catch {
    return null;
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
  patchActiveTurn((t) => {
    t.assistant += pendingTokenBuffer;
  });
  pendingTokenBuffer = '';
  scrollChatToBottom();
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
  if (event === 'intent') return 'geekblue';
  if (event === 'guide') return 'magenta';
  if (event === 'kb-meta') return 'cyan';
  if (event === 'policy-hit') return 'orange';
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
.chat-window {
  max-height: min(420px, 50vh);
  overflow-y: auto;
  padding: 12px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
}

.chat-empty {
  color: #8c8c8c;
  font-size: 13px;
  text-align: center;
  padding: 24px 8px;
}

.turn-block {
  margin-bottom: 16px;
}

.msg-row {
  display: flex;
  margin-bottom: 6px;
}

.user-row {
  justify-content: flex-end;
}

.asst-row {
  justify-content: flex-start;
}

.bubble {
  max-width: 88%;
  padding: 10px 14px;
  border-radius: 12px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.user-bubble {
  background: #1677ff;
  color: #fff;
  border-bottom-right-radius: 4px;
}

.asst-bubble {
  background: #fff;
  border: 1px solid #e8e8e8;
  border-bottom-left-radius: 4px;
}

.meta-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 4px 0 8px 4px;
}

.guide-inline {
  margin-bottom: 8px;
  padding: 8px;
  background: #fff7e6;
  border-radius: 8px;
  font-size: 13px;
}

.guide-inline ul {
  margin: 4px 0 0;
  padding-left: 18px;
}

.guide-label {
  font-weight: 600;
  color: #d46b08;
  font-size: 12px;
}

.asst-body {
  white-space: pre-wrap;
}

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

.conv-hint {
  font-size: 12px;
  color: #8c8c8c;
}

.json-snippet {
  margin: 0;
  font-size: 12px;
  white-space: pre-wrap;
}

.guide-list {
  margin: 8px 0 0;
  padding-left: 18px;
}
</style>
