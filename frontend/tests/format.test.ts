import { describe, expect, it } from 'vitest'
/** 覆盖更新载荷的空值、未修改字段及数字转换，防止表单静默改变数据。 */
import { formPayload, fullName, initials, money } from '../src/lib/format'
import { query } from '../src/lib/api'
import type { Field } from '../src/types'
describe('form payloads', () => {
  const fields: Field[] = [
    { key: 'id', label: 'ID', type: 'number', createOnly: true },
    { key: 'salary', label: '月薪', type: 'number' },
    { key: 'enabled', label: '启用', type: 'checkbox' },
    { key: 'password', label: '密码', type: 'password' },
  ]
  it('converts numbers and preserves zero and false during creation', () => {
    expect(formPayload(fields, { id: '7', salary: '0', enabled: false, password: '' })).toEqual({
      id: 7,
      salary: 0,
      enabled: false,
    })
  })
  it('sends only modified fields and omits immutable identifiers and empty reset passwords', () => {
    expect(
      formPayload(
        fields,
        { id: 2, salary: '1250', enabled: true, password: '' },
        { id: 1, salary: 1000, enabled: true },
      ),
    ).toEqual({ salary: 1250 })
  })
  it('rejects unsupported null clearing instead of silently ignoring the change', () => {
    expect(() => formPayload(fields, { salary: '', enabled: true }, { salary: 1000, enabled: true })).toThrow(
      '不能清空',
    )
  })
  it('rejects unchanged updates and non-finite numbers', () => {
    expect(() =>
      formPayload(fields, { salary: '1000', enabled: false }, { salary: 1000, enabled: false }),
    ).toThrow('没有需要保存')
    expect(() => formPayload(fields, { salary: 'oops', enabled: true })).toThrow('有效数字')
  })
})
describe('display and query helpers', () => {
  it('handles nullable first names and initials', () => {
    expect(fullName({ firstName: null, lastName: 'King' })).toBe('King')
    expect(initials('Steven King')).toBe('SK')
    expect(money(null)).toBe('—')
  })
  it('encodes filters and retains zero offsets', () => {
    expect(query({ q: 'A&B', limit: 10, offset: 0, departmentId: '', jobId: undefined })).toBe(
      '?q=A%26B&limit=10&offset=0',
    )
  })
})
