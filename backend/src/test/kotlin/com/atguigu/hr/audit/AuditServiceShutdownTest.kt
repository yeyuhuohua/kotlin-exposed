package com.atguigu.hr.audit

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
    fun `排空超时取消消费者时，在途与排队记录都恰好计一次`() = runBlocking {
        // 写入端在取消后还有清理耗时：复现"先执行 finally 再读快照"的竞态窗口
        repeat(10) {
            AuditService.resetForTest()
            AuditService.drainTimeoutMs = 100
            val gate = CompletableDeferred<Unit>()
            val written = AtomicInteger(0)
            try {
                AuditService.loginWriter = { _, _, _, _, _, _, _ ->
                    try {
                        gate.await()
                        written.incrementAndGet()
                    } finally {
                        withContext(NonCancellable) { delay(30) }
                    }
                }
                repeat(5) { index ->
                    AuditService.recordLogin("user-$index", null, "127.0.0.1", null, success = true, errorCode = null)
                }
                AuditService.shutdown()
                assertEquals(0, written.get(), "挂起的在途写入被取消，不应完成")
                assertEquals(
                    5,
                    AuditService.droppedCount(),
                    "在途 2 条 + 队列 3 条都必须恰好计一次，不能漏计或重复计",
                )
            } finally {
                gate.complete(Unit)
            }
        }
    }
}
