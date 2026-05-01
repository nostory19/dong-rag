import { defineStore } from 'pinia';

export const useGroupStore = defineStore('group', {
  state: () => ({
    currentGroupId: 0 as number,
  }),
  actions: {
    setCurrentGroupId(groupId: number) {
      this.currentGroupId = groupId;
    },
  },
});
