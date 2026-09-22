/** “记住密码”的凭据存储：密码经 Base64 编码避免明文直接展示，但不是加密，公共设备不应使用。 */
const KEY = 'hr-workspace.credentials'
export interface RememberedCredentials {
  username: string
  password: string
}
function encode(value: string): string {
  // 逐字节拼接而不是 spread：输入变长时 String.fromCharCode(...bytes) 会栈溢出。
  let binary = ''
  for (const byte of new TextEncoder().encode(value)) binary += String.fromCharCode(byte)
  return btoa(binary)
}
function decode(value: string): string {
  return new TextDecoder().decode(Uint8Array.from(atob(value), (char) => char.charCodeAt(0)))
}
export function readCredentials(): RememberedCredentials | null {
  try {
    const value = JSON.parse(localStorage.getItem(KEY) || 'null') as {
      username?: unknown
      password?: unknown
    } | null
    if (value && typeof value.username === 'string' && typeof value.password === 'string')
      return { username: value.username, password: decode(value.password) }
  } catch {
    /* 存储不可用、内容被篡改或编码损坏时当作没有记住。 */
  }
  return null
}
export function saveCredentials(username: string, password: string) {
  try {
    localStorage.setItem(KEY, JSON.stringify({ username, password: encode(password) }))
  } catch {
    /* Storage may be disabled. */
  }
}
export function clearCredentials() {
  try {
    localStorage.removeItem(KEY)
  } catch {
    /* Storage may be disabled. */
  }
}
