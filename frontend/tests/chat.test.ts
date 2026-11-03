// @vitest-environment happy-dom
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ChatView from '../src/views/ChatView.vue'
import { saveSession } from '../src/lib/session'

afterEach(() => {
  sessionStorage.clear()
  vi.unstubAllGlobals()
})

describe('ChatView', () => {
  it('uses the current ordinary account to create a DSH session', async () => {
    saveSession('reader-token', 3600)
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(ChatView)
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledWith('http://127.0.0.1:3080/hr-session', {
      method: 'POST',
      credentials: 'include',
      headers: { Authorization: 'Bearer reader-token' },
    })
    expect(wrapper.find('iframe').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('管理员')
    wrapper.unmount()
  })
})
