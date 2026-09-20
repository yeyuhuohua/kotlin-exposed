# HR 人事工作台

本仓库包含两个相互独立的项目：

```text
backend/                 Kotlin / Ktor / Exposed R2DBC 后端
  src/                   后端源码
  gradle/                Gradle Wrapper 与依赖版本目录
  AUTH.md                认证、账号初始化与权限配置说明
frontend/                Vue 3 / TypeScript / Vite / Element Plus 前端
  src/                   页面、组件、状态管理与带类型定义的 API 客户端
  tests/                 前端单元测试
  pnpm-lock.yaml         用于复现依赖安装结果的锁文件
```

## 本地启动

需要 Java 21、Node.js 22.12 或更高版本、pnpm，以及现有 Docker 中的 MySQL 和 Redis。
后端仍使用已有的 `atguigudb` 数据库；目录迁移不会移动、重建或重置数据库。

首次克隆仓库后，在仓库根目录准备本地配置：

```sh
cp backend/src/main/resources/application.example.yaml backend/src/main/resources/application.yaml
cp backend/gradle.properties.example backend/gradle.properties
```

填写 `application.yaml` 中的数据库连接参数、至少 32 字节的随机 `auth.jwtSecret`，以及首次创建管理员需要的账号和密码。
跨域部署时同时配置 `cors.allowedHosts`；`auth.loginRateLimit` 段可调整登录失败限流的窗口与阈值（默认开启）。
这些本地配置已加入忽略规则，仓库只保留不含真实凭据的示例。Gradle 使用 JDK 21；可通过 `JAVA_HOME` 或 IDEA 的 Gradle JVM 设置指定，不应提交本机 JDK 绝对路径。

在一个终端中启动后端：

```sh
cd backend
./gradlew run
```

JWT 密钥和初始管理员配置在 `backend/src/main/resources/application.yaml`。
现有管理员账号为 `admin`。不要将数据库密码、账号密码或 JWT 签名密钥放入任何 `VITE_*` 变量。

在另一个终端中启动前端：

```sh
cd frontend
pnpm install --frozen-lockfile
pnpm dev
```

