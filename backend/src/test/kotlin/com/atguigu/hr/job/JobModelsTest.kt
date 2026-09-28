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

    @Test
    fun `薪资不能为负`() {
        assertEquals(
            "minSalary must be non-negative",
            JobUpdateRequest(minSalary = -100).contentError(),
        )
        assertEquals(
            "maxSalary must be non-negative",
            JobUpdateRequest(maxSalary = -1).contentError(),
        )
        assertNull(JobUpdateRequest(minSalary = 0).contentError())
        assertEquals(
            "minSalary must be non-negative",
            JobCreateRequest(jobId = "KT_DEV", jobTitle = "Kotlin Developer", minSalary = -100, maxSalary = 1000)
                .contentError(),
        )
        assertNull(JobCreateRequest(jobId = "KT_DEV", jobTitle = "Kotlin Developer", minSalary = 0).contentError())
    }

    @Test
    fun `文本长度不能超过列上限`() {
        assertEquals(
            "jobTitle must be at most 35 characters",
            JobUpdateRequest(jobTitle = "a".repeat(36)).contentError(),
        )
        assertNull(JobUpdateRequest(jobTitle = "a".repeat(35)).contentError())
        assertEquals(
            "jobTitle must be at most 35 characters",
            JobCreateRequest(jobId = "KT_DEV", jobTitle = "a".repeat(36)).contentError(),
        )
        assertEquals(
            "jobId must be at most 10 characters",
            JobCreateRequest(jobId = "K".repeat(11), jobTitle = "Kotlin Developer").contentError(),
        )
        assertNull(JobCreateRequest(jobId = "K".repeat(10), jobTitle = "a".repeat(35)).contentError())
    }

    @Test
    fun `岗位名称长度按 Unicode 码点计数`() {
        // 𠮷 + 34 个普通字符共 35 个码点应放行；36 个码点才拒绝
        assertNull(JobUpdateRequest(jobTitle = "𠮷" + "a".repeat(34)).contentError())
        assertEquals(
            "jobTitle must be at most 35 characters",
            JobUpdateRequest(jobTitle = "𠮷" + "a".repeat(35)).contentError(),
        )
    }

    @Test
    fun `点号编码会被路径规范化，创建时必须拒绝`() {
        // /api/jobs/. 会变成 /api/jobs/，/api/jobs/.. 会变成 /api/，创建后永远编辑不到
        assertEquals(
            "jobId must not be '.' or '..'",
            JobCreateRequest(jobId = ".", jobTitle = "Dot").contentError(),
        )
        assertEquals(
            "jobId must not be '.' or '..'",
            JobCreateRequest(jobId = "..", jobTitle = "Dots").contentError(),
        )
        assertNull(JobCreateRequest(jobId = "...", jobTitle = "Dots").contentError())
        assertNull(JobCreateRequest(jobId = "A.B", jobTitle = "Dot Inside").contentError())
    }
}
