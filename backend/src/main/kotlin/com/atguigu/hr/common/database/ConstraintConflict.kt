package com.atguigu.hr.common.database

import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import io.r2dbc.spi.R2dbcException

/** MySQL 完整性约束：1062 重复键，1451/1452 外键。SQLState 23 开头同属约束类。 */
private val mysqlConstraintCodes = setOf(1062, 1451, 1452)

fun Throwable.isConstraintConflict(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is R2dbcDataIntegrityViolationException) return true
        if (current is R2dbcException) {
            val state = current.sqlState
            if (state != null && state.startsWith("23")) return true
            if (current.errorCode in mysqlConstraintCodes) return true
        }
        val msg = current.message.orEmpty()
        if ("Duplicate entry" in msg) return true
        if ("foreign key constraint" in msg.lowercase()) return true
        if ("Cannot delete or update a parent row" in msg) return true
        if ("Cannot add or update a child row" in msg) return true
        current = current.cause
    }
    return false
}
