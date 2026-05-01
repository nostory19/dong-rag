import { defineStore } from 'pinia';

const KEY = 'dong-rag-admin-auth';

type AuthState = {
  token: string;
  userCode: string;
  displayName: string;
  userRole: string;
};

function loadState(): AuthState {
  const raw = localStorage.getItem(KEY);
  if (!raw) return { token: '', userCode: '', displayName: '', userRole: '' };
  try {
    return JSON.parse(raw) as AuthState;
  } catch {
    return { token: '', userCode: '', displayName: '', userRole: '' };
  }
}

export const useAuthStore = defineStore('admin-auth', {
  state: (): AuthState => loadState(),
  getters: {
    isLogin: (s) => Boolean(s.token),
    isAdmin: (s) => s.userRole === 'admin',
  },
  actions: {
    setAuth(payload: AuthState) {
      this.token = payload.token;
      this.userCode = payload.userCode;
      this.displayName = payload.displayName;
      this.userRole = payload.userRole;
      localStorage.setItem(KEY, JSON.stringify(payload));
    },
    clear() {
      this.token = '';
      this.userCode = '';
      this.displayName = '';
      this.userRole = '';
      localStorage.removeItem(KEY);
    },
  },
});
