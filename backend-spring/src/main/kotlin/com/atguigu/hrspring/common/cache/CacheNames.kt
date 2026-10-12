package com.atguigu.hrspring.common.cache

import com.atguigu.hrspring.common.api.PageParams

/**
 * Spring Cache 的缓存名。Redis 键再加 `hrs:` 前缀，避免和 Ktor 的 `hr:` 撞车。
 * 一次清空用 [org.springframework.cache.annotation.CacheEvict.allEntries]。
 */
object CacheNames {
    const val JOBS = "jobs"
    const val DEPARTMENTS = "departments"
    const val DEPARTMENT = "department"
    const val LOCATIONS = "locations"
    const val COUNTRIES = "countries"
    const val REGIONS = "regions"
    const val JOB_GRADES = "job-grades"
    const val JOB_HISTORY = "job-history"
    const val EMPLOYEES = "employees"
    const val EMPLOYEES_BY_DEPT = "employees-by-dept"
    const val EMPLOYEE = "employee"
    const val EMPLOYEE_DETAILS = "employee-details"
    const val EMP_DETAILS = "emp-details"
    const val OVERVIEW = "overview"
    const val T_DEPT = "t-dept"
    const val T_EMP = "t-emp"
    const val ORDERS = "orders"
    const val USERS = "users"
    const val ROLES = "roles"
    const val MENUS = "menus"
}

/** 查询串里的分隔符转义，避免不同筛选条件写成同一个缓存键。 */
object CacheKeys {
    @JvmStatic
    fun employees(
        page: PageParams,
        departmentId: Int?,
        jobId: String?,
        q: String?,
        unassignedOnly: Boolean,
    ): String {
        fun token(value: String?): String = value?.let { "${it.length}:$it" } ?: "N"
        return listOf(
            page.limit.toString(),
            page.offset.toString(),
            departmentId?.toString() ?: "-",
            token(jobId),
            token(q),
            unassignedOnly.toString(),
        ).joinToString(":")
    }

    @JvmStatic
    fun users(limit: Int?, offset: Long?): String {
        val size = (limit ?: 50).coerceIn(1, 200)
        val skip = (offset ?: 0L).coerceAtLeast(0L)
        return "$size:$skip"
    }
}

/** 本请求里第一次缓存读取的命中情况，给 X-Cache。嵌套读取不再覆盖。 */
object CacheHitMarker {
    private val hit = ThreadLocal<Boolean?>()

    fun markFirst(value: Boolean) {
        if (hit.get() == null) hit.set(value)
    }

    fun current(): Boolean = hit.get() == true

    fun clear() {
        hit.remove()
    }
}
