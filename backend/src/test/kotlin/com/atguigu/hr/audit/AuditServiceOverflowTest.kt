package com.atguigu.hr.audit

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** 数据库变慢导致审计写入积压时：丢弃新记录并计数，绝不影响调用方。 */
class AuditServiceOverflowTest {
    @AfterTest
    fun resetWriters() {
        AuditService.loginWriter = { _, _, _, _, _, _ -> }
    }

    @Test
    fun `写入端挂起时超出队列容量的记录被丢弃并计数`() = runBlocking {
        // 写入端在闸门打开前不返回，2 个 worker 各挂住一条，队列随后填满
        val gate = CompletableDeferred<Unit>()
        AuditService.loginWriter = { _, _, _, _, _, _ -> gate.await() }
        val submitted = 1_200
        repeat(submitted) {
            AuditService.recordLogin("reader", null, "127.0.0.1", null, success = true, errorCode = null)
        }
        assertTrue(
            AuditService.droppedCount() >= submitted - 1_000 - 2,
            "容量 1000 + 2 个挂起的 worker 之外的记录必须被丢弃",
        )
        // 打开闸门并恢复快速写入端，等 worker 排空队列，避免挂起的消费者污染后续测试
        gate.complete(Unit)
        AuditService.loginWriter = { _, _, _, _, _, _ -> }
        val baseline = AuditService.droppedCount()
        withTimeout(5_000) {
            while (true) {
                AuditService.recordLogin("drain", null, "127.0.0.1", null, success = true, errorCode = null)
                if (AuditService.droppedCount() == baseline) break
                delay(10)
            }
        }
    }
}
