import {
  countriesPaths,
  departmentsPaths,
  empDetailsPaths,
  jobGradesPaths,
  jobHistoryPaths,
  jobsPaths,
  locationsPaths,
  ordersPaths,
  regionsPaths,
  rolesPaths,
  tDeptPaths,
  tEmpPaths,
  usersPaths,
} from '../api/paths'
import type { Field, Resource } from '../types'
/** 业务目录与可编辑字段集中声明；只为后端真实提供的接口显示写操作。 */
const number = (key: string, label: string, required = false, createOnly = false): Field => ({
  key,
  label,
  type: 'number',
  required,
  createOnly,
  step: '1',
})
const text = (key: string, label: string, required = false, maxLength?: number): Field => ({
  key,
  label,
  required,
  maxLength,
})
const lookup = (key: string, label: string, source: string, required = false): Field => ({
  key,
  label,
  type: 'select',
  lookup: source,
  required,
})

export const employeeCreateFields: Field[] = [
  { ...number('employeeId', '员工编号', true, true), min: 1 },
  text('firstName', '名', false, 20),
  text('lastName', '姓', true, 25),
  text('email', '邮箱账号', true, 25),
  text('phoneNumber', '联系电话', false, 20),
  { key: 'hireDate', label: '入职日期', type: 'date', required: true },
  lookup('jobId', '岗位', 'jobs', true),
  { ...number('salary', '月薪'), min: 0, step: '0.01' },
  { ...number('commissionPct', '提成比例'), min: 0, max: 1, step: '0.01' },
  number('managerId', '直属经理编号'),
  lookup('departmentId', '所属部门', 'departments'),
]
// 员工支持 PATCH：salary / departmentId / phoneNumber 对应可空列，可以清空；
// jobId 对应非空列（jobs.job_id），只能改成另一个岗位。
export const employeeUpdateFields: Field[] = [
  { ...number('salary', '月薪'), min: 0, step: '0.01', clearable: true },
  { ...lookup('departmentId', '所属部门', 'departments'), clearable: true },
  lookup('jobId', '岗位', 'jobs', true),
  { ...text('phoneNumber', '联系电话', false, 20), clearable: true },
]

