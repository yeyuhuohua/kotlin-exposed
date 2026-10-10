package com.atguigu.hrspring.entity
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
@TableName("auth_menus")
class MenuEntity {
    @TableId(value = "`key`", type = IdType.INPUT)
    var key: String = ""
    var title: String = ""
    var path: String = ""
    var icon: String? = null
    var groupLabel: String = ""
    var sort: Int = 0
    var adminOnly: Boolean = false
    var builtin: Boolean = false
    var enabled: Boolean = true
}
