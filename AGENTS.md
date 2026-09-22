# 仓库贡献指南

## 项目结构与模块职责

本仓库包含两个独立项目：`backend/` 是 Kotlin/JVM 服务，`frontend/` 是 Vue 3 + TypeScript + Vite 前端，基础 UI 组件统一使用 Element Plus。前端依赖只使用 pnpm，保留 `pnpm-lock.yaml`，禁止生成 npm/Yarn 锁文件。前端源码位于 `frontend/src/`，构建与开发命令在 `frontend/` 下执行。页面清单（权限码、路由路径、菜单标题）只在 `frontend/src/config/resources.ts` 声明一份，路由与权限判断都从它派生；提示文案按后端返回的错误码选择，不要匹配后端的英文 message；请求路径按页面放在 `frontend/src/api/paths/`，页面结构与 `views/` 一一对应。

样式分两层：`frontend/src/styles.css` 只放语义 token、基础元素重置、跨页面复用的基础件和 Element Plus 覆盖；页面与组件自己的样式写在对应 `.vue` 的 `<style scoped>` 里（选择器落到子组件内部时用 `:deep()`）。

配色只在 `frontend/src/styles.css` 的 `html.light`（浅色）与 `html.dark`（夜间）两处定义成语义 token（`:root` 只放尺寸与指向 token 的别名），任何样式规则里都禁止写死颜色关键字或十六进制值，新增颜色先加 token。主题状态由 `frontend/src/stores/theme.ts` 管理，落在 `<html>` 的 `dark` class 上（`index.html` 里有首屏前置脚本防止闪白），Element Plus 的暗色变量由 `theme-chalk/dark/css-vars.css` 提供。

以下后端路径均相对于 `backend/`。后端使用 Ktor、Exposed R2DBC、MySQL 和 Redis 提供 HR API。代码按业务分包，目录扁平，文件名表达职责。

- `src/main/kotlin/com/atguigu/hr/Application.kt`：装配插件并注册文档路由与 `/api` 下各业务路由。
- `config/`：MySQL、Redis 连接。`DatabaseFactory.ping()` 做库连通探测。
- `common/`：无业务含义的复用代码。`api` 为统一信封、错误码与响应扩展，`cache` 为通用 Redis 工具，`database` 为主键分配。禁止笼统的 `Utils.kt`。
- 业务包（如 `employee/`、`department/`、`job/`、`location/`、`geography/`、`jobhistory/`、`jobgrade/`、`demo/tdept/`、`demo/temp/`、`demo/order/`、`overview/`、`health/`）：Routes → Service → Repository；缓存 key 与失效在本模块 `*Cache`；跨业务 JOIN 可引用其它包的表定义；跨业务读取调对方 Service（其内部自带缓存），不要直接用对方 Repository；需要失效/广播时只调对方 `*Cache`。
- `docs/`：Knife4j / Swagger 页面与 OpenAPI JSON 兼容处理、共用示例 DSL。业务示例放在对应业务目录的 `*Examples.kt`。接口描述只写在路由的 `.describe {}` 里，KDoc 只保留一句话说明，不重复方法/路径/响应清单。
- `src/main/resources/`：应用、日志与 CORS/限流配置示例，以及 Swagger 静态页面。运行时的 OpenAPI 由 `/v3/api-docs` 从路由生成，不再维护单独的 YAML。
- `gradle/libs.versions.toml`：统一管理依赖和插件版本。
- 后端单元测试位于 `src/test/kotlin/`。

新增业务时新增业务目录。修改已有功能优先在所属目录完成。只有被多个业务复用且不含业务含义的代码才能进入 `common`。

## 构建、测试与开发命令

以下命令在 `backend/` 目录执行。使用 JDK 21 和 Gradle Wrapper。`gradle.properties` 包含本机专用 JDK 路径，请在本地覆盖，例如添加参数 `-Dorg.gradle.java.home="$JAVA_HOME"`。JWT 密钥和可选的初始管理员账号在 `src/main/resources/application.yaml` 的 `auth` 段。

