package com.atguigu.hrspring.common.cache

/**
 * Spring 侧缓存键。前缀与 Ktor 的 hr: 分开，两边序列化格式不同，不能共用同一批 key。
 */
object CacheNames {
    const val JOBS = "hrs:jobs"
    const val DEPARTMENTS = "hrs:departments"
    fun department(id: Int) = "hrs:department:$id"
    const val LOCATIONS = "hrs:locations"
    const val COUNTRIES = "hrs:countries"
    const val REGIONS = "hrs:regions"
    const val JOB_GRADES = "hrs:job-grades"
    const val JOB_HISTORY_PREFIX = "hrs:job-history:"
    const val EMPLOYEE_PREFIX = "hrs:employee:"
    const val EMPLOYEES_PREFIX = "hrs:employees:"
    const val EMP_DETAILS_PREFIX = "hrs:emp-details:"
    const val OVERVIEW = "hrs:overview"
    const val T_DEPT = "hrs:t-dept"
    const val T_EMP_PREFIX = "hrs:t-emp:"
    const val ORDERS_PREFIX = "hrs:orders:"
    const val USERS_PREFIX = "hrs:users:"
    const val ROLES = "hrs:roles"
    const val MENUS = "hrs:menus"

    fun employee(id: Int) = "hrs:employee:$id"
    fun employeeDetails(id: Int) = "hrs:employee:$id:details"
    fun employeesDept(departmentId: Int) = "hrs:employees:dept:$departmentId"
}
