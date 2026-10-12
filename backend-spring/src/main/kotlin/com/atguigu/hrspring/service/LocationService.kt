package com.atguigu.hrspring.service
import com.atguigu.hrspring.common.api.ApiException
import com.atguigu.hrspring.common.cache.CacheNames
import com.atguigu.hrspring.dto.LocationCreateRequest
import com.atguigu.hrspring.dto.LocationDto
import com.atguigu.hrspring.dto.LocationUpdateRequest
import com.atguigu.hrspring.entity.LocationEntity
import com.atguigu.hrspring.mapper.LocationMapper
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated

@Validated
@Service
class LocationService(
    private val locationMapper: LocationMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Cacheable(cacheNames = [CacheNames.LOCATIONS], key = "'all'")
    @Transactional(readOnly = true)
    fun list(): List<LocationDto> =
        locationMapper.selectList(QueryWrapper<LocationEntity>().orderByAsc("location_id")).map { it.toDto() }

    @CacheEvict(cacheNames = [CacheNames.LOCATIONS, CacheNames.OVERVIEW], allEntries = true)
    @Transactional
    fun create(@Valid request: LocationCreateRequest): LocationDto {
        val entity = LocationEntity().apply {
            locationId = request.locationId
            streetAddress = request.streetAddress
            postalCode = request.postalCode
            city = request.city
            stateProvince = request.stateProvince
            countryId = request.countryId
        }
        try {
            locationMapper.insert(entity)
        } catch (e: DataIntegrityViolationException) {
            log.warn("location create conflict: {}", e.message)
            throw ApiException.conflict("create failed")
        }
        return locationMapper.selectById(request.locationId)?.toDto()
            ?: error("location insert missing row")
    }

    @CacheEvict(
        cacheNames = [
            CacheNames.LOCATIONS,
            CacheNames.EMPLOYEE,
            CacheNames.EMPLOYEE_DETAILS,
            CacheNames.EMP_DETAILS,
            CacheNames.OVERVIEW,
        ],
        allEntries = true,
    )
    @Transactional
    fun update(id: Int, @Valid patch: LocationUpdateRequest): LocationDto {
        if (locationMapper.selectById(id) == null) {
            throw ApiException.notFound("location not found")
        }
        val wrapper = UpdateWrapper<LocationEntity>().eq("location_id", id)
        patch.streetAddress?.let { wrapper.set("street_address", it) }
        patch.postalCode?.let { wrapper.set("postal_code", it) }
        patch.city?.let { wrapper.set("city", it) }
        patch.stateProvince?.let { wrapper.set("state_province", it) }
        patch.countryId?.let { wrapper.set("country_id", it) }
        try {
            locationMapper.update(null, wrapper)
        } catch (e: DataIntegrityViolationException) {
            log.warn("location update conflict: {}", e.message)
            throw ApiException.conflict("update failed")
        }
        return locationMapper.selectById(id)?.toDto()
            ?: throw ApiException.notFound("location not found")
    }
}

private fun LocationEntity.toDto() = LocationDto(
    locationId = locationId,
    streetAddress = streetAddress,
    postalCode = postalCode,
    city = city,
    stateProvince = stateProvince,
    countryId = countryId,
)