- `./gradlew build`：编译并生成构建产物。
- `./gradlew test`：运行后端单元测试（不需要 MySQL/Redis）。
- `./gradlew run`：临时验证用，会占用 8080，不要长期驻留。

**本机启动后端统一走 IDEA 的 Debug 模式**：运行配置 `ApplicationKt`（Ktor 应用程序，工作目录 `backend/`），
这样可以随时打断点。需要后端在线时先探测 `http://127.0.0.1:8080/api/health`，如果没起来就请使用者用 Debug 启动，
不要用 `./gradlew run` 占用端口。文档入口包括 `/doc.html`、`/swagger` 和 `/v3/api-docs`。

本地运行需要已具备 `atguigudb` 库及对应表结构的 MySQL，以及 Redis；连接参数在 `application.yaml` 中配置。Redis 暂时不可用时服务也能启动（缓存旁路，健康检查触发重连），MySQL 不可用则无法正常工作。

## 代码风格与命名规范

遵循项目配置的 Kotlin 官方风格：使用四空格缩进，类型采用 `UpperCamelCase`，函数和属性采用 `lowerCamelCase`，常量采用 `UPPER_SNAKE_CASE`。保持显式导入和多行参数末尾逗号的现有风格。当前未配置格式化或代码检查插件。

业务包内路由调用本模块服务，数据库访问放在本模块仓储。使用可挂起的 R2DBC 事务，并在 `dbQuery` 或 `dbUpdate` 内收集查询 Flow。修改 API 行为时，同步更新本模块缓存失效逻辑及 OpenAPI 描述和示例；失败响应使用 `ApiResult`/`ErrorCode` 里的稳定错误码，不要把内部异常信息或数据库细节回显给调用方。

搜索类查询依赖列的 `*_ci` 排序规则做大小写不敏感匹配，禁止在 SQL 里对列套 `LOWER()` 等函数。

## 测试规范

后端单元测试放在 `backend/src/test/kotlin/`，在 `backend/` 下用 `./gradlew test` 运行，要求不依赖 MySQL/Redis（权限目录、密码哈希、限流、参数解析这类纯逻辑必须覆盖）。鉴权拦截这类 HTTP 层行为用 `ktor-server-test-host` 的 `testApplication` 固定，同样不连真实服务。涉及数据库的集成验证仍在目标环境手工执行，项目不使用内存 H2。

前端单元测试在 `frontend/` 下用 `pnpm test` 运行，`pnpm typecheck` 与 `pnpm build` 必须通过。

## 提交与合并请求规范

使用简短、以动作开头的提交标题，例如 `修复员工缓存失效逻辑`。合并请求应说明行为变化、关联问题、测试结果及运行所需服务；API 变更附请求与响应示例，文档界面变更附截图。推送与合并请求会触发 `.github/workflows/ci.yml`（后端测试、前端 typecheck/test/build），合并前必须全绿。

## 安全与智能体临时文件

禁止提交凭据或敏感日志。部署密钥不得存放在版本控制跟踪的配置文件中。
`backend/src/main/resources/application.yaml` 和 `backend/gradle.properties` 仅供本机使用；仓库提交对应的无密钥示例，不提交本机连接密码或绝对 JDK 路径。

创建临时文件前，使用 `git rev-parse --show-toplevel` 确认项目根目录；若不是 Git 仓库，则使用当前项目目录。检查 `.gitignore`，确保包含精确的 `.debug/` 条目，补充时保留原有内容。临时测试、脚本、预览等统一存放于 `.debug/<task>/`（不存在则创建），禁止使用系统临时目录。任务完成并验证后删除这些临时文件，但保留 `.debug/` 目录本身；最终交付文件放在该目录之外的用户指定位置。
