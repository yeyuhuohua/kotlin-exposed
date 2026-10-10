package com.atguigu.hrspring.entity
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
@TableName("departments")
class DepartmentEntity {
    @TableId(value = "department_id", type = IdType.INPUT)
    var departmentId: Int = 0
    var departmentName: String = ""
    var managerId: Int? = null
    var locationId: Int? = null
}
