package com.atguigu.hrspring.common.cache

import com.atguigu.hrspring.common.api.PageParams
import com.atguigu.hrspring.common.api.ApiList
import com.atguigu.hrspring.config.RecordingCache
import com.atguigu.hrspring.config.cacheSerializer
import com.atguigu.hrspring.dto.DepartmentDto
import tools.jackson.databind.exc.InvalidTypeIdException
import org.springframework.data.redis.serializer.SerializationException
import org.springframework.cache.concurrent.ConcurrentMapCache
import org.springframework.cache.transaction.TransactionAwareCacheDecorator
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CacheConsistencyTest {
    @Test
    fun `业务空列表单条列表及空分页可以完成Redis序列化往返`() {
        val serializer = cacheSerializer()
        val department = DepartmentDto(10, "Operations", null, null)
        for (value in listOf(
            emptyList<DepartmentDto>(),
            listOf(department),
            listOf(department, department.copy(departmentId = 20)),
            ApiList(0, emptyList<DepartmentDto>()),
        )) {
            assertEquals(value, serializer.deserialize(serializer.serialize(value)))
        }
    }

    @Test
    fun `空集合白名单不允许读取无关的任意类型`() {
        val serializer = cacheSerializer()
        val bytes = serializer.serialize(java.io.File("untrusted"))
        val error = assertFailsWith<SerializationException> { serializer.deserialize(bytes) }
        assertIs<InvalidTypeIdException>(error.cause)
    }

    @AfterTest
    fun cleanup() {
        CacheLoads.clear()
        CacheHitMarker.clear()
    }

    @Test
    fun `缺省筛选与横线及含分隔符的真实筛选互不碰撞`() {
        val page = PageParams()
        assertNotEquals(CacheKeys.employees(page, null, null, null, false),
            CacheKeys.employees(page, null, "-", null, false))
        assertNotEquals(CacheKeys.employees(page, null, null, null, false),
            CacheKeys.employees(page, null, null, "-", false))
        assertNotEquals(CacheKeys.employees(page, null, "a:b", "c", false),
            CacheKeys.employees(page, null, "a", "b:c", false))
    }

    @Test
    fun `另一个实例失效后分页与详情的在途旧回填均不能复活`() {
        val storage = ConcurrentMapCache("employees")
        val epoch = java.util.concurrent.atomic.AtomicInteger()
        val first = RecordingCache(storage, { epoch.get().toString() }, { epoch.incrementAndGet() })
        val second = RecordingCache(storage, { epoch.get().toString() }, { epoch.incrementAndGet() })
        val loading = CountDownLatch(1)
        val complete = CountDownLatch(1)
        val error = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val worker = thread {
            try {
                assertNull(first.get("50:0"))
                assertNull(first.get("100:details"))
                loading.countDown()
                assertTrue(complete.await(5, TimeUnit.SECONDS))
                first.put("50:0", "old-page")
                first.put("100:details", "old-details")
            } catch (e: Throwable) {
                error.set(e)
            } finally {
                loading.countDown()
                CacheLoads.clear()
            }
        }
        try {
            assertTrue(loading.await(5, TimeUnit.SECONDS))
            second.clear()
        } finally {
            complete.countDown()
            worker.join()
        }
        error.get()?.let { throw it }
        assertNull(second.get("50:0"))
        assertNull(second.get("100:details"))
        second.put("50:0", "new-page")
        assertEquals("new-page", first.get("50:0")?.get())
    }

    @Test
    fun `失效失败期间旁路且退避结束后先换代再读`() {
        var now = 0L
        var version = "old"
        var failure = false
        var rotations = 0
        val state = EpochState(read = { version }, rotate = {
            rotations++
            if (failure) throw IllegalStateException("offline")
            version = "version-$rotations"
        }, clock = { now })
        assertEquals("version-1", state.current())
        failure = true
        state.invalidate()
        repeat(20) { assertNull(state.current()) }
        assertEquals(2, rotations)
        now = 5_000_000_001L
        failure = false
        assertEquals("version-3", state.current())
    }

    @Test
    fun `重启不接受上个进程遗留的失效失败版本`() {
        var version = "old"
        val restarted = EpochState(read = { version }, rotate = { version = "new" })
        assertEquals("new", restarted.current())
    }

    @Test
    fun `事务延迟回填在提交前被失效也不会写入新版本`() {
        var version = "before"
        val cache = RecordingCache(TransactionAwareCacheDecorator(ConcurrentMapCache("jobs")),
            { version }, { version = "after" })
        TransactionSynchronizationManager.initSynchronization()
        try {
            assertNull(cache.get("all"))
            cache.put("all", "old")
            version = "external-write"
            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
        assertNull(cache.get("all"))
    }

    @Test
    fun `失效通知等到事务提交而不是回滚时发布`() {
        var notifications = 0
        val cache = RecordingCache(ConcurrentMapCache("jobs"), { "v" }, { notifications++ })
        TransactionSynchronizationManager.initSynchronization()
        try {
            cache.clear()
            assertEquals(0, notifications)
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
        assertEquals(0, notifications)
        TransactionSynchronizationManager.initSynchronization()
        try {
            cache.clear()
            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
            assertEquals(1, notifications)
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }
}
