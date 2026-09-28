package com.atguigu.hr.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 地点各文本列的长度校验：streetAddress 40 / postalCode 12 / city 30 / stateProvince 25 / countryId 2。 */
class LocationModelsTest {
    @Test
    fun `更新请求按列上限校验`() {
        assertEquals(
            "streetAddress must be at most 40 characters",
            LocationUpdateRequest(streetAddress = "a".repeat(41)).contentError(),
        )
        assertEquals(
            "postalCode must be at most 12 characters",
            LocationUpdateRequest(postalCode = "1".repeat(13)).contentError(),
        )
        assertEquals(
            "city must be at most 30 characters",
            LocationUpdateRequest(city = "a".repeat(31)).contentError(),
        )
        assertEquals(
            "stateProvince must be at most 25 characters",
            LocationUpdateRequest(stateProvince = "a".repeat(26)).contentError(),
        )
        assertEquals(
            "countryId must be at most 2 characters",
            LocationUpdateRequest(countryId = "USA").contentError(),
        )
        assertNull(
            LocationUpdateRequest(
                streetAddress = "a".repeat(40),
                postalCode = "1".repeat(12),
                city = "a".repeat(30),
                stateProvince = "a".repeat(25),
                countryId = "US",
            ).contentError(),
        )
    }

    @Test
    fun `新增请求按列上限校验且城市必填`() {
        assertEquals("city is required", create(city = " ").contentError())
        assertEquals("city must be at most 30 characters", create(city = "𠮷" + "a".repeat(30)).contentError())
        assertNull(create(city = "𠮷" + "a".repeat(29)).contentError())
        assertNull(create(city = "Hangzhou").contentError())
    }

    private fun create(city: String) = LocationCreateRequest(locationId = 3300, city = city)
}
