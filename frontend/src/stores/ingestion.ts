import { defineStore } from 'pinia';

export type IngestionTaskState = {
  documentId: number;
  jobId: number;
  documentStatus: string;
  jobStatus: string;
  failureReason?: string;
  lastError?: string;
  createdAt: string;
  updatedAt: string;
};

const STORAGE_KEY = 'dong-rag-recent-ingestion-tasks';

function readTasks(): IngestionTaskState[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return [];
    }
    const parsed = JSON.parse(raw) as IngestionTaskState[];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function writeTasks(tasks: IngestionTaskState[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(tasks));
}

export const useIngestionStore = defineStore('ingestion', {
  state: () => ({
    recentTasks: readTasks() as IngestionTaskState[],
  }),
  getters: {
    latestTask: (state) => state.recentTasks[0],
    runningCount: (state) =>
      state.recentTasks.filter(
        (task) =>
          !['READY', 'FAILED'].includes(task.documentStatus) &&
          !['SUCCESS', 'FAILED'].includes(task.jobStatus),
      ).length,
  },
  actions: {
    upsertTask(payload: Omit<IngestionTaskState, 'createdAt' | 'updatedAt'>) {
      const now = new Date().toISOString();
      const idx = this.recentTasks.findIndex((item) => item.jobId === payload.jobId);
      if (idx >= 0) {
        const previous = this.recentTasks[idx];
        this.recentTasks[idx] = {
          ...previous,
          ...payload,
          updatedAt: now,
        };
      } else {
        this.recentTasks.unshift({
          ...payload,
          createdAt: now,
          updatedAt: now,
        });
      }
      this.recentTasks = this.recentTasks.slice(0, 5);
      writeTasks(this.recentTasks);
    },
  },
});
