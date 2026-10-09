// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
/** 动态路由：后端清单 → 组件映射 → addRoute 注册，权限变化后重新注册。 */
import { api } from '../src/lib/api'
import { saveSession } from '../src/lib/session'
import type { PageRoute, User } from '../src/types'

vi.mock('../src/lib/api', async (importOriginal) => {
  const module = await importOriginal<typeof import('../src/lib/api')>()
  return { ...module, api: vi.fn() }
})

const mockedApi = vi.mocked(api)

const admin: User = {
  id: 1,
  username: 'admin',
  roleCode: 'ADMIN',
  enabled: true,
  permissions: ['page:overview', 'page:departments'],
}
const grantedPages: PageRoute[] = [
  { key: 'overview', title: '工作概览', path: '/', icon: 'LayoutDashboard', group: '工作空间', sort: 10 },
  {
    key: 'departments',
    title: '部门管理',
    path: '/departments',
    icon: 'Building2',
    group: '工作空间',
    sort: 30,
  },
]

describe('动态路由注册', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
    mockedApi.mockReset()
  })

  it('buildRouteRecord 映射静态页与资源页，未知页面跳过', async () => {
    const { buildRouteRecord } = await import('../src/router')
    const overview = buildRouteRecord(grantedPages[0]!)
    expect(overview?.path).toBe('')
    expect(overview?.name).toBe('overview')
    expect(overview?.meta?.page).toBe('overview')
    const departments = buildRouteRecord(grantedPages[1]!)
    expect(departments?.path).toBe('departments')
    expect(departments?.props).toMatchObject({ resource: { key: 'departments' } })
    expect(
      buildRouteRecord({
        key: 'not-a-page',
        title: '未知',
        path: '/not-a-page',
        icon: null,
        group: 'g',
        sort: 1,
      }),
    ).toBeNull()
  })

  it('ensurePageRoutes 注册下发清单，指纹不变时不重复注册', async () => {
    saveSession('token-r1', 600)
    mockedApi.mockResolvedValueOnce(grantedPages)
    const { useAuth } = await import('../src/stores/auth')
    const { ensurePageRoutes, router } = await import('../src/router')
    useAuth().user = admin
    expect(await ensurePageRoutes()).toBe(true)
    const names = router.getRoutes().map((route) => route.name)
    expect(names).toContain('overview')
    expect(names).toContain('departments')
    expect(await ensurePageRoutes()).toBe(false)
    expect(mockedApi).toHaveBeenCalledTimes(1)
  })

  it('页面权限变化后重新拉取并注册新页面', async () => {
    saveSession('token-r2', 600)
    mockedApi.mockResolvedValueOnce(grantedPages)
    const { useAuth } = await import('../src/stores/auth')
    const { ensurePageRoutes, router } = await import('../src/router')
    const auth = useAuth()
    auth.user = admin
    expect(await ensurePageRoutes()).toBe(true)
    expect(router.getRoutes().some((route) => route.name === 'jobs')).toBe(false)

    // 后台给角色追加了岗位页面：下一次导航会带着新权限指纹重新注册。
    auth.user = { ...admin, permissions: [...admin.permissions, 'page:jobs'] }
    mockedApi.mockResolvedValueOnce([
      ...grantedPages,
      {
        key: 'jobs',
        title: '岗位管理',
        path: '/jobs',
        icon: 'BriefcaseBusiness',
        group: '工作空间',
        sort: 40,
      },
    ])
    expect(await ensurePageRoutes()).toBe(true)
    expect(router.getRoutes().some((route) => route.name === 'jobs')).toBe(true)
    expect(mockedApi).toHaveBeenCalledTimes(2)
  })
})
