<template>
  <a-card title="我的组">
    <a-space style="margin-bottom: 12px">
      <a-button type="primary" @click="showCreate = true">创建组</a-button>
      <a-button @click="showJoin = true">加入组</a-button>
      <a-button @click="load">刷新</a-button>
    </a-space>
    <a-table :data-source="groups" :pagination="false" row-key="id">
      <a-table-column title="ID" data-index="id" />
      <a-table-column title="组编码" data-index="groupCode" />
      <a-table-column title="组名" data-index="groupName" />
      <a-table-column title="状态" data-index="status" />
      <a-table-column title="操作">
        <template #default="{ record }">
          <a-button type="link" @click="selectGroup(record.id)">设为当前组</a-button>
        </template>
      </a-table-column>
    </a-table>
  </a-card>

  <a-modal v-model:open="showCreate" title="创建组" @ok="createGroup">
    <a-form layout="vertical">
      <a-form-item label="组编码"><a-input v-model:value="createForm.groupCode" /></a-form-item>
      <a-form-item label="组名"><a-input v-model:value="createForm.groupName" /></a-form-item>
    </a-form>
  </a-modal>

  <a-modal v-model:open="showJoin" title="加入组" @ok="joinGroup">
    <a-form layout="vertical">
      <a-form-item label="组 ID"><a-input-number v-model:value="joinGroupId" style="width:100%" /></a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { groupApi } from '../api/services';
import type { GroupVO } from '../api/types';
import { useGroupStore } from '../stores/group';

const groupStore = useGroupStore();
const groups = ref<GroupVO[]>([]);
const showCreate = ref(false);
const showJoin = ref(false);
const joinGroupId = ref<number | null>(null);
const createForm = reactive({ groupCode: '', groupName: '' });

async function load() {
  groups.value = await groupApi.myList();
}

function selectGroup(groupId: number) {
  groupStore.setCurrentGroupId(groupId);
  message.success(`已切换当前组: ${groupId}`);
}

async function createGroup() {
  await groupApi.create(createForm);
  showCreate.value = false;
  message.success('创建成功');
  await load();
}

async function joinGroup() {
  if (!joinGroupId.value) {
    message.warning('请输入组 ID');
    return;
  }
  await groupApi.join({ groupId: joinGroupId.value });
  showJoin.value = false;
  message.success('加入成功');
  await load();
}

onMounted(load);
</script>
