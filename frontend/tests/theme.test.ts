// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
/** 主题偏好：本地存储优先、没有记录时跟随系统，切换后写回存储并落到 <html> 的 class 上。 */
import { applyTheme, useTheme } from '../src/stores/theme'

const KEY = 'hr-workspace.theme'

function stubSystem(dark: boolean) {
  vi.stubGlobal(
    'matchMedia',
    (query: string) =>
      ({
        matches: dark && query.includes('prefers-color-scheme: dark'),
        media: query,
        onchange: null,
        addEventListener: () => {},
        removeEventListener: () => {},
        addListener: () => {},
        removeListener: () => {},
        dispatchEvent: () => false,
      }) as unknown as MediaQueryList,
  )
}

describe('theme store', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.className = 'light'
    document.documentElement.removeAttribute('data-theme')
    setActivePinia(createPinia())
  })

  it('没有记录时跟随系统偏好', () => {
    stubSystem(true)
    const theme = useTheme()
    expect(theme.theme).toBe('dark')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
    expect(document.documentElement.classList.contains('light')).toBe(false)

    setActivePinia(createPinia())
    stubSystem(false)
    expect(useTheme().theme).toBe('light')
    expect(document.documentElement.classList.contains('light')).toBe(true)
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })

  it('本地存储优先于系统偏好', () => {
    stubSystem(true)
    localStorage.setItem(KEY, 'light')
    expect(useTheme().theme).toBe('light')
  })

  it('切换后写入存储并同步 html 的 class 与 color-scheme', () => {
    stubSystem(false)
    const theme = useTheme()
    expect(theme.isDark).toBe(false)

    theme.toggle()
    expect(theme.theme).toBe('dark')
    expect(localStorage.getItem(KEY)).toBe('dark')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(document.documentElement.style.colorScheme).toBe('dark')

    theme.toggle()
    expect(theme.theme).toBe('light')
    expect(localStorage.getItem(KEY)).toBe('light')
    expect(document.documentElement.classList.contains('dark')).toBe(false)
    expect(document.documentElement.classList.contains('light')).toBe(true)
  })

  it('手工选择后不再跟随系统变化', () => {
    stubSystem(false)
    const theme = useTheme()
    theme.set('light')
    expect(theme.followsSystem).toBe(false)
    applyTheme('light')
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })

  it('存储不可用时仍能切换', () => {
    stubSystem(false)
    const theme = useTheme()
    const spy = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('quota')
    })
    expect(() => theme.toggle()).not.toThrow()
    expect(theme.theme).toBe('dark')
    spy.mockRestore()
  })
})
