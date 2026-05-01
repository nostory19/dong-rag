import { defineStore } from 'pinia';

type AuthState = {
  token: string;
  userCode: string;
  displayName: string;
  userRole: string;
};

const KEY = 'dong-rag-user-auth';

function loadState(): AuthState {
  const raw = localStorage.getItem(KEY);
  if (!raw) {
    return { token: '', userCode: '', displayName: '', userRole: '' };
  }
  try {
    return JSON.parse(raw) as AuthState;
  } catch {
    return { token: '', userCode: '', displayName: '', userRole: '' };
  }
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => loadState(),
  getters: {
    isLogin: (state) => Boolean(state.token),
    isAdmin: (state) => state.userRole === 'admin',
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
