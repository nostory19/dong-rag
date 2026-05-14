<template>
  <a-card title="检索检测">
    <a-alert
      type="info"
      show-icon
      style="margin-bottom: 12px"
      message="对指定组批量执行混合检索（向量 + ES + RRF）。无金标时仅看置信度与证据列表；同时提供 goldDocumentId + goldChunkIndex 时计算 Hit@1、Hit@topK、MRR（仅统计有金标的用例）。"
    />
    <a-space direction="vertical" style="width: 100%">
      <a-space wrap>
        <a-input-number v-model:value="groupId" :min="1" placeholder="groupId" />
        <a-input-number v-model:value="topK" :min="1" :max="10" placeholder="topK" />
        <span>重排前后对比</span>
        <a-switch v-model:checked="includeRerankComparison" />
        <a-button type="primary" :loading="loading" @click="runDetect">执行检测</a-button>
      </a-space>
      <div class="hint">cases JSON 示例（数组）：</div>
      <pre class="sample">{{ sampleJson }}</pre>
      <a-textarea v-model:value="casesJson" :rows="12" placeholder='[{"question":"…"},{"question":"…","goldDocumentId":1,"goldChunkIndex":0}]' />
    </a-space>
    <a-descriptions v-if="result" bordered :column="2" style="margin-top: 16px">
      <a-descriptions-item label="用例数">{{ result.caseCount }}</a-descriptions-item>
      <a-descriptions-item label="金标用例数">{{ result.labeledCount }}</a-descriptions-item>
      <a-descriptions-item label="mean Hit@1">{{ fmt(result.meanHitAt1) }}</a-descriptions-item>
      <a-descriptions-item label="mean Hit@K">{{ fmt(result.meanHitAtK) }}</a-descriptions-item>
      <a-descriptions-item label="MRR">{{ fmt(result.mrr) }}</a-descriptions-item>
      <a-descriptions-item v-if="result.meanHitAtKBaseline != null" label="基线 mean Hit@1">{{ fmt(result.meanHitAt1Baseline) }}</a-descriptions-item>
      <a-descriptions-item v-if="result.meanHitAtKBaseline != null" label="基线 mean Hit@K">{{ fmt(result.meanHitAtKBaseline) }}</a-descriptions-item>
      <a-descriptions-item v-if="result.mrrBaseline != null" label="基线 MRR">{{ fmt(result.mrrBaseline) }}</a-descriptions-item>
    </a-descriptions>
    <a-table
      v-if="result?.details?.length"
      :columns="detailColumns"
      :data-source="result.details"
      :pagination="false"
      style="margin-top: 16px"
      :row-key="(_record: unknown, i: number) => String(i)"
      size="small"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'gold'">
          {{ record.labeled ? `${record.goldDocumentId} / ${record.goldChunkIndex}` : '—' }}
        </template>
        <template v-else-if="column.key === 'evidences'">
          <a-button type="link" size="small" :disabled="!record.evidences?.length" @click="openEvidences(record)">
            {{ record.evidences?.length ?? 0 }} 条
          </a-button>
        </template>
      </template>
    </a-table>
    <a-modal v-model:open="evModalOpen" title="证据片段" width="800px" :footer="null" destroy-on-close>
      <pre class="ev-json">{{ evModalText }}</pre>
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { message } from 'ant-design-vue';
import { ragDetectApi, type RetrievalDetectCase, type RetrievalDetectRequest } from '../api/services';

const sampleJson = `[
  { "question": "年假天数如何计算？" },
  { "question": "VPN 无法连接该如何排查？", "goldDocumentId": 1, "goldChunkIndex": 0 }
]`;

const groupId = ref<number | null>(null);
const topK = ref<number>(5);
const casesJson = ref(sampleJson);
const loading = ref(false);
const result = ref<any>(null);
const includeRerankComparison = ref(false);

const detailColumns = [
  { title: '问题', dataIndex: 'question', key: 'question', width: 200, ellipsis: true },
  { title: '证据', key: 'evidences', width: 90 },
  { title: '证据足', dataIndex: 'evidenceEnough', key: 'evidenceEnough', width: 80 },
  { title: '置信分', dataIndex: 'confidenceScore', key: 'confidenceScore' },
  { title: '等级', dataIndex: 'confidenceLevel', key: 'confidenceLevel' },
  { title: '金标rank', dataIndex: 'rankOfGold', key: 'rankOfGold' },
  { title: '基线rank', dataIndex: 'rankOfGoldBaseline', key: 'rankOfGoldBaseline' },
  { title: 'Hit@1', dataIndex: 'hitAt1', key: 'hitAt1' },
  { title: 'Hit@K', dataIndex: 'hitAtK', key: 'hitAtK' },
  { title: 'RR', dataIndex: 'reciprocalRank', key: 'reciprocalRank' },
  { title: '金标', key: 'gold', width: 120 },
  { title: '错误', dataIndex: 'error', key: 'error', ellipsis: true },
];

const evModalOpen = ref(false);
const evModalText = ref('');

function fmt(v: number | null | undefined) {
  if (v == null) return '—';
  return Number(v).toFixed(4);
}

function openEvidences(record: any) {
  evModalText.value = JSON.stringify(record.evidences ?? [], null, 2);
  evModalOpen.value = true;
}

async function runDetect() {
  if (!groupId.value) return;
  let cases: RetrievalDetectCase[];
  try {
    cases = JSON.parse(casesJson.value || '[]');
  } catch {
    message.error('cases JSON 解析失败');
    return;
  }
  if (!Array.isArray(cases) || cases.length === 0) {
    message.warning('cases 须为非空数组');
    return;
  }
  loading.value = true;
  try {
    const body: RetrievalDetectRequest = {
      groupId: groupId.value,
      topK: topK.value,
      cases,
      includeRerankComparison: includeRerankComparison.value,
    };
    result.value = await ragDetectApi.detectRetrieval(body);
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.hint {
  font-size: 13px;
  color: #595959;
}
.sample {
  margin: 0;
  padding: 8px 12px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  font-size: 12px;
  overflow: auto;
  max-height: 120px;
}
.ev-json {
  max-height: 60vh;
  overflow: auto;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
