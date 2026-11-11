/** 生成 Agent 独立 MCP 连接配置，并把 Agent ID 写入代理地址。 */
export function agentMcpConfig(config, agentId) {
  const url = new URL(config.proxyUrl)
  url.searchParams.set('agent', agentId)
  return {
    serverName: 'hr',
    transport: 'streamable-http',
    url: url.toString(),
    headers: { 'x-hr-broker-token': config.brokerToken },
    toolCallTimeoutMs: 120000,
    failOnStartupError: false,
  }
}
