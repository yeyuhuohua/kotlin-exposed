/** 员工管理页（/employees）的请求路径。 */
export const employeesPaths = {
  /** GET 分页查询（支持部门、岗位、关键字筛选）/ POST 新增 */
  collection: '/employees',
  /** GET 单个员工 / PUT 修改非空字段 / PATCH 精确修改（可清空可空列）/ DELETE 删除 */
  item: '/employees/{id}',
  /** GET 员工关联详情（JOIN 岗位、部门、地点、国家、地区） */
  itemDetails: '/employees/{id}/details',
} as const
