package com.atguigu.hr.location

import com.atguigu.hr.config.DatabaseFactory.dbQuery
import com.atguigu.hr.config.DatabaseFactory.dbUpdate
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update

/** 办公地点持久层：使用 R2DBC 挂起事务，并在事务结束前收集查询结果。 */

object LocationRepository {
    /** 查询办公地点列表。 */
    suspend fun listLocations(): List<LocationDto> = dbQuery {
        Locations.selectAll()
            .orderBy(Locations.locationId to SortOrder.ASC)
            .map { it.toLocation() }
            .toList()
    }

    /** 查询办公地点。 */
    suspend fun findLocation(id: Int): LocationDto? = dbQuery {
        Locations.selectAll()
            .where { Locations.locationId eq id }
            .map { it.toLocation() }
            .firstOrNull()
    }

    /** 更新办公地点。 */
    suspend fun updateLocation(id: Int, patch: LocationUpdateRequest): LocationDto? = dbUpdate {
        val rows = Locations.update({ Locations.locationId eq id }) {
            patch.streetAddress?.let { value -> it[streetAddress] = value }
            patch.postalCode?.let { value -> it[postalCode] = value }
            patch.city?.let { value -> it[city] = value }
            patch.stateProvince?.let { value -> it[stateProvince] = value }
            patch.countryId?.let { value -> it[countryId] = value }
        }
        if (rows == 0) null else loadLocation(id)
    }

    /** 新增办公地点。 */
    suspend fun createLocation(body: LocationCreateRequest): LocationDto = dbUpdate {
        Locations.insert {
            it[locationId] = body.locationId
            it[streetAddress] = body.streetAddress
            it[postalCode] = body.postalCode
            it[city] = body.city
            it[stateProvince] = body.stateProvince
            it[countryId] = body.countryId
        }
        loadLocation(body.locationId) ?: error("location insert missing row")
    }

    /** 加载办公地点。 */
    private suspend fun loadLocation(id: Int): LocationDto? =
        Locations.selectAll()
            .where { Locations.locationId eq id }
            .map { it.toLocation() }
            .firstOrNull()
}

/** 将持久层数据转换为办公地点传输对象。 */
private fun ResultRow.toLocation() = LocationDto(
    locationId = this[Locations.locationId],
    streetAddress = this[Locations.streetAddress],
    postalCode = this[Locations.postalCode],
    city = this[Locations.city],
    stateProvince = this[Locations.stateProvince],
    countryId = this[Locations.countryId],
)