前端地址：[http://127.0.0.1:5173](http://127.0.0.1:5173)。
后端地址：[http://127.0.0.1:8080](http://127.0.0.1:8080)。
如果 5173 端口已被占用，Vite 会自动选择其他前端端口。
前端的 `/api` 和接口文档请求会被代理到后端。
如需修改代理目标，请根据 `frontend/.env.example` 中的配置键创建 `frontend/.env.local`，然后重启 Vite。

可以在 IntelliJ IDEA 中打开仓库根目录，同时开发前后端。
在 Gradle 工具窗口中关联 `backend/build.gradle.kts`，使用
`backend/gradle/wrapper/gradle-wrapper.properties` 指定的 Gradle Wrapper，并将 Gradle JVM 设置为 JDK 21。
Gradle 运行配置的项目目录必须指向 `backend/`，不能指向仓库根目录。
迁移后如仍有关联到旧根目录的 Gradle 项目，应取消旧关联，再重新加载所有 Gradle 项目。
也可以将 `backend/` 作为独立的 IDEA 项目打开，该目录保留了自己的 `.idea` 配置。
不要将后端构建脚本移回仓库根目录。

## 页面与功能

基础 UI 使用 Element Plus：表单校验、输入框、选择器、开关、日期选择、表格、分页、菜单、抽屉、弹窗、消息和权限复选框。
图表继续使用 Chart.js，图标使用 Lucide，业务状态与路由仍由 Pinia 和 Vue Router 管理。

- 工作概览：员工总数、部门人员分布和员工速览。
- 员工管理：姓名与邮箱搜索、部门与岗位筛选、服务端分页、详情查询及后端支持的写操作。
- 业务目录：部门、岗位、地点、国家、区域、任职历史和薪资等级。
- 详情与演示数据：数据库员工详情视图，以及已有的示例部门、人员和订单。
- 账号管理：管理员创建账号、调整角色、启用或停用账号，以及重置密码。
- 其他页面：角色目录、个人账号，以及 MySQL 和 Redis 健康状态。

权限采用“用户 → 角色 → 页面/接口权限”的 RBAC 模型，同一角色的用户共享同一套权限。
“用户管理”只分配角色，不提供账号级授权入口。只有 `ADMIN` 可以在“角色权限”页面创建普通角色、调整角色状态并通过盾牌按钮配置权限。
页面权限和按 HTTP 方法、接口路径划分的接口权限分别勾选；角色权限为空表示拒绝全部可配置访问。
`ADMIN` 角色固定拥有全部已登记权限，不能修改或停用。新建普通角色初始无业务权限。
全新安装时 `READER` 初始化为业务只读；从旧账号授权升级时，普通角色按其已有成员权限的交集初始化，避免扩大访问范围。
保存角色权限会撤销该角色所有用户的旧 Token；用户切换角色也需要重新登录。
菜单、路由守卫和操作按钮根据服务端返回的有效权限显示，最终访问控制由后端强制执行。
Token 过期或被撤销后会返回登录页。
Token 保存在当前标签页的会话存储中，不写入持久化的本地存储。
退出登录会通过后端撤销该账号在所有设备上的 Token。

员工列表和用户列表使用后端分页。其他业务目录加载现有列表接口后，在前端进行筛选和分页。
概览的部门人数分布与薪资汇总由服务端聚合（`GROUP BY` / `AVG` 等），前端不再拉取全量员工。
由于其他业务模块没有删除接口，前端只提供员工、账号与角色的删除操作。
账号与角色在管理页面按行删除，内置 `admin` 账号、`ADMIN` 角色和当前登录账号的删除按钮是禁用的（后端同样会拒绝）。
更新时只提交改动过的字段。员工支持 `PATCH /api/employees/{id}`：请求体里出现的字段才会被修改，显式 `null` 表示清空可空列
（`salary`、`departmentId`、`phoneNumber`）；`jobId` 对应非空列，只能改成另一个岗位。
没有 `PATCH` 权限时编辑框仍然可用，但清空字段会提示"不能清空"。

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

在前端目录中执行测试、构建或格式化：

```sh
cd frontend
pnpm test
pnpm build
pnpm format
```

在后端目录中执行构建：

```sh
cd backend
./gradlew test
./gradlew build
```

前端构建产物位于 `frontend/dist/`，后端 JAR 位于 `backend/build/libs/`。
前端依赖统一使用 pnpm 管理，不要生成 npm 或 Yarn 锁文件。
前端组件测试使用 Vitest、Vue Test Utils 和内存 DOM，不连接真实后端；`pnpm typecheck` 与 `pnpm build` 必须通过。
后端单元测试覆盖权限目录与路由匹配、密码哈希、登录限流、PATCH 参数解析、缓存分组映射和约束冲突识别，不依赖 MySQL/Redis，也不使用内存 H2。
需要真实数据库的集成验证仍在目标环境手工执行。

## 已知限制

- **模糊搜索**：员工姓名/邮箱使用 `LIKE '%关键字%'`，首尾通配符无法走 B-tree 索引，数据量大时是全表扫描。
  查询已不再对列套 `LOWER()`（依赖列的 `*_ci` 排序规则），但如需支撑大数据量应改为前缀匹配、全文索引或独立搜索服务。
- **登录限流**：`auth.loginRateLimit` 是单实例内存计数，多实例部署时每个实例各算一份，仍需在网关限流。
- **CORS**：默认只允许 `localhost` / `127.0.0.1`，跨域部署必须在 `cors.allowedHosts` 里显式列出域名（`*` 仅供本机调试）。
- **文档入口**：`/doc.html`、`/swagger` 默认公开，不希望对外暴露时应在网关限制。
- **删除不可撤销**：账号与角色是物理删除，没有软删除或回收站；角色仍有账号引用时需先把账号改到其它角色。
- **缓存一致性**：写库与删缓存不在同一事务，进程在提交后崩溃时脏数据最多保留到 Redis TTL（默认 600 秒）。

## 提交忽略规则

- 本机凭据：`backend/src/main/resources/application.yaml`、`.env`、`.env.*`、证书和私钥。
- 本机构建配置：`backend/gradle.properties`、`.idea/`、`*.iml`、`local.properties`。
- 依赖与缓存：`node_modules/`、`.pnpm-store/`、`.gradle/`、`.kotlin/`、`*.tsbuildinfo`。
- 生成产物：`build/`、`dist/`、`out/`、覆盖率和测试报告目录。
- 调试与运行数据：`.debug/`、`.playwright-mcp/`、`logs/`、`*.log`。

Gradle Wrapper、`pnpm-lock.yaml`、源码、文档和无密钥的示例配置需要提交。

## 生产部署

使用 Web 服务器托管 `frontend/dist/`，并配置单页应用的历史路由回退。
将 `/api`、`/swagger`、`/swagger-resources`、`/v3`、`/webjars` 和 `/doc.html` 反向代理到后端。
Vite 的开发代理不会被打包进生产构建产物。
如果 API 使用独立域名或端口，请在构建时设置 `VITE_API_BASE_URL`，并为该部署配置后端 CORS 和文档链接。

生产环境应使用 HTTPS，并在网关配置登录限流。
登录页背景使用 Unsplash 的外部办公空间图片，字体无法加载时会使用本地系统字体。
项目未使用第三方统计或外部头像服务，也不会将员工数据发送到图片服务。
