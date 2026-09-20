export interface ApiEnvelope<T> {
  /** 前后端数据契约及通用表单定义；权限清单来自角色，前端展示不能代替后端鉴权。 */
  code: number
  message: string
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
  elapsedMs: number
  fetchedWith: string
}
export interface Health {
  status: string
  database: string
  redis: string
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
  endpoint: string
  id: string
  columns: Column[]
  fields?: Field[]
  paginated?: boolean
  adminOnly?: boolean
}
