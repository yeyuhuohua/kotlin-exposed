package com.atguigu.hr.location

/** 仅供 OpenAPI 文档展示的办公地点样例，不参与数据库初始化或业务计算。 */

internal val sampleLocation = LocationDto(1700, "2004 Charade Rd", "98199", "Seattle", "Washington", "US")
internal val sampleLocationPatch = LocationUpdateRequest(
    streetAddress = "2004 Charade Rd",
    postalCode = "98199",
    city = "Seattle",
    stateProvince = "Washington",
    countryId = "US",
)
internal val sampleLocationCreate = LocationCreateRequest(
    locationId = 3300,
    streetAddress = "1 Kotlin Rd",
    postalCode = "00000",
    city = "Hangzhou",
    stateProvince = "Zhejiang",
    countryId = "US",
)
