/** “记住密码”的凭据存储：密码经 Base64 编码避免明文直接展示，但不是加密，公共设备不应使用。 */
const KEY = 'hr-workspace.credentials'
export interface RememberedCredentials {
  username: string
  password: string
}
/** 将明文凭据编码为本地存储格式。 */
function encode(value: string): string {
  // 逐字节拼接而不是 spread：输入变长时 String.fromCharCode(...bytes) 会栈溢出。
  let binary = ''
  for (const byte of new TextEncoder().encode(value)) binary += String.fromCharCode(byte)
  return btoa(binary)
}
/** 解码本地保存的凭据；格式不合法时返回 null。 */
function decode(value: string): string {
  return new TextDecoder().decode(Uint8Array.from(atob(value), (char) => char.charCodeAt(0)))
}
/** 读取本机保存的登录凭据。 */
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
/** 加密并保存本机登录凭据。 */
export function saveCredentials(username: string, password: string) {
  try {
    localStorage.setItem(KEY, JSON.stringify({ username, password: encode(password) }))
  } catch {
    /* 存储功能可能被禁用。 */
  }
}
/** 删除本机保存的登录凭据。 */
export function clearCredentials() {
  try {
    localStorage.removeItem(KEY)
  } catch {
    /* 存储功能可能被禁用。 */
  }
}
