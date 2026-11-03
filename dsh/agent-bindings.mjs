import { randomUUID } from 'node:crypto'

export function createAgentBindings() {
  const byAgent = new Map()

  function bind(agentId, session) {
    if (typeof agentId !== 'string' || !agentId || agentId.length > 200) {
      throw Object.assign(new Error('invalid agent session'), { status: 400 })
    }
    const current = byAgent.get(agentId)
    if (current && current.id !== session.id) {
      if (current.expiresAt > Date.now() && current.userId !== session.userId) {
        throw Object.assign(new Error('session owned by another user'), { status: 403 })
      }
      current.agentIds.delete(agentId)
    }
    byAgent.set(agentId, session)
    session.agentIds.add(agentId)
  }

  function resolve(agentId) {
    return byAgent.get(agentId)
  }

  function revoke(session) {
    for (const agentId of session.agentIds) {
      if (byAgent.get(agentId) === session) byAgent.delete(agentId)
    }
    session.agentIds.clear()
  }

  return { bind, resolve, revoke }
}

/**
 * 在 DSH 处理 RPC 前绑定会话；创建请求缺少 ID 时由代理提前分配，
 * 否则 Agent 初始化 MCP 时还不知道该使用哪个 HR 会话。
 */
export async function bindRpcRequest(req, session, bindings, maxBytes = 1_048_576) {
  if (req.method !== 'POST' || !req.url?.startsWith('/api/')) return null
  if (!req.headers['content-type']?.toLowerCase().startsWith('application/json')) return null
  const declared = Number(req.headers['content-length'])
  if (Number.isFinite(declared) && declared > maxBytes) return null

  const chunks = []
  let size = 0
  for await (const chunk of req) {
    size += chunk.length
    if (size > maxBytes) throw Object.assign(new Error('request too large'), { status: 413 })
    chunks.push(chunk)
  }
  const body = Buffer.concat(chunks)
  let message
  try {
    message = JSON.parse(body.toString('utf8'))
  } catch {
    return { body }
  }
  const args = message?.payload?.args
  if (!args || typeof args !== 'object' || Array.isArray(args)) return { body }
  const request = args.request && typeof args.request === 'object' && !Array.isArray(args.request)
    ? args.request
    : args
  let modified = false
  if (message.method === 'session/create' && request.sessionId === undefined) {
    request.sessionId = `session-${randomUUID()}`
    modified = true
  }
  if (request.sessionId !== undefined) bindings.bind(request.sessionId, session)
  return { body: modified ? Buffer.from(JSON.stringify(message)) : body }
}
