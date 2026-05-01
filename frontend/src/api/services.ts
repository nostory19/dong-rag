import { request } from './http';
import type { GroupVO, LoginUserVO } from './types';

export const userApi = {
  register(payload: { userCode: string; displayName: string; userPassword: string; checkPassword: string }) {
    return request<number>({ method: 'POST', url: '/user/register', data: payload });
  },
  login(payload: { userCode: string; userPassword: string }) {
    return request<LoginUserVO>({ method: 'POST', url: '/user/login', data: payload });
  },
  logout() {
    return request<boolean>({ method: 'POST', url: '/user/logout' });
  },
};

export const groupApi = {
  myList() {
    return request<GroupVO[]>({ method: 'GET', url: '/group/my/list' });
  },
  create(payload: { groupCode: string; groupName: string }) {
    return request<number>({ method: 'POST', url: '/group/create', data: payload });
  },
  join(payload: { groupId: number }) {
    return request<boolean>({ method: 'POST', url: '/group/join', data: payload });
  },
};

export const ragApi = {
  ingestText(payload: { groupId: number; fileName: string; content: string }) {
    return request<{ documentId: number; jobId: number; documentStatus: string; jobStatus: string; failureReason?: string; lastError?: string }>({
      method: 'POST',
      url: '/rag/ingest/text',
      data: payload,
    });
  },
  async ingestFile(groupId: number, file: File) {
    const formData = new FormData();
    formData.append('groupId', String(groupId));
    formData.append('file', file);
    return request<{ documentId: number; jobId: number; documentStatus: string; jobStatus: string; failureReason?: string; lastError?: string }>({
      method: 'POST',
      url: '/rag/ingest/file',
      data: formData,
    });
  },
  getTaskStatus(jobId: number) {
    return request<{ documentId: number; jobId: number; documentStatus: string; jobStatus: string; failureReason?: string; lastError?: string }>({
      method: 'GET',
      url: `/rag/ingest/task/${jobId}`,
    });
  },
  ask(payload: { groupId: number; question: string; topK: number }) {
    return request<any>({ method: 'POST', url: '/rag/qa/ask', data: payload });
  },
};
