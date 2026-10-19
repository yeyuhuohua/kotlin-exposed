# HR Spring 后端

Ktor 后端的 Spring Boot 复刻版：Kotlin + Spring Boot 4 + MyBatis-Plus，使用相同的 `atguigudb`
MySQL 库和 Redis。REST 路径、响应信封和角色权限模型与 Ktor 版对齐，另通过 Spring AI
提供 HR MCP 工具（Streamable HTTP，默认 `/mcp`），供 DSH 等客户端调用。

本文件的命令均在 `backend-spring/` 下执行，除非明确说明其他目录。
业务与接口契约见 [仓库 README](../README.md)，角色授权规则见 [认证指南](../backend/AUTH.md)。
认证指南中的 Ktor 配置键和旧账号权限迁移流程不直接适用于 Spring。

## 环境与本机配置

需要 JDK 21、已有 HR 表和视图的 MySQL，以及 Redis。默认端口 8081，可与 Ktor（8080）同时运行。
应用不会创建数据库或导入 HR 业务数据，所需表见 [仓库 README](../README.md)。

首次准备配置：

```sh
cp src/main/resources/application.example.yaml src/main/resources/application.yaml
```

已有配置不要覆盖。填写数据库连接参数、至少 32 字节的随机 `hr.auth.jwt-secret`、
管理员用户名和独立设置的强密码。可用 `openssl rand -base64 48` 生成 JWT 密钥。
本机配置被 Git 忽略，也由 `processResources` 排除，不会被打进 JAR。

| 配置 | 作用 |
| --- | --- |
| `server.port` / `address` | 监听地址与端口，示例为 `0.0.0.0:8081` |
| `spring.datasource.*` | JDBC URL、MySQL 账号和密码；URL 的 TLS 参数应与服务端能力一致 |
| `spring.data.redis.*` | Redis 地址、密码和超时 |
| `hr.cache.ttl-seconds` | 业务缓存 TTL，默认 600 秒 |
| `hr.auth.jwt-secret` / `issuer` / `audience` | JWT 签名与校验；双后端共用 Token 时三项都要一致 |
| `hr.auth.ttl-seconds` | Token 有效期，默认 3600，允许 60-86400 秒 |
| `hr.auth.initialize-schema` | 是否创建认证、菜单、审计表并执行初始播种 |
| `hr.auth.admin-username` | 初始管理员、受保护管理员和 MCP 固定口令的执行身份 |
| `hr.auth.admin-password` | 仅在初始化且指定账号不存在时创建管理员，已有密码不覆盖 |
| `hr.auth.permission-cache-seconds` | 进程内鉴权缓存，默认 3 秒，设为 0 关闭 |
| `hr.auth.login-rate-limit.*` | 账号/IP 两维度登录失败限流，仅本实例计数 |
| `hr.security.trusted-proxies` | 用于来源 IP 解析的可信直连代理地址 |
| `hr.cors.allowed-hosts` | 跨域来源；可写完整 Origin，主机形式会展开成 http/https |
| `hr.mcp.service-token` | MCP 固定口令，空时只接受管理员 JWT |
| `spring.ai.mcp.server.streamable-http.mcp-endpoint` | MCP 路径，默认 `/mcp` |
| `springdoc.swagger-ui.path` / `springdoc.api-docs.path` | 文档入口与 OpenAPI 路径 |

配置示例见 [application.example.yaml](src/main/resources/application.example.yaml)。
两种后端的配置键不同，不能直接复制 Ktor 的 `application.yaml` 来运行 Spring。

## IDEA Debug 启动

1. 在 IDEA 中关联 `backend-spring/build.gradle.kts`，使用本模块 Gradle Wrapper 与 JDK 21。
2. 创建 Spring Boot 或 Kotlin 运行配置，主类为 `com.atguigu.hrspring.ApplicationKt`，使用 Spring 模块 classpath。
3. 工作目录设为 `backend-spring/`；入口也支持从仓库根目录查找本机配置。
4. 点击 Debug，等待启动成功日志和文档地址。

