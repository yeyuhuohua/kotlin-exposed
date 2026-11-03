import fs from 'node:fs'
import { pathToFileURL } from 'node:url'

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

export function migrateProfile(source, replacement) {
  const migrated = source.includes(legacy)
    ? source.replace(legacy, source.includes("name: '@hr/mcp'") ? '' : replacement)
    : source
  if (migrated.includes('id: mcp-hr') && migrated.includes("name: '@deepseek-ai/dsh-mcp-client'")) {
    throw new Error('mcp-hr 配置已被修改，无法安全迁移；请移除旧的固定口令配置后重试')
  }
  if (migrated.includes("name: '@hr/mcp'")) return migrated
  return `${migrated.trimEnd()}\n${replacement}`
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [target, seed] = process.argv.slice(2)
  const source = fs.readFileSync(target, 'utf8')
  const replacement = fs.readFileSync(seed, 'utf8')
  const migrated = migrateProfile(source, replacement)
  if (migrated !== source) fs.writeFileSync(target, migrated)
}
