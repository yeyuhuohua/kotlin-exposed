// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia } from 'pinia'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import AppLayout from '../src/components/AppLayout.vue'
import AppLogo from '../src/components/AppLogo.vue'
import DataPanel from '../src/components/DataPanel.vue'
import PageHeader from '../src/components/PageHeader.vue'

const mocks = vi.hoisted(() => ({
  api: vi.fn(),
  logout: vi.fn(),
  push: vi.fn(),
  replace: vi.fn(),
  show: vi.fn(),
  toggle: vi.fn(),
}))
vi.mock('../src/lib/api', async (importOriginal) => {
  const module = await importOriginal<typeof import('../src/lib/api')>()
  return { ...module, api: mocks.api }
})
vi.mock('../src/stores/auth', () => ({
  useAuth: () => ({
    user: { id: 1, username: 'admin', roleCode: 'ADMIN', permissions: [] },
    isAdmin: true,
    pages: [],
    home: '/',
    canPage: () => false,
    canApi: () => false,
    logout: mocks.logout,
  }),
}))
vi.mock('../src/stores/notices', () => ({ useNotices: () => ({ show: mocks.show }) }))
vi.mock('../src/stores/theme', () => ({ useTheme: () => ({ isDark: false, toggle: mocks.toggle }) }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ meta: {}, path: '/', fullPath: '/' }),
  useRouter: () => ({ push: mocks.push, replace: mocks.replace }),
}))

const mounted: VueWrapper[] = []
const options = {
  attachTo: document.body,
  global: {
    plugins: [ElementPlus, createPinia()],
    stubs: {
      RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
      RouterView: { template: '<div />' },
    },
  },
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.api.mockResolvedValue({ status: 'UP', database: 'UP', redis: 'UP' })
  mocks.logout.mockResolvedValue(undefined)
  mocks.push.mockResolvedValue(undefined)
  mocks.replace.mockResolvedValue(undefined)
  Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', { configurable: true, value: vi.fn() })
  if (!globalThis.ResizeObserver) {
    globalThis.ResizeObserver = class {
      observe() {}
      unobserve() {}
      disconnect() {}
    }
  }
})

afterEach(() => {
  mounted.forEach((wrapper) => wrapper.unmount())
  mounted.length = 0
  document.body.innerHTML = ''
})

describe('统一页面骨架', () => {
  it('品牌组件支持紧凑和反色模式', () => {
    const wrapper = mount(AppLogo, { props: { compact: true, inverse: true } })
    expect(wrapper.classes()).toContain('compact')
    expect(wrapper.classes()).toContain('inverse')
    expect(wrapper.find('.logo-copy').exists()).toBe(false)
    expect(wrapper.find('.logo-mark').exists()).toBe(true)
  })

  it('页头展示业务信息并渲染操作区', () => {
    const wrapper = mount(PageHeader, {
      props: { title: '员工管理', description: '组织成员', category: '组织资料', count: 42 },
      slots: { default: '<button type="button">新增员工</button>' },
    })
    expect(wrapper.text()).toContain('组织资料')
    expect(wrapper.text()).toContain('员工管理')
    expect(wrapper.text()).toContain('42')
    expect(wrapper.find('.page-header-actions button').text()).toBe('新增员工')
  })

  it('数据面板按标题、工具栏、正文和页脚组织插槽', () => {
    const wrapper = mount(DataPanel, {
      props: { title: '员工速览', subtitle: '组织成员摘要', meta: '8 人' },
      slots: {
        toolbar: '<div class="table-toolbar">搜索区</div>',
        default: '<div class="employee-table">表格区</div>',
        footer: '<div class="pagination">分页区</div>',
      },
    })
    expect(wrapper.find('.data-panel-title').text()).toContain('员工速览')
    expect(wrapper.find('.data-panel-title').text()).toContain('8 人')
    expect(wrapper.find('.data-panel-toolbar').text()).toBe('搜索区')
    expect(wrapper.find('.data-panel-body').text()).toBe('表格区')
    expect(wrapper.find('.data-panel-footer').text()).toBe('分页区')
  })
})

describe('顶部账户操作', () => {
  async function mountLayout() {
    const wrapper = mount(AppLayout, options)
    mounted.push(wrapper)
    await flushPromises()
    return wrapper
  }

  it('右上角账户入口直接链接到账户页', async () => {
    const wrapper = await mountLayout()
    expect(wrapper.get('a[aria-label="我的账号"]').attributes('href')).toBe('/account')
  })

  it('直接退出按钮调用注销并返回登录页', async () => {
    const wrapper = await mountLayout()
    await wrapper.get('button[aria-label="退出登录"]').trigger('click')
    await flushPromises()
    expect(mocks.logout).toHaveBeenCalledTimes(1)
    expect(mocks.replace).toHaveBeenCalledWith('/login')
  })
})
