import http from 'node:http'
import fs from 'node:fs'
import net from 'node:net'

const upstreamPort = 3081
const tokenPath = '/dsh-home/launch-token'
const hrApi = process.env.HR_API_BASE || 'http://host.docker.internal:8081'
const cookieCache = new Map()

function launchToken() {
  return fs.readFileSync(tokenPath, 'utf8').trim()
}

function waitForToken() {
  return new Promise((resolve) => {
    const timer = setInterval(() => {
      if (fs.existsSync(tokenPath) && launchToken()) {
        clearInterval(timer)
        resolve()
      }
    }, 200)
  })
}

function hasSession(req) {
  return (req.headers.cookie || '').includes('dsh-auth-')
}

function allowOrigin(origin) {
  if (!origin) return null
  try {
    const url = new URL(origin)
    if (url.protocol !== 'http:' && url.protocol !== 'https:') return null
    if (url.hostname === '127.0.0.1' || url.hostname === 'localhost' || url.hostname === '[::1]') return origin
  } catch {
    return null
  }
  return null
}

function bearer(req) {
  const header = req.headers.authorization || ''
  return header.startsWith('Bearer ') ? header : ''
}

async function isHrAdmin(authorization) {
  if (!authorization) return false
  const response = await fetch(`${hrApi}/api/auth/me`, {
    headers: { Authorization: authorization, Accept: 'application/json' },
  })
  if (!response.ok) return false
  const body = await response.json()
  const user = body?.data
  return user?.roleCode === 'ADMIN' && user?.enabled !== false
}

function cookieFor(host) {
  const cached = cookieCache.get(host)
  if (cached) return cached
  const pending = new Promise((resolve, reject) => {
    const request = http.request({
      host: '127.0.0.1',
      port: upstreamPort,
      path: `/?token=${encodeURIComponent(launchToken())}`,
      method: 'GET',
      headers: { host },
    }, (response) => {
      response.resume()
      const setCookie = response.headers['set-cookie']
      if (!setCookie) {
        reject(new Error('dsh did not issue a session cookie'))
        return
      }
      resolve(Array.isArray(setCookie) ? setCookie : [setCookie])
    })
    request.on('error', reject)
    request.end()
  }).catch((error) => {
    cookieCache.delete(host)
    throw error
  })
  cookieCache.set(host, pending)
  return pending
}

function cookieHeader(setCookie) {
  return setCookie.map((item) => item.split(';')[0]).join('; ')
}

function writeCors(req, res) {
  const origin = allowOrigin(req.headers.origin)
  if (!origin) return
  res.setHeader('Access-Control-Allow-Origin', origin)
  res.setHeader('Access-Control-Allow-Credentials', 'true')
  res.setHeader('Access-Control-Allow-Headers', 'Authorization, Content-Type')
  res.setHeader('Vary', 'Origin')
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url || '/', 'http://127.0.0.1:3080')
  if (url.pathname === '/hr-session') {
    writeCors(req, res)
    if (req.method === 'OPTIONS') {
      res.writeHead(204)
      res.end()
      return
    }
    if (req.method !== 'POST' || !(await isHrAdmin(bearer(req)))) {
      res.writeHead(401, { 'content-type': 'text/plain; charset=utf-8', 'cache-control': 'no-store' })
      res.end('authentication required')
      return
    }
    try {
      const setCookie = await cookieFor(req.headers.host || '127.0.0.1:3080')
      res.writeHead(204, { 'set-cookie': setCookie, 'cache-control': 'no-store' })
      res.end()
    } catch {
      res.writeHead(503, { 'content-type': 'text/plain; charset=utf-8', 'cache-control': 'no-store' })
      res.end('DSH 正在启动，请稍后再打开。')
    }
    return
  }

  if (!hasSession(req)) {
    res.writeHead(401, { 'content-type': 'text/plain; charset=utf-8', 'cache-control': 'no-store' })
    res.end('authentication required')
    return
  }

  const headers = { ...req.headers, host: req.headers.host || '127.0.0.1:3080' }
  const upstream = http.request({
    host: '127.0.0.1',
    port: upstreamPort,
    method: req.method,
    path: req.url,
    headers,
  }, (upstreamResponse) => {
    res.writeHead(upstreamResponse.statusCode || 502, upstreamResponse.headers)
    upstreamResponse.pipe(res)
  })
  upstream.on('error', () => {
    if (!res.headersSent) res.writeHead(502, { 'content-type': 'text/plain; charset=utf-8' })
    res.end('DSH 暂时不可用。')
  })
  req.pipe(upstream)
})

server.on('upgrade', (req, socket, head) => {
  if (!hasSession(req)) {
    socket.destroy()
    return
  }
  const upstream = net.connect(upstreamPort, '127.0.0.1', () => {
    const lines = [`${req.method} ${req.url} HTTP/1.1`]
    for (const [name, value] of Object.entries(req.headers)) {
      if (value == null) continue
      const items = Array.isArray(value) ? value : [value]
      for (const item of items) lines.push(`${name}: ${item}`)
    }
    upstream.write(`${lines.join('\r\n')}\r\n\r\n`)
    if (head.length) upstream.write(head)
    upstream.pipe(socket)
    socket.pipe(upstream)
  })
  upstream.on('error', () => socket.destroy())
})

await waitForToken()
server.listen(3080, '0.0.0.0')
