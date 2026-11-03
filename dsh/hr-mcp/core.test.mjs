import { test } from 'node:test'
import assert from 'node:assert/strict'
import { agentMcpConfig } from './core.mjs'

test('each DSH agent gets an isolated MCP proxy URL with the internal credential', () => {
  const config = { proxyUrl: 'http://127.0.0.1:3080/internal/mcp', brokerToken: 'broker-secret' }
  const first = agentMcpConfig(config, 'session-first')
  const second = agentMcpConfig(config, 'session-second')
  assert.notEqual(first.url, second.url)
  assert.equal(new URL(first.url).searchParams.get('agent'), 'session-first')
  assert.equal(new URL(second.url).searchParams.get('agent'), 'session-second')
  assert.equal(first.headers['x-hr-broker-token'], 'broker-secret')
  assert.equal(first.serverName, 'hr')
  assert.equal(first.failOnStartupError, false)
})
