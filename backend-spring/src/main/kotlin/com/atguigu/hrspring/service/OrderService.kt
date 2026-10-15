package com.atguigu.hrspring.service
import com.atguigu.hrspring.common.cache.CacheNames
import com.atguigu.hrspring.dto.OrderDto
import com.atguigu.hrspring.entity.OrderEntity
import com.atguigu.hrspring.mapper.OrderMapper
import com.baomidou.mybatisplus.extension.kotlin.KtQueryWrapper
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 示例订单只读，整表一个缓存项。 */
@Service
class OrderService(
    private val orderMapper: OrderMapper,
) {
    @Cacheable(cacheNames = [CacheNames.ORDERS], key = "'all'")
    @Transactional(readOnly = true)
    fun list(): List<OrderDto> =
        orderMapper.selectList(KtQueryWrapper(OrderEntity::class.java)).map { it.toDto() }

    private fun OrderEntity.toDto() = OrderDto(
        orderId = orderId,
        orderName = orderName,
    )
}
