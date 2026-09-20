/** 用户管理页（/users）页（由 views/ResourceView.vue 渲染）的请求路径。仅 ADMIN 可访问。 */
export const usersPaths = {
  /** GET 分页查询 / POST 新建 */
  collection: '/auth/users',
  /** PUT 调整角色、启停账号或重置密码 */
  item: '/auth/users/{id}',
} as const
