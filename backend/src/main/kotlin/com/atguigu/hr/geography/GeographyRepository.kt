package com.atguigu.hr.geography

import com.atguigu.hr.config.DatabaseFactory.dbQuery
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.r2dbc.selectAll

/** 国家与区域持久层：使用 R2DBC 挂起事务，并在事务结束前收集查询结果。 */

object GeographyRepository {
    /** 查询国家列表。 */
    suspend fun listCountries(): List<CountryDto> = dbQuery {
        Countries.selectAll()
            .orderBy(Countries.countryId to SortOrder.ASC)
            .map { it.toCountry() }
            .toList()
    }

    /** 查询区域列表。 */
    suspend fun listRegions(): List<RegionDto> = dbQuery {
        Regions.selectAll()
            .orderBy(Regions.regionId to SortOrder.ASC)
            .map { it.toRegion() }
            .toList()
    }
}

/** 将持久层数据转换为国家传输对象。 */
private fun ResultRow.toCountry() = CountryDto(
    countryId = this[Countries.countryId],
    countryName = this[Countries.countryName],
    regionId = this[Countries.regionId],
)

/** 将持久层数据转换为区域传输对象。 */
private fun ResultRow.toRegion() = RegionDto(
    regionId = this[Regions.regionId],
    regionName = this[Regions.regionName],
)
