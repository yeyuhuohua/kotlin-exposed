package com.atguigu.hr.geography

import org.jetbrains.exposed.v1.core.Table

/** 国家与区域表的 Exposed 映射；字段类型、长度和可空性对应现有 MySQL 结构。 */

object Countries : Table("countries") { // 国家
    val countryId = char("country_id", 2)
    val countryName = varchar("country_name", 40).nullable()
    val regionId = integer("region_id").nullable()
    override val primaryKey = PrimaryKey(countryId)
}
