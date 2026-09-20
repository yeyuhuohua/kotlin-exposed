import type { ApiEnvelope } from '../types'
/** 统一封装响应信封、Bearer Token、超时和取消；旧请求不能使新登录会话失效。 */
import { readSession } from './session'

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}
interface RequestOptions {
  method?: string
  body?: unknown
  signal?: AbortSignal
  auth?: boolean
  acceptUnavailable?: boolean
}
const messages: Record<string, string> = {
  'invalid username or password': '用户名或密码错误，或账号已停用',
  'insufficient permissions': '当前账号没有此操作权限',
  'username already exists or role is unavailable': '用户名已存在或角色不可用',
  'employee not found': '员工不存在或已被删除',
  'no fields to update': '没有需要更新的字段',
  'cannot disable or demote your own administrator account': '不能停用或降级当前管理员账号',
  'ADMIN role is protected': 'ADMIN 角色受保护，不能修改其权限、名称或状态',
  'admin account is protected': 'admin 账号受保护，不能修改其角色、停用或代为重置密码',
  'permissions changed; reload before saving': '权限已被其他管理员更新，请重新加载后再保存',
  'unknown permission code': '权限项不存在，请重新加载权限目录',
  'management permissions require ADMIN role': '管理权限仅能授予 ADMIN 角色',
  'only ADMIN can manage permissions': '只有 ADMIN 角色可以管理权限',
  'role not found': '角色不存在',
  'role already exists': '角色编码已存在',
  'role must exist and be enabled': '请选择已存在且启用的角色',
  'role name must contain 1-50 characters': '角色名称长度必须为 1-50 个字符',
  'role code must contain 2-20 uppercase letters, digits or underscores and start with a letter':
    '角色编码须以字母开头，由 2-20 位大写字母、数字或下划线组成',
}
export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const token = options.auth === false ? undefined : readSession()?.token
  const controller = new AbortController()
  const abort = () => controller.abort(options.signal?.reason)
  if (options.signal?.aborted) abort()
  else options.signal?.addEventListener('abort', abort, { once: true })
  const timeout = setTimeout(
    () => controller.abort(new DOMException('Request timeout', 'TimeoutError')),
    15000,
  )
  try {
    const response = await fetch(`${import.meta.env.VITE_API_BASE_URL || ''}/api${path}`, {
      method: options.method || 'GET',
      signal: controller.signal,
      headers: {
        Accept: 'application/json',
        ...(options.body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    })
    const envelope = (await response.json().catch(() => null)) as ApiEnvelope<T> | null
    const currentToken = readSession()?.token
    if (response.status === 401 && options.auth !== false && (!currentToken || currentToken === token))
      window.dispatchEvent(new Event('hr:unauthorized'))
    if (response.status === 403) window.dispatchEvent(new Event('hr:forbidden'))
    if (!response.ok && !(options.acceptUnavailable && response.status === 503 && envelope?.data)) {
      const fallback =
        response.status === 401
          ? '登录已失效，请重新登录'
          : response.status === 409
            ? '数据冲突，请检查唯一编号或关联记录'
            : response.status >= 500
              ? '服务暂时不可用，请稍后重试'
              : '请求失败，请重试'
      const detail = envelope?.message
      throw new ApiError(
        response.status,
        detail && messages[detail] ? messages[detail] : response.status === 400 && detail ? detail : fallback,
      )
    }
    if (!envelope || typeof envelope.code !== 'number' || !('data' in envelope))
      throw new ApiError(502, '服务返回了无法识别的数据')
    return envelope.data
  } catch (error) {
    if (error instanceof ApiError || options.signal?.aborted) throw error
    throw new ApiError(
      0,
      controller.signal.aborted ? '请求超时，请重试' : '无法连接服务，请检查后端是否已启动',
    )
  } finally {
    clearTimeout(timeout)
    options.signal?.removeEventListener('abort', abort)
  }
}
export function query(params: Record<string, string | number | undefined | null>) {
  const result = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') result.set(key, String(value))
  })
  return result.toString() ? `?${result}` : ''
}
