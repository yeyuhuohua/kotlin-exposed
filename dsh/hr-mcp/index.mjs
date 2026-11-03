import { createRequire } from 'node:module'
import { agentMcpConfig } from './core.mjs'

// 从 DSH 安装锚点解析官方客户端，避免 profile 自己的 node_modules 找不到它。
const dshRequire = createRequire('/usr/local/lib/node_modules/@deepseek-ai/dsh/package.json')
const { apply: applyMcp } = await import(dshRequire.resolve('@deepseek-ai/dsh-mcp-client'))

export const name = 'hr-mcp'

/** 每个 Agent 使用独立 MCP 连接；代理由会话 ID 动态选择当前用户令牌。 */
export function apply(ctx, config) {
  ctx.on('agent/created', async ({ agent }) => {
    try {
      await applyMcp(agent.ctx, agentMcpConfig(config, agent.session.id))
    } catch (error) {
      ctx.logger.error(`HR MCP 初始化失败: ${String(error)}`)
    }
  })
}
