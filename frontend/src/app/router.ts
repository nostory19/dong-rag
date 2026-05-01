import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth';

const routes = [
  { path: '/login', component: () => import('../pages/LoginPage.vue') },
  { path: '/register', component: () => import('../pages/RegisterPage.vue') },
  {
    path: '/',
    component: () => import('../components/UserLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/groups' },
      { path: '/groups', component: () => import('../pages/GroupPage.vue') },
      { path: '/documents', component: () => import('../pages/DocumentPage.vue') },
      { path: '/qa', component: () => import('../pages/QaPage.vue') },
      { path: '/assistant', component: () => import('../pages/AssistantPage.vue') },
    ],
  },
  { path: '/:pathMatch(.*)*', component: () => import('../pages/NotFoundPage.vue') },
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
