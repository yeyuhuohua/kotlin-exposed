import { createRouter, createWebHistory } from 'vue-router'
import type { Component } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
/** 路由守卫根据最新角色权限限制页面访问，并为无权限账号保留个人资料入口。 */
import { useAuth } from './stores/auth'
import { useNotices } from './stores/notices'
import { readSession } from './lib/session'
import { resources } from './config/resources'
import type { PageRoute } from './types'

// 页面清单由后端 /api/auth/routes 按当前用户权限下发（数据源是菜单表），这里只补视图组件映射；
// 新增页面需在后端菜单管理里登记（内置菜单随启动播种），并在这里（静态页）或 config/resources（资源页）补一条。
type LazyView = () => Promise<{ default: Component }>
const staticViews: Record<string, LazyView> = {
  overview: () => import('./views/DashboardView.vue'),
  employees: () => import('./views/EmployeesView.vue'),
  system: () => import('./views/SystemView.vue'),
  audit: () => import('./views/AuditView.vue'),
  menus: () => import('./views/MenusView.vue'),
}

/** 把后端下发的页面转成 AppLayout 下的子路由；前端没有对应组件的页面跳过并告警。 */
export function buildRouteRecord(page: PageRoute): RouteRecordRaw | null {
  const meta = { title: page.title, adminOnly: page.adminOnly, page: page.key }
  const staticView = staticViews[page.key]
  if (staticView) {
    return {
      path: page.path === '/' ? '' : page.path.replace(/^\//, ''),
      name: page.key,
      component: staticView,
      meta,
    }
  }
  const resource = resources.find((item) => item.key === page.key)
  if (resource) {
    return {
      path: page.path.replace(/^\//, ''),
      name: page.key,
      component: () => import('./views/ResourceView.vue'),
      props: { resource },
      meta,
    }
  }
  console.warn(`后端下发了未知页面 ${page.key}，请在 router.ts 或 config/resources.ts 补组件映射`)
  return null
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
      name: 'app',
      component: () => import('./components/AppLayout.vue'),
      children: [
        // 业务页面路由登录后由 ensurePageRoutes 按后端下发清单动态注册。
        { path: 'account', component: () => import('./views/AccountView.vue'), meta: { title: '我的账号' } },
        {
          path: 'forbidden',
          component: () => import('./views/ForbiddenView.vue'),
          meta: { title: '无访问权限' },
        },
        // 布局内兜底：页面不存在或路由尚未注册时不至于重定向死循环。
        {
          path: ':pathMatch(.*)*',
          component: () => import('./views/RouteMissingView.vue'),
          meta: { title: '页面不存在' },
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

let removePageRoutes: (() => void)[] = []
/** 已注册路由的指纹：会话 Token + 页面权限集合。权限被后台调整后下次导航会重新拉取注册。 */
let registeredFingerprint = ''
let registering: Promise<boolean> | undefined

function routeFingerprint(): string {
  const auth = useAuth()
  const granted = (auth.user?.permissions || [])
    .filter((code) => code.startsWith('page:'))
    .sort()
    .join(',')
  return `${readSession()?.token ?? ''}|${granted}`
}

/** 按后端下发的页面清单注册动态路由；返回 true 表示本次有新路由，需要重新匹配当前导航。 */
export function ensurePageRoutes(): Promise<boolean> {
  const fingerprint = routeFingerprint()
  if (fingerprint === registeredFingerprint) return Promise.resolve(false)
  registering ??= (async () => {
    const auth = useAuth()
    // 非首次注册说明权限或会话发生变化，必须重新拉取而不是复用内存里的清单。
    await auth.loadPages({ force: registeredFingerprint !== '' })
    removePageRoutes.forEach((remove) => remove())
    removePageRoutes = []
    for (const page of auth.pages) {
      const record = buildRouteRecord(page)
      if (record) removePageRoutes.push(router.addRoute('app', record))
    }
    // 以加载后的状态为准：加载期间会话切换时 loadPages 不会覆盖 pages，指纹也随之失效。
    registeredFingerprint = routeFingerprint()
    return true
  })().finally(() => {
    registering = undefined
  })
  return registering
}

router.beforeEach(async (to) => {
  const auth = useAuth()
  await auth.restore()
  // 后台刷新即可：权限回收由 hr:unauthorized / hr:forbidden 事件兜底，导航不被 /auth/me 阻塞。
  if (auth.user && !to.meta.public) void auth.refreshUser()
  if (!to.meta.public && !auth.user) return { name: 'login', query: { redirect: to.fullPath } }
  if (auth.user && !to.meta.public) {
    try {
      if (await ensurePageRoutes()) return { path: to.path, query: to.query, hash: to.hash, replace: true }
    } catch {
      useNotices().show('页面清单加载失败，请刷新重试', true)
      return false
    }
  }
  if (to.meta.adminOnly && !auth.isAdmin) return '/forbidden'
  if (typeof to.meta.page === 'string' && !auth.canPage(to.meta.page)) return '/forbidden'
  if (to.name === 'login' && auth.user) return auth.home
  document.title = `${String(to.meta.title || '人事工作台')} · HR Workspace`
})
