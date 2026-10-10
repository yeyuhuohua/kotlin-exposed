package com.atguigu.hrspring.service
import com.atguigu.hrspring.common.cache.CacheNames
import com.atguigu.hrspring.common.cache.RedisCache
import com.atguigu.hrspring.dto.CountryDto
import com.atguigu.hrspring.dto.RegionDto
import com.atguigu.hrspring.entity.CountryEntity
import com.atguigu.hrspring.entity.RegionEntity
import com.atguigu.hrspring.mapper.CountryMapper
import com.atguigu.hrspring.mapper.RegionMapper
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GeographyService(
    private val countryMapper: CountryMapper,
    private val regionMapper: RegionMapper,
    private val cache: RedisCache,
) {
    @Transactional(readOnly = true)
    fun listCountries(): Pair<List<CountryDto>, Boolean> =
        cache.withCacheList(CacheNames.COUNTRIES, CountryDto::class.java) {
            countryMapper.selectList(QueryWrapper<CountryEntity>().orderByAsc("country_id")).map { it.toDto() }
        }

    @Transactional(readOnly = true)
    fun listRegions(): Pair<List<RegionDto>, Boolean> =
        cache.withCacheList(CacheNames.REGIONS, RegionDto::class.java) {
            regionMapper.selectList(QueryWrapper<RegionEntity>().orderByAsc("region_id")).map { it.toDto() }
        }
}

private fun CountryEntity.toDto() = CountryDto(
    countryId = countryId,
    countryName = countryName,
    regionId = regionId,
)

private fun RegionEntity.toDto() = RegionDto(
    regionId = regionId,
    regionName = regionName,
)
