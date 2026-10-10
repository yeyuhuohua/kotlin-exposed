# HR Spring 后端

Ktor 后端（`backend/`）的 Spring Boot 复刻版：Kotlin + Spring Boot 4 + MyBatis-Plus，
同一套 `atguigudb` MySQL 库与 Redis，接口路径、信封格式、权限模型与 Ktor 版保持一致。
额外通过 Spring AI 把全部业务接口注册为 MCP 工具（Streamable HTTP，入口 `/mcp`），供 DSH 等 MCP 客户端调用。

## 本地启动

需要 JDK 21、MySQL（已有 `atguigudb` 库）和 Redis。端口 **8081**，可与 Ktor 版（8080）同时运行。

首次克隆后准备本机配置：

```sh
cp src/main/resources/application.example.yaml src/main/resources/application.yaml
```

填写 `application.yaml`：数据库密码、至少 32 字节的随机 `hr.auth.jwt-secret`、初始管理员密码。
该文件已加入忽略规则，不会提交。DSH 调用 MCP 时设置 `hr.mcp.service-token`，并与 `dsh/docker-compose.yml` 的 `HR_MCP_TOKEN` 保持一致。

```sh
./gradlew bootRun
```

- 接口文档：<http://127.0.0.1:8081/swagger-ui/index.html>
- 健康检查：<http://127.0.0.1:8081/api/health>
- MCP 端点：<http://127.0.0.1:8081/mcp>（管理员 Bearer JWT 或 `hr.mcp.service-token`）

首次启动且 `hr.auth.initialize-schema: true` 时，会自动创建自建表（用户、角色、权限、菜单、审计）
并播种角色、菜单与初始管理员；HR 业务表沿用 Ktor 版已存在的结构。

## 测试与构建

```sh
./gradlew test    # 单元测试不依赖 MySQL/Redis
./gradlew build   # 产物在 build/libs/
```

## 部署

```sh
./gradlew bootJar
java -jar build/libs/backend-spring-0.0.1.jar
```

生产环境通过环境变量或外部 `application.yaml`（`--spring.config.additional-location`）注入数据库、
Redis 与 JWT 密钥，不要把凭据打进镜像。前端切到本后端：开发期改 `frontend/.env.local` 的
`API_PROXY_TARGET=http://127.0.0.1:8081`；生产在网关把 `/api` 反代到 8081，并配置 `hr.cors.allowed-hosts`。
