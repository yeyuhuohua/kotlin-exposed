package com.atguigu.hr.common.cache

import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Redis 不可用时的旁路行为（测试中从未调用 RedisFactory.connect）：
 * 读写都直接回源不抛异常；失效仍会把分组标记为脏，保证 Redis 恢复后不会暴露旧缓存。
 */
class RedisCacheDownTest {
    private val group = RedisCache.Group.EMPLOYEES

    @BeforeTest
    fun resetGroup() {
        group.version.set(RedisCache.Version())
        group.recoverNotBefore.set(0)
        group.failNotBefore.set(0)
    }

    @Test
    fun `Redis 不可用时 getOrLoad 直接回源`() = runBlocking {
        val cached = RedisCache.getOrLoad("hr:employees:list:down-test") { "from-db" }
        assertFalse(cached.hit)
        assertEquals("from-db", cached.value)
        Unit
    }

    @Test
    fun `Redis 不可用时 withInvalidation 返回业务结果并标记分组脏`() = runBlocking {
        val result = RedisCache.withInvalidation("hr:employees:list:any") { 42 }
        assertEquals(42, result)
        assertTrue(group.version.get().dirty, "失效清理失败时分组必须保持脏，不能重新暴露旧缓存")
    }

    @Test
    fun `写操作抛异常时失效仍执行且异常向上抛`() = runBlocking {
        val outcome = runCatching {
            RedisCache.withInvalidation("hr:employees:list:any") { throw IllegalStateException("db failed") }
        }
        assertTrue(outcome.isFailure)
        assertTrue(group.version.get().dirty, "写库可能已部分提交，异常时也必须完成失效标记")
    }

    @Test
    fun `读取或回填失败后进入退避，退避期内不再触碰 Redis`() = runBlocking {
        RedisCache.getOrLoad("hr:employees:list:fail-backoff") { "v1" }
        val backoff = group.failNotBefore.get()
        assertTrue(backoff > System.nanoTime(), "Redis 操作失败后必须设置退避截止时间")
        RedisCache.getOrLoad("hr:employees:list:fail-backoff-2") { "v2" }
        assertEquals(backoff, group.failNotBefore.get(), "退避期内的读取与回填直接跳过，不再串行等待超时")
    }
}
