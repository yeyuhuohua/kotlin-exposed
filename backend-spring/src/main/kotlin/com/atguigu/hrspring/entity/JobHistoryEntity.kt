package com.atguigu.hrspring.entity
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDate
@TableName("job_history")
class JobHistoryEntity {
    var employeeId: Int = 0
    var startDate: LocalDate = LocalDate.of(1970, 1, 1)
    var endDate: LocalDate = LocalDate.of(1970, 1, 1)
    var jobId: String = ""
    var departmentId: Int? = null
}
