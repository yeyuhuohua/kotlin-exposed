package com.atguigu.hrspring.entity
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
@TableName("jobs")
class JobEntity {
    @TableId(value = "job_id", type = IdType.INPUT)
    var jobId: String = ""
    var jobTitle: String = ""
    var minSalary: Int? = null
    var maxSalary: Int? = null
}
