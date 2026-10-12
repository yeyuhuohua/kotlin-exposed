// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
/** 会话恢复与刷新：只有 401 才能销毁会话，网络错误必须保留。 */
import { ApiError, api } from '../src/lib/api'
import { readSession, saveSession } from '../src/lib/session'
import type { PageRoute, User } from '../src/types'

vi.mock('../src/lib/api', async (importOriginal) => {
  const module = await importOriginal<typeof import('../src/lib/api')>()
  return { ...module, api: vi.fn() }
})

const mockedApi = vi.mocked(api)

const user: User = {
  id: 1,
  username: 'reader',
  roleCode: 'READER',
  enabled: true,
  permissions: ['page:overview'],
}

describe('auth store 会话恢复', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
    mockedApi.mockReset()
  })

  async function store() {
    const { useAuth } = await import('../src/stores/auth')
    return useAuth()
  }

  it('网络错误时保留会话并允许下次重试恢复', async () => {
    saveSession('token-1', 600)
    mockedApi.mockRejectedValueOnce(new ApiError(0, '无法连接服务'))
    const auth = await store()
    await auth.restore()
    expect(auth.user).toBeNull()
    expect(readSession()?.token).toBe('token-1')

    // 后端恢复后再次 restore 会真正重试，而不是沿用缓存的失败结果。
    mockedApi.mockResolvedValueOnce(user)
    await auth.restore()
    expect(auth.user?.username).toBe('reader')
  })

  it('401 时清除会话', async () => {
    saveSession('token-2', 600)
    mockedApi.mockRejectedValueOnce(new ApiError(401, '登录已失效'))
    const auth = await store()
    await auth.restore()
    expect(auth.user).toBeNull()
    expect(readSession()).toBeNull()
  })

  it('refreshUser 遇到网络错误时保留已登录用户', async () => {
    saveSession('token-3', 600)
    mockedApi.mockResolvedValueOnce(user)
    const auth = await store()
    await auth.restore()
    expect(auth.user?.username).toBe('reader')

    mockedApi.mockRejectedValueOnce(new ApiError(0, '请求超时'))
    await auth.refreshUser({ force: true })
    expect(auth.user?.username).toBe('reader')
    expect(readSession()?.token).toBe('token-3')
  })

  it('refreshUser 遇到 401 时清除会话', async () => {
    saveSession('token-4', 600)
    mockedApi.mockResolvedValueOnce(user)
    const auth = await store()
    await auth.restore()

    mockedApi.mockRejectedValueOnce(new ApiError(401, '登录已失效'))
    await auth.refreshUser({ force: true })
    expect(auth.user).toBeNull()
    expect(readSession()).toBeNull()
  })

  it('并发身份刷新时旧响应不能恢复已移除的页面权限', async () => {
    saveSession('token-refresh', 600)
    mockedApi.mockResolvedValueOnce(user)
    const auth = await store()
    await auth.restore()

    let release!: (value: User) => void
    mockedApi.mockImplementationOnce(() => new Promise<User>((resolve) => (release = resolve)))
    const pending = auth.refreshUser({ force: true })
    const fresh = { ...user, permissions: [] }
    mockedApi.mockResolvedValueOnce(fresh)
    await auth.refreshUser({ force: true })
    release(user)
    await pending

    expect(auth.user).toEqual(fresh)
    expect(auth.canPage('overview')).toBe(false)
  })

  it('恢复会话的迟到响应不能覆盖强制刷新后的身份', async () => {
    saveSession('token-restore', 600)
    let release!: (value: User) => void
    mockedApi.mockImplementationOnce(() => new Promise<User>((resolve) => (release = resolve)))
    const auth = await store()
    const pending = auth.restore()
    const fresh = { ...user, permissions: [] }
    mockedApi.mockResolvedValueOnce(fresh)
    await auth.refreshUser({ force: true })
    release(user)
    await pending

    expect(auth.user).toEqual(fresh)
  })

  it('身份刷新失败且恢复响应已过时时仍可重试恢复会话', async () => {
    saveSession('token-retry', 600)
    let release!: (value: User) => void
    mockedApi.mockImplementationOnce(() => new Promise<User>((resolve) => (release = resolve)))
    const auth = await store()
    const pending = auth.restore()
    mockedApi.mockRejectedValueOnce(new ApiError(0, '请求超时'))
    await auth.refreshUser({ force: true })
    release(user)
    await pending

    const fresh = { ...user, permissions: [] }
    mockedApi.mockResolvedValueOnce(fresh)
    await auth.restore()
    expect(auth.user).toEqual(fresh)
    expect(readSession()?.token).toBe('token-retry')
    expect(mockedApi).toHaveBeenCalledTimes(3)
  })

  it('请求期间切换到新账号后，旧请求的成功结果不覆盖新身份', async () => {
    saveSession('token-a', 600)
    let release!: (value: User) => void
    mockedApi.mockImplementationOnce(() => new Promise<User>((resolve) => (release = resolve)))
    const auth = await store()
    const pending = auth.restore()
    // /auth/me 尚未返回时，用户已登录另一个账号。
    saveSession('token-b', 600)
    release({ ...user, username: 'alice' })
    await pending
    expect(auth.user).toBeNull()
    expect(readSession()?.token).toBe('token-b')
  })

  it('请求期间切换到新账号后，旧请求的 401 不清除新会话', async () => {
    saveSession('token-a', 600)
    let reject!: (reason: unknown) => void
    mockedApi.mockImplementationOnce(() => new Promise<User>((_, r) => (reject = r)))
    const auth = await store()
    const pending = auth.restore()
    saveSession('token-b', 600)
    reject(new ApiError(401, '登录已失效'))
    await pending
    expect(readSession()?.token).toBe('token-b')
  })

  it('refreshUser 的旧 401 响应不清除新登录的会话', async () => {
    saveSession('token-a', 600)
    mockedApi.mockResolvedValueOnce(user)
    const auth = await store()
    await auth.restore()
    expect(auth.user?.username).toBe('reader')

    let reject!: (reason: unknown) => void
    mockedApi.mockImplementationOnce(() => new Promise<User>((_, r) => (reject = r)))
    const pending = auth.refreshUser({ force: true })
    saveSession('token-b', 600)
    reject(new ApiError(401, '登录已失效'))
    await pending
    expect(auth.user?.username).toBe('reader')
    expect(readSession()?.token).toBe('token-b')
  })

  it('退出请求返回前已登录新账号时，旧退出不清掉新会话', async () => {
    saveSession('token-a', 600)
    let release!: () => void
    mockedApi.mockImplementationOnce(() => new Promise<void>((resolve) => (release = resolve)))
    const auth = await store()
    const pending = auth.logout()
    // 退出请求还在路上，用户已用另一个账号登录。
    saveSession('token-b', 600)
    release()
    await pending
    expect(readSession()?.token).toBe('token-b')
  })

  it('退出请求正常完成时清理自己的会话', async () => {
    saveSession('token-a', 600)
    mockedApi.mockResolvedValueOnce(undefined)
    const auth = await store()
    await auth.logout()
    expect(readSession()).toBeNull()
    expect(auth.user).toBeNull()
  })

  it('强制刷新页面清单后旧响应不能覆盖新菜单路径', async () => {
    saveSession('token-pages', 600)
    let release!: (value: PageRoute[]) => void
    mockedApi.mockImplementationOnce(() => new Promise<PageRoute[]>((resolve) => (release = resolve)))
    const auth = await store()
    const pending = auth.loadPages()
    const page: PageRoute = {
      key: 'employees',
      title: '员工',
      path: '/staff',
      icon: null,
      group: '工作空间',
      sort: 1,
    }
    mockedApi.mockResolvedValueOnce([page])
    await auth.loadPages({ force: true })
    release([{ ...page, path: '/employees' }])
    await pending
    expect(auth.pages).toEqual([page])
  })

  it('过时页面请求失败不能清掉较新请求的加载状态', async () => {
    saveSession('token-pages', 600)
    let reject!: (reason: unknown) => void
    mockedApi.mockImplementationOnce(() => new Promise<PageRoute[]>((_, r) => (reject = r)))
    const auth = await store()
    const pending = auth.loadPages().catch(() => undefined)
    mockedApi.mockResolvedValueOnce([])
    await auth.loadPages({ force: true })
    reject(new ApiError(0, '旧请求超时'))
    await pending
    await auth.loadPages()
    expect(mockedApi).toHaveBeenCalledTimes(2)
  })
})
