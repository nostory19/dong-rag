<template>
  <a-card title="用户管理">
    <a-input v-model:value="keyword" placeholder="按账号过滤" style="width:240px;margin-bottom:12px" />
    <a-table :data-source="filtered" row-key="id">
      <a-table-column title="ID" data-index="id" />
      <a-table-column title="账号" data-index="userCode" />
      <a-table-column title="昵称" data-index="displayName" />
      <a-table-column title="角色">
        <template #default="{ record }">
          <a-tag :color="record.userRole === 'admin' ? 'red' : 'blue'">{{ record.userRole }}</a-tag>
        </template>
      </a-table-column>
    </a-table>
  </a-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { userApi, type LoginUserVO } from '../api/services';

const keyword = ref('');
const users = ref<LoginUserVO[]>([]);
const filtered = computed(() =>
  users.value.filter((u) => u.userCode.includes(keyword.value.trim())),
);

onMounted(async () => {
  users.value = await userApi.list();
});
</script>
