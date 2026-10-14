# DSH 对话助手

将 [DeepSeek Harness](https://www.npmjs.com/package/@deepseek-ai/dsh) 的 Web UI 装入容器，
通过 `auth-proxy.mjs` 接入 HR 登录态，通过 Spring MCP 调用 HR 工具，默认模型提供方为 LM Studio。
仓库固定 DSH `0.2.0-rc.2`。本文件的 Compose 命令在 `dsh/` 下执行，另行标注的测试命令在仓库根目录执行。

## 请求链路与访问条件

```text
浏览器 HR 前端 -> 3080 登录态代理 -> 容器内 DSH Web 3081
                    |                    |-> LM Studio 1234/v1
                    |                    |-> Spring MCP 8081/mcp
                    |-> Spring 8081/api/auth/me
```

前端“对话助手”页先用当前 HR Bearer JWT 调用代理 `/hr-session`，仅 ADMIN 可换取 HttpOnly Cookie，
再通过 iframe 打开 DSH。直接访问 3080 而没有该 Cookie 会返回 401，DSH 启动令牌不能绕过 HR 校验。
浏览器收到的是不透明会话 ID，HR JWT 与 DSH 上游 Cookie 保留在代理内存中。

每个 HR 会话使用独立代理 Cookie 和上游 Cookie；每次 HTTP 请求重新检查管理员身份。
WebSocket 默认每 3 秒复核，并在 JWT 到期时关闭。退出登录、停用或降权会使访问被撤销，
实际感知时间还受 HR 鉴权短 TTL 和请求耗时影响。HR 校验超时为 5 秒，故障时拒绝访问。
容器重启后代理内存会话失效，需要重新打开 HR 助手页换取 Cookie；已持久化对话不会因此删除。
同一 HR Token 再次兑换代理会话时，会撤销此前使用该 Token 建立的代理会话及连接。

HR 登录态校验和模型调用工具是两条鉴权链：前者使用浏览器用户 JWT，后者默认使用 MCP 固定口令。
模型实际执行身份是 Spring `hr.auth.admin-username` 指定的管理员，不是每个浏览器用户。
HR Cookie 隔离不等于为每个管理员建立独立的 DSH 数据目录；本服务共用 `home/` 挂载卷。

## 首次启动

需要 Docker / Compose、已启动的 [Spring 后端](../backend-spring/README.md)，以及模型服务。

1. 在 LM Studio 加载目标模型并启动兼容 OpenAI 的 API 服务，默认端口 1234。
2. 确保容器能访问模型服务；仅绑定主机回环地址时，`host.docker.internal` 不一定能连通，需配置合适的监听地址和主机防火墙。
3. 在 Spring 设置 `hr.mcp.service-token`，确认配置管理员已启用且属于 ADMIN。
4. 在 `dsh/` 首次准备 `.env`，填写与 Spring 一致的固定口令。

```sh
cp .env.example .env
docker compose up --build -d
docker compose ps
docker compose logs --tail=100 dsh
```

已有 `.env` 不要覆盖。示例固定口令仅供本机联调，部署时换成独立生成的强随机值。
Compose 默认只向主机回环发布 3080，不发布容器内的 3081。
代理或 DSH 进程退出都会结束容器，再由 `restart: unless-stopped` 恢复完整服务。

前端在 `.env.local` 设置 `API_PROXY_TARGET=http://127.0.0.1:8081`，保持
`VITE_DSH_URL=http://127.0.0.1:3080`，重启 Vite 后用 ADMIN 登录并打开助手。
本机浏览器统一使用 `127.0.0.1`，避免 HR 页面用 `localhost`、助手用 `127.0.0.1` 导致 Cookie 站点不一致。

## 环境变量和模型配置

| 设置 | 默认值 | 作用 |
| --- | --- | --- |
| `LM_STUDIO_API_KEY` | `lm-studio` | 模型 API 凭据；本机未开启校验时仍需非空值 |
| `HR_MCP_TOKEN` | `local-dsh-mcp-token` | 注入 MCP 请求的 Bearer 固定口令，与 Spring 保持一致 |
| `HR_API_BASE` | `http://host.docker.internal:8081` | 代理检查 HR 身份的服务地址，自动追加 `/api/auth/me` |
| `DSH_SECURE_COOKIE` | `false` | 设为 `true` 时代理 Cookie 带 Secure，HTTPS 部署使用 |
| `DSH_HOME` | 容器内 `/dsh-home` | 运行数据目录，对应主机 `dsh/home/` |
| `VITE_DSH_URL` | 前端默认 `http://127.0.0.1:3080` | 浏览器访问地址，在前端配置并在构建时注入 |

`HR_API_BASE` 只控制登录态代理，MCP 地址单独位于 Web profile 的 `mcp-hr` 配置中，
初始值为 `http://host.docker.internal:8081/mcp`。修改 Spring 端口、部署前缀或 MCP 路径后，两处都要修改。
修改 Compose 环境变量后重新执行 `docker compose up -d`，不能只依赖 `docker compose restart` 更新环境。

默认模型模板在 [seed/settings.yaml](seed/settings.yaml)：

| 参数 | 模板值 |
| --- | --- |
| provider / model | `lm-studio` / `qwen/qwen3.6-35b-a3b` |
| API 地址 | `http://host.docker.internal:1234/v1` |
| API 兼容方式 | `openai-completions`，输出字段 `max_tokens`，不发送 developer role |
| `contextWindow` | 262144 token |
| `maxTokens` | 8192 token |

模型 ID 必须与 LM Studio 实际提供的 ID 一致；DSH 声明的上下文容量必须匹配模型实际加载的容量。
改模型时同时核对 provider、模型 ID、上下文容量和输出上限，不要把模板的 262144 当成所有模型都支持的值。
对话与 HR 工具返回值会发送给所选模型提供方，切换远程模型时应据此安排数据访问范围。

## 配置播种、迁移与持久化

入口只在相应文件缺失时，将 `seed/settings.yaml` 和 `seed/profiles/web/cordis.patch.yml` 复制到 `home/`。
`seed/` 是镜像中的初始化模板，不是运行时实时读取的配置；重建镜像不会覆盖已有的 Web profile 补丁。

DSH `0.2.0-rc.2` 可以将旧式 `settings.yaml` 导入 profile 配置，并将原文件保留为
`settings.yaml.imported`。运行时可能因此不再有 `home/settings.yaml`。
调整已有安装时应核对 DSH 设置页以及 `home/profiles/web/cordis.patch.yml` 中生成的配置，
不能仅修改 `.imported` 文件并期待它生效。编辑 profile 时保留 `mcp-hr` 及已有插件项。
备份和升级前应保留整个 `home/`，而不是只保存最初的设置文件。

`home/` 持久化对话、profile、存储和启动令牌，被 Git 忽略。
备份时先 `docker compose stop`，复制到受保护的备份位置后用 `docker compose start` 恢复，避免并发写入。
`docker compose down` 不会删除该主机挂载目录；主动删除 `home/` 会丢失保存的对话和配置。
日志中的启动令牌会被入口脱敏，仍不应公开分享原始运行目录或用户对话数据。

## 上下文自动压缩

Standard、PTC 和 Cordis 代理预设包含自动压缩插件及 `/compact` 命令；Minimal 预设没有它们。
Web host 层关闭基础插件后，由每个预设加载自己的压缩插件，不能仅凭 host 配置里的 `disabled: true` 判断功能关闭。

启用压缩的预设默认在每个代理步骤前检查 token 压力，接近上限时先缩减较大的工具结果，必要时把较早对话归纳为摘要，
近期对话保留原文。摘要取代的是模型随后看到的上下文，完整历史仍保存在会话日志里。

默认触发阈值为 `floor(min(W × 0.8, W − O − 65536))`，其中 `W` 是声明的上下文容量，
`O` 是有效输出预留。保留近期原文的默认预算为 `floor((W − O) × 0.16)`。
按模板的 262144 上下文和 8192 输出预留计算，约在 **188416 token** 触发，近期原文预算约 **40632 token**。
更换模型、输出参数或压缩策略后，这些值会改变；工具结果缩减已足够时不一定生成摘要。

会话空闲时输入 `/compact` 可手动压缩；代理执行或已在压缩时需等待完成。
摘要会额外调用模型，可能遗漏细节。确认上下文溢出时默认尝试压缩并重试一次，但摘要失败、
超大的不可拆分工具调用、系统提示或工具定义过长时仍可能无法恢复，不能保证无限延长会话。

## 版本、测试与升级

`Dockerfile` 固定 `@deepseek-ai/dsh@0.2.0-rc.2`。
`brand-patch.mjs` 在构建时精确替换该版本的产品名、引导文案和图标，匹配不到会让构建失败。
升级前备份 `home/`，逐条核对第三方源字符串，并同步更新 Dockerfile 和补丁；
不能只改 npm 版本后跳过补丁检查。

在仓库根目录运行无真实 HR/模型依赖的代理测试和脚本语法检查：

```sh
node --test dsh/auth-proxy.test.mjs
sh -n dsh/docker-entrypoint.sh
```

CI 检查 Cookie 隔离、HR 授权撤销、Bearer 处理、失败/超时和 WebSocket 关闭。
镜像构建、模型连通、MCP 协议和真正长会话的压缩仍需要运行环境验证。

## 跨设备和 HTTPS 部署

浏览器中的 `127.0.0.1` 指用户自己的机器，不能用于远程部署。
部署通常使用与 HR 页面同站点的 HTTPS 助手子域名，将其根路径反向代理到 3080，并完成以下配置：

- `VITE_DSH_URL` 设置为浏览器可达的助手地址，重新构建前端；后端 `HR_API_BASE` 和 MCP URL 则按容器可达地址配置。
- `auth-proxy.mjs` 的 `allowOrigin` 目前只接受本机回环 Origin，需要显式加入 HR 前端及助手的实际部署 Origin。
- `docker-entrypoint.sh` 的 `--trusted-host` 目前只有本机地址，需要加入实际传到 DSH 的 Host（含非默认端口）。
- 网关转发 WebSocket Upgrade 并保留所需 Host；确认 DSH 的 iframe 加载、静态资源和协议连接都能通过。
- HTTPS 使用 `DSH_SECURE_COOKIE=true`。Cookie 为 `SameSite=Lax`，不同站点间嵌入可能被浏览器拦截，应使用同站点方案并验证。

当前项目没有实现通用的 DSH 子路径适配。只将 `/dsh/` 转发到 3080 并设置相对 URL，
不能保证其根路径资源、接口和 WebSocket 正常；需要额外验证和路径适配，不能作为开箱即用方案。
不要把 3081 直接暴露给浏览器，以免绕开 HR 登录态代理。

## 常见问题

| 现象 | 检查与处理 |
| --- | --- |
| Docker 无法连接 daemon | 先启动 Docker Desktop/OrbStack，确认 `docker compose ps` 可用 |
| 直接访问 3080 返回 401 | 从 HR 前端用 ADMIN 打开助手，先建立 `/hr-session` Cookie |
| 前端提示需要管理员 | 检查当前账号及角色状态，Token 是否过期或已撤销 |
| 代理返回 503 | 查看容器日志，检查 Spring 地址、管理员校验、启动令牌及上游 DSH 状态 |
| 页面加载后 WebSocket 断开 | 检查授权复核、JWT 到期、Origin、trusted host 和网关 Upgrade 转发 |
| HR 页面在线但 MCP 工具失败 | 检查独立的 MCP URL、固定口令和配置管理员，不能只检查 `HR_API_BASE` |
| 模型无法连接或不存在 | 检查 LM Studio 服务监听、容器访问地址、实际模型 ID 和凭据 |
| 上下文提前溢出 | 核对真实模型容量与 DSH `contextWindow`，检查预设是否为 Minimal；空闲时尝试 `/compact` |
| 改 seed 后旧配置没变 | 检查已有 profile 和设置导入状态，按有效配置修改并保留 MCP 项 |
| 升级时品牌补丁失败 | 对照新版本源字符串更新补丁，再构建；不要取消匹配失败检查 |
