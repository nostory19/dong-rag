import { request } from './http';

export type LoginUserVO = {
  id: number;
  userCode: string;
  displayName: string;
  userRole: string;
  token: string;
};

export const userApi = {
  login(payload: { userCode: string; userPassword: string }) {
    return request<LoginUserVO>({ method: 'POST', url: '/user/login', data: payload });
  },
  list() {
    return request<LoginUserVO[]>({ method: 'GET', url: '/user/list' });
  },
  logout() {
    return request<boolean>({ method: 'POST', url: '/user/logout' });
  },
};

export const assistantApi = {
  evalComplaint(groupId: number, templateId?: string) {
    return request<any>({
      method: 'POST',
      url: '/assistant/eval/complaint',
      params: templateId ? { groupId, templateId } : { groupId },
    });
  },
};

export type IngestionJobVO = {
  id: number;
  documentId: number;
  groupId: number;
  jobType: string;
  status: string;
  retryCount: number;
  maxRetries: number;
  startedAt?: string;
  finishedAt?: string;
  nextRetryAt?: string;
  lastError?: string;
};

export type IngestionMetricsVO = {
  totalJobs: number;
  successJobs: number;
  failedJobs: number;
  failureRate: number;
  avgDurationSeconds: number;
  successRateByFileType?: Record<string, number>;
  retryCountDistribution?: Record<string, number>;
};

export type RetrievalDetectCase = {
  question: string;
  goldDocumentId?: number;
  goldChunkIndex?: number;
};

export type RetrievalDetectRequest = {
  groupId: number;
  topK?: number;
  cases: RetrievalDetectCase[];
  includeRerankComparison?: boolean;
};

export type RetrievalDetectResponse = {
  caseCount: number;
  labeledCount: number;
  meanHitAt1: number | null;
  meanHitAtK: number | null;
  mrr: number | null;
  meanHitAt1Baseline?: number | null;
  meanHitAtKBaseline?: number | null;
  mrrBaseline?: number | null;
  details: any[];
};

export const ragDetectApi = {
  detectRetrieval(body: RetrievalDetectRequest) {
    return request<RetrievalDetectResponse>({
      method: 'POST',
      url: '/rag/detect/retrieval',
      data: body,
      timeout: 120000,
    });
  },
};

export const ragIngestionApi = {
  listJobs(limit = 50) {
    return request<IngestionJobVO[]>({ method: 'GET', url: '/rag/ingest/jobs', params: { limit } });
  },
  getJob(jobId: number) {
    return request<IngestionJobVO>({ method: 'GET', url: `/rag/ingest/jobs/${jobId}` });
  },
  metrics() {
    return request<IngestionMetricsVO>({ method: 'GET', url: '/rag/ingest/metrics' });
  },
};