入口通过 `spring.config.additional-location` 读取源码目录的本机配置，不能依赖它出现在构建 classpath 中。
需要覆盖配置时，在程序参数中传入 `--spring.config.additional-location=file:/path/to/application.yaml`。
如果手动指定了该参数，默认的源码配置位置会被替换。

| 入口 | 默认地址 |
| --- | --- |
| Knife4j | <http://127.0.0.1:8081/doc.html> |
| Swagger UI | <http://127.0.0.1:8081/swagger-ui/index.html> |
| Swagger UI 快捷入口 | <http://127.0.0.1:8081/swagger> |
| OpenAPI JSON | <http://127.0.0.1:8081/v3/api-docs> |
| 健康检查 | <http://127.0.0.1:8081/api/health> |
| MCP | <http://127.0.0.1:8081/mcp> |

启动成功后控制台打印 Knife4j、Swagger UI 和 OpenAPI JSON 地址，包含实际端口、HTTP/HTTPS、context path 和 servlet path。
禁用 OpenAPI 或 Swagger UI 时，相应入口不会打印。
`/doc.html` 提供本地 JAR 中的 Knife4j 页面，`/swagger` 跳转到 Swagger UI；两者共用 springdoc 生成的接口文档。

```sh
curl -i http://127.0.0.1:8081/api/health
```

MySQL 和 Redis 都可用时返回 200；任一依赖 DOWN 时返回 503、`dependency_unavailable`，
`data` 保留健康详情。Redis 故障期间业务缓存旁路，不需要将 503 误判为进程没有启动。

## 初始化与管理员

`hr.auth.initialize-schema: true` 时创建缺失的认证、菜单和审计表，并在事务中播种角色、菜单和管理员。
这不是业务库迁移工具，不会更改 HR 业务表或修复已有表的列结构。
`READER` 默认权限只在首次创建权限档案时播种；已有档案即使权限为空，重启也不会恢复授权。
指定管理员已存在时不会覆盖密码、角色或启用状态。

生产初始化完成后可设为 `false`，使用权限较小的运行数据库账号，并移除不再需要的初始密码。
仍需保留正确的 `hr.auth.admin-username`，因为管理员保护和 MCP 固定口令执行身份会读取它。
内置 `admin` 和配置指定的管理员都不能被删除、停用、降级或由其他管理员重置密码，管理员可以修改自己的密码。

## MCP 与 DSH

MCP 过滤器保护默认及配置指定的端点，支持以下两种 `Authorization: Bearer ...`：

- 有效的 ADMIN JWT：需通过签名、有效期、账号状态、角色状态和 Token 版本检查。
- 与 `hr.mcp.service-token` 一致的固定口令：每次以 `hr.auth.admin-username` 对应的启用 ADMIN 账号执行工具。

固定口令不代替管理员账号；账号不存在、停用或不再属于启用 ADMIN 角色时同样拒绝访问。
Bearer 认证方案忽略大小写。普通用户不能访问 MCP，MCP 也不通过前端页面权限提供部分授权。
工具直接调用业务 Service，共用参数校验和缓存，返回业务 DTO 或工具结果，不套 REST 的 `ApiResult` 信封。
工具含查询、写入、删除、用户和角色管理，不能当成只读接口。

连接 DSH 时：

1. 设置非空 `hr.mcp.service-token`，并让 `dsh/.env` 的 `HR_MCP_TOKEN` 使用相同值。
2. 确保 `hr.auth.admin-username` 对应账号仍启用且属于 ADMIN。
3. DSH 的 HR 登录态校验由 `HR_API_BASE` 指向 Spring；MCP URL 则单独保存在 DSH profile 的 `mcp-hr` 配置。
4. 改端口或 MCP 路径后，同时调整 DSH 中这两处地址；它们不会互相自动同步。

支持 Streamable HTTP 的 MCP 客户端应完成 `initialize` 与 `tools/list` 协议交互，
不能把浏览器直接 GET `/mcp` 当成工具连通性验证。可先验证客户端能列出 `get_current_user`、`list_employees` 等工具。
DSH 安装、模型与 Cookie 配置见 [DSH 指南](../dsh/README.md)。

