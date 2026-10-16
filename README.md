# HR 人事工作台

人事管理工作台，包含两个可独立运行的后端、一套共享前端和可选的 DSH 对话助手。
普通 HR 页面可选用任一后端；DSH 的 HR 工具依赖 Spring 后端的 MCP 服务。

## 文档导航

| 文档 | 内容 |
| --- | --- |
| [Ktor 后端](backend/README.md) | 配置、IDEA Debug 启动、缓存、打包与排障 |
| [认证与权限](backend/AUTH.md) | 账号初始化、角色授权、Token 撤销与菜单管理 |
| [Spring 后端](backend-spring/README.md) | IDEA Debug、Spring 配置、MCP、缓存与部署 |
| [前端](frontend/README.md) | 环境变量、切换后端、动态路由、开发与部署 |
| [DSH 助手](dsh/README.md) | LM Studio、HR 会话、配置迁移、上下文压缩与排障 |
| [贡献指南](AGENTS.md) | 模块边界、代码风格、测试与临时文件约定 |

## 项目结构

```text
backend/                 Kotlin / Ktor / Exposed R2DBC 后端
  README.md              配置、开发与部署说明
  src/                   后端源码
  gradle/                Gradle Wrapper 与依赖版本目录
  AUTH.md                认证、账号初始化与权限配置说明
backend-spring/          Spring Boot 复刻版后端（Kotlin + MyBatis-Plus + Spring AI MCP）
  README.md              启动、配置与部署说明
frontend/                Vue 3 / TypeScript / Vite / Element Plus 前端
  README.md              前端开发、路由与环境变量说明
  src/                   页面、组件、状态管理与带类型定义的 API 客户端
  tests/                 前端单元测试
  pnpm-lock.yaml         用于复现依赖安装结果的锁文件
dsh/                     DeepSeek Harness 对话助手容器（品牌补丁 + HR 登录态代理）
  README.md              启动、版本固定与部署说明
```

## 本地启动

需要 JDK 21、Node.js 22.12 或更高版本、`frontend/package.json` 指定的 pnpm，以及 MySQL 和 Redis。
使用助手还需要 Docker / Compose 和可访问的模型服务，默认采用 LM Studio。

| 服务 | 默认地址 | 是否必需 |
| --- | --- | --- |
| 前端 | <http://127.0.0.1:5173> | 浏览器工作台 |
| Ktor API | <http://127.0.0.1:8080> | 两个后端至少选一个 |
| Spring API / MCP | <http://127.0.0.1:8081> | 前端开发默认连接；使用 DSH 的 HR 工具时必需 |
| DSH 登录态代理 | <http://127.0.0.1:3080> | 使用助手时必需 |
| LM Studio API | <http://127.0.0.1:1234/v1> | 默认模型提供方，可在 DSH 中更换 |

后端使用已有的 `atguigudb` 数据库。仓库没有完整的 HR 业务库导入脚本，需要预先准备
`employees`、`departments`、`jobs`、`locations`、`countries`、`regions`、`job_history`、`job_grades`、
`emp_details_view`，以及演示模块使用的 `t_dept`、`t_emp` 和 `order` 表。
自动初始化只负责认证、菜单和审计表，不会创建数据库或这些业务表。
Redis 故障时业务缓存旁路；`GET /api/health` 在任一依赖不可用时返回 503，详情仍保留在 `data` 中。

以下流程使用 Ktor 后端；前端开发默认直连 Spring（8081），跟随本流程时先把
`VITE_API_BASE_URL` 设为 `http://127.0.0.1:8080`（见 `frontend/.env.example`）。
使用 Spring 时按 [Spring 指南](backend-spring/README.md) 准备配置并启动即可；
使用助手时继续完成 [DSH 指南](dsh/README.md) 的模型和 MCP 配置。

首次克隆仓库后，在仓库根目录准备本地配置：

```sh
cp backend/src/main/resources/application.example.yaml backend/src/main/resources/application.yaml
cp backend/gradle.properties.example backend/gradle.properties
```

填写 `application.yaml` 中的数据库连接参数、至少 32 字节的随机 `auth.jwtSecret`，以及首次创建管理员需要的账号和密码。
跨域部署时同时配置 `cors.allowedHosts`；`auth.loginRateLimit` 段可调整登录失败限流的窗口与阈值（默认开启）。
这些本地配置已加入忽略规则，仓库只保留不含真实凭据的示例。Gradle 使用 JDK 21；可通过 `JAVA_HOME` 或 IDEA 的 Gradle JVM 设置指定，不应提交本机 JDK 绝对路径。

