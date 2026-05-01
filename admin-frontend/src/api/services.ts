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
  evalComplaint(groupId: number) {
    return request<any>({ method: 'POST', url: '/assistant/eval/complaint', params: { groupId } });
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

export const ragIngestionApi = {
  listJobs(limit = 50) {
    return request<IngestionJobVO[]>({ method: 'GET', url: '/rag/ingest/jobs', params: { limit } });
  },
  getJob(jobId: number) {
    return request<IngestionJobVO>({ method: 'GET', url: `/rag/ingest/jobs/${jobId}` });
  },
};
