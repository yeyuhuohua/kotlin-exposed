package com.atguigu.hr.common.database

import io.r2dbc.spi.R2dbcBadGrammarException
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 约束冲突要映射成 409，识别不出来就会变成 500，所以这里覆盖各类包装形式。 */
class ConstraintConflictTest {
    @Test
    fun `识别重复键与外键冲突`() {
        assertTrue(R2dbcDataIntegrityViolationException("Duplicate entry 'x' for key 'email'").isConstraintConflict())
        assertTrue(
            R2dbcDataIntegrityViolationException("Cannot add or update a child row", "23000", 1452)
                .isConstraintConflict(),
        )
        assertTrue(R2dbcDataIntegrityViolationException("parent row", "23000", 1451).isConstraintConflict())
    }

    @Test
    fun `识别被包装在异常链里的约束冲突`() {
        val wrapped = IllegalStateException(
            "transaction failed",
            RuntimeException("driver error", R2dbcDataIntegrityViolationException("Duplicate entry 'y'")),
        )
        assertTrue(wrapped.isConstraintConflict())
    }

    @Test
    fun `按 SQLState 与错误码兜底识别`() {
        assertTrue(R2dbcDataIntegrityViolationException("boom", "23000", 0).isConstraintConflict())
        assertTrue(RuntimeException("foreign key constraint fails").isConstraintConflict())
    }

    @Test
    fun `普通异常不会被误判成冲突`() {
        assertFalse(IllegalStateException("boom").isConstraintConflict())
        assertFalse(R2dbcBadGrammarException("syntax error", "42000", 1064).isConstraintConflict())
        assertFalse(RuntimeException("Connection refused").isConstraintConflict())
    }
}
