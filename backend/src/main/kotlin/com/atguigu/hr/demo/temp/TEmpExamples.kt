package com.atguigu.hr.demo.temp

/** 仅供 OpenAPI 文档展示的示例人员样例，不参与数据库初始化或业务计算。 */

internal val sampleTEmp = TEmpDto(1, "令狐冲", 24, 1, 1001)
internal val sampleTEmpPatch = TEmpUpdateRequest(name = "令狐冲", age = 24, deptId = 1, empno = 1001)
internal val sampleTEmpCreate = TEmpCreateRequest(name = "仪琳", age = 18, deptId = 1, empno = 2001)
