import { readdirSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
/** 路径注册表是请求地址与权限码的唯一来源，这里固定它的形状与拼接规则。 */
import * as paths from '../src/api/paths'
import { fillPath, fillTemplate, itemUrl, templateParam } from '../src/api/paths'
import { resources } from '../src/config/resources'
import { canApi } from '../src/lib/permissions'
import type { User } from '../src/types'

const pathsDir = join(process.cwd(), 'src/api/paths')
const viewsDir = join(process.cwd(), 'src/views')
/** 不绑定单个视图的共享模块：登录态、文档入口、汇总文件。 */
const sharedModules = ['auth', 'docs', 'index']
const camel = (key: string) => key.replace(/-(\w)/g, (_match, letter: string) => letter.toUpperCase())

const modules = Object.entries(paths).filter(
  ([name, value]) => name.endsWith('Paths') && typeof value === 'object',
) as [string, Record<string, string>][]

const allPaths = modules.flatMap(([name, group]) =>
  Object.entries(group).map(([key, value]) => ({ name, key, value })),
)

describe('api path registry', () => {
  it('按页面分文件导出，且不带 /api 前缀', () => {
    expect(modules.length).toBeGreaterThanOrEqual(15)
    for (const { name, key, value } of allPaths) {
      expect(value.startsWith('/'), `${name}.${key} = ${value}`).toBe(true)
      expect(value.startsWith('/api'), `${name}.${key} 不应带 /api 前缀`).toBe(false)
      expect(value.endsWith('/') && value !== '/', `${name}.${key} 不应有结尾斜杠`).toBe(false)
    }
  })

  it('模板参数统一写成 {name}，且只剩一层路径参数', () => {
    for (const { name, key, value } of allPaths) {
      const params = [...value.matchAll(/\{([^}]*)\}/g)].map((match) => match[1])
      params.forEach((param) => expect(param, `${name}.${key}`).toMatch(/^[a-zA-Z]\w*$/))
      expect(params.length, `${name}.${key} = ${value}`).toBeLessThanOrEqual(1)
    }
  })

  it('fillPath 会编码参数并拒绝缺失参数', () => {
    expect(fillPath('/employees/{id}', { id: 100 })).toBe('/employees/100')
    expect(fillPath('/auth/roles/{code}', { code: 'HR VIEWER' })).toBe('/auth/roles/HR%20VIEWER')
    expect(fillPath('/health', {})).toBe('/health')
    expect(() => fillPath('/employees/{id}', {})).toThrow('缺少参数 id')
  })

  it('fillTemplate 与 itemUrl 覆盖单参数场景', () => {
    expect(fillTemplate(paths.employeesPaths.item, 100)).toBe('/employees/100')
    expect(fillTemplate(paths.rolesPaths.permissions, 'ADMIN')).toBe('/auth/roles/ADMIN/permissions')
    expect(fillTemplate('/health', 'ignored')).toBe('/health')
    expect(itemUrl('/departments', 10)).toBe('/departments/10')
    expect(templateParam('/auth/roles/{code}')).toBe('code')
    expect(templateParam('/health')).toBeUndefined()
  })

  it('每个可编辑资源都带 updateTemplate，主键参数可用', () => {
    const editable = resources.filter((resource) => resource.fields)
    expect(editable.length).toBeGreaterThan(5)
    for (const resource of editable) {
      expect(resource.updateTemplate, `${resource.key} 缺少 updateTemplate`).toBeTruthy()
      const param = templateParam(resource.updateTemplate!)!
      expect(['id', 'code'], `${resource.key} 的参数名`).toContain(param)
      expect(fillTemplate(resource.updateTemplate!, 1)).toContain('/1')
    }
  })

  it('顶层文件与 views 下的视图一一对应', () => {
    const views = readdirSync(viewsDir)
      .filter((file) => file.endsWith('View.vue'))
      .map((file) => file.replace(/View\.vue$/, '').toLowerCase())
    const files = readdirSync(pathsDir)
      .filter((file) => file.endsWith('.ts'))
      .map((file) => file.replace(/\.ts$/, ''))
    for (const file of files) {
      if (sharedModules.includes(file)) continue
      expect(views, `src/api/paths/${file}.ts 找不到同名视图`).toContain(file)
    }
    // 反向：有专属视图的页面都应该有自己的路径文件
    for (const view of views) {
      if (['resource', 'login', 'account', 'forbidden'].includes(view)) continue
      expect(files, `views/${view}View.vue 没有对应的路径文件`).toContain(view)
    }
  })

  it('resources 目录与 config/resources.ts 的页面一一对应', () => {
    const files = readdirSync(join(pathsDir, 'resources'))
      .filter((file) => file.endsWith('.ts') && file !== 'index.ts')
      .map((file) => file.replace(/\.ts$/, ''))
    const keys = resources.map((resource) => camel(resource.key))
    expect([...files].sort()).toEqual([...keys].sort())
    // 资源声明的 endpoint 必须来自路径注册表，不能自己写字符串
    const registered = new Set(allPaths.map(({ value }) => value))
    for (const resource of resources) {
      expect(registered, `${resource.key} 的 endpoint 不在路径注册表里`).toContain(resource.endpoint)
    }
  })

  it('注册的路径能拼出后端使用的权限码', () => {
    const user = { id: 1, username: 't', roleCode: 'ADMIN', enabled: true, permissions: [] } as User
    // 健康检查公开；/auth/me、/auth/logout 对所有已登录账号开放，不参与权限判断
    const alwaysAllowed: string[] = [paths.systemPaths.health, paths.authPaths.me, paths.authPaths.logout]
    for (const { key, value } of allPaths) {
      user.permissions = [`api:GET:/api${value}`]
      expect(canApi(user, 'GET', value), `${key} = ${value}`).toBe(true)
      if (alwaysAllowed.includes(value)) continue
      user.permissions = []
      expect(canApi(user, 'GET', value), `${key} = ${value} 不应默认放行`).toBe(false)
    }
  })
})
