<template>
  <a-layout style="min-height: 100vh">
    <a-layout-sider>
      <div style="color:#fff;padding:16px;font-weight:700">Dong RAG Admin</div>
      <a-menu theme="dark" mode="inline" :selected-keys="[selected]">
        <a-menu-item key="/dashboard" @click="go('/dashboard')">仪表盘</a-menu-item>
        <a-menu-item key="/users" @click="go('/users')">用户管理</a-menu-item>
        <a-menu-item key="/ingestion-jobs" @click="go('/ingestion-jobs')">入库任务</a-menu-item>
        <a-menu-item key="/complaint-eval" @click="go('/complaint-eval')">投诉评测</a-menu-item>
        <a-menu-item key="/system-runtime" @click="go('/system-runtime')">系统运行</a-menu-item>
      </a-menu>
    </a-layout-sider>
    <a-layout>
      <a-layout-header style="background:#fff;display:flex;justify-content:space-between;align-items:center">
        <div>管理员：{{ auth.displayName || auth.userCode }}</div>
        <a-button @click="logout">退出</a-button>
      </a-layout-header>
      <a-layout-content style="margin:16px">
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
