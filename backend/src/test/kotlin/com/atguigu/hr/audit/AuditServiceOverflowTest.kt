package com.atguigu.hr.audit

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** 数据库变慢导致审计写入积压时：丢弃新记录并计数，绝不影响调用方。 */
class AuditServiceOverflowTest {
    @AfterTest
    fun resetWriters() {
        AuditService.loginWriter = { _, _, _, _, _, _, _ -> }
    }

    @Test
    fun `写入端挂起时超出队列容量的记录被丢弃并计数`() = runBlocking {
        // 写入端在闸门打开前不返回，2 个 worker 各挂住一条，队列随后填满
        val gate = CompletableDeferred<Unit>()
        val written = AtomicInteger(0)
        val droppedBefore = AuditService.droppedCount()
        AuditService.loginWriter = { _, _, _, _, _, _, _ ->
            gate.await()
            written.incrementAndGet()
        }
        try {
            val submitted = 1_200
            repeat(submitted) {
                AuditService.recordLogin("reader", null, "127.0.0.1", null, success = true, errorCode = null)
            }
            assertTrue(
                AuditService.droppedCount() - droppedBefore >= submitted - 1_000 - 2,
                "容量 1000 + 2 个挂起的 worker 之外的记录必须被丢弃",
            )
            // 打开闸门并恢复快速写入端，用完成信号等队列排空：每条记录要么写出、要么被丢弃
            gate.complete(Unit)
            AuditService.loginWriter = { _, _, _, _, _, _, _ -> written.incrementAndGet() }
            withTimeout(10_000) {
                while (written.get() + (AuditService.droppedCount() - droppedBefore) < submitted) {
                    delay(10)
                }
            }
        } finally {
            gate.complete(Unit)
            AuditService.loginWriter = { _, _, _, _, _, _, _ -> }
        }
    }
}
