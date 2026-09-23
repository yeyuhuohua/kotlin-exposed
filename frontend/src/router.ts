import { createRouter, createWebHistory } from 'vue-router'
import type { Component } from 'vue'
/** 路由守卫根据最新角色权限限制页面访问，并为无权限账号保留个人资料入口。 */
import { useAuth } from './stores/auth'
import { resources, staticPages } from './config/resources'

// 页面清单在 config/resources 里，路由这里只补组件，避免两处各写一份页面列表。
type LazyView = () => Promise<{ default: Component }>
const pageComponents: Record<string, LazyView> = {
  overview: () => import('./views/DashboardView.vue'),
  employees: () => import('./views/EmployeesView.vue'),
  system: () => import('./views/SystemView.vue'),
  audit: () => import('./views/AuditView.vue'),
}

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
        ...staticPages.map((page) => ({
          path: page.path === '/' ? '' : page.path.slice(1),
          name: page.key,
          component: pageComponents[page.key]!,
          meta: { title: page.title, adminOnly: page.adminOnly, page: page.key },
        })),
        ...resources.map((resource) => ({
          path: resource.key,
          component: () => import('./views/ResourceView.vue'),
          props: { resource },
          meta: { title: resource.title, adminOnly: resource.adminOnly, page: resource.key },
        })),
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
  // 后台刷新即可：权限回收由 hr:unauthorized / hr:forbidden 事件兜底，导航不被 /auth/me 阻塞。
  if (auth.user && !to.meta.public) void auth.refreshUser()
  if (!to.meta.public && !auth.user) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.meta.adminOnly && !auth.isAdmin) return '/forbidden'
  if (typeof to.meta.page === 'string' && !auth.canPage(to.meta.page)) return '/forbidden'
  if (to.name === 'login' && auth.user) return auth.home
  document.title = `${String(to.meta.title || '人事工作台')} · HR Workspace`
})
