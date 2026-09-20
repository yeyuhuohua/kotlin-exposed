import type { Employee, Field, Row } from '../types'
/** 统一数据格式与部分更新载荷；未标记 clearable 的字段拒绝静默清空。 */
export const integer = (value: number) => new Intl.NumberFormat('zh-CN').format(value)
export const money = (value: unknown) =>
  value == null ? '—' : new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(Number(value))
export const fullName = (employee: Pick<Employee, 'firstName' | 'lastName'>) =>
  [employee.firstName, employee.lastName].filter(Boolean).join(' ')
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
      if (value === (original[field.key] ?? null)) continue
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
