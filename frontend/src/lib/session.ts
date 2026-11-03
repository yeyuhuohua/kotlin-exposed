const KEY = 'hr-workspace.session'
export interface Session {
  token: string
  expiresAt: number
}
let memorySession: Session | null = null
export function readSession(): Session | null {
  let value = memorySession
  try {
    value = JSON.parse(sessionStorage.getItem(KEY) || 'null') as Session | null ?? memorySession
  } catch {
    /* 存储不可用时使用当前页面内存中的会话。 */
  }
  if (
    value &&
    typeof value.token === 'string' &&
    Number.isFinite(value.expiresAt) &&
    value.expiresAt > Date.now()
  )
    return value
  clearSession()
  return null
}
export function saveSession(token: string, seconds: number) {
  const session = { token, expiresAt: Date.now() + seconds * 1000 }
  memorySession = session
  try {
    sessionStorage.setItem(KEY, JSON.stringify(session))
  } catch {
    /* 存储不可用时仅在当前页面保留会话。 */
  }
}
export function clearSession() {
  memorySession = null
  try {
    sessionStorage.removeItem(KEY)
  } catch {
    /* 存储功能可能被禁用。 */
  }
}
