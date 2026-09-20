/** 部门管理页（/departments）页（由 views/ResourceView.vue 渲染）的请求路径。 */
export const departmentsPaths = {
  /** GET 部门列表 / POST 新增部门 */
  collection: '/departments',
  /** GET 单个部门 / PUT 修改部门 */
  item: '/departments/{id}',
} as const
