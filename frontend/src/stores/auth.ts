import { computed, ref } from 'vue'
/** 认证状态只信任后端用户信息；登录、刷新身份和退出共同维护当前会话。 */
import { defineStore } from 'pinia'
import { api } from '../lib/api'
import { clearSession, readSession, saveSession } from '../lib/session'
import type { LoginResult, User } from '../types'
import { canApi as allowsApi, canPage as allowsPage, homePath } from '../lib/permissions'

export const useAuth = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const isAdmin = computed(() => user.value?.roleCode === 'ADMIN')
  const home = computed(() => homePath(user.value))
  const canPage = (path: string) => allowsPage(user.value, path)
  const canApi = (method: string, path: string) => allowsApi(user.value, method, path)
  let initialized: Promise<void> | undefined
  let expiryTimer: ReturnType<typeof setTimeout> | undefined
  function clear() {
    clearSession()
    user.value = null
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
          user.value = await api<User>('/auth/me')
          scheduleExpiry()
        } catch {
          clear()
        }
      })()
    await initialized
  }
  async function login(username: string, password: string) {
    const result = await api<LoginResult>('/auth/login', {
      method: 'POST',
      body: { username, password },
      auth: false,
    })
    saveSession(result.accessToken, result.expiresIn)
    user.value = result.user
    initialized = Promise.resolve()
    scheduleExpiry()
  }
  async function refreshUser() {
    if (!readSession()) return
    try {
      user.value = await api<User>('/auth/me')
    } catch {
      clear()
    }
  }
  async function logout() {
    try {
      await api('/auth/logout', { method: 'POST' })
    } finally {
      clear()
    }
  }
  return { user, isAdmin, home, canPage, canApi, restore, login, logout, clear, refreshUser }
})
