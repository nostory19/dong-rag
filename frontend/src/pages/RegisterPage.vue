<template>
  <div style="max-width:520px;margin:80px auto;background:#fff;padding:24px;border-radius:8px">
    <h2>用户注册</h2>
    <a-form layout="vertical">
      <a-form-item label="账号"><a-input v-model:value="form.userCode" /></a-form-item>
      <a-form-item label="昵称"><a-input v-model:value="form.displayName" /></a-form-item>
      <a-form-item label="密码"><a-input-password v-model:value="form.userPassword" /></a-form-item>
      <a-form-item label="确认密码"><a-input-password v-model:value="form.checkPassword" /></a-form-item>
      <a-space>
        <a-button type="primary" :loading="loading" @click="submit">提交注册</a-button>
        <a-button @click="router.push('/login')">返回登录</a-button>
      </a-space>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { userApi } from '../api/services';

const router = useRouter();
const loading = ref(false);
const form = reactive({
  userCode: '',
  displayName: '',
  userPassword: '',
  checkPassword: '',
});

async function submit() {
  loading.value = true;
  try {
    await userApi.register(form);
    message.success('注册成功，请登录');
    router.push('/login');
  } finally {
    loading.value = false;
  }
}
</script>
