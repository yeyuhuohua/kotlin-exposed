package com.atguigu.hr.config

import io.ktor.server.config.ApplicationConfig
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Lettuce 异步 Redis 连接。
 * 一条 StatefulRedisConnection 可被多协程共用；命令返回 RedisFuture，await() 只挂起协程。
 * 启动时连不上 Redis 不致命：缓存层会旁路（见 RedisCache.cacheAttempt），
 * 之后每次健康检查都会尝试重连，恢复后缓存自动生效。
 */
object RedisFactory {
    private val log = LoggerFactory.getLogger(RedisFactory::class.java)
    private var client: RedisClient? = null
    private var connection: StatefulRedisConnection<String, String>? = null
    private var uri: RedisURI? = null

    /** 缓存过期秒数，默认 10 分钟，来自 application.yaml redis.ttlSeconds。 */
    var ttlSeconds: Long = 600
        private set

    val async: RedisAsyncCommands<String, String>
        get() = connection?.async() ?: throw IllegalStateException("Redis 未连接，缓存旁路")

    fun connect(config: ApplicationConfig) {
        val host = config.property("redis.host").getString()
        val port = config.property("redis.port").getString().toInt()
        val password = config.propertyOrNull("redis.password")?.getString().orEmpty()
        ttlSeconds = config.propertyOrNull("redis.ttlSeconds")?.getString()?.toLongOrNull() ?: 600L

        uri = RedisURI.Builder.redis(host, port).apply {
            if (password.isNotBlank()) withPassword(password.toCharArray())
            // 命令超时只兜底（RedisCache 另有 2 秒预算），主要避免启动时久等挂起的 TCP。
            withTimeout(Duration.ofSeconds(5))
        }.build()
        tryOpen()
    }

    /** 已连接直接返回 true；未连接时尝试建连，失败只记日志，由缓存层旁路。 */
    @Synchronized
    private fun tryOpen(): Boolean {
        if (connection != null) return true
        val redisUri = uri ?: return false
        return runCatching {
            val newClient = RedisClient.create(redisUri)
            try {
                val newConnection = newClient.connect()
                connection = newConnection
                client?.shutdown()
                client = newClient
                log.info("Configured non-blocking Lettuce Redis at {}:{}", redisUri.host, redisUri.port)
            } catch (cause: Exception) {
                newClient.shutdown()
                throw cause
            }
        }.onFailure { log.warn("Redis at {}:{} unavailable; cache bypassed until reconnect", redisUri.host, redisUri.port) }
            .isSuccess
    }

    /** 健康检查用。未连接时先尝试重连，失败返回 false，不抛给调用方。 */
    suspend fun ping(): Boolean {
        if (connection == null) tryOpen()
        return runCatching { async.ping().await() == "PONG" }.isSuccess
    }

    fun shutdown() {
        connection?.close()
        client?.shutdown()
    }
}
