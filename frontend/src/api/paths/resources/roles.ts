/** 角色权限页（/roles）与权限相关判断使用页（由 views/ResourceView.vue 渲染）的请求路径。仅 ADMIN 可访问。 */
export const rolesPaths = {
  /** GET 角色列表 / POST 新建角色 */
  collection: '/auth/roles',
  /** PUT 修改角色名称或启用状态 */
  item: '/auth/roles/{code}',
  /** GET 查看 / PUT 替换角色的页面与接口权限 */
  permissions: '/auth/roles/{code}/permissions',
  /** GET 已登记的页面与接口权限目录 */
  catalog: '/auth/permissions',
} as const
