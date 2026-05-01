<template>
  <a-row :gutter="16">
    <a-col :span="8">
      <a-card title="用户总数">{{ users.length }}</a-card>
    </a-col>
    <a-col :span="8">
      <a-card title="管理员数量">{{ adminCount }}</a-card>
    </a-col>
    <a-col :span="8">
      <a-card title="普通用户数量">{{ users.length - adminCount }}</a-card>
    </a-col>
  </a-row>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { userApi, type LoginUserVO } from '../api/services';

const users = ref<LoginUserVO[]>([]);
const adminCount = computed(() => users.value.filter((u) => u.userRole === 'admin').length);

onMounted(async () => {
  users.value = await userApi.list();
});
</script>
