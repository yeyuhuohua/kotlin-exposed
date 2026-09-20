package com.atguigu.hr.geography

import org.jetbrains.exposed.v1.core.Table

/** 国家与区域表的 Exposed 映射；字段类型、长度和可空性对应现有 MySQL 结构。 */

object Regions : Table("regions") { // 地区
    val regionId = integer("region_id")
    val regionName = varchar("region_name", 25).nullable()
    override val primaryKey = PrimaryKey(regionId)
}
