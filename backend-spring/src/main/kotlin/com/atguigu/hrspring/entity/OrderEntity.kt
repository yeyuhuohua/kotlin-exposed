package com.atguigu.hrspring.entity
import com.baomidou.mybatisplus.annotation.TableName
@TableName("`order`")
class OrderEntity {
    var orderId: Int? = null
    var orderName: String? = null
}
