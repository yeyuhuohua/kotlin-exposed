package com.atguigu.hr.config

import io.ktor.server.config.MapApplicationConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Redis 不可达时的重连行为：失败进入退避，退避期内的并发 ping 不再发起新的建连。 */
class RedisFactoryReconnectTest {
    @AfterTest
    fun reset() = RedisFactory.resetForTest()

    @Test
    fun `建连失败后进入退避，退避期内不再尝试`() = runBlocking {
        // 未监听端口，建连立即被拒绝
        RedisFactory.connect(
            MapApplicationConfig(
                "redis.host" to "127.0.0.1",
                "redis.port" to "6390",
                "redis.password" to "",
            ),
        )
        val backoff = RedisFactory.reconnectNotBefore
        assertTrue(backoff > System.nanoTime(), "connect 的首次尝试失败后必须设置退避")

        assertFalse(RedisFactory.ping())
        assertFalse(RedisFactory.ping())
        assertEquals(backoff, RedisFactory.reconnectNotBefore, "退避期内的 ping 不得发起新的建连")
    }

    @Test
    fun `并发健康检查合并为一次建连尝试且立即返回`() = runBlocking {
        RedisFactory.connect(
            MapApplicationConfig(
                "redis.host" to "127.0.0.1",
                "redis.port" to "6390",
                "redis.password" to "",
            ),
        )
        val backoff = RedisFactory.reconnectNotBefore
        // 退避期内的并发 ping 全部快速失败，且不会刷新退避时间
        val results = List(5) { async { RedisFactory.ping() } }.awaitAll()
        assertTrue(results.none { it })
        assertEquals(backoff, RedisFactory.reconnectNotBefore)
    }
}
