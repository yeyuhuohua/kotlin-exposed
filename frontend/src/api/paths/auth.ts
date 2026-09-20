/**
 * 登录页（/login）与「我的账号」页共用的认证请求路径。
 *
 * 带 `{参数}` 的是后端路由模板：发请求时用 `fillPath` 填充，判断权限时直接传模板
 * （权限码形如 `api:POST:/api/auth/login`）。
 */
export const authPaths = {
  /** POST 登录换取 Token */
  login: '/auth/login',
  /** GET 当前账号、角色与有效权限 */
  me: '/auth/me',
  /** POST 退出登录并撤销该账号所有 Token */
  logout: '/auth/logout',
} as const
