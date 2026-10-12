// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import ElementPlus, { ElForm, ElInput } from 'element-plus'
import LoginView from '../src/views/LoginView.vue'
import { canPage } from '../src/lib/permissions'

const auth = vi.hoisted(() => ({
  user: { id: 7, username: 'reader', roleCode: 'READER', enabled: true, permissions: ['page:employees'] },
  pages: [
    { key: 'employees', path: '/staff' },
    { key: 'jobs', path: '/positions' },
  ],
  isAdmin: false,
  home: '/account',
  login: vi.fn().mockResolvedValue(undefined),
}))
vi.mock('../src/stores/auth', () => ({
  useAuth: () => ({
    ...auth,
    canPage: (key: string) => canPage(auth.user, key),
  }),
}))
vi.mock('../src/stores/theme', () => ({ useTheme: () => ({ isDark: false, toggle: vi.fn() }) }))

let wrapper: VueWrapper | undefined
afterEach(() => {
  wrapper?.unmount()
  vi.clearAllMocks()
  document.body.innerHTML = ''
})

async function login(redirect: string) {
  // 使用真实路由解析器，但不提前注册动态菜单路由。
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/:pathMatch(.*)*', component: { template: '<div />' } }],
  })
  await router.push({ path: '/login', query: { redirect } })
  const replace = vi.spyOn(router, 'replace')
  wrapper = mount(LoginView, { attachTo: document.body, global: { plugins: [ElementPlus, router] } })
  const inputs = wrapper.findAllComponents(ElInput)
  await inputs[0]!.find('input').setValue('reader')
  await inputs[1]!.find('input').setValue('test-password')
  await wrapper.findComponent(ElForm).trigger('submit')
  await flushPromises()
  return replace
}

describe('login redirect', () => {
  it('菜单改名后按页面key鉴权并保留查询参数和hash', async () => {
    const target = '/staff?employeeId=100#details'
    expect(await login(target)).toHaveBeenCalledWith(target)
  })

  it.each(['/account?tab=password', '/forbidden'])('允许静态个人页面 %s', async (target) => {
    expect(await login(target)).toHaveBeenCalledWith(target)
  })

  it.each([
    '/employees?employeeId=100',
    '/positions',
    '/missing',
    '/login',
    'https://example.com',
    '//example.com',
    '/\\example.com',
    '/\t/example.com',
  ])('过期无权或外部目标 %s 回到首页', async (target) => {
    expect(await login(target)).toHaveBeenCalledWith(auth.home)
  })
})
