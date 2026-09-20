/** 岗位管理页（/jobs）页（由 views/ResourceView.vue 渲染）的请求路径。 */
export const jobsPaths = {
  /** GET 岗位列表 / POST 新增岗位 */
  collection: '/jobs',
  /** PUT 修改岗位（主键是岗位编码） */
  item: '/jobs/{id}',
} as const
