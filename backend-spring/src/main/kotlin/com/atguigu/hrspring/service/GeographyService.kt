package com.atguigu.hrspring.service
import com.atguigu.hrspring.common.cache.CacheNames
import com.atguigu.hrspring.dto.CountryDto
import com.atguigu.hrspring.dto.RegionDto
import com.atguigu.hrspring.entity.CountryEntity
import com.atguigu.hrspring.entity.RegionEntity
import com.atguigu.hrspring.mapper.CountryMapper
import com.atguigu.hrspring.mapper.RegionMapper
import com.baomidou.mybatisplus.extension.kotlin.KtQueryWrapper
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GeographyService(
    private val countryMapper: CountryMapper,
    private val regionMapper: RegionMapper,
) {
    @Cacheable(cacheNames = [CacheNames.COUNTRIES], key = "'all'")
    @Transactional(readOnly = true)
    fun listCountries(): List<CountryDto> =
        countryMapper.selectList(KtQueryWrapper(CountryEntity::class.java).orderByAsc(CountryEntity::countryId)).map { it.toDto() }

    @Cacheable(cacheNames = [CacheNames.REGIONS], key = "'all'")
    @Transactional(readOnly = true)
    fun listRegions(): List<RegionDto> =
        regionMapper.selectList(KtQueryWrapper(RegionEntity::class.java).orderByAsc(RegionEntity::regionId)).map { it.toDto() }
}

private fun CountryEntity.toDto() = CountryDto(
    countryId = countryId,
    countryName = countryName,
    regionId = regionId,
)

private fun RegionEntity.toDto() = RegionDto(
    regionId = checkNotNull(regionId),
    regionName = regionName,
)
