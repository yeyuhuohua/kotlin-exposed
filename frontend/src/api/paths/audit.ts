/** 审计日志页（/audit，仅 ADMIN）的请求路径。 */
export const auditPaths = {
  /** GET 登录记录分页（支持 username / success 过滤） */
  logins: '/audit/logins',
  /** GET 接口调用记录分页（支持 username / method / path 过滤） */
  apiCalls: '/audit/api-calls',
} as const
