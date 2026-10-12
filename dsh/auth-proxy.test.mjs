import { test } from 'node:test'
import assert from 'node:assert/strict'
import http from 'node:http'
import net from 'node:net'
import { once } from 'node:events'
import { createAuthProxy } from './auth-proxy.mjs'

function token(id, expiresAt = Date.now() + 60000) {
  return `test.${Buffer.from(JSON.stringify({ sub: id, exp: expiresAt / 1000 })).toString('base64url')}.signature`
}

async function fixture(t, options = {}) {
  let active = true
  let calls = 0
  const upstream = http.createServer((req, res) => {
    if (req.url.startsWith('/?token=')) {
      res.writeHead(302, { 'set-cookie': [`dsh-auth-test=session-${++calls}; HttpOnly`] })
      res.end()
    } else res.end(req.headers.cookie)
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
    fetchImpl: async () => new Response(JSON.stringify({ data: { roleCode: active ? 'ADMIN' : 'READER' } })),
    revalidateMs: 20,
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
  }
}

test('arbitrary DSH cookies cannot bypass HR authentication', async (t) => {
  const { base } = await fixture(t)
  const response = await fetch(base, { headers: { Cookie: 'dsh-auth-forged=anything' } })
  assert.equal(response.status, 401)
})

test('each HR session gets separate upstream cookies and is revoked after demotion', async (t) => {
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
