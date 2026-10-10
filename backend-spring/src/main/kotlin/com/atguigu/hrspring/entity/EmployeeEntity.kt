package com.atguigu.hrspring.entity
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDate
@TableName("employees")
class EmployeeEntity {
    @TableId(value = "employee_id", type = IdType.INPUT)
    var employeeId: Int = 0
    var firstName: String? = null
    var lastName: String = ""
    var email: String = ""
    var phoneNumber: String? = null
    var hireDate: LocalDate = LocalDate.of(1970, 1, 1)
    var jobId: String = ""
    var salary: Double? = null
    var commissionPct: Double? = null
    var managerId: Int? = null
    var departmentId: Int? = null
}
