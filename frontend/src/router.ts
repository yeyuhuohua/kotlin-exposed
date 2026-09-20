import { createRouter, createWebHistory } from 'vue-router'
/** 路由守卫根据最新角色权限限制页面访问，并为无权限账号保留个人资料入口。 */
import { useAuth } from './stores/auth'
import { resources } from './config/resources'
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('./views/LoginView.vue'),
      meta: { public: true, title: '登录' },
    },
    {
      path: '/',
      component: () => import('./components/AppLayout.vue'),
      children: [
        {
          path: '',
          name: 'overview',
          component: () => import('./views/DashboardView.vue'),
          meta: { title: '工作概览', page: 'overview' },
        },
        {
          path: 'employees',
          component: () => import('./views/EmployeesView.vue'),
          meta: { title: '员工管理', page: 'employees' },
        },
        ...resources.map((resource) => ({
          path: resource.key,
          component: () => import('./views/ResourceView.vue'),
          props: { resource },
          meta: { title: resource.title, adminOnly: resource.adminOnly, page: resource.key },
        })),
        {
          path: 'system',
          component: () => import('./views/SystemView.vue'),
          meta: { title: '系统状态', page: 'system' },
        },
        { path: 'account', component: () => import('./views/AccountView.vue'), meta: { title: '我的账号' } },
        {
          path: 'forbidden',
          component: () => import('./views/ForbiddenView.vue'),
          meta: { title: '无访问权限' },
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: () => ({ top: 0 }),
})
router.beforeEach(async (to) => {
  const auth = useAuth()
  await auth.restore()
  if (auth.user && !to.meta.public) await auth.refreshUser()
  if (!to.meta.public && !auth.user) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.meta.adminOnly && !auth.isAdmin) return '/forbidden'
  if (typeof to.meta.page === 'string' && !auth.canPage(to.meta.page)) return '/forbidden'
  if (to.name === 'login' && auth.user) return auth.home
  document.title = `${String(to.meta.title || '人事工作台')} · HR Workspace`
})
