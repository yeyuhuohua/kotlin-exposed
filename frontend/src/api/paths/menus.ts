/**
 * 菜单管理页（views/MenusView.vue）的请求路径。
 *
 * 带 `{参数}` 的是后端路由模板：发请求时用 `fillPath` 填充，判断权限时直接传模板
 * （权限码形如 `api:PUT:/api/menus/{key}`）。
 */
export const menusPaths = {
  /** GET 全部菜单（含停用）/ POST 新增菜单 */
  collection: '/menus',
  /** PUT 整体替换 / DELETE 删除，模板参数为菜单 key */
  item: '/menus/{key}',
} as const
