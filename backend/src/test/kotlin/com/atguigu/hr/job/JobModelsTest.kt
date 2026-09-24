package com.atguigu.hr.job

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 岗位薪资区间校验：只改一端时必须与库里的另一端现值合并比对。 */
class JobModelsTest {
    private val current = JobDto(jobId = "KT_DEV", jobTitle = "Kotlin Developer", minSalary = 4000, maxSalary = 9000)

    @Test
    fun `只调低最高薪资到低于现有最低薪资时被拦截`() {
        val patch = JobUpdateRequest(maxSalary = 3000)
        assertEquals("minSalary must not exceed maxSalary", patch.rangeError(current))
    }

    @Test
    fun `只调高最低薪资到高于现有最高薪资时被拦截`() {
        val patch = JobUpdateRequest(minSalary = 10000)
        assertEquals("minSalary must not exceed maxSalary", patch.rangeError(current))
    }

    @Test
    fun `只改一端的合法更新放行`() {
        assertNull(JobUpdateRequest(minSalary = 5000).rangeError(current))
        assertNull(JobUpdateRequest(maxSalary = 12000).rangeError(current))
        assertNull(JobUpdateRequest(jobTitle = "Senior Kotlin Developer").rangeError(current))
    }

    @Test
    fun `两端同时提交时按新值比对`() {
        assertNull(JobUpdateRequest(minSalary = 10000, maxSalary = 15000).rangeError(current))
        assertEquals(
            "minSalary must not exceed maxSalary",
            JobUpdateRequest(minSalary = 15000, maxSalary = 10000).rangeError(current),
        )
    }

    @Test
    fun `现值缺少一端时无法构成区间直接放行`() {
        val open = current.copy(maxSalary = null)
        assertNull(JobUpdateRequest(minSalary = 10000).rangeError(open))
    }

    @Test
    fun `新增时两端都在请求里直接校验`() {
        assertNull(JobCreateRequest(jobId = "KT_DEV", jobTitle = "Kotlin Developer").contentError())
        assertEquals(
            "minSalary must not exceed maxSalary",
            JobCreateRequest(jobId = "KT_DEV", jobTitle = "Kotlin Developer", minSalary = 9000, maxSalary = 4000)
                .contentError(),
        )
        assertEquals(
            "jobId and jobTitle are required",
            JobCreateRequest(jobId = " ", jobTitle = "Kotlin Developer").contentError(),
        )
    }
}