## 缓存、校验与源码

Service 使用 Spring Cache 的 `@Cacheable` / `@CacheEvict`，业务缓存值采用 `hrs:` 命名空间，
与 Ktor 的 `hr:` 缓存格式分开。两个后端通过同一 Redis 的 `hr:cache:epoch` 和
`hr:auth:invalidate:epoch` 共享失效版本，失效成功后旧版本不能再命中，在途旧回填也不能进入新版本。
事务写入在提交后发布失效，回滚不发布；旧版本缓存值按 TTL 自然过期。
Redis 故障时按退避间隔旁路，恢复和启动时先换代再使用业务缓存。
进程在数据库提交后崩溃、失效通知失败或外部 SQL 写入仍可能让其他实例读到旧值，不能视作强一致缓存。

HTTP 参数使用 Bean Validation；空更新、非法类型、超范围值和未知 PATCH 字段均拒绝。
HTTP 层由 Spring Boot 4 的 Jackson 3 处理；过滤器、Redis 序列化与 PATCH 辅助解析使用 Jackson 2。
因此两个 Kotlin Jackson 模块都有用途，不应仅因名字相近就移除其中一个。

| 源码目录 | 职责 |
| --- | --- |
| `controller/` | REST 路由、响应信封与 OpenAPI 描述 |
| `service/` | 业务逻辑、事务、缓存和跨模块读取 |
| `entity/` / `mapper/` | MyBatis-Plus 实体和数据库查询 |
| `dto/` / `common/validation/` | 请求、返回值与可复用参数约束 |
| `auth/` / `mcp/` | JWT、角色权限、登录限流与 MCP 工具及端点鉴权 |
| `config/` | JSON、缓存、Web、初始化和启动日志 |
| `audit/` | 登录和 `/api` 调用的异步审计与查询 |

## 测试与部署

```sh
./gradlew test
./gradlew build
./gradlew bootJar
```

单元与 HTTP 测试不依赖真实 MySQL/Redis，不使用 H2；覆盖鉴权、MCP、部署路径、JSON 类型、
管理员保护、缓存回填与恢复、审计和初始化授权。真实业务库与模型联调仍需在目标环境验证。

部署使用可执行 JAR，并显式指定外部配置：

```sh
java -jar build/libs/kotlin-spring-0.0.1.jar \
  --spring.config.additional-location=file:/path/to/application.yaml
```

不要使用 `-plain.jar` 作为独立部署包。外部配置和凭据不进入镜像或版本控制。
前端开发默认直连本服务（8081，跨域由本服务 CORS 放行），无需额外配置；
生产反向代理 `/api` 和文档路径，助手还需 `/mcp` 的 Streamable HTTP 转发，避免代理缓冲或截断协议连接。
自定义 `server.servlet.context-path` 或 `spring.mvc.servlet.path` 后要同步更新客户端 URL 和网关路径。
文档默认公开，对外部署按需要在网关限制。

## 常见问题

| 现象 | 检查与处理 |
| --- | --- |
| IDEA Debug 找不到配置或 JWT 密钥不合法 | 检查 `ApplicationKt`、模块 classpath、工作目录、本机配置和覆盖参数 |
| MySQL TLS 或连接失败 | 检查 JDBC URL、账号权限和服务端 TLS，示例 URL 默认要求 SSL |
| MCP 返回 401/403 | 检查 ADMIN JWT，或固定口令和配置管理员状态；普通用户不能调用 |
| DSH 登录成功但 HR 工具不可用 | `HR_API_BASE` 与 MCP URL 是两份配置，逐项检查地址、端口和固定口令 |
| 重启后 READER 没有业务权限 | 已有空权限不会自动重置，需管理员在角色权限页显式授权 |
| 修改菜单后页面跳转不正确 | 核对数据库路径和前端组件映射，刷新前端或重新登录 |
| Swagger 跳转路径不正确 | 检查 context/servlet path、配置的 Swagger 路径和网关转发规则 |
