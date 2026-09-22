import { computed, ref } from 'vue'
/** 认证状态只信任后端用户信息；登录、刷新身份和退出共同维护当前会话。 */
import { defineStore } from 'pinia'
import { authPaths } from '../api/paths'
import { api, ApiError } from '../lib/api'
import { clearSession, readSession, saveSession } from '../lib/session'
import type { LoginResult, User } from '../types'
import { canApi as allowsApi, canPage as allowsPage, homePath } from '../lib/permissions'

const REFRESH_INTERVAL_MS = 30_000

export const useAuth = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const isAdmin = computed(() => user.value?.roleCode === 'ADMIN')
  const home = computed(() => homePath(user.value))
  const canPage = (path: string) => allowsPage(user.value, path)
  const canApi = (method: string, path: string) => allowsApi(user.value, method, path)
  let initialized: Promise<void> | undefined
  let expiryTimer: ReturnType<typeof setTimeout> | undefined
  /** 上一次拉取 /auth/me 的时间，用于避免每次路由跳转都请求一次。 */
  let refreshedAt = 0
  function clear() {
    clearSession()
    user.value = null
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
        if (!readSession()) return
        try {
          user.value = await api<User>(authPaths.me)
          refreshedAt = Date.now()
          scheduleExpiry()
        } catch (error) {
          // 只有 401 说明会话真的失效；网络抖动或后端重启时保留会话，允许下次导航重试。
          if (error instanceof ApiError && error.status === 401) return clear()
          initialized = undefined
        }
      })()
    await initialized
  }
  async function login(username: string, password: string) {
    const result = await api<LoginResult>(authPaths.login, {
      method: 'POST',
      body: { username, password },
      auth: false,
    })
    saveSession(result.accessToken, result.expiresIn)
    user.value = result.user
    refreshedAt = Date.now()
    initialized = Promise.resolve()
    scheduleExpiry()
  }
  /** 短时间内复用上一次结果；权限被服务端拒绝时用 force 立即重新拉取。 */
  async function refreshUser(options: { force?: boolean } = {}) {
    if (!readSession()) return
    if (!options.force && Date.now() - refreshedAt < REFRESH_INTERVAL_MS) return
    try {
      user.value = await api<User>(authPaths.me)
      refreshedAt = Date.now()
    } catch (error) {
      // 401 由 hr:unauthorized 事件统一登出；网络错误保留现有用户，下个周期再试。
      if (error instanceof ApiError && error.status === 401) clear()
    }
  }
  async function logout() {
    try {
      await api(authPaths.logout, { method: 'POST' })
    } finally {
      clear()
    }
  }
  return { user, isAdmin, home, canPage, canApi, restore, login, logout, clear, refreshUser }
})
