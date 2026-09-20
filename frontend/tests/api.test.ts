import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
/** 通过模拟请求与会话存储验证认证边界，测试不连接真实后端。 */
import { api, ApiError } from '../src/lib/api'
import { clearSession, readSession, saveSession } from '../src/lib/session'
let storage: Map<string, string>
const dispatchEvent = vi.fn()
beforeEach(() => {
  storage = new Map()
  vi.stubGlobal('sessionStorage', {
    getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => storage.set(key, value),
    removeItem: (key: string) => storage.delete(key),
  })
  vi.stubGlobal('window', { dispatchEvent })
  dispatchEvent.mockClear()
})
afterEach(() => vi.unstubAllGlobals())
describe('session lifecycle', () => {
  it('keeps valid sessions and removes expired or malformed values', () => {
    saveSession('valid', 60)
    expect(readSession()?.token).toBe('valid')
    saveSession('expired', -1)
    expect(readSession()).toBeNull()
    storage.set('hr-workspace.session', '{broken')
    expect(readSession()).toBeNull()
    clearSession()
    expect(storage.size).toBe(0)
  })
})
describe('typed API client', () => {
  it('does not invalidate a new login because an older in-flight request returned 401', async () => {
    saveSession('old-token', 60)
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(async () => {
        saveSession('new-token', 60)
        return new Response('{}', { status: 401 })
      }),
    )
    await expect(api('/employees')).rejects.toBeInstanceOf(ApiError)
    expect(dispatchEvent).not.toHaveBeenCalled()
    expect(readSession()?.token).toBe('new-token')
  })
  it('attaches the bearer token and unwraps data', async () => {
    saveSession('valid', 60)
    const fetch = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify({ code: 200, message: 'ok', data: { total: 3 } })))
    vi.stubGlobal('fetch', fetch)
    expect(await api('/employees')).toEqual({ total: 3 })
    expect(fetch.mock.calls[0]![1].headers.Authorization).toBe('Bearer valid')
  })
  it('does not attach credentials or expire another session when login fails', async () => {
    saveSession('valid', 60)
    const fetch = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          code: 401,
          message: 'invalid username or password',
          error: 'invalid_credentials',
          data: null,
        }),
        {
          status: 401,
        },
      ),
    )
    vi.stubGlobal('fetch', fetch)
    await expect(api('/auth/login', { auth: false, method: 'POST', body: {} })).rejects.toThrow(
      '用户名或密码错误',
    )
    expect(fetch.mock.calls[0]![1].headers.Authorization).toBeUndefined()
    expect(dispatchEvent).not.toHaveBeenCalled()
  })
  it('dispatches expiration and permission events', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(new Response('{}', { status: 401 }))
        .mockResolvedValueOnce(new Response('{}', { status: 403 })),
    )
    await expect(api('/employees')).rejects.toBeInstanceOf(ApiError)
    expect(dispatchEvent.mock.calls[0]![0].type).toBe('hr:unauthorized')
    await expect(api('/auth/users')).rejects.toBeInstanceOf(ApiError)
    expect(dispatchEvent.mock.calls[1]![0].type).toBe('hr:forbidden')
  })
  it('returns the diagnostic payload on an unavailable health check', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          new Response(
            JSON.stringify({ code: 503, message: 'dependency unavailable', data: { status: 'DOWN' } }),
            { status: 503 },
          ),
        ),
    )
    expect(await api('/health', { acceptUnavailable: true })).toEqual({ status: 'DOWN' })
  })
  it('handles network failures and unexpected non-JSON responses', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockRejectedValueOnce(new TypeError('Failed to fetch'))
        .mockResolvedValueOnce(new Response('<html>error</html>')),
    )
    await expect(api('/employees')).rejects.toThrow('无法连接服务')
    await expect(api('/employees')).rejects.toThrow('无法识别的数据')
  })
})
