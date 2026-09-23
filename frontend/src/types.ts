export interface ApiEnvelope<T> {
  /** 前后端数据契约及通用表单定义；权限清单来自角色，前端展示不能代替后端鉴权。 */
  code: number
  message: string
  /** 稳定的机器可读错误码，成功时为 null；前端按它选择提示文案。 */
  error?: string | null
  data: T
}
export interface Page<T> {
  total: number
  items: T[]
}
export type Row = Record<string, string | number | boolean | null>
export interface User {
  id: number
  username: string
  roleCode: string
  enabled: boolean
  permissions: string[]
}
export interface PermissionDefinition {
  code: string
  kind: 'PAGE' | 'API'
  label: string
  group: string
  path: string
  method: string | null
  adminOnly: boolean
}
export interface RolePermissions {
  role: { code: string; name: string; enabled: boolean }
  revision: number
  permissions: string[]
  protectedRole: boolean
}
export interface LoginResult {
  accessToken: string
  expiresIn: number
  tokenType: string
  user: User
}
export interface Employee extends Row {
  employeeId: number
  firstName: string | null
  lastName: string
  email: string
  phoneNumber: string | null
  hireDate: string
  jobId: string
  salary: number | null
  commissionPct: number | null
  managerId: number | null
  departmentId: number | null
}
export interface Department extends Row {
  departmentId: number
  departmentName: string
  managerId: number | null
  locationId: number | null
}
export interface Job extends Row {
  jobId: string
  jobTitle: string
  minSalary: number | null
  maxSalary: number | null
}
export interface DepartmentHeadcount {
  departmentId: number | null
  departmentName: string
  count: number
}
export interface SalarySummary {
  employeesWithSalary: number
  totalSalary: number
  averageSalary: number | null
  minSalary: number | null
  maxSalary: number | null
}
export interface Overview {
  employeeTotal: number
  departmentCount: number
  jobCount: number
  locationCount: number
  regionCount: number
  tEmpCount: number
  sampleEmployees: Employee[]
  departments: Department[]
  regions: Row[]
  /** 服务端聚合的部门人数分布（按人数倒序）。 */
  departmentHeadcount: DepartmentHeadcount[]
  /** 服务端聚合的薪资汇总。 */
  salarySummary: SalarySummary
  elapsedMs: number
  fetchedWith: string
}
export interface Health {
  status: string
  database: string
  redis: string
}
export interface LoginRecord {
  id: number
  username: string
  userId: number | null
  ip: string
  userAgent: string | null
  success: boolean
  errorCode: string | null
  createdAt: string
}
export interface ApiCallRecord {
  id: number
  userId: number | null
  username: string | null
  method: string
  path: string
  queryString: string | null
  statusCode: number
  durationMs: number
  ip: string
  userAgent: string | null
  createdAt: string
}
export interface Option {
  value: string | number
  label: string
}
export interface Field {
  key: string
  label: string
  type?: 'text' | 'number' | 'date' | 'select' | 'password' | 'checkbox'
  required?: boolean
  createOnly?: boolean
  min?: number
  max?: number
  maxLength?: number
  minLength?: number
  step?: string
  options?: Option[]
  lookup?: string
  /** 文本格式校验，与后端约束保持一致（如用户名、角色编码的字符集）。 */
  pattern?: RegExp
  patternMessage?: string
  /** 允许清空为 null（需要后端支持 PATCH 语义）。 */
  clearable?: boolean
  default?: string | number | boolean
}
export interface Column {
  key: string
  label: string
  kind?: 'money' | 'status' | 'role' | 'id'
}
export interface Resource {
  key: string
  title: string
  subtitle: string
  /** 集合请求路径，来自 src/api/paths 对应页面的常量。 */
  endpoint: string
  /** 单条记录的路径模板（含 {id}/{code}），同时用于发请求与判断 PUT 权限。 */
  updateTemplate?: string
  id: string
  columns: Column[]
  fields?: Field[]
  paginated?: boolean
  adminOnly?: boolean
}
