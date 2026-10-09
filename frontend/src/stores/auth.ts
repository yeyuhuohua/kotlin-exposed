import { computed, ref } from 'vue'
/** 认证状态只信任后端用户信息；登录、刷新身份和退出共同维护当前会话。 */
import { defineStore } from 'pinia'
import { authPaths } from '../api/paths'
import { api, ApiError } from '../lib/api'
import { clearSession, readSession, saveSession } from '../lib/session'
import type { LoginResult, PageRoute, User } from '../types'
import { canApi as allowsApi, canPage as allowsPage, homePath } from '../lib/permissions'

const REFRESH_INTERVAL_MS = 30_000

export const useAuth = defineStore('auth', () => {
  const user = ref<User | null>(null)
  /** 后端下发的可访问页面清单；路由注册与菜单都从这里取，不再用前端静态清单。 */
  const pages = ref<PageRoute[]>([])
  const isAdmin = computed(() => user.value?.roleCode === 'ADMIN')
  const home = computed(() => homePath(user.value, pages.value))
  const canPage = (path: string) => allowsPage(user.value, path)
  const canApi = (method: string, path: string) => allowsApi(user.value, method, path)
  let initialized: Promise<void> | undefined
  let pagesLoaded: Promise<void> | undefined
  let expiryTimer: ReturnType<typeof setTimeout> | undefined
  /** 上一次拉取 /auth/me 的时间，用于避免每次路由跳转都请求一次。 */
  let refreshedAt = 0
  function clear() {
    clearSession()
    user.value = null
    pages.value = []
    pagesLoaded = undefined
    refreshedAt = 0
    clearTimeout(expiryTimer)
  }
  function scheduleExpiry() {
    clearTimeout(expiryTimer)
    const session = readSession()
    if (session)
      expiryTimer = setTimeout(
        () => window.dispatchEvent(new Event('hr:unauthorized')),
        Math.max(0, session.expiresAt - Date.now()),
      )
  }
  async function restore() {
    if (!initialized)
      initialized = (async () => {
        const token = readSession()?.token
        if (!token) return
        try {
          const fresh = await api<User>(authPaths.me)
          // 请求期间已切换账号：旧会话的结果不能覆盖新身份。
          if (readSession()?.token !== token) return
          user.value = fresh
          refreshedAt = Date.now()
          scheduleExpiry()
        } catch (error) {
          if (readSession()?.token !== token) return
          // 只有 401 说明会话真的失效；网络抖动或后端重启时保留会话，允许下次导航重试。
          if (error instanceof ApiError && error.status === 401) return clear()
          initialized = undefined
        }
      })()
    await initialized
  }
  /** 拉取后端下发的页面清单；失败时重置加载状态，下次导航自动重试。 */
  async function loadPages(options: { force?: boolean } = {}) {
    if (options.force) pagesLoaded = undefined
    if (!pagesLoaded)
      pagesLoaded = (async () => {
        const token = readSession()?.token
        if (!token) return
        try {
          const result = await api<PageRoute[]>(authPaths.routes)
          // 请求期间已切换账号：旧会话的页面清单不能覆盖新会话。
          if (readSession()?.token !== token) return
          pages.value = result
        } catch (error) {
          if (readSession()?.token !== token) return
          pagesLoaded = undefined
          if (error instanceof ApiError && error.status === 401) return
          throw error
        }
      })()
    await pagesLoaded
  }
  async function login(username: string, password: string) {
    const result = await api<LoginResult>(authPaths.login, {
      method: 'POST',
      body: { username, password },
      auth: false,
    })
    saveSession(result.accessToken, result.expiresIn)
    user.value = result.user
    pages.value = []
    pagesLoaded = undefined
    refreshedAt = Date.now()
    initialized = Promise.resolve()
    scheduleExpiry()
    // 页面清单随登录一起就绪，落地页才能取到真实首页；失败由路由守卫重试，不阻断登录。
    try {
      await loadPages()
    } catch {
      // 网络抖动时路由守卫会在跳转后重新拉取。
    }
  }
  /** 短时间内复用上一次结果；权限被服务端拒绝时用 force 立即重新拉取。 */
  async function refreshUser(options: { force?: boolean } = {}) {
    const token = readSession()?.token
    if (!token) return
    if (!options.force && Date.now() - refreshedAt < REFRESH_INTERVAL_MS) return
    try {
      const fresh = await api<User>(authPaths.me)
      // 请求期间已切换账号：旧会话的结果不能覆盖新身份，401 也不能清掉新会话。
      if (readSession()?.token !== token) return
      user.value = fresh
      refreshedAt = Date.now()
    } catch (error) {
      // 401 由 hr:unauthorized 事件统一登出；网络错误保留现有用户，下个周期再试。
      if (error instanceof ApiError && error.status === 401 && readSession()?.token === token) clear()
    }
  }
  async function logout() {
    const token = readSession()?.token
    try {
      await api(authPaths.logout, { method: 'POST' })
    } finally {
      // 等待退出请求期间已重新登录时，只清理发起退出的那个会话。
      if (readSession()?.token === token) clear()
    }
  }
  return {
    user,
    pages,
    isAdmin,
    home,
    canPage,
    canApi,
    restore,
    loadPages,
    login,
    logout,
    clear,
    refreshUser,
  }
})
