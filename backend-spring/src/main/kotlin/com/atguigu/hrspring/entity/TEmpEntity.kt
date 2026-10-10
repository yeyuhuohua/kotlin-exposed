package com.atguigu.hrspring.entity
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
@TableName("t_emp")
class TEmpEntity {
    @TableId(value = "id", type = IdType.AUTO)
    var id: Int = 0
    var name: String? = null
    var age: Int? = null

    @TableField("deptId")
    var deptId: Int? = null
    var empno: Int = 0
}
