import type { User } from '../types'
/** 使用服务端返回的角色权限控制界面；管理入口仍额外要求 ADMIN 角色。 */

export const pagePaths = [
  '/',
  '/employees',
  '/departments',
  '/jobs',
  '/locations',
  '/countries',
  '/regions',
  '/job-history',
  '/job-grades',
  '/emp-details',
  '/t-dept',
  '/t-emp',
  '/orders',
  '/system',
  '/users',
  '/roles',
]

export function canPage(user: User | null, keyOrPath: string): boolean {
  if (!user?.enabled) return false
  if (['/account', '/forbidden', 'account'].includes(keyOrPath)) return true
  const key = keyOrPath === '/' ? 'overview' : keyOrPath.replace(/^\//, '')
  if (['users', 'roles'].includes(key) && user.roleCode !== 'ADMIN') return false
  return (user.permissions || []).includes(`page:${key}`)
}

export function canApi(user: User | null, method: string, path: string): boolean {
  if (method === 'GET' && path === '/health') return true
  if (!user?.enabled) return false
  if ((method === 'GET' && path === '/auth/me') || (method === 'POST' && path === '/auth/logout')) return true
  if (path.startsWith('/auth/') && user.roleCode !== 'ADMIN') return false
  const normalized = method.toUpperCase() === 'HEAD' ? 'GET' : method.toUpperCase()
  return (user.permissions || []).includes(`api:${normalized}:/api${path}`)
}

export function homePath(user: User | null) {
  return pagePaths.find((path) => canPage(user, path)) || '/account'
}
