package com.atguigu.hrspring.service
import com.atguigu.hrspring.common.api.ApiException
import com.atguigu.hrspring.common.api.Validation
import com.atguigu.hrspring.common.cache.CacheNames
import com.atguigu.hrspring.common.cache.RedisCache
import com.atguigu.hrspring.dto.LocationCreateRequest
import com.atguigu.hrspring.dto.LocationDto
import com.atguigu.hrspring.dto.LocationUpdateRequest
import com.atguigu.hrspring.entity.LocationEntity
import com.atguigu.hrspring.mapper.LocationMapper
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LocationService(
    private val locationMapper: LocationMapper,
    private val cache: RedisCache,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun list(): Pair<List<LocationDto>, Boolean> =
        cache.withCacheList(CacheNames.LOCATIONS, LocationDto::class.java) {
            locationMapper.selectList(QueryWrapper<LocationEntity>().orderByAsc("location_id")).map { it.toDto() }
        }

    @Transactional
    fun create(request: LocationCreateRequest): LocationDto {
        validateCreate(request)
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
        val created = locationMapper.selectById(request.locationId)?.toDto()
            ?: error("location insert missing row")
        cache.invalidateAfterCommit(listOf(CacheNames.LOCATIONS, CacheNames.OVERVIEW))
        return created
    }

    @Transactional
    fun update(id: Int, patch: LocationUpdateRequest): LocationDto {
        if (!patch.hasUpdates()) throw ApiException.badRequest("no fields to update")
        validateUpdate(patch)
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
        val updated = locationMapper.selectById(id)?.toDto()
            ?: throw ApiException.notFound("location not found")
        cache.invalidateAfterCommit(
            listOf(CacheNames.LOCATIONS, CacheNames.OVERVIEW),
            listOf(CacheNames.EMPLOYEE_PREFIX, CacheNames.EMP_DETAILS_PREFIX),
        )
        return updated
    }

    private fun validateCreate(request: LocationCreateRequest) {
        if (request.city.isBlank()) throw ApiException.badRequest("city is required")
        Validation.requireMaxLength(request.streetAddress, "streetAddress", 40)
        Validation.requireMaxLength(request.postalCode, "postalCode", 12)
        Validation.requireMaxLength(request.city, "city", 30)
        Validation.requireMaxLength(request.stateProvince, "stateProvince", 25)
        Validation.requireMaxLength(request.countryId, "countryId", 2)
    }

    private fun validateUpdate(patch: LocationUpdateRequest) {
        if (patch.city != null && patch.city.isBlank()) throw ApiException.badRequest("city must not be blank")
        Validation.requireMaxLength(patch.streetAddress, "streetAddress", 40)
        Validation.requireMaxLength(patch.postalCode, "postalCode", 12)
        Validation.requireMaxLength(patch.city, "city", 30)
        Validation.requireMaxLength(patch.stateProvince, "stateProvince", 25)
        Validation.requireMaxLength(patch.countryId, "countryId", 2)
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
