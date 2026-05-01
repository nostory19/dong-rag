import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth';

const routes = [
  { path: '/login', component: () => import('../pages/LoginPage.vue') },
  {
    path: '/',
    component: () => import('../components/AdminLayout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      { path: '', redirect: '/dashboard' },
      { path: '/dashboard', component: () => import('../pages/DashboardPage.vue') },
      { path: '/users', component: () => import('../pages/UserManagePage.vue') },
      { path: '/ingestion-jobs', component: () => import('../pages/IngestionJobsPage.vue') },
      { path: '/complaint-eval', component: () => import('../pages/ComplaintEvalPage.vue') },
      { path: '/system-runtime', component: () => import('../pages/SystemRuntimePage.vue') },
    ],
  },
];

export const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach((to) => {
  const auth = useAuthStore();
  if (to.meta.requiresAuth && !auth.isLogin) return '/login';
  if (to.meta.requiresAdmin && !auth.isAdmin) return '/login';
  if (to.path === '/login' && auth.isLogin && auth.isAdmin) return '/dashboard';
  return true;
});
