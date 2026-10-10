import type { PageRoute } from '../types'

/** 菜单路径以服务端清单为准，页面内部不要写死地址。 */
export function pagePath(pages: PageRoute[], key: string): string | null {
  return pages.find((page) => page.key === key)?.path ?? null
}
