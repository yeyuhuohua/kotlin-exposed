# HR 工作台前端

Vue 3 + TypeScript + Vite，UI 使用 Element Plus，状态由 Pinia 管理，路由使用 Vue Router，
图标使用 Lucide，图表使用 Chart.js。可连接 Ktor 或 Spring 后端。
本文件的命令在 `frontend/` 下执行。

## 开发启动

需要 Node.js 22.12 或更高版本，以及 `package.json` 的 `packageManager` 指定版本的 pnpm。
只使用 pnpm，保留 `pnpm-lock.yaml`，不要生成 npm/Yarn 锁文件。

```sh
pnpm install --frozen-lockfile
pnpm dev
```

默认地址为 <http://127.0.0.1:5173>，端口被占用时 Vite 自动顺延，以终端打印的地址为准。
后端使用 IDEA Debug 启动，具体见 [Ktor](../backend/README.md) 或 [Spring](../backend-spring/README.md) 指南。

## 环境变量与后端切换

需要修改默认值时，首次复制配置示例并编辑 `.env.local`：

```sh
cp .env.example .env.local
```

已有配置不要覆盖。修改后重启 Vite；生产构建中使用的 `VITE_*` 变量需要重新构建才生效。

| 变量 | 默认值/示例 | 作用 |
| --- | --- | --- |
| `VITE_API_BASE_URL` | 开发默认 `http://127.0.0.1:8081` | 浏览器 API 的地址前缀；客户端会继续追加 `/api`，不要重复填写 `/api`。显式留空表示同源部署 |
| `VITE_DSH_URL` | `http://127.0.0.1:3080` | 助手 iframe 与 `/hr-session` 请求的基础地址，末尾不要加 `/` |

开发默认直连 Spring（8081），DSH 开箱即用。改用 Ktor 时，在 `.env.local` 配置：

```dotenv
VITE_API_BASE_URL=http://127.0.0.1:8080
```

前端没有开发代理：API 和文档请求由浏览器直接跨域访问后端，本机来源由后端 CORS 放行。
助手请求直接发往 `VITE_DSH_URL`，与 API 地址互不影响。
所有 `VITE_*` 值都会进入浏览器代码，不能保存数据库密码、JWT 签名密钥或 MCP 固定口令。

## 源码与动态菜单

| 路径 | 职责 |
| --- | --- |
| `src/router.ts` | 登录守卫、固定页面，以及后端菜单 key 到专用视图的映射 |
| `src/config/resources.ts` | 通用业务资源 key、字段、表格和写操作的映射 |
| `src/api/paths/` | 按页面组织的 API 路径，与 `views/` 对应 |
| `src/lib/api.ts` | 响应信封、稳定错误码提示、Token、取消和 15 秒请求超时 |
| `src/lib/menuIcons.ts` | 菜单图标名称注册，未知名称使用默认图标 |
| `src/stores/` | 身份、通知和主题状态 |
| `src/views/` / `src/components/` | 页面与组件，局部样式放在对应 `.vue` 中 |
| `src/styles.css` | 语义颜色 token、基础元素、共用样式和 Element Plus 覆盖 |
| `tests/` | Vitest 纯逻辑、组件、登录跳转和动态路由回归测试 |

页面清单唯一来源是后端 `auth_menus` 表，登录后通过 `GET /api/auth/routes` 按权限下发。
页面标题、路径、分组、图标和排序不要再硬编码成另一份前端菜单清单。

新增页面时需要同时完成组件映射和后端菜单登记：专用页面在 `router.ts` 加映射，
通用资源页面在 `resources.ts` 配置字段与接口；菜单通过“菜单管理”添加，或随后端初始化播种。
新菜单 key 没有前端映射时会跳过注册并打印控制台告警，不会自动产生一个可用业务页面。
非 ADMIN 角色还需要页面权限和实际调用的接口权限；新增 API 的权限码仍由后端 `PermissionCatalog` 登记。

