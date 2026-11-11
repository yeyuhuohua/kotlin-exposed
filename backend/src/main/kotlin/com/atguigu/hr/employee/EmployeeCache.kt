package com.atguigu.hr.employee

import com.atguigu.hr.common.cache.RedisCache
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest

object EmployeeCache {
    /** 返回单员工缓存使用的 Redis 键。 */
    fun employee(id: Int) = "hr:employee:$id"
    /** 返回员工详情缓存使用的 Redis 键。 */
    fun employeeDetails(id: Int) = "hr:employee:$id:details"
    /** 返回员工分页条件对应的列表缓存键。 */
    fun employeesList(
        limit: Int,
        offset: Long,
        departmentId: Int?,
        jobId: String?,
        q: String?,
        unassignedOnly: Boolean = false,
    ): String {
        val payload = JsonObject(
            mapOf(
                "limit" to JsonPrimitive(limit),
                "offset" to JsonPrimitive(offset),
                "departmentId" to (departmentId?.let(::JsonPrimitive) ?: JsonNull),
                "jobId" to (jobId?.let(::JsonPrimitive) ?: JsonNull),
                "q" to (q?.let(::JsonPrimitive) ?: JsonNull),
                "unassignedOnly" to JsonPrimitive(unassignedOnly),
            ),
        ).toString()
        return "hr:employees:list:${sha256Hex(payload)}"
    }

    /** 返回部门员工列表缓存使用的 Redis 键。 */
    fun employeesDept(departmentId: Int) = "hr:employees:dept:$departmentId"
    /** 返回员工详情视图分页缓存使用的 Redis 键。 */
    fun empDetails(limit: Int, offset: Long) = "hr:emp-details:$limit:$offset"

    const val EMPLOYEE_PREFIX = "hr:employee:"
    const val EMPLOYEES_PREFIX = "hr:employees:"
    const val EMP_DETAILS_PREFIX = "hr:emp-details:"

    /** 使指定员工的实体和详情缓存失效。 */
    suspend fun evictEmployee(id: Int) {
        RedisCache.evict(employee(id))
        RedisCache.evict(employeeDetails(id))
    }

    /** 使指定部门的员工列表缓存失效。 */
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

/** 计算筛选条件的 SHA-256 十六进制摘要，缩短缓存键。 */
private fun sha256Hex(text: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}