在 IntelliJ IDEA 中以 Debug 模式启动 `com.atguigu.hr.ApplicationKt`，工作目录设为 `backend/`。
入口会读取 `src/main/resources/application.yaml`；构建产物排除此本机配置。
部署时通过 `-config=/path/to/application.yaml` 指定外部配置。

JWT 密钥和初始管理员配置在 `backend/src/main/resources/application.yaml`。
管理员用户名以本机配置为准，仓库不提供可直接登录的通用账号密码。
不要将数据库密码、账号密码或 JWT 签名密钥放入任何 `VITE_*` 变量。

在另一个终端中启动前端：

```sh
cd frontend
pnpm install --frozen-lockfile
pnpm dev
```

前端地址：[http://127.0.0.1:5173](http://127.0.0.1:5173)。
后端地址：[http://127.0.0.1:8080](http://127.0.0.1:8080)。
如果 5173 端口已被占用，Vite 会自动选择其他前端端口。
前端的 API 和接口文档请求由浏览器直接跨域发往后端，本机来源已被后端 CORS 放行，无需开发代理。
前端开发默认直连 Spring（8081）；本流程使用 Ktor，请根据 `frontend/.env.example` 创建
`frontend/.env.local` 并设置 `VITE_API_BASE_URL=http://127.0.0.1:8080`，然后重启 Vite。

可以在 IntelliJ IDEA 中打开仓库根目录，同时开发前后端。
在 Gradle 工具窗口中关联 `backend/build.gradle.kts`，使用
`backend/gradle/wrapper/gradle-wrapper.properties` 指定的 Gradle Wrapper，并将 Gradle JVM 设置为 JDK 21。
Gradle 运行配置的项目目录必须指向 `backend/`，不能指向仓库根目录。
迁移后如仍有关联到旧根目录的 Gradle 项目，应取消旧关联，再重新加载所有 Gradle 项目。
也可以将 `backend/` 或 `backend-spring/` 作为独立的 IDEA 项目打开；`.idea/` 仅供本机使用，不随仓库分发。
不要将后端构建脚本移回仓库根目录。

## 页面与功能

基础 UI 使用 Element Plus：表单校验、输入框、选择器、开关、日期选择、表格、分页、菜单、抽屉、弹窗、消息和权限复选框。
图表继续使用 Chart.js，图标使用 Lucide，业务状态与路由仍由 Pinia 和 Vue Router 管理。

- 工作概览：员工总数、部门人员分布和员工速览。
- 员工管理：姓名与邮箱搜索、部门与岗位筛选、服务端分页、详情查询及后端支持的写操作。
- 业务目录：部门、岗位、地点、国家、区域、任职历史和薪资等级。
- 详情与演示数据：数据库员工详情视图，以及已有的示例部门、人员和订单。
- 账号管理：管理员创建账号、调整角色、启用或停用账号，以及重置密码。
- 审计日志（仅 ADMIN）：登录尝试（成功与失败）记录用户名、IP、时间与失败原因；`/api` 调用记录用户、方法、路径、状态码与耗时。两张表只增不改，记录只增不减，后续可按需加定期清理。
- 菜单管理（仅 ADMIN）：维护页面路径、标题、图标、分组、排序和启用状态；内置菜单不能删除。
- 对话助手（仅 ADMIN）：通过 DSH 与已配置模型对话，并通过 Spring MCP 查询或修改 HR 数据。
- 其他页面：角色目录、个人账号，以及 MySQL 和 Redis 健康状态。

权限采用“用户 → 角色 → 页面/接口权限”的 RBAC 模型，同一角色的用户共享同一套权限。
“用户管理”只分配角色，不提供账号级授权入口。只有 `ADMIN` 可以在“角色权限”页面创建普通角色、调整角色状态并通过盾牌按钮配置权限。
页面权限和按 HTTP 方法、接口路径划分的接口权限分别勾选；角色权限为空表示拒绝全部可配置访问。
`ADMIN` 角色固定拥有全部已登记权限，不能修改或停用。新建普通角色初始无业务权限。
全新安装时 `READER` 初始化为业务只读；Ktor 从旧账号授权升级时，普通角色按其已有成员权限的交集初始化，避免扩大访问范围。
保存角色权限会撤销该角色所有用户的旧 Token；用户切换角色也需要重新登录。
菜单、路由守卫和操作按钮根据服务端返回的有效权限显示，最终访问控制由后端强制执行。
Token 过期或被撤销后会返回登录页。
Token 保存在当前标签页的会话存储中，不写入持久化的本地存储。
登录页的“记住密码”是可选功能：勾选后凭据会写入 `localStorage`（密码经 Base64 编码，仅避免明文直接展示，不是加密），用于下次自动填充；公共设备请勿使用，取消勾选或在未勾选状态下登录会立即清除。
退出登录会通过后端撤销该账号在所有设备上的 Token。

员工列表、用户列表和员工详情视图使用后端分页。其他业务目录加载现有列表接口后，在前端进行筛选和分页。
概览的部门人数分布与薪资汇总由服务端聚合（`GROUP BY` / `AVG` 等），前端不再拉取全量员工。
前端按后端实际接口提供写操作，包括员工、账号、普通角色和非内置菜单的删除。
后端保护内置 `admin` 和配置指定的管理员账号，拒绝删除、停用、降级或由其他管理员重置密码；
当前登录账号和 `ADMIN` 角色也不能删除。配置指定管理员的规则见 [认证文档](backend/AUTH.md)。
更新时只提交改动过的字段。员工支持 `PATCH /api/employees/{id}`：请求体里出现的字段才会被修改，显式 `null` 表示清空可空列
（`salary`、`departmentId`、`phoneNumber`）；`jobId` 对应非空列，只能改成另一个岗位。
没有 `PATCH` 权限时编辑框仍然可用，但清空字段会提示"不能清空"。

## 接口契约

接口字段和可用操作以运行时 `/v3/api-docs` 为准，不维护另一份静态接口规范。
Ktor 的 `/doc.html` 是 Knife4j，`/swagger` 是 Swagger UI；Spring 的兼容入口会跳转到 Swagger UI。
登录返回 `data.accessToken`；受保护请求携带 `Authorization: Bearer <accessToken>`。
分页接口使用 `limit`（1-200，默认 50）和 `offset`（跳过条数，默认 0），而不是页码。

成功响应示例（空分页）：

```json
{
  "code": 200,
  "message": "ok",
  "data": { "total": 0, "items": [] },
  "error": null
}
```

失败时 HTTP 状态与 `code` 对应，客户端应使用稳定的 `error`，不要匹配英文 `message`：

```json
{
  "code": 401,
  "message": "authentication required",
  "data": null,
  "error": "unauthorized"
}
```

员工 PUT 中省略或为 `null` 的字段不更新；PATCH 只更新出现的字段，允许清空可空字段，
拒绝未知字段和 `jobId: null`。岗位编码不能是 `.`、`..`，也不能包含路径分隔符或控制字符。

## 主题（白天 / 夜间）

顶栏和登录页各有一个切换按钮，图标在太阳与月亮之间切换。
选择保存在 `localStorage`（`hr-workspace.theme`）；没有选择过时跟随系统偏好，之后系统切换也会同步。
首屏在 `index.html` 的内联脚本里先应用主题，避免先渲染浅色再切夜间造成闪白。

样式分两层：`frontend/src/styles.css` 只保留语义 token、基础元素重置、跨页面复用的基础件（按钮、徽标、头像、页头、状态块、表格与弹窗骨架）
和 Element Plus 覆盖；每个页面/组件自己的样式写在对应 `.vue` 的 `<style scoped>` 里，落到子组件内部时用 `:deep()`。

配色只在 `frontend/src/styles.css` 里定义两处：`html.light` 是浅色值，`html.dark` 是夜间值（`:root` 只放尺寸与别名），
规则里一律引用 `var(--token)`。`index.html` 的 `<html>` 默认带 `light` 类，切换时两个类互斥替换。Element Plus 组件走官方的 `theme-chalk/dark/css-vars.css`，其中基础底色与文字色在 `html.dark` 里
对齐到项目 token。新增颜色请先加 token，不要在规则里写死色值。

## 测试与构建

下面两个代码块各自从仓库根目录执行。在前端目录中运行测试、类型检查和构建：

```sh
cd frontend
pnpm test
pnpm typecheck
pnpm build
```

在后端目录中执行构建：

```sh
cd backend
./gradlew test
./gradlew build
```

Spring 在 `backend-spring/` 下执行 `./gradlew test`、`./gradlew build`。
DSH 在仓库根目录执行 `node --test dsh/auth-proxy.test.mjs` 和 `sh -n dsh/docker-entrypoint.sh`。
`pnpm format` 会改写前端源码，按需运行，不属于 CI 检查。

前端构建产物位于 `frontend/dist/`，两个后端的 JAR 分别位于各自的 `build/libs/`。
前端依赖统一使用 pnpm 管理，不要生成 npm 或 Yarn 锁文件。
前端组件测试使用 Vitest、Vue Test Utils 和内存 DOM，不连接真实后端；`pnpm typecheck` 与 `pnpm build` 必须通过。
后端单元测试覆盖权限目录与路由匹配、密码哈希、登录限流、PATCH 参数解析、缓存分组映射和约束冲突识别，
并用 `ktor-server-test-host` 固定鉴权拦截的 HTTP 行为（403/401 后路由 handler 不得执行），不依赖 MySQL/Redis，也不使用内存 H2。
需要真实数据库的集成验证仍在目标环境手工执行。
CI 在推送到 `main` 或创建合并请求时分别检查两个后端、前端和 DSH 代理。
CI 不启动真实 MySQL/Redis、LM Studio 或 DSH 容器，也不代替部署环境的联调。

## 已知限制

- **模糊搜索**：员工姓名/邮箱使用 `LIKE '%关键字%'`，首尾通配符无法走 B-tree 索引，数据量大时是全表扫描。
  查询已不再对列套 `LOWER()`（依赖列的 `*_ci` 排序规则），但如需支撑大数据量应改为前缀匹配、全文索引或独立搜索服务。
- **登录限流**：Ktor 的 `auth.loginRateLimit` 和 Spring 的 `hr.auth.login-rate-limit` 都是单实例内存计数，多实例部署仍需在网关限流。
- **CORS**：默认只允许本机来源，跨域部署须设置 Ktor 的 `cors.allowedHosts` 或 Spring 的 `hr.cors.allowed-hosts`（`*` 仅供本机调试）。
- **文档入口**：`/doc.html`、`/swagger` 默认公开，不希望对外暴露时应在网关限制。
- **删除不可撤销**：账号与角色是物理删除，没有软删除或回收站；角色仍有账号引用时需先把账号改到其它角色。
- **缓存一致性**：两个后端分开存放缓存，通过 Redis 共享版本感知写入与撤销；失效发布成功后旧版本不能再命中。
  写库与失效通知不在同一事务，进程在提交后崩溃或通知失败时，其他实例可能在 TTL 内继续读取旧值。
  外部 SQL 写入不会自动广播失效，部署和数据维护时应单独安排缓存失效。
- **请求关联**：Ktor `/api` 日志带 `X-Request-Id`（未携带时生成并回写），网关转发时请透传；Spring 未提供同等请求 ID 功能。
- **助手**：MCP 包含写入和删除工具，模型收到的对话及工具结果会发送给所选模型提供方；DSH Cookie、模型上下文和部署限制见其指南。

## 提交忽略规则

- 本机凭据：两个后端的 `src/main/resources/application.yaml`、`.env`、`.env.*`、证书和私钥；`.env.example` 可以提交。
- 本机构建配置：两个后端的 `gradle.properties`、`.idea/`、`*.iml`、`local.properties`。
- 依赖与缓存：`node_modules/`、`.pnpm-store/`、`.gradle/`、`.kotlin/`、`*.tsbuildinfo`。
- 生成产物：`build/`、`dist/`、`out/`、覆盖率和测试报告目录。
- 调试与运行数据：`.debug/`、`.playwright-mcp/`、`logs/`、`*.log`、DSH 的 `home/` 挂载卷。

Gradle Wrapper、`pnpm-lock.yaml`、源码、文档和无密钥的示例配置需要提交。

## 生产部署

使用 Web 服务器托管 `frontend/dist/`，并配置单页应用的历史路由回退。
同源部署时将 `/api`、`/swagger`、`/v3` 和 `/doc.html` 反向代理到选定后端。
Ktor 还需要 `/swagger-resources` 和 `/webjars`；Spring 需要 `/swagger-ui`。
完整的部署约定见 [前端指南](frontend/README.md)。
如果 API 使用独立域名或端口，请在构建时设置 `VITE_API_BASE_URL` 并为该部署配置后端 CORS，
文档链接会随 `VITE_API_BASE_URL` 一起指向目标后端。

Ktor 在 `backend/` 下运行 `./gradlew buildFatJar` 生成 `build/libs/kotlin-exposed-all.jar`。
以下启动命令从仓库根目录执行，并显式传入外部配置：

```sh
java -jar backend/build/libs/kotlin-exposed-all.jar -config=/path/to/application.yaml
```

Spring 的可执行 JAR、外部配置和 MCP 反向代理见 [Spring 部署说明](backend-spring/README.md)。
两种后端配置键不同，不能直接交换 `application.yaml`；共用登录 Token 时还必须保持 JWT 密钥、issuer、audience 一致。

生产环境应使用 HTTPS，并在网关配置登录限流。
登录页背景使用 Unsplash 的外部办公空间图片，字体无法加载时会使用本地系统字体。
项目未使用第三方统计或外部头像服务，也不会将员工数据发送到图片服务。
