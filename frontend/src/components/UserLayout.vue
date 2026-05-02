<template>
  <a-layout class="layout-shell">
    <a-layout-sider width="220" theme="light" class="layout-sider">
      <div class="brand">Dong RAG</div>
      <a-menu mode="inline" :selected-keys="[selectedKey]">
        <a-menu-item key="/groups" @click="go('/groups')">我的组</a-menu-item>
        <a-menu-item key="/documents" @click="go('/documents')">文档入库</a-menu-item>
        <a-menu-item key="/qa" @click="go('/qa')">知识问答</a-menu-item>
        <a-menu-item key="/assistant" @click="go('/assistant')">智能助手</a-menu-item>
      </a-menu>
    </a-layout-sider>
    <a-layout>
      <a-layout-header class="layout-header">
        <div>
          <div class="page-title">{{ pageTitle }}</div>
          <div class="page-desc">{{ pageDescription }}</div>
        </div>
        <a-space>
          <a-tag color="blue">当前组：{{ currentGroupLabel }}</a-tag>
          <a-button v-if="latestTaskLabel" type="default" @click="go('/documents')">
            最近任务：{{ latestTaskLabel }}
          </a-button>
          <a-badge :count="ingestionStore.runningCount" :show-zero="false">
            <a-button @click="go('/documents')">入库进度</a-button>
          </a-badge>
          <a-button @click="logout">退出登录</a-button>
        </a-space>
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
import { userApi } from '../api/services';
import { useAuthStore } from '../stores/auth';
import { useGroupStore } from '../stores/group';
import { useIngestionStore } from '../stores/ingestion';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const groupStore = useGroupStore();
const ingestionStore = useIngestionStore();

const selectedKey = computed(() => route.path);
const pageTitle = computed(() => (route.meta.title as string) || 'Dong RAG');
const pageDescription = computed(() => (route.meta.description as string) || `当前用户：${auth.displayName || auth.userCode}`);
const currentGroupLabel = computed(() => (groupStore.currentGroupId ? `#${groupStore.currentGroupId}` : '未选择'));
const latestTaskLabel = computed(() => {
  const task = ingestionStore.latestTask;
  if (!task) {
    return '';
  }
  return `job#${task.jobId} (${task.jobStatus})`;
});

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
