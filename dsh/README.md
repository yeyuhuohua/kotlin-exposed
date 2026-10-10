# DSH 对话助手（DeepSeek Harness）

把 [DeepSeek Harness](https://www.npmjs.com/package/@deepseek-ai/dsh) 的 Web UI 装进容器，
通过 `auth-proxy.mjs` 叠加 HR 登录态：前端对话页先用 HR 的 Bearer JWT 向代理的 `/hr-session`
换会话 Cookie（仅 ADMIN 放行），再内嵌打开对话。DSH 通过 MCP 连接 Spring 后端
（`HR_API_BASE` 默认 `http://host.docker.internal:8081`），模型侧默认走本机 LM Studio。

## 本地启动

需要 Docker，以及已启动的 Spring 后端（8081）。

```sh
cd dsh
docker compose up --build -d
```

- 对话入口：<http://127.0.0.1:3080>（容器内 DSH 实际监听 3081，3080 是叠加登录态的代理）
- 首次启动把 `seed/settings.yaml`、`seed/profiles/` 播种到挂载卷 `home/`；后续修改直接改 `home/` 里的文件
- 模型提供方在 `home/settings.yaml` 配置，默认 LM Studio（`http://host.docker.internal:1234/v1`），
  API Key 取环境变量 `LM_STUDIO_API_KEY`
- `HR_MCP_TOKEN` 必须与 Spring 后端 `application.yaml` 的 `hr.mcp.service-token` 一致

前端对话页地址由 `VITE_DSH_URL` 决定（见 `frontend/.env.example`），默认同机 `http://127.0.0.1:3080`。

## 版本与品牌补丁

`Dockerfile` 固定 `@deepseek-ai/dsh@0.2.0-rc.2`。`brand-patch.mjs` 在构建期对该版本的第三方源码做
精确字符串替换（产品名、引导文案、Hero 图标），匹配不到会直接构建失败——这是有意设计：
**升级 dsh 版本前必须逐条核对补丁中的源字符串仍然存在于新版本，再同步更新两处。**

## 部署

跨设备部署时把容器的 3080 映射到目标主机，并注意：

1. `VITE_DSH_URL` 改为用户浏览器可达的地址（构建期注入，改后需重新构建前端）；
   更稳妥的做法是在网关做同源代理（如 `/dsh/` → `127.0.0.1:3080`），`VITE_DSH_URL` 留相对路径。
2. `auth-proxy.mjs` 的 `allowOrigin` 目前只放行本机回环 Origin，对外暴露时需要把部署域名加进白名单。
3. `HR_API_BASE` 指向部署后的 Spring 后端地址，`HR_MCP_TOKEN` 用强随机值。
4. `home/` 挂载卷保存会话与启动令牌，按敏感数据对待，不要公开共享。
