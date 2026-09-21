// @vitest-environment happy-dom
import { beforeEach, describe, expect, it } from 'vitest'
/** 记住密码：凭据的保存、读取、清除与容错。 */
import { clearCredentials, readCredentials, saveCredentials } from '../src/lib/credentials'

const KEY = 'hr-workspace.credentials'

describe('remembered credentials', () => {
  beforeEach(() => localStorage.clear())

  it('保存后能读回用户名与密码（含非 ASCII 字符）', () => {
    saveCredentials('admin', 'p@sswörd 中文')
    expect(readCredentials()).toEqual({ username: 'admin', password: 'p@sswörd 中文' })
  })

  it('清除后读不到凭据', () => {
    saveCredentials('admin', 'secret')
    clearCredentials()
    expect(readCredentials()).toBeNull()
  })

  it('内容被篡改或编码损坏时返回空', () => {
    localStorage.setItem(KEY, 'not-json')
    expect(readCredentials()).toBeNull()
    localStorage.setItem(KEY, '{"username":"admin"}')
    expect(readCredentials()).toBeNull()
    localStorage.setItem(KEY, '{"username":"admin","password":"!!!"}')
    expect(readCredentials()).toBeNull()
  })

  it('存储中不保留明文密码', () => {
    saveCredentials('admin', 'secret')
    expect(localStorage.getItem(KEY)).not.toContain('secret')
  })
})
