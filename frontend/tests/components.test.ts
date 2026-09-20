// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia } from 'pinia'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus, {
  ElButton,
  ElCheckboxGroup,
  ElForm,
  ElInput,
  ElInputNumber,
  ElPagination,
} from 'element-plus'
import RecordDialog from '../src/components/RecordDialog.vue'
import Pagination from '../src/components/Pagination.vue'
import RolePermissionEditor from '../src/components/RolePermissionEditor.vue'
import LoginView from '../src/views/LoginView.vue'

/** 在内存 DOM 中验证组件契约，不读取本机账号、不访问浏览器或真实 API。 */
const mocks = vi.hoisted(() => ({
  api: vi.fn(),
  show: vi.fn(),
  login: vi.fn(),
  replace: vi.fn(),
  canPage: vi.fn(() => false),
}))
vi.mock('../src/lib/api', () => ({ api: mocks.api }))
vi.mock('../src/stores/notices', () => ({ useNotices: () => ({ show: mocks.show }) }))
vi.mock('../src/stores/auth', () => ({
  useAuth: () => ({
    user: { id: 1, roleCode: 'ADMIN' },
    isAdmin: true,
    canApi: () => true,
    canPage: mocks.canPage,
    home: '/account',
    login: mocks.login,
    clear: vi.fn(),
  }),
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: { redirect: '/jobs' } }),
  useRouter: () => ({ replace: mocks.replace, resolve: (path: string) => ({ path }) }),
}))

const mounted: VueWrapper[] = []
// 主题 store 是真实实现（只操作 <html> 的 class），因此挂载时需要 Pinia
const options = { attachTo: document.body, global: { plugins: [ElementPlus, createPinia()] } }
beforeEach(() => {
  vi.clearAllMocks()
  mocks.api.mockResolvedValue({})
  mocks.login.mockResolvedValue(undefined)
  mocks.replace.mockResolvedValue(undefined)
  Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', { configurable: true, value: vi.fn() })
})
afterEach(() => {
  mounted.forEach((wrapper) => wrapper.unmount())
  mounted.length = 0
  document.body.innerHTML = ''
})

describe('Element Plus components', () => {
  it('validates required fields before creating a record', async () => {
    const wrapper = mount(RecordDialog, {
      ...options,
      props: {
        title: '新建角色',
        endpoint: '/auth/roles',
        idKey: 'code',
        fields: [
          { key: 'code', label: '角色编码', required: true },
          { key: 'name', label: '角色名称', required: true },
        ],
      },
    })
    mounted.push(wrapper)
    await flushPromises()
    expect(wrapper.findComponent(ElForm).vm.fields.length).toBe(2)
    await wrapper.findComponent(ElForm).trigger('submit')
    await flushPromises()
    expect(mocks.api).not.toHaveBeenCalled()
    const inputs = wrapper.findAllComponents(ElInput)
    await inputs[0]!.find('input').setValue('HR_VIEWER')
    await inputs[1]!.find('input').setValue('人事查询员')
    await wrapper.findComponent(ElForm).trigger('submit')
    await flushPromises()
    expect(mocks.api).toHaveBeenCalledWith('/auth/roles', {
      method: 'POST',
      body: { code: 'HR_VIEWER', name: '人事查询员' },
    })
    expect(wrapper.emitted('saved')).toHaveLength(1)
  })

  it('preserves numeric partial updates through the number input', async () => {
    const wrapper = mount(RecordDialog, {
      ...options,
      props: {
        title: '编辑员工',
        endpoint: '/employees',
        idKey: 'employeeId',
        original: { employeeId: 100, salary: 1000 },
        fields: [{ key: 'salary', label: '月薪', type: 'number', min: 0, step: '0.01' }],
      },
    })
    mounted.push(wrapper)
    await flushPromises()
    wrapper.findComponent(ElInputNumber).vm.$emit('update:modelValue', 1250.5)
    await wrapper.findComponent(ElForm).trigger('submit')
    await flushPromises()
    expect(mocks.api).toHaveBeenCalledWith('/employees/100', { method: 'PUT', body: { salary: 1250.5 } })
  })

  it('forwards pagination events without changing the API page contract', () => {
    const wrapper = mount(Pagination, { ...options, props: { page: 1, pageSize: 10, total: 107 } })
    mounted.push(wrapper)
    const pagination = wrapper.findComponent(ElPagination)
    pagination.vm.$emit('update:current-page', 2)
    pagination.vm.$emit('update:page-size', 20)
    expect(wrapper.emitted('update:page')).toEqual([[2]])
    expect(wrapper.emitted('update:pageSize')).toEqual([[20]])
  })

  it('saves only role permissions with the loaded revision', async () => {
    mocks.api.mockImplementation((path: string) =>
      Promise.resolve(
        path === '/auth/permissions'
          ? [
              {
                code: 'page:employees',
                kind: 'PAGE',
                label: '员工管理',
                group: '员工管理',
                path: '/employees',
                method: null,
                adminOnly: false,
              },
            ]
          : {
              role: { code: 'HR_VIEWER', name: '人事查询员', enabled: true },
              revision: 7,
              permissions: [],
              protectedRole: false,
            },
      ),
    )
    const wrapper = mount(RolePermissionEditor, {
      ...options,
      props: { role: { code: 'HR_VIEWER', name: '人事查询员', enabled: true } },
    })
    mounted.push(wrapper)
    await flushPromises()
    wrapper.findComponent(ElCheckboxGroup).vm.$emit('update:modelValue', ['page:employees'])
    await flushPromises()
    await wrapper
      .findAllComponents(ElButton)
      .find((button) => button.text() === '保存权限')!
      .trigger('click')
    await flushPromises()
    expect(mocks.api).toHaveBeenCalledWith('/auth/roles/HR_VIEWER/permissions', {
      method: 'PUT',
      body: { revision: 7, permissions: ['page:employees'] },
    })
    expect(wrapper.emitted('saved')).toHaveLength(1)
  })

  it('keeps protected ADMIN role controls disabled', async () => {
    mocks.api.mockImplementation((path: string) =>
      Promise.resolve(
        path === '/auth/permissions'
          ? []
          : {
              role: { code: 'ADMIN', name: '管理员', enabled: true },
              revision: 0,
              permissions: [],
              protectedRole: true,
            },
      ),
    )
    const wrapper = mount(RolePermissionEditor, {
      ...options,
      props: { role: { code: 'ADMIN', name: '管理员', enabled: true } },
    })
    mounted.push(wrapper)
    await flushPromises()
    expect(
      wrapper
        .findAllComponents(ElButton)
        .find((button) => button.text() === '保存权限')!
        .props('disabled'),
    ).toBe(true)
    expect(mocks.api.mock.calls.every((call) => !call[1]?.method)).toBe(true)
  })

  it('validates login and returns to an authorized page', async () => {
    const wrapper = mount(LoginView, options)
    mounted.push(wrapper)
    await flushPromises()
    expect(wrapper.findComponent(ElForm).vm.fields.length).toBe(2)
    await wrapper.findComponent(ElForm).trigger('submit')
    await flushPromises()
    expect(mocks.login).not.toHaveBeenCalled()
    const inputs = wrapper.findAllComponents(ElInput)
    await inputs[0]!.find('input').setValue(' reader ')
    await inputs[1]!.find('input').setValue('test-password')
    await wrapper.findComponent(ElForm).trigger('submit')
    await flushPromises()
    expect(mocks.login).toHaveBeenCalledWith('reader', 'test-password')
    expect(mocks.replace).toHaveBeenCalledWith('/account')
  })
})
