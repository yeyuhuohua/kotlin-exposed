package com.atguigu.hr.employee

import com.atguigu.hr.common.cache.RedisCache
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest

object EmployeeCache {
    fun employee(id: Int) = "hr:employee:$id"
    fun employeeDetails(id: Int) = "hr:employee:$id:details"
    fun employeesList(limit: Int, offset: Long, departmentId: Int?, jobId: String?, q: String?): String {
        val payload = JsonObject(
            mapOf(
                "limit" to JsonPrimitive(limit),
                "offset" to JsonPrimitive(offset),
                "departmentId" to (departmentId?.let(::JsonPrimitive) ?: JsonNull),
                "jobId" to (jobId?.let(::JsonPrimitive) ?: JsonNull),
                "q" to (q?.let(::JsonPrimitive) ?: JsonNull),
            ),
        ).toString()
        return "hr:employees:list:${sha256Hex(payload)}"
    }

    fun employeesDept(departmentId: Int) = "hr:employees:dept:$departmentId"
    fun empDetails(limit: Int, offset: Long) = "hr:emp-details:$limit:$offset"

    const val EMPLOYEE_PREFIX = "hr:employee:"
    const val EMPLOYEES_PREFIX = "hr:employees:"
    const val EMP_DETAILS_PREFIX = "hr:emp-details:"

    suspend fun evictEmployee(id: Int) {
        RedisCache.evict(employee(id))
        RedisCache.evict(employeeDetails(id))
    }

    suspend fun evictDept(departmentId: Int) {
        RedisCache.evict(employeesDept(departmentId))
    }

    /** 列表无法局部更新，整组丢掉，下次 GET 会 MISS 回源。 */
    suspend fun evictLists() {
        RedisCache.evictByPrefix(EMPLOYEES_PREFIX)
        RedisCache.evictByPrefix(EMP_DETAILS_PREFIX)
    }

    /** 部门/岗位/地点变更后，JOIN 详情与详情列表都要丢掉。 */
    suspend fun evictJoinDetails() {
        RedisCache.evictMatching("hr:employee:*:details")
        RedisCache.evictByPrefix(EMP_DETAILS_PREFIX)
    }
}

private fun sha256Hex(text: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}
