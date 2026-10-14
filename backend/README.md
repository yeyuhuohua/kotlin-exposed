# Ktor 后端

Kotlin/JVM + Ktor + Exposed R2DBC，使用 MySQL 提供 HR 数据，Redis 提供业务缓存与跨实例失效通知。
默认端口 8080。认证与权限规则见 [AUTH.md](AUTH.md)，整体功能和接口信封见 [仓库 README](../README.md)。
本文件的命令均在 `backend/` 下执行，除非明确说明其他目录。

## 环境与数据库

- 编译、测试和运行使用 JDK 21；Gradle Wrapper 和依赖版本由仓库管理。
- MySQL 需要已有的 `atguigudb` 库、HR 表、员工详情视图和演示表，完整清单见 [仓库 README](../README.md)。
- Redis 用于缓存和多实例撤销通知。不可用时业务缓存旁路，不代表健康检查会返回成功。

仓库没有完整业务库的建库、导入或迁移脚本。应用自动创建的只有认证、菜单和审计表。
首次初始化需要 DDL 与数据读写权限；已有环境可用 `auth.initializeSchema: false` 禁用建表和授权迁移。
该开关不禁止已配置的管理员播种，具体行为见 [AUTH.md](AUTH.md)。

## 本机配置

```sh
cp src/main/resources/application.example.yaml src/main/resources/application.yaml
cp gradle.properties.example gradle.properties
```

复制只需执行一次，已有配置不要覆盖。填写数据库密码、JWT 密钥和首次管理员凭据；
`application.yaml` 与 `gradle.properties` 被 Git 忽略，`application.yaml` 也不进入 JAR。

| 配置 | 作用 |
| --- | --- |
| `ktor.deployment.host` / `port` | 监听地址和端口，示例为 `0.0.0.0:8080` |
| `database.*` | MySQL 地址、库名、账号、密码和 `sslMode` |
| `redis.*` | Redis 地址、密码和业务缓存 TTL，默认 600 秒 |
| `auth.jwtSecret` | 至少 32 字节的签名密钥，留空会拒绝启动 |
| `auth.issuer` / `audience` | Token 签发者和受众，多实例及双后端共用 Token 时保持一致 |
| `auth.ttlSeconds` | Token 有效期，默认 3600，允许 60-86400 秒 |
| `auth.adminUsername` / `adminPassword` | 成对设置；不存在的账号会被创建为管理员，已有账号不覆盖 |
| `auth.permissionCacheSeconds` | 进程内鉴权缓存，默认 3 秒，设为 0 关闭 |
| `auth.loginRateLimit.*` | 账号/IP 两维度登录失败限流，计数仅在本实例 |
| `cors.allowedHosts` | 跨域允许主机，非默认端口要包含端口；具体写法见配置示例 |
| `security.trustedProxies` | 允许用于解析 `X-Forwarded-For` 的直连代理地址 |

可以用 `openssl rand -base64 48` 生成签名密钥，再填入被忽略的本机配置。
JWT 密钥与数据库密码都不应放进前端 `VITE_*` 变量。
若 JDK 不在标准安装位置，在本机 `gradle.properties` 设置 `org.gradle.java.installations.paths`；
不要把绝对 JDK 路径提交到仓库。

## IDEA Debug 启动

1. 在 IDEA 中打开仓库并关联 `backend/build.gradle.kts`，使用 Gradle Wrapper 和 JDK 21。
2. 创建或选择 Kotlin/Ktor 运行配置 `ApplicationKt`，入口为 `com.atguigu.hr.ApplicationKt`。
3. 运行配置使用 `backend` 模块的 classpath，工作目录设为 `backend/`。
4. 点击 Debug。入口从 `src/main/resources/application.yaml` 读取本机配置。

本机需要后端在线时统一使用 IDEA Debug。先检查 8080 是否已有实例，避免再启动 Gradle 进程占用端口。
仓库不分发 `.idea/` 配置，首次导入需要自行建立运行配置。

