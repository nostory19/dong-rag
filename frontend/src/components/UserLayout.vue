<template>
  <a-layout style="min-height: 100vh">
    <a-layout-sider>
      <div style="color:#fff;padding:16px;font-weight:700">Dong RAG</div>
      <a-menu theme="dark" mode="inline" :selected-keys="[selectedKey]">
        <a-menu-item key="/groups" @click="go('/groups')">我的组</a-menu-item>
        <a-menu-item key="/documents" @click="go('/documents')">文档中心</a-menu-item>
        <a-menu-item key="/qa" @click="go('/qa')">知识问答</a-menu-item>
        <a-menu-item key="/assistant" @click="go('/assistant')">投诉智能客服</a-menu-item>
      </a-menu>
    </a-layout-sider>
    <a-layout>
      <a-layout-header style="background:#fff;display:flex;justify-content:space-between;align-items:center">
        <div>当前用户：{{ auth.displayName || auth.userCode }}</div>
        <a-button @click="logout">退出登录</a-button>
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
import { userApi } from '../api/services';
import { useAuthStore } from '../stores/auth';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const selectedKey = computed(() => route.path);

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
