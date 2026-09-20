package com.atguigu.hr.demo.order

import org.jetbrains.exposed.v1.core.Table

/** 表名 order 是 MySQL 保留字，Exposed 会按标识符引用。 */
object Orders : Table("order") {
    val orderId = integer("order_id").nullable()
    val orderName = varchar("order_name", 15).nullable()
}
