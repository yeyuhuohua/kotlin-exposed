import type { FormRules } from 'element-plus'
import type { Field, Row } from '../types'
import { codePointLength } from './format'

/** 通用字段规则保持与后端约束一致；编辑时空密码表示不重置密码。 */
export function recordRules(fields: Field[], original?: Row): FormRules {
  return Object.fromEntries(
    fields.map((field) => [
      field.key,
      [
        {
          trigger: ['blur', 'change'],
          validator: (_rule: unknown, value: unknown, done: (error?: Error) => void) => {
            const empty =
              value === '' ||
              value === null ||
              value === undefined ||
              (typeof value === 'string' && !value.trim())
            if (empty)
              return done(
                field.required || (field.type === 'password' && !original)
                  ? new Error(`请填写${field.label}`)
                  : undefined,
              )
            if (field.type === 'number') {
              const number = Number(value)
              if (!Number.isFinite(number)) return done(new Error(`${field.label}必须是有效数字`))
              if (field.min !== undefined && number < field.min)
                return done(new Error(`${field.label}不能小于 ${field.min}`))
              if (field.max !== undefined && number > field.max)
                return done(new Error(`${field.label}不能大于 ${field.max}`))
              if ((!field.step || field.step === '1') && !Number.isInteger(number))
                return done(new Error(`${field.label}必须是整数`))
            } else if (typeof value === 'string') {
              const textLength = field.lengthUnit === 'utf16' ? value.length : codePointLength(value)
              if (field.minLength !== undefined && textLength < field.minLength)
                return done(new Error(`${field.label}至少 ${field.minLength} 个字符`))
              if (field.maxLength !== undefined && textLength > field.maxLength)
                return done(new Error(`${field.label}最多 ${field.maxLength} 个字符`))
              if (field.pattern && !field.pattern.test(value))
                return done(new Error(field.patternMessage || `${field.label}格式不正确`))
            }
            done()
          },
        },
      ],
    ]),
  )
}
