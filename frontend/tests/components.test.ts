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
import Modal from '../src/components/Modal.vue'
import LoginView from '../src/views/LoginView.vue'
import { readSession, saveSession } from '../src/lib/session'

/** 在内存 DOM 中验证组件契约，不读取本机账号、不访问浏览器或真实 API。 */
const mocks = vi.hoisted(() => ({
  api: vi.fn(),
  show: vi.fn(),
  login: vi.fn(),
  replace: vi.fn(),
  clear: vi.fn(),
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
    clear: mocks.clear,
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

  it('validates text length by code points so supplementary-plane records stay editable', async () => {
    // 部门名是 𠮷 加 29 个普通字符（30 个码点、31 个 UTF-16 单元）：只改经理编号也应能保存。
    const wrapper = mount(RecordDialog, {
      ...options,
      props: {
        title: '编辑部门',
        endpoint: '/departments',
        idKey: 'departmentId',
        original: { departmentId: 60, departmentName: '𠮷' + 'a'.repeat(29), managerId: 100 },
        fields: [
          { key: 'departmentName', label: '部门名称', required: true, maxLength: 30 },
          { key: 'managerId', label: '经理编号', type: 'number' },
        ],
      },
    })
    mounted.push(wrapper)
    await flushPromises()
    wrapper.findComponent(ElInputNumber).vm.$emit('update:modelValue', 200)
    await wrapper.findComponent(ElForm).trigger('submit')
    await flushPromises()
    expect(mocks.api).toHaveBeenCalledWith('/departments/60', { method: 'PUT', body: { managerId: 200 } })
    expect(wrapper.emitted('saved')).toHaveLength(1)
    // 超出 30 个码点仍然被拦下
    const inputs = wrapper.findAllComponents(ElInput)
    await inputs[0]!.find('input').setValue('𠮷' + 'a'.repeat(30))
    await wrapper.findComponent(ElForm).trigger('submit')
    await flushPromises()
    expect(mocks.api).toHaveBeenCalledTimes(1)
  })

  it('counts password length in UTF-16 units to match the backend rule', async () => {
    const wrapper = mount(RecordDialog, {
      ...options,
      props: {
        title: '新建用户',
        endpoint: '/auth/users',
        idKey: 'id',
        fields: [
          { key: 'username', label: '用户名', required: true },
          {
            key: 'password',
            label: '密码',
            type: 'password',
            required: true,
            minLength: 8,
            maxLength: 128,
            lengthUnit: 'utf16',
          },
        ],
      },
    })
    mounted.push(wrapper)
    await flushPromises()
    const inputs = wrapper.findAllComponents(ElInput)
    await inputs[0]!.find('input').setValue('reader')
    // 65 个 𠮷 = 65 个码点、130 个 UTF-16 单元：后端按 130 判超 128，前端必须同样拦下
    await inputs[1]!.find('input').setValue('𠮷'.repeat(65))
    await wrapper.findComponent(ElForm).trigger('submit')
    await flushPromises()
    expect(mocks.api).not.toHaveBeenCalled()
    // 64 个 𠮷 = 128 个 UTF-16 单元：前后端一致放行
    await inputs[1]!.find('input').setValue('𠮷'.repeat(64))
    await wrapper.findComponent(ElForm).trigger('submit')
    await flushPromises()
    expect(mocks.api).toHaveBeenCalledWith('/auth/users', {
      method: 'POST',
      body: { username: 'reader', password: '𠮷'.repeat(64) },
    })
  })

  it('rejects dot-only job ids that would break the edit path', async () => {
    const { resources } = await import('../src/config/resources')
    const jobs = resources.find((resource) => resource.key === 'jobs')!
    const wrapper = mount(RecordDialog, {
      ...options,
      props: { title: '新增岗位', endpoint: '/jobs', idKey: 'jobId', fields: jobs.fields! },
    })
    mounted.push(wrapper)
    await flushPromises()
    const inputs = wrapper.findAllComponents(ElInput)
    await inputs[1]!.find('input').setValue('Dots')
    // "." 与 ".." 会被浏览器按路径段规范化，创建后 /jobs/{id} 永远到不了更新接口
    for (const bad of ['.', '..']) {
      await inputs[0]!.find('input').setValue(bad)
      await wrapper.findComponent(ElForm).trigger('submit')
      await flushPromises()
    }
    expect(mocks.api).not.toHaveBeenCalled()
    await inputs[0]!.find('input').setValue('KT_DEV')
    await wrapper.findComponent(ElForm).trigger('submit')
    await flushPromises()
    expect(mocks.api).toHaveBeenCalledWith('/jobs', {
      method: 'POST',
      body: { jobId: 'KT_DEV', jobTitle: 'Dots' },
    })
  })

  it('clears the session after changing own password only when it is unchanged', async () => {
    saveSession('token-old', 600)
    let release!: () => void
    mocks.api.mockImplementationOnce(() => new Promise((resolve) => (release = () => resolve({}))))
    const props = {
      title: '编辑用户',
      endpoint: '/auth/users',
      updateTemplate: '/auth/users/{id}',
      idKey: 'id',
      original: { id: 1, username: 'admin', roleCode: 'ADMIN', enabled: true },
      fields: [{ key: 'password', label: '密码', type: 'password' as const }],
    }
    const wrapper = mount(RecordDialog, { ...options, props })
    mounted.push(wrapper)
    await flushPromises()
    await wrapper.findComponent(ElInput).find('input').setValue('new-password-1')
    const submitted = wrapper.findComponent(ElForm).trigger('submit')
    await flushPromises()
    // 响应延迟期间会话过期并以同账号重新登录：旧响应回来不能清掉新会话
    saveSession('token-new', 600)
    release()
    await submitted
    await flushPromises()
    expect(mocks.clear).not.toHaveBeenCalled()
    expect(readSession()?.token).toBe('token-new')

    // 会话未变化时正常清理并要求重新登录
    mocks.api.mockResolvedValueOnce({})
    const wrapper2 = mount(RecordDialog, { ...options, props })
    mounted.push(wrapper2)
    await flushPromises()
    await wrapper2.findComponent(ElInput).find('input').setValue('new-password-2')
    await wrapper2.findComponent(ElForm).trigger('submit')
    await flushPromises()
    expect(mocks.clear).toHaveBeenCalledTimes(1)
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

  it('Modal 透传 footer 插槽，删除确认按钮可见', async () => {
    const wrapper = mount(Modal, {
      ...options,
      props: { title: '删除用户' },
      slots: {
        default: '<p>确定删除该用户？</p>',
        footer: '<button class="danger-confirm">确认删除</button>',
      },
    })
    mounted.push(wrapper)
    await flushPromises()
    expect(document.body.querySelector('.el-dialog__footer .danger-confirm')).toBeTruthy()
  })
})
