/**
 * API 文档入口（侧边栏与系统状态页共用）。
 *
 * 这些地址由后端的文档路由提供，不带 `/api` 前缀，也不参与接口权限判断，
 * 注册表里保持相对路径；浏览器导航用 `docsHref` 拼上 API 前缀——
 * 开发环境指向后端源站，生产同源部署时仍是相对路径。
 */
import { apiBaseUrl } from '../../lib/api'

export const docsPaths = {
  swagger: '/swagger',
  knife4j: '/doc.html',
  openapi: '/v3/api-docs',
} as const

/** 拼出浏览器可直接打开的文档地址。 */
export function docsHref(path: string): string {
  return `${apiBaseUrl}${path}`
}
