package com.atguigu.hr.config

import io.ktor.server.config.MapApplicationConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Redis 重连行为：失败进入退避、并发调用合并为一次建连；用可控替身代替固定端口假设。 */
class RedisFactoryReconnectTest {
    @AfterTest
    fun reset() = RedisFactory.resetForTest()

    private fun connectWithStub(attempts: AtomicInteger, connect: suspend () -> Unit) {
        RedisFactory.connectFn = {
            attempts.incrementAndGet()
            connect()
            throw RuntimeException("stub: redis unavailable")
        }
        RedisFactory.connect(
            MapApplicationConfig(
                "redis.host" to "127.0.0.1",
                "redis.port" to "6379",
                "redis.password" to "",
            ),
        )
    }

    @Test
    fun `建连失败后进入退避，退避期内不再尝试`() = runBlocking {
        val attempts = AtomicInteger(0)
        connectWithStub(attempts) { }
        assertEquals(1, attempts.get(), "connect 完成首次尝试")
        val backoff = RedisFactory.reconnectNotBefore
        assertTrue(backoff > System.nanoTime(), "失败后必须设置退避")

        assertFalse(RedisFactory.ping())
        assertFalse(RedisFactory.ping())
        assertEquals(1, attempts.get(), "退避期内的 ping 不得发起新的建连")
        assertEquals(backoff, RedisFactory.reconnectNotBefore)
    }

    @Test
    fun `正在建连时的并发调用合并为同一次尝试`() = runBlocking {
        val attempts = AtomicInteger(0)
        // 建连需要 300ms 并注定失败：并发 ping 在这期间到达，必须共享同一次尝试
        connectWithStub(attempts) { delay(300) }
        assertEquals(1, attempts.get())
        // 清掉启动尝试留下的退避，让接下来的并发 ping 真正走到建连路径
        RedisFactory.reconnectNotBefore = 0
        val results = List(5) { async { RedisFactory.ping() } }.awaitAll()
        assertTrue(results.none { it })
        assertEquals(2, attempts.get(), "互斥锁合并并发重连：5 个并发调用只增加一次建连尝试")
    }
}
