import type { PageRoute, User } from '../types'
import { authPaths, menusPaths, rolesPaths, systemPaths, usersPaths } from '../api/paths'
/** 使用服务端返回的角色权限控制界面；管理入口仍额外要求 ADMIN 角色。 */

/** 只有 ADMIN 能调用的管理类接口；后端也会用 adminOnly 权限再校验一次。 */
const adminOnlyApis: string[] = [
  usersPaths.collection,
  usersPaths.item,
  rolesPaths.collection,
  rolesPaths.item,
  rolesPaths.permissions,
  rolesPaths.catalog,
  menusPaths.collection,
  menusPaths.item,
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
  if (method === 'GET' && (path === authPaths.me || path === authPaths.routes)) return true
  if (method === 'POST' && path === authPaths.logout) return true
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

/** 落地页：在后端下发的页面清单中找第一个有权限的，没有任何页面权限时退到「我的账号」。 */
export function homePath(user: User | null, pages: PageRoute[]) {
  return pages.find((page) => canPage(user, page.key))?.path || '/account'
}
