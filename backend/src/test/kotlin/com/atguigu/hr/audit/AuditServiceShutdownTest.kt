package com.atguigu.hr.audit

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** 优雅停机：先关闭入队并等待消费者排空；超时取消并统计未写出的损失。 */
class AuditServiceShutdownTest {
    @BeforeTest
    fun reset() {
        AuditService.resetForTest()
        AuditService.drainTimeoutMs = 5_000
    }

    @Test
    fun `停机前排空已入队的记录`() = runBlocking {
        val written = CopyOnWriteArrayList<String>()
        AuditService.loginWriter = { username, _, _, _, _, _, _ -> written.add(username) }
        repeat(3) { index ->
            AuditService.recordLogin("user-$index", null, "127.0.0.1", null, success = true, errorCode = null)
        }
        AuditService.shutdown()
        // 两个 worker 并发消费，完成顺序不做假定
        assertEquals(setOf("user-0", "user-1", "user-2"), written.toSet(), "已入队的记录必须在停机前写出")
    }

    @Test
    fun `排空超时取消消费者并统计损失`() = runBlocking {
        AuditService.drainTimeoutMs = 200
        val gate = CompletableDeferred<Unit>()
        val written = AtomicInteger(0)
        try {
            // 两个 worker 各挂住一条在途写入，队列里还有 3 条等待
            AuditService.loginWriter = { _, _, _, _, _, _, _ -> gate.await(); written.incrementAndGet() }
            repeat(5) { index ->
                AuditService.recordLogin("user-$index", null, "127.0.0.1", null, success = true, errorCode = null)
            }
            AuditService.shutdown()
            assertEquals(0, written.get(), "挂起的在途写入被取消，不应完成")
            assertEquals(5, AuditService.droppedCount(), "在途 2 条 + 队列 3 条都必须计入丢弃，不能漏计")
        } finally {
            gate.complete(Unit)
        }
    }
}
