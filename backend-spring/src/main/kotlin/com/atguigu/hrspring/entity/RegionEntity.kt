package com.atguigu.hrspring.entity
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
@TableName("regions")
class RegionEntity {
    @TableId(value = "region_id", type = IdType.INPUT)
    var regionId: Int = 0
    var regionName: String? = null
}
