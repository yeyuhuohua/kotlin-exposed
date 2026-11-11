const sessionCookie = 'hr-dsh-session'

/** 从请求 Cookie 中提取不透明 HR 会话 ID。 */
export function sessionId(req) {
  return (req.headers.cookie || '').split(';').map((part) => part.trim())
    .find((part) => part.startsWith(`${sessionCookie}=`))?.slice(sessionCookie.length + 1)
}

/** 创建带身份复核和撤销能力的内存会话存储。 */
export function createSessionStore({ hrApi, fetchImpl, timeoutMs, agentBindings }) {
  const sessions = new Map()
  const validations = new Map()

  /** 撤销指定会话及其全部 Agent 绑定。 */
  function revoke(id) {
    const session = sessions.get(id)
    sessions.delete(id)
    if (!session) return
    agentBindings.revoke(session)
    for (const socket of session.sockets || []) socket.destroy()
  }

  /** 使用当前 JWT 向 Spring 复核账号是否仍然有效。 */
  async function activeUser(authorization) {
    if (!authorization) return null
    const existing = validations.get(authorization)
    if (existing) return existing
    // 并发请求共享一次身份校验，不延长有效期，也不增加撤销感知时间。
    const pending = (async () => {
      const response = await fetchImpl(`${hrApi}/api/auth/me`, {
        headers: { Authorization: authorization, Accept: 'application/json' },
        signal: AbortSignal.timeout(timeoutMs),
      })
      if (!response.ok) {
        if (response.status >= 500) throw new Error('HR unavailable')
        return null
      }
      const body = await response.json()
      const user = body?.data
      return Number.isInteger(user?.id) && user.enabled !== false ? user : null
    })()
    validations.set(authorization, pending)
    try {
      return await pending
    } finally {
      if (validations.get(authorization) === pending) validations.delete(authorization)
    }
  }

  /** 返回未过期且身份复核通过的原会话。 */
  async function authorizedSession(req) {
    const id = sessionId(req)
    const session = sessions.get(id)
    if (!session) return null
    if (session.expiresAt <= Date.now() || !(await activeUser(session.authorization))) {
      revoke(id)
      return null
    }
    // 等待 /auth/me 返回期间会话可能已被撤销；只能返回仍然有效的原会话。
    return sessions.get(id) === session ? session : null
  }

  return { sessions, activeUser, authorizedSession, revoke }
}