export const resources: Resource[] = [
  {
    key: 'departments',
    title: '部门管理',
    subtitle: '组织与团队',
    endpoint: departmentsPaths.collection,
    updateTemplate: departmentsPaths.item,
    id: 'departmentId',
    columns: [
      { key: 'departmentId', label: '部门编号', kind: 'id' },
      { key: 'departmentName', label: '部门名称' },
      { key: 'managerId', label: '经理编号' },
      { key: 'locationId', label: '地点编号' },
    ],
    fields: [
      number('departmentId', '部门编号', true, true),
      text('departmentName', '部门名称', true, 30),
      number('managerId', '经理编号'),
      lookup('locationId', '办公地点', 'locations'),
    ],
  },
  {
    key: 'jobs',
    title: '岗位管理',
    subtitle: '岗位与薪酬',
    endpoint: jobsPaths.collection,
    updateTemplate: jobsPaths.item,
    id: 'jobId',
    columns: [
      { key: 'jobId', label: '岗位编码', kind: 'id' },
      { key: 'jobTitle', label: '岗位名称' },
      { key: 'minSalary', label: '最低月薪', kind: 'money' },
      { key: 'maxSalary', label: '最高月薪', kind: 'money' },
    ],
    fields: [
      { ...text('jobId', '岗位编码', true, 10), createOnly: true },
      text('jobTitle', '岗位名称', true, 35),
      { ...number('minSalary', '最低月薪'), min: 0 },
      { ...number('maxSalary', '最高月薪'), min: 0 },
    ],
  },
  {
    key: 'locations',
    title: '办公地点',
    subtitle: '全球组织',
    endpoint: locationsPaths.collection,
    updateTemplate: locationsPaths.item,
    id: 'locationId',
    columns: [
      { key: 'locationId', label: '地点编号', kind: 'id' },
      { key: 'city', label: '城市' },
      { key: 'streetAddress', label: '街道地址' },
      { key: 'stateProvince', label: '州 / 省' },
      { key: 'countryId', label: '国家代码' },
      { key: 'postalCode', label: '邮政编码' },
    ],
    fields: [
      number('locationId', '地点编号', true, true),
      text('city', '城市', true, 30),
      text('streetAddress', '街道地址', false, 40),
      text('stateProvince', '州 / 省', false, 25),
      lookup('countryId', '国家', 'countries'),
      text('postalCode', '邮政编码', false, 12),
    ],
  },
  {
    key: 'countries',
    title: '国家与地区',
    subtitle: '全球组织',
    endpoint: countriesPaths.list,
    id: 'countryId',
    columns: [
      { key: 'countryId', label: '国家代码', kind: 'id' },
      { key: 'countryName', label: '国家名称' },
      { key: 'regionId', label: '区域编号' },
    ],
  },
  {
    key: 'regions',
    title: '区域目录',
    subtitle: '全球组织',
    endpoint: regionsPaths.list,
    id: 'regionId',
    columns: [
      { key: 'regionId', label: '区域编号', kind: 'id' },
      { key: 'regionName', label: '区域名称' },
    ],
  },
  {
    key: 'job-history',
    title: '任职历史',
    subtitle: '人事档案',
    endpoint: jobHistoryPaths.list,
    id: 'employeeId',
    columns: [
      { key: 'employeeId', label: '员工编号', kind: 'id' },
      { key: 'startDate', label: '开始日期' },
      { key: 'endDate', label: '结束日期' },
      { key: 'jobId', label: '岗位编码' },
      { key: 'departmentId', label: '部门编号' },
    ],
  },
  {
    key: 'job-grades',
    title: '薪资等级',
    subtitle: '岗位与薪酬',
    endpoint: jobGradesPaths.list,
    id: 'gradeLevel',
    columns: [
      { key: 'gradeLevel', label: '薪级', kind: 'id' },
      { key: 'lowestSal', label: '薪资下限', kind: 'money' },
      { key: 'highestSal', label: '薪资上限', kind: 'money' },
    ],
  },
  {
    key: 'emp-details',
    title: '员工详情视图',
    subtitle: '人事档案',
    endpoint: empDetailsPaths.list,
    id: 'employeeId',
    paginated: true,
    columns: [
      { key: 'employeeId', label: '员工编号', kind: 'id' },
      { key: 'firstName', label: '名' },
      { key: 'lastName', label: '姓' },
      { key: 'jobTitle', label: '岗位' },
      { key: 'departmentName', label: '部门' },
      { key: 'city', label: '城市' },
      { key: 'countryName', label: '国家' },
      { key: 'salary', label: '月薪', kind: 'money' },
    ],
  },
  {
    key: 't-dept',
    title: '示例部门',
    subtitle: '演示数据',
    endpoint: tDeptPaths.collection,
    updateTemplate: tDeptPaths.item,
    id: 'id',
    columns: [
      { key: 'id', label: '编号', kind: 'id' },
      { key: 'deptName', label: '部门名称' },
      { key: 'address', label: '地址' },
    ],
    fields: [text('deptName', '部门名称', true), text('address', '地址')],
  },
  {
    key: 't-emp',
    title: '示例人员',
    subtitle: '演示数据',
    endpoint: tEmpPaths.collection,
    updateTemplate: tEmpPaths.item,
    id: 'id',
    columns: [
      { key: 'id', label: '编号', kind: 'id' },
      { key: 'name', label: '姓名' },
      { key: 'age', label: '年龄' },
      { key: 'deptId', label: '部门编号' },
      { key: 'empno', label: '工号' },
    ],
    fields: [
      text('name', '姓名'),
      { ...number('age', '年龄'), min: 0 },
      lookup('deptId', '示例部门', 't-dept'),
      number('empno', '工号', true),
    ],
  },
  {
    key: 'orders',
    title: '示例订单',
    subtitle: '演示数据',
    endpoint: ordersPaths.list,
    id: 'orderId',
    columns: [
      { key: 'orderId', label: '订单编号', kind: 'id' },
      { key: 'orderName', label: '订单名称' },
    ],
  },
  {
    key: 'users',
    title: '用户管理',
    subtitle: '访问控制',
    endpoint: usersPaths.collection,
    updateTemplate: usersPaths.item,
    id: 'id',
    paginated: true,
    adminOnly: true,
    columns: [
      { key: 'id', label: '用户编号', kind: 'id' },
      { key: 'username', label: '用户名' },
      { key: 'roleCode', label: '角色', kind: 'role' },
      { key: 'enabled', label: '账号状态', kind: 'status' },
    ],
    fields: [
      { ...text('username', '用户名', true, 64), minLength: 3, createOnly: true },
      { key: 'password', label: '密码', type: 'password', minLength: 8, maxLength: 128 },
      { key: 'roleCode', label: '角色', type: 'select', lookup: 'roles', required: true, default: 'READER' },
      { key: 'enabled', label: '启用账号', type: 'checkbox', default: true },
    ],
  },
  {
    key: 'roles',
    title: '角色权限',
    subtitle: '访问控制',
    endpoint: rolesPaths.collection,
    updateTemplate: rolesPaths.item,
    id: 'code',
    adminOnly: true,
    columns: [
      { key: 'code', label: '角色编码', kind: 'role' },
      { key: 'name', label: '角色名称' },
      { key: 'enabled', label: '角色状态', kind: 'status' },
    ],
    fields: [
      { ...text('code', '角色编码', true, 20), minLength: 2, createOnly: true },
      text('name', '角色名称', true, 50),
      { key: 'enabled', label: '启用角色', type: 'checkbox', default: true },
    ],
  },
]
export const lookupSources: Record<string, { endpoint: string; id: string; label: string }> = {
  departments: { endpoint: departmentsPaths.collection, id: 'departmentId', label: 'departmentName' },
  jobs: { endpoint: jobsPaths.collection, id: 'jobId', label: 'jobTitle' },
  locations: { endpoint: locationsPaths.collection, id: 'locationId', label: 'city' },
  countries: { endpoint: countriesPaths.list, id: 'countryId', label: 'countryName' },
  't-dept': { endpoint: tDeptPaths.collection, id: 'id', label: 'deptName' },
  roles: { endpoint: rolesPaths.collection, id: 'code', label: 'name' },
}

/**
 * 页面清单的唯一来源：权限码 `page:<key>`、路由 path、菜单标题都由这里派生，
 * 避免出现"后端登记了页面、前端却忘了加路由"或反过来的漂移。
 */
export interface PageDefinition {
  key: string
  title: string
  path: string
  adminOnly?: boolean
}

/** 有独立视图组件的页面，顺序即登录后的默认落地顺序。 */
export const staticPages: PageDefinition[] = [
  { key: 'overview', title: '工作概览', path: '/' },
  { key: 'employees', title: '员工管理', path: '/employees' },
  { key: 'system', title: '系统状态', path: '/system' },
]

/** 全部可授权的业务页面，资源页面的 key 与 path 一律取资源定义。 */
export const pages: PageDefinition[] = [
  ...staticPages,
  ...resources.map((resource) => ({
    key: resource.key,
    title: resource.title,
    path: `/${resource.key}`,
    adminOnly: resource.adminOnly,
  })),
]

export const pagePaths = pages.map((page) => page.path)
