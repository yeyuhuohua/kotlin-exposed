import type { Employee, Field, Row } from '../types'
/** 统一数据格式与部分更新载荷；未标记 clearable 的字段拒绝静默清空。 */
/** 使用中文地区格式显示整数。 */
export const integer = (value: number) => new Intl.NumberFormat('zh-CN').format(value)
/** 把数值格式化为最多两位小数的中文数字，空值显示占位符。 */
export const money = (value: unknown) =>
  value == null ? '—' : new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(Number(value))
/** 拼接员工的名和姓，并去除多余空白。 */
export const fullName = (employee: Pick<Employee, 'firstName' | 'lastName'>) =>
  [employee.firstName, employee.lastName].filter(Boolean).join(' ')
/** 从姓名中提取用于头像显示的缩写。 */
export const initials = (name: string) =>
  name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0])
    .join('')
    .toUpperCase()
export interface FormPayloadOptions {
  /** 后端支持 PATCH 语义时，clearable 字段可以提交 null 表示清空。 */
  allowClear?: boolean
}
/** 与后端/数据库一致的字符数：按 Unicode 码点计数，𠮷 等增补平面字符算 1 个。 */
export function codePointLength(value: string): number {
  return [...value].length
}
/** 将当前表单转换为接口请求体，只提交实际修改的字段。 */
export function formPayload(
  fields: Field[],
  values: Row,
  original?: Row,
  options: FormPayloadOptions = {},
): Row {
  const payload: Row = {}
  for (const field of fields) {
    if (original && field.createOnly) continue
    const raw = values[field.key]
    if (field.type === 'password' && original && !raw) continue
    const value =
      field.type === 'checkbox'
        ? Boolean(raw)
        : raw === '' || raw === undefined
          ? null
          : field.type === 'number'
            ? Number(raw)
            : raw
    if (value !== null && typeof value === 'number' && !Number.isFinite(value))
      throw new Error(`${field.label}必须是有效数字`)
    if (original) {
      const originalValue = original[field.key]
      // 数据库里的空字符串与表单留空（null）是同一种"没有值"，不算清空修改。
      if (value === (originalValue ?? null) || (value === null && originalValue === '')) continue
      if (value === null) {
        if (!(options.allowClear && field.clearable)) {
          throw new Error(`${field.label}不能清空，当前接口仅支持修改为非空值`)
        }
        payload[field.key] = null
        continue
      }
      payload[field.key] = value
    } else if (value !== null) payload[field.key] = value
  }
  if (original && Object.keys(payload).length === 0) throw new Error('没有需要保存的修改')
  return payload
}