从其他工作目录启动，或验证部署配置时，可在程序参数中显式指定 `-config=/path/to/application.yaml`。
传入 `-config=` 后不会自动使用源码目录中的本机配置。

| 入口 | 默认地址 |
| --- | --- |
| 健康检查 | <http://127.0.0.1:8080/api/health> |
| Knife4j | <http://127.0.0.1:8080/doc.html> |
| Swagger UI | <http://127.0.0.1:8080/swagger> |
| OpenAPI JSON | <http://127.0.0.1:8080/v3/api-docs> |

```sh
curl -i http://127.0.0.1:8080/api/health
```

健康检查公开。MySQL 与 Redis 都可用时返回 200；任一依赖不可用时返回 503、
`error: dependency_unavailable`，`data` 仍包含各依赖状态。Redis DOWN 时业务请求仍可走数据库。

## 缓存与审计

业务模块负责本模块缓存键与失效，跨业务写入需要失效相关列表、详情和概览。
Ktor 缓存键使用 `hr:` 命名空间；Spring 缓存值使用 `hrs:`，序列化格式不共用。
两个后端通过同一 Redis 的 `hr:cache:epoch` 与 `hr:auth:invalidate:epoch` 感知数据和鉴权变化。
共享版本变化后拒绝旧缓存，回填时再次核对版本，防止在途读取把失效数据重新写回。

Redis 不可用时业务缓存旁路，恢复后再使用缓存。鉴权缓存仍有自己的短 TTL，
无法同步广播时其他实例可能延迟到该 TTL 到期才感知撤销。
数据库提交与失效通知不具备跨系统原子性，进程崩溃或外部 SQL 写入仍需要运维处理缓存一致性。

审计异步记录登录尝试和 `/api` 调用，健康检查不写接口审计，查询实时读库、不经过 Redis。
审计不是同步持久化保证；高负载、写入故障或进程退出可能丢失记录。
`X-Request-Id` 会透传或自动生成，作为日志关联标识。

## 测试与部署

```sh
./gradlew test
./gradlew build
./gradlew buildFatJar
```

测试不依赖真实 MySQL/Redis，包括纯逻辑和 `testApplication` HTTP 鉴权测试，不使用 H2。
测试通过不代表业务库表结构和生产连接已经验证。

部署使用 fat JAR；普通 `kotlin-exposed-0.0.1.jar` 不是含全部依赖的启动包：

```sh
java -jar build/libs/kotlin-exposed-all.jar -config=/path/to/application.yaml
```

前端、API 的反向代理与单页应用回退见 [前端部署指南](../frontend/README.md)。
文档默认公开，需要限制时在网关控制 `/doc.html`、`/swagger`、`/v3`、`/swagger-resources` 和 `/webjars`。

## 常见问题

| 现象 | 检查与处理 |
| --- | --- |
| IDEA 启动找不到配置或提示 JWT 密钥为空 | 使用 `ApplicationKt` 入口，工作目录为 `backend/`；确认本机配置已复制、填写，或传入 `-config=` |
| 找不到 JDK 21 toolchain | 检查 Gradle JVM 与 JDK 安装；非标准位置用本机 `gradle.properties` 指定 |
| `caching_sha2_password` 要求 SSL | 按数据库服务能力调整 `database.sslMode`；示例注释列出了支持的模式 |
| 数据库或视图不存在 | 准备现有 HR 库和 `emp_details_view`，自动建认证表不会补齐业务库 |
| `/api/health` 返回 503 | 查看 `data.database` 与 `data.redis`，区分数据库故障和缓存降级 |
| 登录后接口仍返回 401 | 检查 Token 是否过期、撤销，JWT 密钥/issuer/audience 是否一致，账号与角色是否启用 |
| 菜单存在但页面或接口返回 403 | 页面权限与接口权限独立，检查角色授权和所需的下拉查询接口 |
| 端口 8080 被占用 | 检查 IDEA 中已有的 Debug 实例，避免并行启动同端口后端 |
