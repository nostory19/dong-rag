<template>
  <a-layout class="layout-shell">
    <a-layout-sider width="220" theme="light" class="layout-sider">
      <div class="brand">Dong RAG Admin</div>
      <a-menu mode="inline" :selected-keys="[selected]">
        <a-menu-item key="/dashboard" @click="go('/dashboard')">仪表盘</a-menu-item>
        <a-menu-item key="/users" @click="go('/users')">用户管理</a-menu-item>
        <a-menu-item key="/ingestion-jobs" @click="go('/ingestion-jobs')">入库任务</a-menu-item>
        <a-menu-item key="/complaint-eval" @click="go('/complaint-eval')">投诉评测</a-menu-item>
        <a-menu-item key="/system-runtime" @click="go('/system-runtime')">系统运行</a-menu-item>
      </a-menu>
    </a-layout-sider>
    <a-layout>
      <a-layout-header class="layout-header">
        <div>
          <div class="page-title">管理后台</div>
          <div class="page-desc">管理员：{{ auth.displayName || auth.userCode }}</div>
        </div>
        <a-button @click="logout">退出</a-button>
      </a-layout-header>
      <a-layout-content class="layout-content">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { useAuthStore } from '../stores/auth';
import { userApi } from '../api/services';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const selected = computed(() => route.path);

function go(path: string) {
  router.push(path);
}

async function logout() {
  try {
    await userApi.logout();
  } finally {
    auth.clear();
    message.success('已退出');
    router.push('/login');
  }
}
</script>

<style scoped>
.layout-shell {
  min-height: 100vh;
}

.layout-sider {
  border-right: 1px solid #f0f0f0;
}

.brand {
  font-weight: 700;
  font-size: 18px;
  padding: 20px 16px 12px;
}

.layout-header {
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
  padding: 0 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  line-height: 1.4;
}

.page-desc {
  color: #8c8c8c;
  font-size: 13px;
  line-height: 1.4;
}

.layout-content {
  margin: 16px;
}
</style>
