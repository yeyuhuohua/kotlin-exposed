const KEY = 'hr-workspace.session'
export interface Session {
  token: string
  expiresAt: number
}
export function readSession(): Session | null {
  try {
    const value = JSON.parse(sessionStorage.getItem(KEY) || 'null') as Session | null
    if (
      value &&
      typeof value.token === 'string' &&
      Number.isFinite(value.expiresAt) &&
      value.expiresAt > Date.now()
    )
      return value
  } catch {
    /* 存储不可用或保存的会话无效时，需要重新登录。 */
  }
  clearSession()
  return null
}
export function saveSession(token: string, seconds: number) {
  sessionStorage.setItem(KEY, JSON.stringify({ token, expiresAt: Date.now() + seconds * 1000 }))
}
export function clearSession() {
  try {
    sessionStorage.removeItem(KEY)
  } catch {
    /* 存储功能可能被禁用。 */
  }
}
