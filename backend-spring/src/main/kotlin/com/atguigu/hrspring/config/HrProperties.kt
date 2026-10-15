package com.atguigu.hrspring.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** 绑定 application.yaml 的 hr 段。字段名用驼峰，配置键用短横线。 */
@ConfigurationProperties(prefix = "hr")
data class HrProperties(
    val cache: CacheProperties = CacheProperties(),
    val auth: AuthProperties = AuthProperties(),
    val security: SecurityProperties = SecurityProperties(),
    val cors: CorsProperties = CorsProperties(),
    val mcp: McpProperties = McpProperties(),
    val export: ExportProperties = ExportProperties(),
) {
    data class McpProperties(
        /** 本地 DSH 调用 /mcp 用的固定口令。为空则只接受管理员 JWT。 */
        val serviceToken: String = "",
    )

    data class CacheProperties(
        /** Redis 缓存 TTL，秒。键前缀是 hrs:。 */
        val ttlSeconds: Long = 600,
    )

    data class AuthProperties(
        /** 至少 32 字节。空密钥时签发会失败。 */
        val jwtSecret: String = "",
        /** JWT iss。 */
        val issuer: String = "hr-api",
        /** JWT aud。 */
        val audience: String = "hr-api-client",
        /** 访问令牌有效期，秒。 */
        val ttlSeconds: Long = 3600,
        /** 启动时补齐缺的表，并在没有管理员时创建下面的账号。 */
        val initializeSchema: Boolean = true,
        /** 也是 MCP 固定口令对应的执行身份。关闭建表后仍要填对，已有账号不会被重置。 */
        val adminUsername: String = "admin",
        /** 只在库里还没有该用户时使用。 */
        val adminPassword: String = "admin123456",
        /** 权限目录的进程内短缓存，秒。 */
        val permissionCacheSeconds: Long = 3,
        val loginRateLimit: LoginRateLimitProperties = LoginRateLimitProperties(),
    ) {
        /** 登录失败滑动窗口。成功只清账号维度，IP 维度保留。 */
        data class LoginRateLimitProperties(
            val enabled: Boolean = true,
            /** 窗口长度，秒。 */
            val windowSeconds: Long = 300,
            /** 同一账号在窗口内允许的失败次数。 */
            val maxAccountFailures: Int = 8,
            /** 同一 IP 在窗口内允许的失败次数。 */
            val maxAddressFailures: Int = 30,
        )
    }

    data class SecurityProperties(
        /** 可信反向代理。空列表只信任本机回环上的 X-Forwarded-For。 */
        val trustedProxies: List<String> = emptyList(),
    )

    data class CorsProperties(
        /** 空则放行 localhost、127.0.0.1、::1 的任意端口。* 表示全部放行。 */
        val allowedHosts: List<String> = emptyList(),
    )

    data class ExportProperties(
        /** 写进下载链接的浏览器可访问地址，不含路径。 */
        val publicBaseUrl: String = "http://127.0.0.1:8081",
        /** 导出文件保留时间，秒。服务里会夹到 60..86400。 */
        val ttlSeconds: Long = 1800,
        /** 空则使用系统临时目录下的 hr-spring-exports。 */
        val directory: String = "",
        /**
         * 生成 PDF 时优先嵌入的字体，ttf 或 ttc。
         * 空则按候选列表自动找。缺字会继续试后面的字体，不会把这个文件移出队列。
         */
        val pdfFont: String = "",
    )
}
