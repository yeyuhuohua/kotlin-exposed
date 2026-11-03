import { test } from 'node:test'
import assert from 'node:assert/strict'
import http from 'node:http'
import net from 'node:net'
import { once } from 'node:events'
import { createAuthProxy } from './auth-proxy.mjs'
import { migrateProfile } from './migrate-profile.mjs'

function token(id, expiresAt = Date.now() + 60000) {
  return `test.${Buffer.from(JSON.stringify({ sub: id, exp: expiresAt / 1000 })).toString('base64url')}.signature`
}

async function fixture(t, options = {}) {
  let active = true
  let calls = 0
  let nextUserId = 0
  let mcpAuthorization = ''
  const userIds = new Map()
  const upstream = http.createServer((req, res) => {
    if (req.url.startsWith('/?token=')) {
      res.writeHead(302, { 'set-cookie': [`dsh-auth-test=session-${++calls}; HttpOnly`] })
      res.end()
      return
    }
    if (req.url === '/mcp') mcpAuthorization = req.headers.authorization || ''
    if (req.method !== 'POST') {
      res.end(req.headers.cookie)
      return
    }
    const chunks = []
    req.on('data', (chunk) => chunks.push(chunk))
    req.on('end', () => {
      if (req.url === '/mcp') {
        res.end('mcp-ok')
        return
      }
      try {
        const message = JSON.parse(Buffer.concat(chunks).toString('utf8'))
        res.writeHead(200, { 'content-type': 'application/json' })
        res.end(JSON.stringify(message))
      } catch {
        res.writeHead(400)
        res.end()
      }
    })
  })
  upstream.on('upgrade', (req, socket) => {
    socket.on('error', () => {})
    socket.write('HTTP/1.1 101 Switching Protocols\r\nConnection: Upgrade\r\nUpgrade: websocket\r\n\r\n')
  })
  upstream.listen(0, '127.0.0.1')
  await once(upstream, 'listening')
  const proxy = createAuthProxy({
    upstreamPort: upstream.address().port,
    launchToken: () => 'test-launch-token',
    fetchImpl: async (_url, init) => {
      const authorization = init.headers.Authorization
      if (!userIds.has(authorization)) userIds.set(authorization, ++nextUserId)
      return new Response(JSON.stringify({
        data: { id: userIds.get(authorization), roleCode: 'READER', enabled: active },
      }))
    },
    revalidateMs: 20,
    brokerToken: 'test-broker-token',
    mcpUpstream: `http://127.0.0.1:${upstream.address().port}/mcp`,
    ...options,
  })
  proxy.listen(0, '127.0.0.1')
  await once(proxy, 'listening')
  t.after(() => {
    proxy.closeAllConnections()
    proxy.close()
    upstream.closeAllConnections()
    upstream.close()
  })
  const base = `http://127.0.0.1:${proxy.address().port}`
  return {
    base,
    proxy,
    revoke: () => { active = false },
    exchange: async (jwt = token('admin'), scheme = 'Bearer') => {
      const response = await fetch(`${base}/hr-session`, {
        method: 'POST', headers: { Authorization: `${scheme} ${jwt}` },
      })
      assert.equal(response.status, 204)
      return response.headers.get('set-cookie').split(';')[0]
    },
    bindAgent: async (cookie, agentId) => {
      const response = await fetch(`${base}/api/session/create`, {
        method: 'POST',
        headers: { Cookie: cookie, 'Content-Type': 'application/json' },
        body: JSON.stringify({
          type: 'client-request',
          rpcId: 'rpc-test',
          method: 'session/create',
          payload: { args: { request: agentId ? { sessionId: agentId } : {} } },
        }),
      })
      const message = response.status === 200 ? await response.json() : null
      return { response, agentId: message?.payload?.args?.request?.sessionId }
    },
    callMcp: (agentId, secret = 'test-broker-token') => fetch(`${base}/internal/mcp?agent=${encodeURIComponent(agentId)}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-hr-broker-token': secret },
      body: '{}',
    }),
    mcpAuthorization: () => mcpAuthorization,
  }
}

test('arbitrary DSH cookies cannot bypass HR authentication', async (t) => {
  const { base } = await fixture(t)
  const response = await fetch(base, { headers: { Cookie: 'dsh-auth-forged=anything' } })
  assert.equal(response.status, 401)
})

test('ordinary active users can use DSH and separate sessions are revoked after disablement', async (t) => {
  const f = await fixture(t)
  const first = await f.exchange(token('first'))
  const second = await f.exchange(token('second'))
  assert.notEqual(first, second)
  const a = await fetch(f.base, { headers: { Cookie: first } })
  const b = await fetch(f.base, { headers: { Cookie: second } })
  assert.notEqual(await a.text(), await b.text())
  assert.equal(a.headers.get('set-cookie'), null)
  f.revoke()
  assert.equal((await fetch(f.base, { headers: { Cookie: first } })).status, 401)
})

test('expired tokens never get a proxy session', async (t) => {
  const { base } = await fixture(t)
  const response = await fetch(`${base}/hr-session`, {
    method: 'POST', headers: { Authorization: `Bearer ${token('expired', Date.now() - 1000)}` },
  })
  assert.equal(response.status, 401)
})

test('Bearer authentication schemes are case insensitive and other schemes are rejected', async (t) => {
  const f = await fixture(t)
  for (const scheme of ['Bearer', 'bearer', 'bEaReR']) {
    const cookie = await f.exchange(token('admin'), scheme)
    assert.equal((await fetch(f.base, { headers: { Cookie: cookie } })).status, 200)
  }
  assert.equal((await fetch(`${f.base}/hr-session`, {
    method: 'POST', headers: { Authorization: `Basic ${token('admin')}` },
  })).status, 401)
})

test('concurrent session validations share one upstream request', async (t) => {
  let validations = 0
  const f = await fixture(t, {
    fetchImpl: async () => {
      validations++
      await new Promise((resolve) => setTimeout(resolve, 20))
      return new Response(JSON.stringify({ data: { id: 1, enabled: true } }))
    },
  })
  const cookie = await f.exchange()
  await Promise.all(Array.from({ length: 6 }, () => fetch(f.base, { headers: { Cookie: cookie } })))
  assert.equal(validations, 2)
})

test('HR network and malformed JSON errors return 503 without killing the proxy', async (t) => {
  let failure = 'network'
  const { base } = await fixture(t, {
    fetchImpl: async () => {
      if (failure === 'network') throw new Error('connection refused')
      return new Response('invalid-json')
    },
  })
  for (const mode of ['network', 'json']) {
    failure = mode
    assert.equal((await fetch(`${base}/hr-session`, {
      method: 'POST', headers: { Authorization: `Bearer ${token('admin')}` },
    })).status, 503)
    assert.equal((await fetch(base)).status, 401)
  }
})

test('HR validation has a bounded timeout', async (t) => {
  const { base } = await fixture(t, {
    timeoutMs: 30,
    fetchImpl: async (_url, { signal }) => new Promise((_resolve, reject) => {
      signal.addEventListener('abort', () => reject(signal.reason), { once: true })
    }),
  })
  assert.equal((await fetch(`${base}/hr-session`, {
    method: 'POST', headers: { Authorization: `Bearer ${token('admin')}` },
  })).status, 503)
})

test('an established websocket closes after HR authorization is revoked', { timeout: 2000 }, async (t) => {
  const f = await fixture(t)
  const cookie = await f.exchange()
  const socket = net.connect(f.proxy.address().port, '127.0.0.1')
  t.after(() => socket.destroy())
  await once(socket, 'connect')
  socket.write(`GET /ws HTTP/1.1\r\nHost: localhost\r\nConnection: Upgrade\r\nUpgrade: websocket\r\nCookie: ${cookie}\r\n\r\n`)
  const [data] = await once(socket, 'data')
  assert.match(data.toString(), /101 Switching Protocols/)
  const closed = once(socket, 'close')
  f.revoke()
  await closed
})

test('untrusted origins cannot create a session', async (t) => {
  const { base } = await fixture(t)
  assert.equal((await fetch(`${base}/hr-session`, {
    method: 'POST', headers: { Authorization: `Bearer ${token('admin')}`, Origin: 'https://untrusted.example' },
  })).status, 403)
})

test('MCP proxy injects the current user JWT and requires the internal broker credential', async (t) => {
  const f = await fixture(t)
  const jwt = token('reader')
  const cookie = await f.exchange(jwt)
  const { response, agentId } = await f.bindAgent(cookie)
  assert.equal(response.status, 200)
  assert.match(agentId, /^session-/)
  assert.equal((await f.callMcp(agentId)).status, 200)
  assert.equal(f.mcpAuthorization(), `Bearer ${jwt}`)
  assert.equal((await f.callMcp(agentId, 'wrong-token')).status, 403)
  assert.equal((await f.callMcp('session-unknown')).status, 401)
})

test('an active DSH agent cannot be taken over by another HR account', async (t) => {
  const f = await fixture(t)
  const firstToken = token('first-user')
  const first = await f.exchange(firstToken)
  const second = await f.exchange(token('second-user'))
  const created = await f.bindAgent(first, 'session-shared')
  assert.equal(created.response.status, 200)
  const conflict = await f.bindAgent(second, 'session-shared')
  assert.equal(conflict.response.status, 403)
  assert.equal((await f.callMcp('session-shared')).status, 200)
  assert.equal(f.mcpAuthorization(), `Bearer ${firstToken}`)
})

test('legacy fixed-token MCP profile migrates once and modified legacy config fails closed', () => {
  const legacy = `- insert:
    - id: mcp-hr
      name: '@deepseek-ai/dsh-mcp-client'
      config:
        serverName: hr
        transport: streamable-http
        url: http://host.docker.internal:8081/mcp
        headers:
          Authorization: !!js '\`Bearer \${process.env.HR_MCP_TOKEN}\`'
        toolCallTimeoutMs: 120000
        failOnStartupError: false
`
  const replacement = `- insert:
    - id: mcp-hr
      name: '@hr/mcp'
`
  const migrated = migrateProfile(legacy, replacement)
  assert.equal(migrated, replacement)
  assert.equal(migrateProfile(migrated, replacement), migrated)
  assert.throws(() => migrateProfile(legacy.replace('8081', '9090'), replacement), /无法安全迁移/)
  const combined = migrateProfile(`${legacy}\n${replacement}`, replacement)
  assert.doesNotMatch(combined, /@deepseek-ai\/dsh-mcp-client/)
  assert.match(combined, /@hr\/mcp/)
})
