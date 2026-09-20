import type { User } from '../types'
import { authPaths, rolesPaths, systemPaths, usersPaths } from '../api/paths'
import { pagePaths } from '../config/resources'
/** 使用服务端返回的角色权限控制界面；管理入口仍额外要求 ADMIN 角色。 */

// 页面清单来自 config/resources，这里只做转发，避免两处清单不同步。
export { pagePaths }

/** 只有 ADMIN 能调用的管理类接口；后端也会用 adminOnly 权限再校验一次。 */
const adminOnlyApis: string[] = [
  usersPaths.collection,
  usersPaths.item,
  rolesPaths.collection,
  rolesPaths.item,
  rolesPaths.permissions,
  rolesPaths.catalog,
]

export function canPage(user: User | null, keyOrPath: string): boolean {
  if (!user?.enabled) return false
  if (['/account', '/forbidden', 'account'].includes(keyOrPath)) return true
  const key = keyOrPath === '/' ? 'overview' : keyOrPath.replace(/^\//, '')
  if (['users', 'roles'].includes(key) && user.roleCode !== 'ADMIN') return false
  return (user.permissions || []).includes(`page:${key}`)
}

export function canApi(user: User | null, method: string, path: string): boolean {
  if (method === 'GET' && path === systemPaths.health) return true
  if (!user?.enabled) return false
  if ((method === 'GET' && path === authPaths.me) || (method === 'POST' && path === authPaths.logout))
    return true
  if (adminOnlyApis.includes(path) && user.roleCode !== 'ADMIN') return false
  const normalized = method.toUpperCase() === 'HEAD' ? 'GET' : method.toUpperCase()
  return (user.permissions || []).includes(`api:${normalized}:/api${path}`)
}

/** 内置 ADMIN 角色不允许删除或改名。 */
export function isProtectedRole(code: unknown): boolean {
  return String(code).toUpperCase() === 'ADMIN'
}

/** 内置 admin 账号与当前登录账号不允许删除（与后端保护规则一致）。 */
export function isProtectedUser(row: { id?: unknown; username?: unknown }, currentUserId?: number): boolean {
  return String(row.username ?? '').toLowerCase() === 'admin' || row.id === currentUserId
}

export function homePath(user: User | null) {
  return pagePaths.find((path) => canPage(user, path)) || '/account'
}
