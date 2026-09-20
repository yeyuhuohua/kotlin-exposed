package com.atguigu.hr.common.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 缓存 key 到分组是一张需要人工维护的对照表：分错组等于该模块的写操作
 * 清不掉旧缓存，改这里必须同步检查各业务模块的 `*Cache` 前缀。
 */
class RedisCacheGroupTest {
    @Test
    fun `把业务 key 映射到对应分组`() {
        assertSame(RedisCache.Group.EMPLOYEES, RedisCache.groupOf("hr:employees:list:abc"))
        assertSame(RedisCache.Group.EMPLOYEES, RedisCache.groupOf("hr:employees:dept:90"))
        assertSame(RedisCache.Group.EMPLOYEE, RedisCache.groupOf("hr:employee:100"))
        assertSame(RedisCache.Group.EMPLOYEE, RedisCache.groupOf("hr:employee:100:details"))
        assertSame(RedisCache.Group.EMP_DETAILS, RedisCache.groupOf("hr:emp-details:50:0"))
        assertSame(RedisCache.Group.DEPARTMENT, RedisCache.groupOf("hr:departments"))
        assertSame(RedisCache.Group.DEPARTMENT, RedisCache.groupOf("hr:department:10"))
        assertSame(RedisCache.Group.JOBS, RedisCache.groupOf("hr:jobs"))
        assertSame(RedisCache.Group.LOCATIONS, RedisCache.groupOf("hr:locations"))
        assertSame(RedisCache.Group.GEOGRAPHY, RedisCache.groupOf("hr:countries"))
        assertSame(RedisCache.Group.GEOGRAPHY, RedisCache.groupOf("hr:regions"))
        assertSame(RedisCache.Group.JOB_HISTORY, RedisCache.groupOf("hr:job-history"))
        assertSame(RedisCache.Group.JOB_GRADES, RedisCache.groupOf("hr:job-grades"))
        assertSame(RedisCache.Group.T_DEPT, RedisCache.groupOf("hr:t-dept"))
        assertSame(RedisCache.Group.T_EMP, RedisCache.groupOf("hr:t-emp"))
        assertSame(RedisCache.Group.ORDERS, RedisCache.groupOf("hr:orders"))
        assertSame(RedisCache.Group.OVERVIEW, RedisCache.groupOf("hr:overview"))
    }

    @Test
    fun `员工列表与员工实体不会串组`() {
        // hr:employee: 是 hr:employees: 的前缀，顺序写反会把实体当列表清理
        assertEquals(RedisCache.Group.EMPLOYEE, RedisCache.groupOf("hr:employee:1"))
        assertEquals(RedisCache.Group.EMPLOYEES, RedisCache.groupOf("hr:employees:list:1"))
    }

    @Test
    fun `未登记前缀不接受缓存与失效`() {
        assertNull(RedisCache.groupOf("hr:unknown"))
        assertNull(RedisCache.groupOf("other:key"))
    }

    @Test
    fun `每个分组都至少有一个清理模式`() {
        RedisCache.Group.entries.forEach { group ->
            assertTrue(group.patterns.isNotEmpty(), "${group.name} 缺少清理模式")
            group.patterns.forEach { pattern ->
                assertTrue(pattern.startsWith("hr:"), "${group.name} 的模式必须落在 hr: 命名空间")
            }
        }
    }
}