页面间跳转根据后端下发的实际路径生成。修改菜单路径后刷新页面或重新登录，避免继续使用旧地址。
“我的账号”和无权限页保留固定入口，业务接口授权最终由后端执行。

## 身份、表单与主题

Token 放在当前标签页的 `sessionStorage`，不提供刷新 Token。过期或被撤销后需重新登录。
退出登录会撤销当前账号在所有设备上的 Token；更改角色权限也会撤销相关账号的 Token。
“记住密码”会把账号和 Base64 编码的密码保存到 `localStorage`，不是加密；公共设备不应勾选。

员工、用户及员工详情视图使用服务端分页，其他目录按接口结果在浏览器内筛选、分页。
员工 PUT 只修改非空值，清空 `salary`、`departmentId`、`phoneNumber` 需要 PATCH 权限。
表单的按钮和下拉查询按权限显示；这些前端限制不能代替服务端鉴权。

主题由 `src/stores/theme.ts` 管理，在 `<html>` 的 `light` / `dark` class 间切换。
新增颜色先在 `styles.css` 的 `html.light` 与 `html.dark` 添加语义 token，规则里引用变量。
页面和组件样式使用 `<style scoped>`，需要深入子组件时使用 `:deep()`。

## 检查与构建

```sh
pnpm typecheck
pnpm test
pnpm build
```

测试使用 Vitest、Vue Test Utils 和 happy-dom，不连接真实后端。
`pnpm format` 会改写代码，按需使用；CI 执行类型检查、测试和构建。
生产产物在 `dist/`；`pnpm preview` 只用于本机预览构建产物，不是生产服务器，也不提供 API 代理。

## 生产部署

用 Web 服务器托管 `dist/`，Vue Router 使用 history 模式，需要未知页面回退到 `index.html`。
API 和文档路径必须先匹配反向代理，不能把 `/api` 的失败响应改成前端 HTML。

同源部署时 `VITE_API_BASE_URL` 保持空，网关将 `/api` 代理到选定后端，并根据后端转发文档入口：
Ktor 使用 `/swagger`、`/swagger-resources`、`/v3`、`/webjars`、`/doc.html`；
Spring 使用 `/swagger`、`/swagger-ui`、`/v3`、`/doc.html`。

跨域部署在构建时设置 `VITE_API_BASE_URL`，并配置对应后端 CORS；
导航中的文档链接与 API 共用这个前缀，会一并指向目标后端。
目前 Vite 没有设置自定义 `base`，路由也没有子路径基址，不应仅修改环境变量就宣称支持子目录部署。

助手部署需要额外处理 Origin 白名单、Cookie、iframe、WebSocket 和 DSH trusted host，见 [DSH 指南](../dsh/README.md)。

## 常见问题

| 现象 | 检查与处理 |
| --- | --- |
| 请求超时或无法连接后端 | 先检查目标后端 `/api/health`，确认 IDEA Debug 在线及 `VITE_API_BASE_URL` 指向正确 |
| 切换后端后登录失效 | 重新登录；如要共用 Token，两套后端需使用相同 JWT 密钥、issuer、audience |
| 新增菜单没有页面 | 核对菜单 key 是否已在 `router.ts` 或 `resources.ts` 注册，以及菜单是否启用 |
| 页面可见但查询/保存返回 403 | 核对页面、查询、写入和下拉数据各自的接口权限 |
| 部署后刷新页面返回 404 | 配置单页应用的 history 回退，同时保留 API 代理优先级 |
| 生产 API 仍连错地址 | 确认构建时的 `VITE_API_BASE_URL` 与网关配置；修改后需要重新构建 |
| 助手返回 401/503 或 iframe 无法连接 | 检查 Spring、DSH、模型服务和 Cookie；本机地址统一使用 `127.0.0.1`，避免与 `localhost` 混用 |
