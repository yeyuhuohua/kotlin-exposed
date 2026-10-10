package com.atguigu.hrspring.entity
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
@TableName("locations")
class LocationEntity {
    @TableId(value = "location_id", type = IdType.INPUT)
    var locationId: Int = 0
    var streetAddress: String? = null
    var postalCode: String? = null
    var city: String = ""
    var stateProvince: String? = null
    var countryId: String? = null
}
