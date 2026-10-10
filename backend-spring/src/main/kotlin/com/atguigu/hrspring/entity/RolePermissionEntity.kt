package com.atguigu.hrspring.entity
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

@TableName("auth_role_permissions")
class RolePermissionEntity {
    var roleCode: String = ""

    /** 复合主键的写入锚点。查询和删除必须用 Wrapper，不要 selectById。 */
    @TableId(value = "permission_code", type = IdType.INPUT)
    var permissionCode: String = ""
}
