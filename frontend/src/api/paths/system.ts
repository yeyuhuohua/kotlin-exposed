/** 系统状态页（/system）的请求路径。 */
export const systemPaths = {
  /** GET MySQL 与 Redis 连通性；依赖不可用时返回 503 且仍带诊断数据 */
  health: '/health',
} as const
