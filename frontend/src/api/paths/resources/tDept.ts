/** 示例部门页（/t-dept）页（由 views/ResourceView.vue 渲染）的请求路径。 */
export const tDeptPaths = {
  /** GET 示例部门列表 / POST 新增 */
  collection: '/t-dept',
  /** PUT 修改示例部门 */
  item: '/t-dept/{id}',
} as const
