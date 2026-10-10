package com.atguigu.hrspring.entity
import com.baomidou.mybatisplus.annotation.TableName
@TableName("job_grades")
class JobGradeEntity {
    var gradeLevel: String? = null
    var lowestSal: Int? = null
    var highestSal: Int? = null
}
