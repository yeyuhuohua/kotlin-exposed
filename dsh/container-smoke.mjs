import http from 'node:http'
import { once } from 'node:events'
import { execFileSync } from 'node:child_process'
import { randomUUID } from 'node:crypto'

const image = process.env.DSH_TEST_IMAGE || 'hr-dsh-test'
const mcpAuthorizations = []
let jwt = ''
const fake = http.createServer((req, res) => {
  if (req.url === '/api/auth/me') {
    res.writeHead(200, { 'content-type': 'application/json' })
    res.end(JSON.stringify({ data: { id: 2, username: 'reader', roleCode: 'READER', enabled: true, permissions: [] } }))
    return
  }
  if (req.url === '/mcp') {
    mcpAuthorizations.push(req.headers.authorization || '')
    const chunks = []
    req.on('data', (chunk) => chunks.push(chunk))
    req.on('end', () => {
      let message
      try { message = JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}') } catch { message = {} }
      if (message.id === undefined) {
        res.writeHead(202)
        res.end()
        return
      }
      const result = message.method === 'initialize'
        ? {
            protocolVersion: message.params?.protocolVersion || '2025-03-26',
            capabilities: {},
            serverInfo: { name: 'smoke', version: '1' },
          }
        : message.method === 'tools/list' ? { tools: [] } : {}
      res.writeHead(200, { 'content-type': 'application/json' })
      res.end(JSON.stringify({ jsonrpc: '2.0', id: message.id, result }))
    })
    return
  }
  res.writeHead(404)
  res.end()
})
fake.listen(0, '0.0.0.0')
await once(fake, 'listening')
const fakePort = fake.address().port
const name = `hr-dsh-smoke-${process.pid}`
let proxyBase

async function waitFor(description, check, attempts = 60) {
  for (let i = 0; i < attempts; i++) {
    try {
      const value = await check()
      if (value) return value
    } catch {}
    await new Promise((resolve) => setTimeout(resolve, 500))
  }
  throw new Error(`等待超时: ${description}`)
}

try {
  execFileSync('docker', ['run', '-d', '--name', name,
    '--add-host', 'host.docker.internal:host-gateway',
    '--env', `HR_API_BASE=http://host.docker.internal:${fakePort}`,
    '--env', `HR_MCP_URL=http://host.docker.internal:${fakePort}/mcp`,
    '--env', 'HR_MCP_TOKEN=smoke-broker-token',
    '--env', 'LM_STUDIO_API_KEY=smoke-model-key',
    '--publish', '127.0.0.1:0:3080', image], { stdio: 'inherit' })
  const mapped = execFileSync('docker', ['port', name, '3080/tcp'], { encoding: 'utf8' }).trim()
  proxyBase = `http://127.0.0.1:${mapped.split(':').at(-1)}`
  await waitFor('DSH 代理', async () => (await fetch(proxyBase)).status === 401)

  jwt = `test.${Buffer.from(JSON.stringify({ sub: '2', exp: (Date.now() + 600000) / 1000 })).toString('base64url')}.signature`
  const exchange = await fetch(`${proxyBase}/hr-session`, {
    method: 'POST', headers: { Authorization: `Bearer ${jwt}` },
  })
  if (exchange.status !== 204) throw new Error(`HR 会话创建失败: ${exchange.status} ${await exchange.text()}`)
  const cookie = exchange.headers.get('set-cookie').split(';')[0]
  const agentId = `session-${randomUUID()}`
  const created = await fetch(`${proxyBase}/api/session/create`, {
    method: 'POST',
    headers: { Cookie: cookie, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      type: 'client-request', rpcId: 'smoke-create', method: 'session/create',
      payload: { args: { request: { sessionId: agentId } } },
    }),
  })
  if (created.status !== 200) throw new Error(`DSH Agent 创建失败: ${created.status} ${await created.text()}`)
  const createdBody = await created.json()
  if (!createdBody.result?.ok) throw new Error(`DSH Agent 创建被拒绝: ${JSON.stringify(createdBody)}`)
  await waitFor('会话级 MCP 调用', async () => mcpAuthorizations.length > 0, 80)
  if (!mcpAuthorizations.every((value) => value === `Bearer ${jwt}`)) {
    throw new Error(`MCP 身份不匹配: ${JSON.stringify(mcpAuthorizations)}`)
  }
  console.log(`容器冒烟通过：普通账号创建了 Agent，${mcpAuthorizations.length} 次 MCP 请求均携带该账号 JWT。`)
} catch (error) {
  console.error(error)
  try {
    console.error(execFileSync('docker', ['logs', '--tail', '50', name], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }))
  } catch {}
  process.exitCode = 1
} finally {
  try { execFileSync('docker', ['rm', '-f', name], { stdio: 'ignore' }) } catch {}
  fake.closeAllConnections()
  fake.close()
}
