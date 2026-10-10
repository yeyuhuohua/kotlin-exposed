package com.atguigu.hrspring.entity
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
@TableName("auth_roles")
class RoleEntity {
    @TableId(value = "code", type = IdType.INPUT)
    var code: String = ""
    var name: String = ""
    var enabled: Boolean = true
}
