// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { clearSession, readSession, saveSession } from '../src/lib/session'

afterEach(() => {
  clearSession()
  vi.restoreAllMocks()
})

describe('session storage', () => {
  it('persists a valid session', () => {
    saveSession('reader-token', 3600)
    expect(readSession()?.token).toBe('reader-token')
  })

  it('keeps the current page session when persistent storage is unavailable', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('disabled', 'SecurityError')
    })
    expect(() => saveSession('memory-token', 3600)).not.toThrow()
    expect(readSession()?.token).toBe('memory-token')
  })
})
