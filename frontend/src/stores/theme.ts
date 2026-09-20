import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

/** 主题偏好：本地存储优先，没有记录时跟随系统；切换后写入本地存储。 */
export type ThemeName = 'light' | 'dark'

const STORAGE_KEY = 'hr-workspace.theme'

function systemPrefersDark(): boolean {
  return typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    ? window.matchMedia('(prefers-color-scheme: dark)').matches
    : false
}

function storedTheme(): ThemeName | null {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    return value === 'light' || value === 'dark' ? value : null
  } catch {
    /* 隐私模式下读取会抛错，按未设置处理 */
    return null
  }
}

/**
 * 主题只落在 <html> 的 class 上：颜色变量定义在 html.light / html.dark 两个块里，
 * Element Plus 的暗色变量认的也是 html.dark。两个类互斥，始终保留一个。
 */
export function applyTheme(theme: ThemeName) {
  if (typeof document === 'undefined') return
  const root = document.documentElement
  root.classList.remove('light', 'dark')
  root.classList.add(theme)
  root.dataset.theme = theme
  root.style.colorScheme = theme
}

export const useTheme = defineStore('theme', () => {
  const stored = storedTheme()
  const theme = ref<ThemeName>(stored ?? (systemPrefersDark() ? 'dark' : 'light'))
  /** 用户还没手动选过时，跟随系统变化。 */
  const followsSystem = ref(stored === null)
  applyTheme(theme.value)

  function set(next: ThemeName) {
    theme.value = next
    followsSystem.value = false
    applyTheme(next)
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      /* 存不了就只在本次会话生效 */
    }
  }

  function toggle() {
    set(theme.value === 'dark' ? 'light' : 'dark')
  }

  if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
    const query = window.matchMedia('(prefers-color-scheme: dark)')
    query.addEventListener?.('change', (event) => {
      if (!followsSystem.value) return
      theme.value = event.matches ? 'dark' : 'light'
      applyTheme(theme.value)
    })
  }

  return { theme, isDark: computed(() => theme.value === 'dark'), followsSystem, set, toggle }
})
