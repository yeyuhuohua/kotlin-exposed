package com.atguigu.hr.audit

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        try {
            // 两个 worker 都挂住，队列里还有 3 条等待
            AuditService.loginWriter = { _, _, _, _, _, _, _ -> gate.await() }
            repeat(5) { index ->
                AuditService.recordLogin("user-$index", null, "127.0.0.1", null, success = true, errorCode = null)
            }
            AuditService.shutdown()
            assertTrue(AuditService.droppedCount() >= 3, "未写出的积压记录要计入丢弃，不能静默丢失")
        } finally {
            gate.complete(Unit)
        }
    }
}
