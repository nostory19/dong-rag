<template>
  <div style="max-width:420px;margin:80px auto;background:#fff;padding:24px;border-radius:8px">
    <h2>用户登录</h2>
    <a-form layout="vertical">
      <a-form-item label="账号">
        <a-input v-model:value="form.userCode" />
      </a-form-item>
      <a-form-item label="密码">
        <a-input-password v-model:value="form.userPassword" />
      </a-form-item>
      <a-space>
        <a-button type="primary" :loading="loading" @click="submit">登录</a-button>
        <a-button @click="router.push('/register')">注册</a-button>
      </a-space>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { userApi } from '../api/services';
import { useAuthStore } from '../stores/auth';

const router = useRouter();
const auth = useAuthStore();
const loading = ref(false);
const form = reactive({ userCode: '', userPassword: '' });

async function submit() {
  loading.value = true;
  try {
    const data = await userApi.login(form);
    auth.setAuth({
      token: data.token,
      userCode: data.userCode,
      displayName: data.displayName,
      userRole: data.userRole,
    });
    message.success('登录成功');
    router.push('/groups');
  } finally {
    loading.value = false;
  }
}
</script>
