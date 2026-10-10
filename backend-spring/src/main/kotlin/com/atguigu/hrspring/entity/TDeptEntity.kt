package com.atguigu.hrspring.entity
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
@TableName("t_dept")
class TDeptEntity {
    @TableId(value = "id", type = IdType.AUTO)
    var id: Int = 0

    @TableField("deptName")
    var deptName: String? = null
    var address: String? = null
}
