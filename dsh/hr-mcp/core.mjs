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
