package com.atguigu.hr.audit

/** 仅供 OpenAPI 文档展示的审计日志样例，不参与数据库初始化或业务计算。 */

internal val sampleLoginRecord = LoginRecordDto(
    id = 12,
    username = "admin",
    userId = 1,
    ip = "127.0.0.1",
    userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)",
    success = true,
    errorCode = null,
    createdAt = "2026-09-23T09:30:11",
)

internal val sampleLoginRecordFailed = sampleLoginRecord.copy(
    id = 11,
    userId = null,
    success = false,
    errorCode = "invalid_credentials",
    createdAt = "2026-09-23T09:28:47",
)

internal val sampleApiCallRecord = ApiCallRecordDto(
    id = 1024,
    userId = 1,
    username = "admin",
    method = "GET",
    path = "/api/employees",
    queryString = "limit=50&offset=0",
    statusCode = 200,
    durationMs = 23,
    ip = "127.0.0.1",
    userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)",
    createdAt = "2026-09-23T09:31:02",
)
