import { describe, expect, it } from 'vitest'
import { recordRules } from '../src/lib/recordForm'
import type { Field } from '../src/types'

type Validator = (rule: unknown, value: unknown, done: (error?: Error) => void) => void

function validate(field: Field, value: unknown, original?: Record<string, string | number | boolean | null>) {
  let result: Error | undefined
  const configured = recordRules([field], original)[field.key]!
  const rule = (Array.isArray(configured) ? configured[0] : configured) as { validator: Validator }
  rule.validator({}, value, (error?: Error) => { result = error })
  return result
}

describe('recordRules', () => {
  it('validates required fields and edit-only empty passwords', () => {
    const field: Field = { key: 'password', label: '密码', type: 'password', required: true }
    expect(validate(field, '')?.message).toBe('请填写密码')
    expect(validate({ ...field, required: false }, '', { password: 'old' })?.message).toBeUndefined()
  })

  it('uses Unicode code points for text and UTF-16 only for designated fields', () => {
    const text: Field = { key: 'phone', label: '电话', maxLength: 20 }
    expect(validate(text, `𠮷${'a'.repeat(19)}`)?.message).toBeUndefined()
    const password: Field = { key: 'password', label: '密码', maxLength: 8, lengthUnit: 'utf16' }
    expect(validate(password, '𠮷𠮷𠮷𠮷𠮷')?.message).toBe('密码最多 8 个字符')
  })

  it('validates numeric ranges and integers', () => {
    const field: Field = { key: 'salary', label: '月薪', type: 'number', min: 1, max: 100 }
    expect(validate(field, 0)?.message).toBe('月薪不能小于 1')
    expect(validate(field, 1.5)?.message).toBe('月薪必须是整数')
    expect(validate(field, 50)?.message).toBeUndefined()
  })
})
