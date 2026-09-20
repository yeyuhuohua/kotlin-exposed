package com.atguigu.hr.demo.tdept

/** 仅供 OpenAPI 文档展示的示例部门样例，不参与数据库初始化或业务计算。 */

internal val sampleTDept = TDeptDto(1, "华山", "西安")
internal val sampleTDeptPatch = TDeptUpdateRequest(deptName = "华山", address = "西安")
internal val sampleTDeptCreate = TDeptCreateRequest(deptName = "恒山", address = "大同")
