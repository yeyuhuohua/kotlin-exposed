package com.atguigu.hrspring.entity
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
@TableName("countries")
class CountryEntity {
    @TableId(value = "country_id", type = IdType.INPUT)
    var countryId: String = ""
    var countryName: String? = null
    var regionId: Int? = null
}
