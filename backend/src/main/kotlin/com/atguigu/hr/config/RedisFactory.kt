package com.atguigu.hr.config

import io.ktor.server.config.ApplicationConfig
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.codec.StringCodec
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Lettuce 异步 Redis 连接。
 * 一条 StatefulRedisConnection 可被多协程共用；命令返回 RedisFuture，await() 只挂起协程。
 * 启动时连不上 Redis 不致命：缓存层会旁路（见 RedisCache.cacheAttempt），
 * 之后健康检查会尝试重连——建连挂起协程不阻塞线程，任一时刻只有一次建连，
 * 失败后进入退避，避免并发健康检查排队重试拖慢请求线程。
 */
object RedisFactory {
    private const val RECONNECT_BACKOFF_MS = 5_000L
    private val log = LoggerFactory.getLogger(RedisFactory::class.java)
    private var client: RedisClient? = null
    private var connection: StatefulRedisConnection<String, String>? = null
    private var uri: RedisURI? = null
    private val reconnectMutex = Mutex()

    /** 重连失败后的退避截止时间（System.nanoTime）；internal 供测试断言。 */
    internal var reconnectNotBefore = 0L
        private set

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
        runBlocking { tryOpen() }
    }

    /**
     * 已连接直接返回 true；未连接时尝试建连，失败只记日志并退避。
     * 互斥锁让并发调用共享同一次建连，排队只是挂起协程，不占用线程。
     */
    private suspend fun tryOpen(): Boolean {
        if (connection != null) return true
        if (System.nanoTime() < reconnectNotBefore) return false
        return reconnectMutex.withLock {
            if (connection != null) return@withLock true
            if (System.nanoTime() < reconnectNotBefore) return@withLock false
            val redisUri = uri ?: return@withLock false
            val newClient = RedisClient.create(redisUri)
            val opened = runCatching {
                val newConnection = newClient.connectAsync(StringCodec.UTF8, redisUri).await()
                connection = newConnection
                client?.shutdown()
                client = newClient
                log.info("Configured non-blocking Lettuce Redis at {}:{}", redisUri.host, redisUri.port)
            }.onFailure { cause ->
                newClient.shutdown()
                log.warn(
                    "Redis at {}:{} unavailable; cache bypassed until reconnect: {}",
                    redisUri.host,
                    redisUri.port,
                    cause.message,
                )
            }.isSuccess
            if (!opened) reconnectNotBefore = System.nanoTime() + RECONNECT_BACKOFF_MS * 1_000_000
            opened
        }
    }

    /** 健康检查用。未连接时先尝试重连（退避期内直接跳过），失败返回 false，不抛给调用方。 */
    suspend fun ping(): Boolean {
        if (!tryOpen()) return false
        return runCatching { async.ping().await() == "PONG" }.isSuccess
    }

    fun shutdown() {
        connection?.close()
        client?.shutdown()
    }

    internal fun resetForTest() {
        connection?.close()
        connection = null
        client?.shutdown()
        client = null
        uri = null
        reconnectNotBefore = 0
    }
}
