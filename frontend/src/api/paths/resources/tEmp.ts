/** 示例人员页（/t-emp）页（由 views/ResourceView.vue 渲染）的请求路径。 */
export const tEmpPaths = {
  /** GET 示例人员列表 / POST 新增 */
  collection: '/t-emp',
  /** PUT 修改示例人员 */
  item: '/t-emp/{id}',
} as const
