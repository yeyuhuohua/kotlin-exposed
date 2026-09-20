/**
 * API 文档入口（侧边栏与系统状态页共用）。
 *
 * 这些地址由后端的文档路由提供，不带 `/api` 前缀，也不参与接口权限判断。
 */
export const docsPaths = {
  swagger: '/swagger',
  knife4j: '/doc.html',
  openapi: '/v3/api-docs',
} as const
