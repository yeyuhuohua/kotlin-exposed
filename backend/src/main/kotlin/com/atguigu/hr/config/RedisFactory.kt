package com.atguigu.hr.config

import io.ktor.server.config.ApplicationConfig
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory

/**
 * Lettuce 异步 Redis 连接。
 * 一条 StatefulRedisConnection 可被多协程共用；命令返回 RedisFuture，await() 只挂起协程。
 */
object RedisFactory {
    private val log = LoggerFactory.getLogger(RedisFactory::class.java)
    private lateinit var client: RedisClient
    private lateinit var connection: StatefulRedisConnection<String, String>
    /** 缓存过期秒数，默认 10 分钟，来自 application.yaml redis.ttlSeconds。 */
    var ttlSeconds: Long = 600
        private set

    val async: RedisAsyncCommands<String, String>
        get() = connection.async()

    fun connect(config: ApplicationConfig) {
        val host = config.property("redis.host").getString()
        val port = config.property("redis.port").getString().toInt()
        val password = config.propertyOrNull("redis.password")?.getString().orEmpty()
        ttlSeconds = config.propertyOrNull("redis.ttlSeconds")?.getString()?.toLongOrNull() ?: 600L

        val uri = RedisURI.Builder.redis(host, port).apply {
            if (password.isNotBlank()) withPassword(password.toCharArray())
        }.build()
        client = RedisClient.create(uri)
        connection = client.connect()
        log.info("Configured non-blocking Lettuce Redis at {}:{}", host, port)
    }

    /** 健康检查用。失败返回 false，不抛给调用方。 */
    suspend fun ping(): Boolean = runCatching {
        async.ping().await() == "PONG"
    }.isSuccess

    fun shutdown() {
        if (::connection.isInitialized) connection.close()
        if (::client.isInitialized) client.shutdown()
    }
}
