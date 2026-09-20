# 仓库贡献指南

## 项目结构与模块职责

本仓库包含两个独立项目：`backend/` 是 Kotlin/JVM 服务，`frontend/` 是 Vue 3 + TypeScript + Vite 前端，基础 UI 组件统一使用 Element Plus。前端依赖只使用 pnpm，保留 `pnpm-lock.yaml`，禁止生成 npm/Yarn 锁文件。前端源码位于 `frontend/src/`，构建与开发命令在 `frontend/` 下执行。

以下后端路径均相对于 `backend/`。后端使用 Ktor、Exposed R2DBC、MySQL 和 Redis 提供 HR API。代码按业务分包，目录扁平，文件名表达职责。

- `src/main/kotlin/com/atguigu/hr/Application.kt`：装配插件并注册文档路由与 `/api` 下各业务路由。
- `config/`：MySQL、Redis 连接。`DatabaseFactory.ping()` 做库连通探测。
- `common/`：无业务含义的复用代码。`api` 为统一信封与响应扩展，`cache` 为通用 Redis 工具，`database` 为主键分配。禁止笼统的 `Utils.kt`。
- 业务包（如 `employee/`、`department/`、`job/`、`location/`、`geography/`、`jobhistory/`、`jobgrade/`、`demo/tdept/`、`demo/temp/`、`demo/order/`、`overview/`、`health/`）：Routes → Service → Repository；缓存 key 与失效在本模块 `*Cache`；跨业务 JOIN 可引用其它包的表定义；跨业务缓存只调对方 `*Cache`，不调对方 Service。
- `docs/`：Knife4j / Swagger 页面与 OpenAPI JSON 兼容处理、共用示例 DSL。业务示例放在对应业务目录的 `*Examples.kt`。
- `src/main/resources/`：应用与日志配置、`openapi/documentation.yaml` 以及 API 文档静态页面。
- `gradle/libs.versions.toml`：统一管理依赖和插件版本。
- 当前没有后端自动化测试。

新增业务时新增业务目录。修改已有功能优先在所属目录完成。只有被多个业务复用且不含业务含义的代码才能进入 `common`。

## 构建、测试与开发命令

以下命令在 `backend/` 目录执行。使用 JDK 21 和 Gradle Wrapper。`gradle.properties` 包含本机专用 JDK 路径，请在本地覆盖，例如添加参数 `-Dorg.gradle.java.home="$JAVA_HOME"`。JWT 密钥和可选的初始管理员账号在 `src/main/resources/application.yaml` 的 `auth` 段。

- `./gradlew build`：编译并生成构建产物。
- `./gradlew run`：启动 Netty，默认端口为 8080。文档入口包括 `/doc.html`、`/swagger` 和 `/v3/api-docs`。

本地运行需要已具备 `atguigudb` 库及对应表结构的 MySQL，以及 Redis；连接参数在 `application.yaml` 中配置。

## 代码风格与命名规范

遵循项目配置的 Kotlin 官方风格：使用四空格缩进，类型采用 `UpperCamelCase`，函数和属性采用 `lowerCamelCase`，常量采用 `UPPER_SNAKE_CASE`。保持显式导入和多行参数末尾逗号的现有风格。当前未配置格式化或代码检查插件。

业务包内路由调用本模块服务，数据库访问放在本模块仓储。使用可挂起的 R2DBC 事务，并在 `dbQuery` 或 `dbUpdate` 内收集查询 Flow。修改 API 行为时，同步更新本模块缓存失效逻辑及 OpenAPI 描述和示例。

## 测试规范

当前没有后端自动化测试，也不再使用内存 H2。前端单元测试在 `frontend/` 下用 `pnpm test` 运行。

## 提交与合并请求规范

使用简短、以动作开头的提交标题，例如 `修复员工缓存失效逻辑`。合并请求应说明行为变化、关联问题、测试结果及运行所需服务；API 变更附请求与响应示例，文档界面变更附截图。

## 安全与智能体临时文件

禁止提交凭据或敏感日志。部署密钥不得存放在版本控制跟踪的配置文件中。
`backend/src/main/resources/application.yaml` 和 `backend/gradle.properties` 仅供本机使用；仓库提交对应的无密钥示例，不提交本机连接密码或绝对 JDK 路径。

创建临时文件前，使用 `git rev-parse --show-toplevel` 确认项目根目录；若不是 Git 仓库，则使用当前项目目录。检查 `.gitignore`，确保包含精确的 `.debug/` 条目，补充时保留原有内容。临时测试、脚本、预览等统一存放于 `.debug/<task>/`，禁止使用系统临时目录。任务完成并验证后清理对应文件，目录为空时删除 `.debug/`；最终交付文件放在该目录之外的用户指定位置。
