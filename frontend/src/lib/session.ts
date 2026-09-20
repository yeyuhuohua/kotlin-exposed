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
    /* Unavailable storage or an invalid saved session requires a new login. */
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
    /* Storage may be disabled. */
  }
}
