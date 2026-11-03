// @vitest-environment happy-dom
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ChatView from '../src/views/ChatView.vue'
import { clearSession, saveSession } from '../src/lib/session'

afterEach(() => {
  clearSession()
  vi.unstubAllGlobals()
})

describe('ChatView', () => {
  it('uses the current ordinary account to create a DSH session', async () => {
    saveSession('reader-token', 3600)
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(ChatView)
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledWith('http://127.0.0.1:3080/hr-session', expect.objectContaining({
      method: 'POST',
      credentials: 'include',
      headers: { Authorization: 'Bearer reader-token' },
    }))
    expect(wrapper.find('iframe').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('管理员')
    wrapper.unmount()
  })

  it('does not open a stale session after the account changes', async () => {
    saveSession('old-token', 3600)
    let finish: (response: Response) => void
    vi.stubGlobal('fetch', vi.fn().mockImplementation(() => new Promise<Response>((resolve) => {
      finish = resolve
    })))
    const wrapper = mount(ChatView)
    saveSession('new-token', 3600)
    finish!(new Response(null, { status: 204 }))
    await flushPromises()

    expect(wrapper.find('iframe').exists()).toBe(false)
    wrapper.unmount()
  })

  it('aborts the session exchange when the component is unmounted', async () => {
    saveSession('reader-token', 3600)
    let signal: AbortSignal
    vi.stubGlobal('fetch', vi.fn().mockImplementation((_url, options) => {
      signal = options.signal
      return new Promise<Response>(() => {})
    }))
    const wrapper = mount(ChatView)
    wrapper.unmount()

    expect(signal!.aborted).toBe(true)
  })
})
