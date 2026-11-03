import http from 'node:http'
import https from 'node:https'
import { timingSafeEqual } from 'node:crypto'

function matchesSecret(actual, expected) {
  if (!expected || typeof actual !== 'string') return false
  const left = Buffer.from(actual)
  const right = Buffer.from(expected)
  return left.length === right.length && timingSafeEqual(left, right)
}

function isLoopback(address) {
  return address === '127.0.0.1' || address === '::1' || address === '::ffff:127.0.0.1'
}

export function createMcpForwarder({ sessions, agentBindings, brokerToken, mcpUpstream, fail }) {
  return async function handleMcp(req, res) {
    // 该入口只允许同容器插件使用内部口令访问。
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
}
