package com.atguigu.hrspring.entity
import com.baomidou.mybatisplus.annotation.TableName
@TableName("emp_details_view")
class EmpDetailsViewEntity {
    var employeeId: Int = 0
    var jobId: String = ""
    var managerId: Int? = null
    var departmentId: Int? = null
    var locationId: Int? = null
    var countryId: String? = null
    var firstName: String? = null
    var lastName: String = ""
    var salary: Double? = null
    var commissionPct: Double? = null
    var departmentName: String = ""
    var jobTitle: String = ""
    var city: String = ""
    var stateProvince: String? = null
    var countryName: String? = null
    var regionName: String? = null
}
