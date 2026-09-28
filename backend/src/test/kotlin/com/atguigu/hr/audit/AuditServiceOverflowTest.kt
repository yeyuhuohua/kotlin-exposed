package com.atguigu.hr.audit

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
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
        // 写入端永远不完成，2 个 worker 各挂住一条，队列随后填满
        AuditService.loginWriter = { _, _, _, _, _, _ -> awaitCancellation() }
        val submitted = 1_200
        repeat(submitted) {
            AuditService.recordLogin("reader", null, "127.0.0.1", null, success = true, errorCode = null)
        }
        assertTrue(
            AuditService.droppedCount() >= submitted - 1_000 - 2,
            "容量 1000 + 2 个挂起的 worker 之外的记录必须被丢弃",
        )
    }
}
