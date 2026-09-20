/** 办公地点页（/locations）页（由 views/ResourceView.vue 渲染）的请求路径。 */
export const locationsPaths = {
  /** GET 地点列表 / POST 新增地点 */
  collection: '/locations',
  /** PUT 修改地点 */
  item: '/locations/{id}',
} as const
