import { describe, expect, it } from 'vitest'
/** 验证角色有效授权在页面和接口上的独立性，以及管理权限的角色限制。 */
import {
  canApi,
  canPage,
  homePath,
  isProtectedRole,
  isProtectedUser,
  pagePaths,
} from '../src/lib/permissions'
import type { User } from '../src/types'

const reader: User = {
  id: 2,
  username: 'reader',
  roleCode: 'READER',
  enabled: true,
  permissions: ['page:employees', 'api:GET:/api/employees', 'api:PUT:/api/employees/{id}'],
}
describe('page and API permissions', () => {
  it('checks effective role page grants returned by the server', () => {
    expect(canPage(reader, '/employees')).toBe(true)
    expect(canPage(reader, 'employees')).toBe(true)
    expect(canPage(reader, '/jobs')).toBe(false)
    expect(canPage(reader, '/')).toBe(false)
    expect(homePath(reader)).toBe('/employees')
  })
  it('distinguishes API methods and list vs detail endpoints', () => {
    expect(canApi(reader, 'GET', '/employees')).toBe(true)
    expect(canApi(reader, 'HEAD', '/employees')).toBe(true)
    expect(canApi(reader, 'GET', '/employees/{id}')).toBe(false)
    expect(canApi(reader, 'PUT', '/employees/{id}')).toBe(true)
    expect(canApi(reader, 'DELETE', '/employees/{id}')).toBe(false)
    expect(canApi(reader, 'POST', '/employees')).toBe(false)
  })
  it('page grants do not grant API access and API grants do not grant a page', () => {
    expect(canApi({ ...reader, permissions: ['page:employees'] }, 'GET', '/employees')).toBe(false)
    expect(canPage({ ...reader, permissions: ['api:GET:/api/employees'] }, 'employees')).toBe(false)
  })
  it('fails closed for missing and empty grants including an ordinary ADMIN account', () => {
    const empty = { ...reader, roleCode: 'ADMIN' as const, permissions: [] }
    expect(canPage(empty, '/employees')).toBe(false)
    expect(canApi(empty, 'POST', '/employees')).toBe(false)
    expect(homePath(empty)).toBe('/account')
    expect(canPage(null, '/employees')).toBe(false)
  })
  it('management remains admin-only even with an erroneous grant', () => {
    const forged = {
      ...reader,
      permissions: ['page:users', 'page:roles', 'api:PUT:/api/auth/roles/{code}/permissions'],
    }
    expect(canPage(forged, '/users')).toBe(false)
    expect(canPage(forged, '/roles')).toBe(false)
    expect(canApi(forged, 'PUT', '/auth/roles/{code}/permissions')).toBe(false)
    expect(canApi({ ...forged, roleCode: 'ADMIN' }, 'PUT', '/auth/roles/{code}/permissions')).toBe(true)
  })
  it('supports custom business roles without granting administrator access', () => {
    const custom = { ...reader, roleCode: 'HR_VIEWER' }
    expect(canPage(custom, '/employees')).toBe(true)
    expect(canApi(custom, 'GET', '/employees')).toBe(true)
    expect(canApi(custom, 'POST', '/auth/roles')).toBe(false)
    expect(canPage(custom, '/roles')).toBe(false)
  })
  it('allows fixed personal account and logout without granting any business permissions', () => {
    const empty = { ...reader, permissions: [] }
    expect(canPage(empty, '/account')).toBe(true)
    expect(canApi(empty, 'GET', '/auth/me')).toBe(true)
    expect(canApi(empty, 'POST', '/auth/logout')).toBe(true)
    expect(canApi(null, 'GET', '/auth/me')).toBe(false)
    expect(canApi(null, 'GET', '/health')).toBe(true)
    expect(canPage({ ...reader, enabled: false }, '/employees')).toBe(false)
  })
  it('protects the built-in role, the built-in account and the current account', () => {
    expect(isProtectedRole('ADMIN')).toBe(true)
    expect(isProtectedRole('admin')).toBe(true)
    expect(isProtectedRole('HR_VIEWER')).toBe(false)
    expect(isProtectedUser({ id: 1, username: 'admin' }, 2)).toBe(true)
    expect(isProtectedUser({ id: 1, username: 'Admin' }, 2)).toBe(true)
    expect(isProtectedUser({ id: 2, username: 'operator' }, 2)).toBe(true)
    expect(isProtectedUser({ id: 3, username: 'operator' }, 2)).toBe(false)
  })
  it('keeps routable permission paths unique', () => {
    expect(new Set(pagePaths).size).toBe(pagePaths.length)
  })
})
