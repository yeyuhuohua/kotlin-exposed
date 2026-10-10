package com.atguigu.hrspring.entity
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
@TableName("auth_role_permission_profiles")
class RolePermissionProfileEntity {
    @TableId(value = "role_code", type = IdType.INPUT)
    var roleCode: String = ""
    var revision: Int = 0
}
