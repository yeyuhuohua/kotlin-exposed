// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import ElementPlus from 'element-plus'
import DashboardView from '../src/views/DashboardView.vue'
import ResourceView from '../src/views/ResourceView.vue'
import { resources } from '../src/config/resources'

const mocks = vi.hoisted(() => ({ api: vi.fn() }))
vi.mock('../src/lib/api', async (original) => ({
  ...await original<typeof import('../src/lib/api')>(), api: mocks.api,
}))
vi.mock('../src/stores/auth', () => ({ useAuth: () => ({
  user: { id: 1, username: 'admin', roleCode: 'ADMIN' },
  pages: ['employees', 'departments', 'jobs', 'locations'].map((key) => ({ key, path: `/changed/${key}` })),
  canPage: () => true,
  canApi: () => true,
}) }))
vi.mock('../src/stores/notices', () => ({ useNotices: () => ({ show: vi.fn() }) }))
vi.mock('../src/stores/theme', () => ({ useTheme: () => ({ isDark: false }) }))

let wrapper: VueWrapper | undefined
afterEach(() => { wrapper?.unmount(); vi.clearAllMocks() })

async function mountView(view: typeof DashboardView | typeof ResourceView, props = {}) {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/:pathMatch(.*)*', component: { template: '<div />' } },
  ] })
  await router.push('/')
  wrapper = mount(view, { props, global: { plugins: [ElementPlus, router] } })
  await flushPromises()
  return wrapper
}

describe('server menu paths', () => {
  it('概览指标和员工详情跳转跟随修改后的菜单地址', async () => {
    mocks.api.mockResolvedValue({
      employeeTotal: 1, departmentCount: 1, jobCount: 1, locationCount: 1, regionCount: 1,
      departmentHeadcount: [{ departmentId: 10, departmentName: 'Sales', count: 1 }],
      departments: [{ departmentId: 10, departmentName: 'Sales' }],
      salarySummary: { averageSalary: 100 },
      sampleEmployees: [{ employeeId: 7, firstName: 'A', lastName: 'B', departmentId: 10, salary: 100 }],
    })
    const view = await mountView(DashboardView)
    const links = view.findAll('a').map((link) => link.attributes('href'))
    for (const key of ['employees', 'departments', 'jobs', 'locations']) {
      expect(links).toContain(`/changed/${key}`)
    }
    expect(links).toContain('/changed/employees?employeeId=7')
    expect(links).toContain('/changed/employees?departmentId=10')
  })

  it('部门列表查看员工使用动态菜单路径及部门筛选', async () => {
    mocks.api.mockResolvedValue([{ departmentId: 10, departmentName: 'Sales' }])
    const view = await mountView(ResourceView, {
      resource: resources.find((resource) => resource.key === 'departments')!,
    })
    expect(view.findAll('a').map((link) => link.attributes('href')))
      .toContain('/changed/employees?departmentId=10')
  })
})
