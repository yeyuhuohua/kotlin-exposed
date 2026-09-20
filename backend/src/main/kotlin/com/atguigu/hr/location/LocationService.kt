package com.atguigu.hr.location

import com.atguigu.hr.common.cache.Cached
import com.atguigu.hr.common.cache.RedisCache
import com.atguigu.hr.employee.EmployeeCache
import com.atguigu.hr.overview.OverviewCache

/** 协调办公地点查询与缓存，写操作使用统一失效入口避免返回旧数据。 */

object LocationService {
    suspend fun listLocations(): Cached<List<LocationDto>> =
        RedisCache.getOrLoad(LocationCache.LOCATIONS) { LocationRepository.listLocations() }

    suspend fun updateLocation(id: Int, patch: LocationUpdateRequest): LocationDto? = RedisCache.withInvalidation(
        LocationCache.LOCATIONS,
        EmployeeCache.EMPLOYEE_PREFIX,
        EmployeeCache.EMP_DETAILS_PREFIX,
        OverviewCache.OVERVIEW,
    ) {
        LocationRepository.updateLocation(id, patch)
    }

    suspend fun createLocation(body: LocationCreateRequest): LocationDto = RedisCache.withInvalidation(
        LocationCache.LOCATIONS,
        OverviewCache.OVERVIEW,
    ) {
        LocationRepository.createLocation(body)
    }
}
