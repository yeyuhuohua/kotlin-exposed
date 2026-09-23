/**
 * 请求路径的唯一来源。目录结构与页面结构一一对应：
 *
 * - 顶层：有专属视图的页面，文件名 = `views/<Name>View.vue` 去掉 View（`dashboard.ts` / `employees.ts` / `system.ts`）；
 *   另有三个不绑定单个视图的共享模块：`auth.ts`（登录态，stores/auth.ts 用）、`docs.ts`（文档入口）、`index.ts`（本文件）。
 * - `resources/`：由 `views/ResourceView.vue` 泛化渲染的业务目录页面，文件名 = `config/resources.ts` 里的资源 key
 *   （`t-dept` → `tDept.ts`）。它们没有各自的 .vue 文件，所以单独放在子目录里。
 *
 * 约定：
 * - 路径不带 `/api` 前缀，`lib/api.ts` 会统一补上（生产环境另有 `VITE_API_BASE_URL`）。
 * - `{参数}` 是后端路由模板：发请求用 `fillPath`/`fillTemplate` 填充，判断权限直接把模板交给 `canApi`，
 *   这样实际请求地址与权限码永远来自同一个字符串。
 * - 页面路由（Vue Router 的 path、权限码 `page:<key>`）不在这里，见 `config/resources.ts`。
 */
export * from './auth'
export * from './dashboard'
export * from './employees'
export * from './system'
export * from './docs'
export * from './resources'

/** 用路径参数填充模板：fillPath('/employees/{id}', { id: 100 }) → '/employees/100'。 */
export function fillPath(template: string, params: Record<string, string | number>): string {
  return template.replace(/\{(\w+)\}/g, (_match, name: string) => {
    const value = params[name]
    if (value === undefined) throw new Error(`路径模板 ${template} 缺少参数 ${name}`)
    return encodeURIComponent(String(value))
  })
}

/** 模板只有一个参数时直接填值：fillTemplate('/auth/roles/{code}', 'ADMIN') → '/auth/roles/ADMIN'。 */
export function fillTemplate(template: string, value: string | number): string {
  const name = templateParam(template)
  return name ? fillPath(template, { [name]: value }) : template
}

/** 集合路径 + 主键拼明细路径：itemUrl('/departments', 10) → '/departments/10'。 */
export function itemUrl(collection: string, id: string | number): string {
  return `${collection}/${encodeURIComponent(String(id))}`
}

/** 取模板里的参数名，例如 '/auth/roles/{code}' → 'code'。 */
export function templateParam(template: string): string | undefined {
  return /\{(\w+)\}/.exec(template)?.[1]
}
