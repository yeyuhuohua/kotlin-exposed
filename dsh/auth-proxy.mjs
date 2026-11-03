import http from 'node:http'
import https from 'node:https'
import fs from 'node:fs'
import net from 'node:net'
import { randomUUID, timingSafeEqual } from 'node:crypto'
import { pathToFileURL } from 'node:url'
import { bindRpcRequest, createAgentBindings } from './agent-bindings.mjs'

const sessionCookie = 'hr-dsh-session'

function allowOrigin(origin) {
  if (!origin) return true
  try {
    const url = new URL(origin)
    return ['http:', 'https:'].includes(url.protocol) &&
      ['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname)
  } catch {
    return false
  }
}

function sessionId(req) {
  return (req.headers.cookie || '').split(';').map((part) => part.trim())
    .find((part) => part.startsWith(`${sessionCookie}=`))?.slice(sessionCookie.length + 1)
}

function matchesSecret(actual, expected) {
  if (!expected || typeof actual !== 'string') return false
  const left = Buffer.from(actual)
  const right = Buffer.from(expected)
  return left.length === right.length && timingSafeEqual(left, right)
}

function isLoopback(address) {
  return address === '127.0.0.1' || address === '::1' || address === '::ffff:127.0.0.1'
}

/** HR 令牌只保存在代理端；浏览器只收到不透明的 HttpOnly 会话标识。 */
export function createAuthProxy({
  hrApi = process.env.HR_API_BASE || 'http://host.docker.internal:8081',
  upstreamPort = 3081,
  launchToken = () => fs.readFileSync('/dsh-home/launch-token', 'utf8').trim(),
  fetchImpl = fetch,
  timeoutMs = 5000,
  revalidateMs = 3000,
  brokerToken = process.env.HR_MCP_TOKEN || '',
  mcpUpstream = process.env.HR_MCP_URL || `${hrApi.replace(/\/+$/, '')}/mcp`,
} = {}) {
  const sessions = new Map()
  const agentBindings = createAgentBindings()

  function revoke(id) {
    const session = sessions.get(id)
    sessions.delete(id)
    if (!session) return
    agentBindings.revoke(session)
    for (const socket of session.sockets || []) socket.destroy()
  }

  async function activeUser(authorization) {
    if (!authorization) return null
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
  }

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

  function upstreamCookies(host) {
    return new Promise((resolve, reject) => {
      const request = http.request({
        host: '127.0.0.1', port: upstreamPort,
        path: `/?token=${encodeURIComponent(launchToken())}`,
        headers: { host },
      }, (response) => {
        response.resume()
        const cookies = response.headers['set-cookie']
        if (!cookies || response.statusCode >= 400) {
          reject(new Error('DSH session unavailable'))
          return
        }
        resolve(cookies.map((cookie) => cookie.split(';')[0]).join('; '))
      })
      request.setTimeout(timeoutMs, () => request.destroy(new Error('DSH timeout')))
      request.on('error', reject)
      request.end()
    })
  }

  function fail(res, status) {
    if (res.headersSent) return res.destroy()
    const messages = {
      400: 'invalid session binding',
      401: 'authentication required',
      403: 'session owned by another user',
      413: 'request too large',
      502: 'upstream unavailable',
    }
    res.writeHead(status, {
      'content-type': 'text/plain; charset=utf-8', 'cache-control': 'no-store',
    })
    res.end(messages[status] || 'service unavailable')
  }

  async function handleMcp(req, res) {
    // 该入口返回的是代理后的用户权限，只允许同容器插件使用内部口令访问。
    if (!isLoopback(req.socket.remoteAddress) ||
      !matchesSecret(req.headers['x-hr-broker-token'], brokerToken)) {
      return fail(res, 403)
    }
    const agentId = new URL(req.url || '/', 'http://127.0.0.1:3080').searchParams.get('agent')
    const bound = agentBindings.resolve(agentId)
    const session = bound && sessions.get(bound.id) === bound ? bound : null
    if (!session || session.expiresAt <= Date.now()) return fail(res, 401)

    const target = new URL(mcpUpstream)
    if (!['http:', 'https:'].includes(target.protocol)) return fail(res, 503)
    const client = target.protocol === 'https:' ? https : http
    const headers = {
      ...req.headers,
      host: target.host,
      authorization: session.authorization,
    }
    delete headers.cookie
    delete headers['x-hr-broker-token']
    const upstream = client.request(target, {
      method: req.method,
      headers,
    }, (response) => {
      res.writeHead(response.statusCode || 502, response.headers)
      response.on('error', () => res.destroy())
      response.pipe(res)
    })
    upstream.on('error', () => fail(res, 502))
    res.on('close', () => upstream.destroy())
    req.pipe(upstream)
  }

  const server = http.createServer((req, res) => {
    handle(req, res).catch((error) => fail(res, error.status || 503))
  })

  async function handle(req, res) {
    const url = new URL(req.url || '/', 'http://127.0.0.1:3080')
    if (url.pathname === '/internal/mcp') return handleMcp(req, res)
    if (url.pathname === '/hr-session') {
      const origin = req.headers.origin
      if (!allowOrigin(origin)) return fail(res, 403)
      if (origin) {
        res.setHeader('Access-Control-Allow-Origin', origin)
        res.setHeader('Access-Control-Allow-Credentials', 'true')
        res.setHeader('Access-Control-Allow-Headers', 'Authorization, Content-Type')
        res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS')
        res.setHeader('Vary', 'Origin')
      }
      if (req.method === 'OPTIONS') {
        res.writeHead(204)
        res.end()
        return
      }
      const authorization = /^Bearer /i.test(req.headers.authorization ?? '') ? req.headers.authorization : ''
      const user = req.method === 'POST' ? await activeUser(authorization) : null
      if (!user) return fail(res, 401)
      let expiresAt
      try {
        expiresAt = JSON.parse(Buffer.from(authorization.slice(7).split('.')[1], 'base64url')).exp * 1000
      } catch {
        return fail(res, 401)
      }
      if (!Number.isFinite(expiresAt) || expiresAt <= Date.now()) return fail(res, 401)
      for (const [id, session] of sessions) {
        if (session.expiresAt <= Date.now() || session.authorization === authorization) revoke(id)
      }
      if (sessions.size >= 1000) return fail(res, 503)
      const cookie = await upstreamCookies(req.headers.host || '127.0.0.1:3080')
      // 等待上游响应后再次检查，避免并发登录使会话数超过上限。
      if (sessions.size >= 1000) return fail(res, 503)
      const id = randomUUID()
      sessions.set(id, {
        id,
        userId: user.id,
        authorization,
        expiresAt,
        cookie,
        sockets: new Set(),
        agentIds: new Set(),
      })
      const secure = process.env.DSH_SECURE_COOKIE === 'true' ? '; Secure' : ''
      res.writeHead(204, {
        'set-cookie': `${sessionCookie}=${id}; Path=/; HttpOnly; SameSite=Lax; Max-Age=${Math.floor((expiresAt - Date.now()) / 1000)}${secure}`,
        'cache-control': 'no-store',
      })
      res.end()
      return
    }
    const session = await authorizedSession(req)
    if (!session) return fail(res, 401)
    const binding = await bindRpcRequest(req, session, agentBindings)
    const headers = { ...req.headers, cookie: session.cookie }
    delete headers.authorization
    url.searchParams.delete('token')
    if (binding) {
      headers['content-length'] = String(binding.body.length)
      delete headers['transfer-encoding']
    }
    const upstream = http.request({
      host: '127.0.0.1', port: upstreamPort, method: req.method,
      path: url.pathname + url.search, headers,
    }, (response) => {
      const responseHeaders = { ...response.headers }
      delete responseHeaders['set-cookie']
      res.writeHead(response.statusCode || 502, responseHeaders)
      response.on('error', () => res.destroy())
      response.pipe(res)
    })
    upstream.on('error', () => fail(res, 502))
    res.on('close', () => upstream.destroy())
    if (binding) upstream.end(binding.body)
    else req.pipe(upstream)
  }

  server.on('upgrade', (req, socket, head) => {
    socket.on('error', () => socket.destroy())
    socket.pause()
    upgrade(req, socket, head).catch(() => socket.destroy())
  })

  async function upgrade(req, socket, head) {
    const session = await authorizedSession(req)
    if (!session || !allowOrigin(req.headers.origin) || socket.destroyed) {
      socket.destroy()
      return
    }
    session.sockets.add(socket)
    const upstream = net.connect(upstreamPort, '127.0.0.1')
    let checking = false
    const timer = setInterval(async () => {
      if (checking) return
      checking = true
      try {
        if (!(await authorizedSession(req))) socket.destroy()
      } catch {
        socket.destroy()
      } finally {
        checking = false
      }
    }, revalidateMs)
    timer.unref()
    const expiryTimer = setTimeout(() => revoke(sessionId(req)), Math.max(1, session.expiresAt - Date.now()))
    expiryTimer.unref()
    socket.on('close', () => {
      clearInterval(timer)
      clearTimeout(expiryTimer)
      session.sockets.delete(socket)
      upstream.destroy()
    })
    upstream.on('error', () => socket.destroy())
    upstream.on('close', () => socket.destroy())
    upstream.setTimeout(timeoutMs, () => socket.destroy())
    upstream.on('connect', () => {
      upstream.setTimeout(0)
      if (socket.destroyed) return upstream.destroy()
      const url = new URL(req.url, 'http://127.0.0.1')
      url.searchParams.delete('token')
      const lines = [`${req.method} ${url.pathname}${url.search} HTTP/1.1`]
      const headers = { ...req.headers, cookie: session.cookie }
      delete headers.authorization
      for (const [name, value] of Object.entries(headers)) {
        if (value == null) continue
        for (const item of Array.isArray(value) ? value : [value]) lines.push(`${name}: ${item}`)
      }
      upstream.write(`${lines.join('\r\n')}\r\n\r\n`)
      if (head.length) upstream.write(head)
      upstream.pipe(socket)
      socket.pipe(upstream)
      socket.resume()
    })
  }

  server.on('close', () => { for (const id of sessions.keys()) revoke(id) })
  return server
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const timer = setInterval(() => {
    try {
      if (!fs.readFileSync('/dsh-home/launch-token', 'utf8').trim()) return
      clearInterval(timer)
      createAuthProxy().listen(3080, '0.0.0.0')
    } catch (error) {
      if (error.code !== 'ENOENT') {
        clearInterval(timer)
        throw error
      }
    }
  }, 200)
}
