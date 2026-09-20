package com.atguigu.hr.location

import org.jetbrains.exposed.v1.core.Table

/** 办公地点表的 Exposed 映射；字段类型、长度和可空性对应现有 MySQL 结构。 */

object Locations : Table("locations") { // 地点
    val locationId = integer("location_id")
    val streetAddress = varchar("street_address", 40).nullable()
    val postalCode = varchar("postal_code", 12).nullable()
    val city = varchar("city", 30)
    val stateProvince = varchar("state_province", 25).nullable()
    val countryId = char("country_id", 2).nullable()
    override val primaryKey = PrimaryKey(locationId)
}
