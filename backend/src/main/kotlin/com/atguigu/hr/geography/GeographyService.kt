package com.atguigu.hr.geography

import com.atguigu.hr.common.cache.Cached
import com.atguigu.hr.common.cache.RedisCache

/** 协调国家与区域查询与缓存，写操作使用统一失效入口避免返回旧数据。 */

object GeographyService {
    suspend fun listCountries(): Cached<List<CountryDto>> =
        RedisCache.getOrLoad(GeographyCache.COUNTRIES) { GeographyRepository.listCountries() }

    suspend fun listRegions(): Cached<List<RegionDto>> =
        RedisCache.getOrLoad(GeographyCache.REGIONS) { GeographyRepository.listRegions() }
}
