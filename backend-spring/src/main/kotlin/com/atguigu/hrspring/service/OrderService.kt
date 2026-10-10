package com.atguigu.hrspring.service
import com.atguigu.hrspring.common.cache.RedisCache
import com.atguigu.hrspring.dto.OrderDto
import com.atguigu.hrspring.entity.OrderEntity
import com.atguigu.hrspring.mapper.OrderMapper
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 示例订单只读。Ktor 是整表一个键，不用 [com.atguigu.hrspring.common.cache.CacheNames.ORDERS_PREFIX] 的分页键。
 */
@Service
class OrderService(
    private val orderMapper: OrderMapper,
    private val redisCache: RedisCache,
) {
    @Transactional(readOnly = true)
    fun list(): Pair<List<OrderDto>, Boolean> =
        redisCache.withCacheList(ORDERS, OrderDto::class.java) {
            orderMapper.selectList(QueryWrapper<OrderEntity>()).map { it.toDto() }
        }

    private fun OrderEntity.toDto() = OrderDto(
        orderId = orderId,
        orderName = orderName,
    )

    private companion object {
        const val ORDERS = "hrs:orders"
    }
}
