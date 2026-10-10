package com.atguigu.hrspring.entity
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
@TableName("auth_users")
class UserEntity {
    @TableId(value = "id", type = IdType.AUTO)
    var id: Int = 0
    var username: String = ""
    var passwordHash: String = ""
    var roleCode: String = ""
    var enabled: Boolean = true
    var tokenVersion: Int = 0
}
