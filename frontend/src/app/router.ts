import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    component: () => import('../pages/LoginPage.vue'),
    meta: { title: '用户登录', description: '使用账号登录系统' },
  },
  {
    path: '/register',
    component: () => import('../pages/RegisterPage.vue'),
    meta: { title: '用户注册', description: '创建新账号' },
  },
  {
    path: '/',
    component: () => import('../components/UserLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/groups' },
      {
        path: '/groups',
        component: () => import('../pages/GroupPage.vue'),
        meta: { title: '我的组', description: '创建组、加入组并切换当前知识组' },
      },
      {
        path: '/documents',
        component: () => import('../pages/DocumentPage.vue'),
        meta: { title: '文档入库', description: '上传文档并查看入库任务进展' },
      },
      {
        path: '/qa',
        component: () => import('../pages/QaPage.vue'),
        meta: { title: '知识问答', description: '基于当前组文档进行检索增强问答' },
      },
      {
        path: '/assistant',
        component: () => import('../pages/AssistantPage.vue'),
        meta: { title: '知识助手', description: '基于当前组文档的多专家编排与流式汇总' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    component: () => import('../pages/NotFoundPage.vue'),
    meta: { title: '页面不存在', description: '请检查访问路径' },
  },
];

export const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach((to) => {
  const auth = useAuthStore();
  if (to.meta.requiresAuth && !auth.isLogin) {
    return '/login';
  }
  if ((to.path === '/login' || to.path === '/register') && auth.isLogin) {
    return '/groups';
  }
  return true;
});
