import http from 'node:http'
import fs from 'node:fs'
import net from 'node:net'
import { randomUUID } from 'node:crypto'
import { pathToFileURL } from 'node:url'

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

/** HR token stays on the proxy; browsers receive only an opaque, HttpOnly session ID. */
export function createAuthProxy({
  hrApi = process.env.HR_API_BASE || 'http://host.docker.internal:8081',
  upstreamPort = 3081,
  launchToken = () => fs.readFileSync('/dsh-home/launch-token', 'utf8').trim(),
  fetchImpl = fetch,
  timeoutMs = 5000,
  revalidateMs = 3000,
} = {}) {
  const sessions = new Map()

  function revoke(id) {
    const session = sessions.get(id)
    sessions.delete(id)
    for (const socket of session?.sockets || []) socket.destroy()
  }

  async function isAdmin(authorization) {
    if (!authorization) return false
    const response = await fetchImpl(`${hrApi}/api/auth/me`, {
      headers: { Authorization: authorization, Accept: 'application/json' },
      signal: AbortSignal.timeout(timeoutMs),
    })
    if (!response.ok) {
      if (response.status >= 500) throw new Error('HR unavailable')
      return false
    }
    const body = await response.json()
    return body?.data?.roleCode === 'ADMIN' && body.data.enabled !== false
  }

  async function authorizedSession(req) {
    const id = sessionId(req)
    const session = sessions.get(id)
    if (!session) return null
    if (session.expiresAt <= Date.now() || !(await isAdmin(session.authorization))) {
      revoke(id)
      return null
    }
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
    if (!res.headersSent) res.writeHead(status, {
      'content-type': 'text/plain; charset=utf-8', 'cache-control': 'no-store',
    })
    res.end(status === 401 ? 'authentication required' : 'service unavailable')
  }

  const server = http.createServer((req, res) => {
    handle(req, res).catch(() => fail(res, 503))
  })

  async function handle(req, res) {
    const url = new URL(req.url || '/', 'http://127.0.0.1:3080')
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
      if (req.method !== 'POST' || !(await isAdmin(authorization))) return fail(res, 401)
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
      if (sessions.size >= 1000) return fail(res, 503)
      const id = randomUUID()
      sessions.set(id, { authorization, expiresAt, cookie, sockets: new Set() })
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
    const headers = { ...req.headers, cookie: session.cookie }
    delete headers.authorization
    url.searchParams.delete('token')
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
    req.pipe(upstream)
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
